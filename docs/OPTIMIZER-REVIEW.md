# Review der Optimierer von Grund auf (Sensi-Finder und Curve-Finder)

Stand: 2026-10-17, Branch `dev` (`c8dd4f3`). Geprüft wird die Methode, nicht nur der Code. Alle Zahlen stammen aus dem Simulator
(`core/build/reports/harness/review-optimizer-e1.txt` … `e5.txt`, erzeugt von einer lokalen, nicht eingecheckten Testklasse
`OptimizerReviewTest`) oder aus vorhandenen Reports und `docs/ASSESSMENT.md`. Echte Spielerdaten gibt es nur von einem Spieler,
deshalb sind die Aussagen zum Menschen Folgerungen aus der Literatur, keine Messung.

## Kurzfazit

- **Sensi-Finder: behalten, an zwei Stellen reparieren.** Für einen einzelnen Wert wie cm/360 ist Bayes-Optimierung über
  einen verrauschten Score das richtige Werkzeug. "Gezielt an Schwächen" lässt sich ein einzelner Wert kaum sinnvoller suchen.
  Die Schwachstellen liegen woanders. Der Score ist um das Optimum sehr flach (±15 % kosten unter 1 Punkt). Das Zusammenführen
  der Sessions ignoriert Drift: Wandert das Optimum um 5–10 % pro Session, liegt das Ergebnis im Median 14 % daneben.
- **Curve-Finder: Grundannahme und Auswertung reparieren, kein ML-Neubau.** Die Kalibrierung ist im Ansatz schon diagnostisch
  (Fehler je Geschwindigkeit → gewünschter Gain). Drei Fehler von Grund auf machen sie aber schwach:
  1. Die Kalibrierbewegungen decken nur 4,5–8,3 in/s ab, die exportierte Kurve gilt bis über 40 in/s. Die Kurvenfamilien
     extrapolieren deshalb stark: Bei +15 % Präferenz empfiehlt die Pipeline 1,38 statt 1,06 bei 40 in/s, und die empfohlenen
     Kurven liegen im Mittel weiter von der Wahrheit weg als gar keine Kurve.
  2. Das Binning nach gemessener Geschwindigkeit hat einen eingebauten Bias. Innerhalb gleicher Zieldistanz korreliert der
     Fehler mit der Geschwindigkeit mit r = 0,36. Das macht die Erkennung asymmetrisch.
  3. Simulator und Analyse nehmen an, dass sich der Spieler nie an den Gain anpasst. Für Menschen stimmt das nicht.
- Die blinde Stärkensuche ist ein generischer 1-D-Suchlauf ohne Bezug zu den gemessenen Fehlern. Bei flachen Spielern schließt
  sie in 5 von 16 Läufen "keine Kurve" aus.
- **ML/RL lohnt sich mit den realistisch erreichbaren Daten nicht.** Ein diagnostischer geschlossener Kreis
  (messen → gedämpft korrigieren → anpassen lassen → wieder messen) mit Lookup-Table-Export ist der bessere Weg.

## 1. Sensi-Finder

**Was er tut.** Ein GP (Matern 5/2) über ln(cm/360) mit Offsets pro Session und Szenario und einem linearen Trend innerhalb
einer Session. Die Hyperparameter kommen aus einem Grid über die Marginal Likelihood. Die nächste Sensitivity wählt zuerst ein
gestreutes Design, dann Expected Improvement, mit Wiederholungen. Das Optimum ist das Maximum der Posterior-Mittelwerte.
Intervall und Konfidenz kommen aus Posterior-Draws. Der Score besteht aus Φ(z)-Werten von Accuracy, Time-to-hit, Tracking und
Flash, gewichtet, mit z über den gesamten Pool je Szenariogruppe.

**Ergibt das Sinn?** Ja, für die Frage "welcher Wert spielt sich im Trainer am besten".
- Sensitivity ist ein einzelner Skalar, der Score ist laut (Runden-SD etwa 2,4 Punkte), und jede Runde kostet 45 s. Genau
  dafür ist rauschrobuste BO gemacht.
- "Gezielt an den Schwächen" würde heißen, aus Über- und Unterschießen die Richtung abzuleiten. Bei der *linearen*
  Sensitivity hilft das wenig. Der Spieler ist an seine aktuelle Sensitivity angepasst, systematische Fehler sind dort nahe
  null, und nach jeder Änderung passt er sich in wenigen Dutzend Bewegungen neu an (Smith et al. 2006: schneller Prozess
  etwa 2, langsamer etwa 125 Trials). Ein Fehlervorzeichen sagt deshalb wenig darüber, welche Sensitivity *nach* der
  Anpassung besser ist. Ein Outcome-Optimierer ist hier richtig.

**Wo es hakt.**
1. **Flaches Optimum.** Laut `assess-precision` kosten ×0,85 0,9 Punkte, ×1,15 0,5 Punkte und ×1,30 1,7 Punkte. Die Literatur
   zeigt dasselbe Plateau (arXiv 2203.12050: bestes Ergebnis über 20–80 cm/360, also Faktor 4). Ein Punktwert ist deshalb
   weniger wert als das Plateau, auf dem der Spieler nach Gefühl wählen darf. Die App zeigt zwar ein Intervall, kommuniziert
   aber eine Zahl.
2. **Validität für Valorant.** Das Simulator-Optimum für kopfgroße Ziele liegt über 143 cm/360, Profis spielen 33–65. Der
   Trainer belohnt Präzision auf kleinen Zielen und kennt keine Kosten für große Drehungen, Bewegung, Recoil oder Ermüdung
   über eine Stunde. Das Ergebnis ist "bestes im Trainer", nicht "bestes in Valorant". Abhilfe: Szenarien mit großen Winkeln
   (90–180°) und Zeitdruck stärker gewichten, und ein Prior zum Bereich, in dem Valorant-Spieler tatsächlich spielen
   (eDPI um 250, etwa 33–65 cm/360), als weiche Leitplanke statt als harte Grenze.
3. **Score relativ zum Pool.** Die z-Werte hängen davon ab, welche Runden im Pool sind. Das ist fürs Ranking innerhalb eines
   Pools in Ordnung. Zahlen verschiedener Tage sind aber nicht vergleichbar, und eine neue Session verschiebt die alten Scores.
4. **Nebenbefunde.** `evaluateAb` bewertet ohne Szenariogruppen, `ScoreCalculator` sonst mit. ASSESSMENT nennt BIC 5 und
   Range 1,04, der Code nutzt 8 und 1,02. ALGORITHMS nennt für den A/B-Test einmal 10 und einmal 12 Paare, der Code nutzt
   6 bis 12.

## 2. Zusammenführung der Sessions

**Heute.** Alle blinden Sessions mit gleicher DPI kommen ungewichtet in einen GP, jede mit eigenem additivem Offset
("besserer oder schlechterer Tag"). Dazu kommt ein linearer Trend innerhalb einer Session.

**Gültig?** Nur wenn das Optimum selbst stabil bleibt und sich nur das Niveau verschiebt. Genau das ist fraglich:
- *Lernen und Gewöhnung:* Wer zwischen den Sessions mit der empfohlenen Sensitivity spielt, passt sich an sie an. Das
  Optimum wandert dann zum zuletzt gespielten Wert.
- *Tagesform und Ermüdung* wirken nicht nur additiv. Müde Hände leiden bei hoher Sensitivity stärker, das verschiebt das
  Optimum. Ein additiver Offset bildet das nicht ab.
- *Gewöhnung an die getesteten Werte* innerhalb eines Laufs ist durch das Wechseln zwischen den Runden begrenzt, aber nicht null.

**Messung (E5, 4 Sessions × 10 Runden, 20 Seeds, Fehler gegen das aktuelle Optimum, Median/p90 in %):**

| Schätzer | keine Drift | 5 %/Session | 10 %/Session |
|---|---|---|---|
| alles poolen (heute) | 7,1 / 17,3 | 14,2 / 19,7 | 14,4 / 21,5 |
| nur neueste Session | 7,0 / 25,0 | 15,3 / 41,1 | 8,3 / 23,7 |
| neueste 2 Sessions | 8,2 / 31,7 | 12,3 / 38,9 | 6,2 / 13,7 |
| Gewichte 4/2/1/1 | 7,0 / 22,5 | 13,8 / 25,9 | 9,2 / 19,2 |

- Ohne Drift ist Poolen am stabilsten (bestes p90).
- Mit Drift wird Poolen systematisch falsch: Der Median-Bias liegt bei −14 %, das Ergebnis klebt an alten Werten.
- Kein fester Schnitt gewinnt überall.
- Einschränkungen: Die Varianten wurden auf derselben Historie ausgewertet und nicht separat gespielt. Das Drift-Optimum ist
  per Sweep nur verrauscht bestimmt.

**Besser.** Zeit gehört ins Modell, statt sie wegzuwerfen:
- Ein GP-Kern über (ln cm/360, Session-Zeitpunkt), z. B. Produktkern mit einem Random-Walk- oder Matern-Kern über die
  Kalendertage. Alte Sessions zählen dann so viel, wie die Daten Stabilität zeigen. Die Länge des Zeitkerns wird wie die
  anderen Hyperparameter über das Grid gewählt.
- Einfacher als Zwischenschritt: ein Drift-Check. Liegt das Optimum der neuesten Session außerhalb des 80-%-Intervalls des
  Pools, wird gewarnt und auf die letzten zwei Sessions gefallen.
- Zusätzlich die Sessions auf maximal etwa 6 Wochen begrenzen und anzeigen, wie viele Tage im Ergebnis stecken.

## 3. Curve-Finder

**Ziel des Users:** Unterschießt die Hand bei langsamen Bewegungen systematisch, soll der Gain dort steigen; überschießt sie
bei schnellen, soll er dort fallen.

**Was heute passiert.** Es gibt zwei unabhängige Wege:
1. **Kalibrierung:** 15 Runden ohne Kurve. Gemessen wird pro Bewegung der Fehler der ersten Submovement relativ zur Distanz,
   gebinnt nach deren Spitzengeschwindigkeit. Der Median von ln(1+e) je Bin ergibt den gewünschten Gain. Darauf werden
   classic, natural und power gefittet. Angeboten wird eine Kurve nur bei BIC ≥ 8 und Range ≥ 1,02. **Das ist im Ansatz genau
   der gezielte Weg, den du willst.**
2. **Stärkensuche:** 1-D-BO über das Limit einer natural-Kurve mit fester Decay (0,70–1,45), bewertet nur über den Score.
   **Das ist nicht gezielt.** Sie probiert eine generische Kurvenform blind aus und nutzt die gemessenen Fehler gar nicht.

Danach entscheidet der blinde A/B-Test mit 6–12 Paaren.

**Was von Grund auf schief ist.**

1. **Extrapolation weit außerhalb der Daten (Hauptursache).** Die genutzten Bewegungen der Kalibrierung liegen bei
   p10/p50/p90 = 4,5/5,9/8,3 in/s, also nur Faktor 2. Raw Accel wendet die Kurve aber auf jede Geschwindigkeit an, auch auf
   schnelle Flicks. Die Familien werden in einem schmalen Fenster gefittet und dann bis über 40 in/s fortgesetzt (E2):

   | Präferenz | wahrer Gain bei 40 in/s | gefittet (heute) |
   |---|---|---|
   | +15 % | 1,055 | 1,376 |
   | +30 % | 1,109 | 1,547 |
   | −15 % | 0,943 | 0,860 |
   | −30 % | 0,883 | 0,689 |

   Gemessen als Kurvenfehler über 5–40 in/s (E3) sind die empfohlenen Kurven im Mittel *schlechter als gar keine*:

   | Präferenz | ohne Kurve | heute empfohlen |
   |---|---|---|
   | +15 % | 0,038 | 0,069 |
   | +30 % | 0,073 | 0,193 |
   | −15 % | 0,043 | 0,042 |
   | −30 % | 0,093 | 0,107 |

   Das erklärt "angeboten, aber nicht bestätigt": Nicht der Test ist zu schwach, die angebotene Kurve ist zu stark. Mit der
   *wahren* Kurve gewinnt derselbe A/B-Test in 18–20 von 20 Läufen (E4, +8 bis +22 Punkte pro Paar).

2. **Das Binning nach gemessener Geschwindigkeit ist nicht exogen.** Geschwindigkeit und Fehler hängen am selben Motorrauschen:
   Ein zu kräftiger Impuls ist schneller *und* weiter. Innerhalb gleicher Zieldistanz korreliert ln(1+e) mit ln(Speed) mit
   r = 0,36 (E1). Über alle Distanzen hebt sich das fast auf (r = −0,04), weil lange Reichweiten zugleich schnell sind und
   eher kurz landen. Diese Aufhebung ist aber eine Eigenschaft des Simulator-Rauschmodells, nicht der echten Hand.

3. **Daraus folgt die Asymmetrie.** Beide Binnings haben einen Steigungsbias von etwa ±0,01 in ln(Gain) pro ln(Speed), mit
   entgegengesetztem Vorzeichen. Im schmalen Fenster ist das so groß wie das Signal einer 15-%-Präferenz.

   | Binning | flache Spieler, mittlere Steigung | +15 % angeboten | −15 % angeboten |
   |---|---|---|---|
   | nach Speed (heute) | −0,008 | 0/20 | 16/20 |
   | nach Distanz | +0,010 | 17/20 | 2/20 |

   Die frühere Beobachtung "fallend wird gefunden, steigend kaum" (ASSESSMENT: −25 % in 27–29/30, +50 % nur in einem Drittel
   bis zur Hälfte) ist also zum großen Teil ein Schätzer-Bias. Dazu kommt, dass eine steigende Präferenz die Hand verlangsamt
   und schnelle Samples noch seltener macht. Falsch-positive gibt es bei flachen Spielern trotzdem keine (0/40), weil die
   BIC-Schwelle den Bias abdeckt. Dafür werden echte kleine Präferenzen auf der "falschen" Seite übersehen.

4. **Peak statt Profil.** Raw Accel wirkt auf die momentane Geschwindigkeit, eine Bewegung bekommt also einen über ihr Profil
   gemittelten Gain. Die Analyse ordnet den ganzen Fehler der Spitzengeschwindigkeit zu. Das ist ein weiterer Grund, warum
   gefittete Kurven zu steil werden. Er lässt sich nicht von Punkt 1 trennen, wiegt aber weniger.

5. **Annahme "keine Anpassung" (der tiefste Punkt).** Im Simulator plant die Hand mit einer festen Erwartung
   `gainPreference(speed)` und lernt nie dazu (`SimulatedAimer.kt:236`). An die lineare Sensitivity ist sie dagegen perfekt
   angepasst. Deshalb bringt eine 5-%-Fehlanpassung bei 40 in/s im Simulator +8 Punkte, eine 15-%-Fehleinstellung der
   Sensitivity aber unter 1 Punkt. Bei Menschen ist es eher umgekehrt: Systematische Fehler werden über einige Dutzend bis
   hundert Bewegungen weg-adaptiert, und was übrig bleibt, ist großteils Strategie. Dazu gehört der primäre Undershoot nach
   Elliott et al. Man korrigiert ihn nicht mit Gain, er stellt sich nach der Anpassung wieder ein. Eine Kurve, die einen
   *gemessenen* Fehler einmalig invertiert, behebt also vor allem Fehler, die der Spieler ohnehin selbst ausgleichen würde.
   Der echte Nutzen von Accel liegt laut Literatur woanders: niedriger Gain für Präzision, hoher Gain für große Drehungen
   (Casiez & Roussel 2011: bis 24 % besser als konstanter Gain). Das ist ein Trade-off, den man über das *Ergebnis* bewertet,
   nicht über das Fehlervorzeichen.

6. **Stärkensuche.** Sie ist ein reiner Outcome-Optimierer über eine einzige Kurvenform. Der Ansatz ist nicht falsch, aber
   nicht gezielt, mit 16–24 Runden teuer und zu gierig: Bei flachen Spielern schließt sie "keine Kurve" in 5/16 aus.
   Dein echtes Ergebnis (0,70 am Rand) kann ein echter Wunsch nach weniger Gain bei schnellen Bewegungen sein (weniger
   signalabhängiges Rauschen) oder dieser Bias. Unterscheiden kann das nur der A/B-Test.

**Gegenprobe: geschlossener Kreis (E3, Prototyp).** Je Iteration 6 Runden: Fehler je Bin schätzen, 50 % der Korrektur als
Kurve anwenden, mit der Kurve spielen, Restfehler neu schätzen. Nach 24 Runden sinkt der Kurvenfehler stetig: +15 % von 0,038
auf 0,015, +30 % von 0,073 auf 0,034, −30 % von 0,093 auf 0,047. Bei flachen Spielern wird die Kurve höchstens 2–4 % vom
neutralen Gain entfernt. Der Kreis erholt sich von seinem eigenen Bias, weil er den *Restfehler mit der angewandten Kurve*
misst. Einschränkungen:
- Die Tabelle musste als natural-Kurve gerendert werden (`AccelCurve` ist sealed).
- Der Simulator passt sich nicht an, der Kreis ist also im Simulator leichter als beim Menschen.

## 4. Alternativen

| Ansatz | Ergibt Sinn? | Daten | Validierung | Kosten |
|---|---|---|---|---|
| **Heute:** Fit generischer Familien + blinde Stärkensuche + A/B | teilweise: Kalibrierung gezielt, aber extrapolierend und mit Bias; Suche ungezielt | 15 + 16–24 + 12–24 Runden pro Spieler | A/B-Test (gut, wenn die Kurve stimmt) | vorhanden |
| **Diagnostischer geschlossener Kreis** (messen → gedämpft korrigieren → anpassen lassen → messen, LUT-Export, AutoGain-artig) | **ja**, entspricht deinem Ziel und korrigiert eigenen Bias | 3–4 Iterationen à 6 Runden, je 1–2 Runden Gewöhnung vorweg, über 2–3 Tage | Konvergenz des Restfehlers + A/B gegen plain am Ende | mittel: neue Analyse, LUT-Export, Simulator mit Anpassung |
| **Staircase je Geschwindigkeitsband** (up-down auf den Gain je Band nach dem Fehlervorzeichen) | eingeschränkt: braucht monotone Antwort und stationären Spieler, Anpassung verschiebt die Schwelle (PSI reagiert langsam auf Verschiebungen) | viele Trials pro Band, Bewegungen pro Band schwer zu steuern | Konvergenz, A/B | gering bis mittel, aber im Kern ein schlechterer geschlossener Kreis |
| **Preferential BO / Dueling** (der Spieler vergleicht zwei Kurven nach Gefühl) | ja, als Ergänzung: misst Komfort, den der Score nicht sieht | etwa 15–30 Vergleiche (Literatur nennt keine harten Zahlen) | Score-A/B, Stabilität über Tage | gering: UI für "A oder B?" |
| **ML überwacht, viele Spieler** (Modell von Aim-Daten → Kurve) | nein, derzeit nicht: Es gibt kein Label "optimale Kurve", das Ziel ist pro Person und nicht beobachtbar | hunderte Spieler × Stunden, Opt-in-Datenspende, Datenschutz | nur indirekt (A/B je Nutzer) | hoch; höchstens später als Populations-Prior für den GP sinnvoll |
| **Reinforcement Learning** (Agent stellt die Kurve ein, Belohnung = Score) | nein: Der Mensch ist eine nicht-stationäre, sich anpassende Umgebung, RL braucht tausende Episoden | tausende Runden pro Spieler | schwierig, Reward laut | sehr hoch |

## 5. Empfehlung

**Urteil: reparieren, kein Neubau mit ML.** Die Architektur (Kern ohne UI, Simulator, A/B-Test, gepoolte Speicherung) trägt.
Der Kern des Curve-Finders muss aber umgebaut werden.

Sensi-Finder (klein bis mittel):
1. Zeit ins Modell: Drift-Check als ersten Schritt, danach ein Zeitkern im GP. Sessions auf etwa 6 Wochen begrenzen.
2. Das Plateau statt einer Zahl kommunizieren: Bereich, in dem es weniger als 1 Punkt kostet, plus "wähle darin nach Gefühl".
3. Mehr große Winkel und Zeitdruck in die Szenarien, ein weicher Prior auf übliche Valorant-Bereiche.
4. `evaluateAb` mit Szenariogruppen bewerten, Doku-Zahlen angleichen.

Curve-Finder (mittel bis groß, Reihenfolge nach Nutzen):
1. **Sofort:** Keine Extrapolation mehr. Die Kurve wird oberhalb und unterhalb des gemessenen Geschwindigkeitsbereichs
   flach gehalten (Cap bzw. Offset) oder als Lookup Table mit flachen Enden exportiert (Raw Accel unterstützt LUT). Das allein
   entfernt den größten Fehler aus E2/E3.
2. **Schätzer ohne Bias:** Fehler gegen Distanz *und* Geschwindigkeit gemeinsam regressieren, z. B.
   ln(1+e) ~ ln(Distanz) + ln(geplante Speed), wobei die geplante Speed aus der Distanz vorhergesagt wird. Die Kurve wird nur
   aus dem Teil geschätzt, der nicht durch das Rauschen des eigenen Impulses erklärt ist.
3. **Geschlossener Kreis statt einmaligem Fit:** Iteration mit 50 % Dämpfung, vor jeder Messung 1–2 Runden Gewöhnung, Stopp
   bei flachem Restfehler. Am Ende entscheidet der A/B-Test. Die blinde Stärkensuche fällt weg oder dient nur noch als
   Fallback ohne Kalibrierdaten.
4. **Breiter messen:** Szenarien mit großen, schnellen Drehungen, damit der Bereich, für den die Kurve gilt, auch gemessen
   wird (heute nur 4,5–8,3 in/s im Simulator; an echten Daten prüfen).
5. **Simulator ehrlich machen:** Ein Zwei-Prozess-Anpassungsmodell (Smith et al. 2006) für Gain *und* Kurve. Erst dann sagen
   Simulator-Zahlen etwas darüber, ob eine Kurve nach der Gewöhnung noch hilft. Ohne das bleiben alle Kurven-Erfolgsraten
   optimistisch.

## 6. Plan für den Umbau des Curve-Finders

Kein separates ML-Projekt, sondern ein Umbau im `core/accel` hinter der bestehenden Naht (`AccelRun` steuert, `core` rechnet).

| Phase | Inhalt | Fertig, wenn | Risiko |
|---|---|---|---|
| 0 Messen | Speed-Verteilung und Fehler-Kopplung r an *echten* Runden (vorhandene DB) prüfen | bekannt, ob echte Hände dasselbe Fenster und dieselbe Kopplung zeigen | Daten von nur einem Spieler |
| 1 Schadensbegrenzung | flache Enden außerhalb des gemessenen Bereichs, LUT-Export, `AccelCurve` um eine Lookup-Variante erweitern | E2-Kurvenfehler der Empfehlung ≤ "ohne Kurve" für alle Präferenzen | Raw-Accel-LUT-Format vorher an der offiziellen Doku prüfen |
| 2 Simulator | Zwei-Prozess-Anpassung, Day-Offset, Lernkurve | Simulator reproduziert Aftereffects; alte Experimente neu gerechnet | Parameter aus Literatur, nicht von dir |
| 3 Schätzer | gemeinsame Regression Distanz + Speed, Bias-Test mit flachen Spielern beider Rauschmodelle | Steigungsbias \|·\| < 0,003, Erkennung ±15 % symmetrisch | weniger Power, mehr Runden nötig |
| 4 Kreis | Iteration mit Dämpfung und Gewöhnungsrunden, gespeichert über Tage (vorhandenes Pooling wiederverwenden) | konvergiert in ≤ 24 Runden im Simulator mit Anpassung; flache Spieler ≤ 3 % Abweichung | mit Anpassung kann der messbare Nutzen fast verschwinden. Das wäre ein ehrliches Ergebnis, kein Fehler |
| 5 Bestätigung | A/B gegen plain, optional Gefühlsvergleich (Preferential) | du testest es über 2–3 Tage | Tagesform |

## Verwendete Agenten und Modelle

- Bewertung, Urteil und Bericht: Hauptsession, Claude Opus 5.5.
- Code-Karte (Optimizer, Score, Pooling, Kurvenanalyse, A/B, Simulator): Explore-Agent, Claude Haiku 5.5.
- Web-Recherche (Motorik, Transferfunktionen, Anpassung, Valorant-Daten, Raw Accel, Staircase und Preferential BO):
  general-purpose-Agent, Claude Sonnet 5.5.
- Experimente E1–E5 (lokale Testklasse `OptimizerReviewTest`, nicht eingecheckt): general-purpose-Agent, Claude Sonnet 5.5.
- Der architect-Agent wurde nicht eingesetzt, weil das Urteil "reparieren" und nicht "neu bauen" lautet.
