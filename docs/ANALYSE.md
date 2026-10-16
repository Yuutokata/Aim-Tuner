# AimTune: Analyse nach dem Flash-Szenario und den Zielhöhen

Stand 2026-10-16, nach M10 (Flash turn-away) und M11 (Zielhöhen, Mini-Map; Abschnitt E). Das ist die deutsche Fortschreibung von `docs/ASSESSMENT.md` (englisch, Stand nach M9, bleibt unverändert): Teil A (Sensitivity-Finder) und Teil B (Kurvenfinder) wurden mit dem aktuellen Code neu gemessen und den Werten von vorher gegenübergestellt. Alle Zahlen kommen von simulierten Spielern; die simulierte Hand ist nicht an Menschen validiert (ASSESSMENT.md, Teil C). Zellen haben 30–40 simulierte Spieler, ein Prozentwert hat also etwa ±15 Punkte Stichprobenfehler (95 %, schlimmster Fall); **Unterschiede unter dieser Größe behaupte ich nicht.**

## Kurzfazit

1. **Das Flash-Szenario verbessert den Sensitivity-Finder nicht, es verschiebt und verbreitert ihn.** Der mediane Fehler bleibt bei 13–20 % (8–15 Runden) bzw. 10–11 % (30 Runden), aber die schlechten Fälle werden schlechter (90. Perzentil bei 8 Runden „oft“: 94 % statt 40 %), und der wahre Verlust in Score-Punkten steigt bei 8–15 Runden etwa auf das Doppelte, weil die Flash-Scorekurve steiler ist. Das Optimum wandert erwartungsgemäß: 50,0 → 46,3 („manchmal“) → 40,8 cm/360 („oft“). Das ist gewollt (wer geblendet wird, verliert das Duell), aber die 8-Runden-Schätzung wird dadurch grober.
2. **Die Konfidenzangabe bleibt konservativ** gegenüber „Verlust vernachlässigbar“ (meist 89–98 % tatsächlich), gegenüber „innerhalb ±15 %“ ist sie jetzt leicht zu optimistisch (stated 80+ % → tatsächlich 71–73 %, vorher 75–82 %).
3. **Beim Kurvenfinder ist die Wirkung zweigeteilt.** Die **Fehleranalyse** (Phase 1) nutzt Flash-Runden bewusst nicht: Flash-Rückkehren sind schnell und lang, haben aber einen anderen Fehlerbias als Flicks; gemischt täuschen sie eine geschwindigkeitsabhängige Verstärkung vor (Schwanz der Nullverteilung: 1 falsches Angebot bei 500 flachen Spielern, Maximum der BIC-Differenz 9,4 gegen Schwelle 8) und verschlechtern das Finden steigender Kurven. Die **blinde Stärkensuche und der blinde A/B-Test** profitieren dagegen deutlich (siehe 4–5).
4. **Stärkensuche:** Bei kopfgroßen Zielen schließt die Suche mit reinen Klick-Szenarien für einen Spieler *ohne* Vorliebe in 21–23 von 32 Läufen „keine Kurve“ zu Unrecht aus. Mit Flash in der Hälfte der Runden sind es 1–4 von 32; echte Vorlieben werden gleich gut oder besser gefunden (−15 % → ×0,87 statt ×0,93). Das war vorher nicht bekannt, weil die früheren Experimente große Ziele nutzten (dort 5 von 16).
5. **A/B-Test mit Flash-Paaren:** Falsche Urteile bei identischen Armen bleiben bei etwa 2 % (vorher 1,3–1,9 %). Eine echte fallende Kurve (−15 %) wird deutlich häufiger bestätigt (schnelle Hand, 15 Runden: 27 statt 16 von 30). Eine steigende Kurve von +50 % aus der Fehleranalyse wird dagegen seltener bestätigt (10 statt 20 von 30), weil die angepasste Kurve zu schwach ist, um in Flash-Runden zu helfen.
6. **Der „Test-Kurve“-Button wird strenger:** Ein flacher Spieler bekommt jetzt in 15–29 von 30 Fällen „schlechter“ (vorher 0–21), eine echte +15-%-Vorliebe „besser“ in 23–26 statt 28–29. Er bleibt ein Test mit ungewohnter Hand.
7. **Was sich nicht geändert hat:** Es gibt weiterhin keine Validierung an Menschen, die simulierte Hand ist bei kleinen Zielen langsam (einfache Bewegungen erreichen nur 3–5 in/s), und die Flash-Zeiten sind teils Annahmen (Flug vor dem Windup, 10°-Rand). Das ist weiter der größte Posten.
8. **Reihenfolge der nächsten Schritte:** Flash-Zeiten im Spiel prüfen, 10 echte Spieler aufnehmen (jetzt auch mit Flash-Runden; der Forschungsexport enthält sie), dann erst weitere Algorithmusarbeit (Handlungsoptionen unten).
9. **Zielhöhen (M11, Abschnitt E):** Die Ziele stehen jetzt auf Kopfhöhe (88 % mit einer Streuung von 1,5° um Kopfhöhe, 12 % auf anderer Ebene), auf dem Boden einer Mini-Map mit Ecken und Türen, und die Ergebnisse zeigen, aus welcher Achse die Fehler kommen. In der Simulation **ändert das die Zahlen nicht messbar**: Optimum der simulierten Spieler, Median-Fehler und Konfidenz des Finders, Stärkensuche und A/B-Test bleiben im Rahmen des Rauschens gleich; einzig steigende schwache Kurvenvorlieben (+15/+25 %, 15 Runden) erkennt die Fehleranalyse seltener (6 → 0 bzw. 24 → 17 von 30). Der Gewinn ist der Realismus der Aufgabe, nicht der Zahlen; Höhen und Anteile sind Annahmen (`verified: false`).

## Ampel-Übersicht

Grün: in der Simulation belastbar, Rest ist Validierung. Gelb: funktioniert mit klaren Einschränkungen. Rot: nicht tragfähig oder nicht geprüft. Die Farben der Spalte „Vorher“ sind meine nachträgliche Einordnung des damaligen Berichts, nicht dort so vergeben.

| Bereich | Vorher (ASSESSMENT.md, nach M9) | Nachher (M10, M11) | Grund der Änderung |
| --- | --- | --- | --- |
| Sensitivity-Finder, Statistik (Median-Fehler, Intervall) | 🟡 | 🟡 | Median gleich, Ausreißer und Punktverlust bei 8–15 Runden mit Flash größer; 30 Runden unverändert gut |
| Konfidenzangabe | 🟢 | 🟢 | konservativ gegenüber Verlust; gegenüber ±15 % leicht optimistisch (71–73 % bei 80+) |
| Absolutwert der Sensitivity (Zielgröße, Hand) | 🟡 | 🟡 | zusätzlich verschiebt die Flash-Wichtigkeit das Optimum um −7 % / −18 % |
| Flash-Szenario selbst | – | 🟡 | neu; Pop-Delay (Flug) und Randwinkel sind ungeprüfte Annahmen, Zeit-Windups sind belegt |
| Fehleranalyse (Phase 1) | 🟡 | 🟡 | unverändert; Flash-Daten bewusst nicht verwendet, weil sie Fehlalarme erzeugen |
| Blinde Stärkensuche | 🟡 | 🟡 (besser) | mit realistischen Zielen und Flash 1–4/32 statt 21–23/32 falsche Ausschlüsse; weiter nur mit A/B-Abschluss |
| Blinder A/B-Test | 🟢 | 🟢 | Falschurteile ~2 %, bestätigt echte Kurven in 95–100 %, mit Flash mehr fallende −15 % |
| „Test-Kurve“-Button | 🟡 | 🟡 | trennt jetzt besser (flach: schlechter), misst weiter eine ungewohnte Hand |
| Zielverteilung (Höhe, Karte) | 🔴 (voll 3D) | 🟡 | Ziele auf Kopfhöhe mit kleinem Anteil anderer Ebenen und Mini-Map; Höhen und Anteile sind ungeprüfte Annahmen, die Zahlen ändern sich in der Simulation nicht messbar |
| Crosshair (eigener Valorant-Code, pixelgenau) | 🔴 (festes Kreuz) | 🟡 | Format inoffiziell, Farben und Rundung ungerader Dicken sind Annahmen; Vergleich mit dem Spiel steht aus |
| Simulierte Hand / Realismus | 🔴 | 🔴 | unverändert; Flash-Rückkehren erreichen nur 3–5 in/s |
| Externe Validierung, echte Daten | 🔴 | 🔴 | unverändert |

## Methode und Vergleichbarkeit

* **Vorher** sind die Werte aus `ASSESSMENT.md` (große Ziele, Radius 0,25 m, Klick-Mix) und der Tabelle in `ALGORITHMS.md`. Die Tabelle „B3 Legacy“ habe ich mit dem aktuellen Code neu gerechnet (`AssessmentTest.power`): sie reproduziert die Werte von damals, der Code der Klick-Szenarien hat sich also nicht verändert.
* **Neu gemessen unter realistischen Bedingungen** (Zielgrößen und Winkel aus dem Valorant-Profil, kopfgroß 0,4–1,9°, `SimulatedPlayer.forHeadSizedTargets(50)`): jeweils der Zustand ohne Flash (Flick bzw. Klick-Mix) und mit Flash. Beide Seiten dieses Vergleichs laufen unter gleichen Bedingungen; der Vergleich mit den Legacy-Werten ist wegen der anderen Ziele nur qualitativ.
* **A/B-Simulation:** Die Legacy-Tabelle (B2) nutzt für beide Arme eines Paars dieselbe Zufallsfolge der simulierten Hand, die neuen Tabellen (B3, B5, B6) geben dem Kurven-Arm eine eigene. Das ist realistischer und strenger; deshalb sind die „bestätigt“-Zahlen von B2 und B3 nicht direkt vergleichbar.
* Für Teil B habe ich zwei Hände simuliert: die **Standard-Hand** (30 cm/s Handgeschwindigkeit, Fitts-Steigung 90 ms) und eine **schnelle Hand** (60 cm/s, 55 ms). Mit der Standard-Hand liegen alle einfachen Bewegungen bei 3–5 in/s, die Analyse verwirft die Daten dann wegen des zu engen Geschwindigkeitsbereichs („narrow speed range“); die schnelle Hand ist die realistischere Bedingung für die Analyse.
* Reproduzierbar mit dem committeten Code: `AssessmentTest.power`/`trialCurve` (Legacy) und `FlashExperimentsTest.*` (Fehleranalyse mit und ohne Flash, Nullverteilung, Stärkensuche, A/B-Test, Sensitivity mit Flash, Wanted-Gain je Szenario). Die Vorher/Nachher-Tabellen zu A1–A3, B3 (realistisch) und B4 stammen aus einem einmaligen, nicht committeten Skript derselben Machart (gleiche Helfer, gleiche Seeds-Logik); wer sie nachrechnen will, kann es als Test aufnehmen.

## A. Sensitivity-Finder

### A1. Wie genau trifft er das Optimum?

Wahrheit = Maximum einer glatten Anpassung an einen Referenz-Sweep (9 Punkte, 20–160 cm/360, je 24 Runden und Szenario, mit der Mischung der Szenarien gewichtet); Suchbereich 20–140; 40 Spieler pro Zelle; Rauschen ×1,0. „Wahrer Verlust“ = Score-Punkte, die der Spieler mit der Empfehlung gegenüber dem wahren Optimum verliert (in der Score-Einheit der jeweiligen Mischung, nur innerhalb einer Zeile gleich zu lesen).

| Setup | Runden | Fehler Median / p75 / p90 | innerhalb ±15 % | wahrer Verlust Mittel (p90) |
| --- | --- | --- | --- | --- |
| Vorher, große Ziele (ASSESSMENT A1) | 8 / 15 / 30 | 19/31/40 · 13/21/33 · 8/16/21 % | 38 · 63 · 73 % | 1,27 (2,83) · 0,76 (2,18) · 0,39 (1,17) |
| Flick, kopfgroße Ziele (neu) | 8 | 16 / 25 / 40 % | 45 % | 0,92 (2,12) |
| | 15 | 13 / 27 / 36 % | 53 % | 0,85 (1,83) |
| | 30 | 10 / 17 / 23 % | 68 % | 0,41 (1,09) |
| Flick + Flash „manchmal“ (18 %) | 8 | 15 / 36 / 51 % | 50 % | 2,43 (3,53) |
| | 15 | 14 / 28 / 34 % | 53 % | 1,80 (2,60) |
| | 30 | 11 / 20 / 27 % | 65 % | 0,60 (1,40) |
| Flick + Flash „oft“ (33 %) | 8 | 20 / 44 / 94 % | 33 % | 4,39 (12,84) |
| | 15 | 14 / 26 / 36 % | 53 % | 1,24 (3,45) |
| | 30 | 10 / 17 / 27 % | 63 % | 0,63 (1,77) |

Bei Rauschen ×1,6 ist das Bild gleich, nur gröber: Median-Fehler Flick 14 / 11 / 12 %, „manchmal“ 20 / 17 / 16 %, „oft“ 29 / 15 / 16 % bei 8 / 15 / 30 Runden; wahrer Verlust bei 15 Runden 1,36 / 1,91 / 2,90.

Lesart: **Der Median ändert sich kaum, der Schwanz schon.** Bei 8 Runden mit „oft“ liegt jeder zehnte Lauf um 94 % daneben. Der höhere Verlust in Punkten kommt zu einem Teil aus der steileren Scorekurve (siehe A2): derselbe prozentuale Fehler kostet mit Flash mehr Punkte. Praktisch heißt das: mit Flash-Anteil sind **mindestens 15 Runden** nötig, 8 sind nur ein grober Hinweis (ASSESSMENT A1 sagte das schon für Flick). Bei 30 Runden ist der Unterschied klein.

### A2. Wie flach ist die Kurve?

Verlorene Score-Punkte gegenüber dem wahren Optimum (Rauschen ×1,0):

| Änderung der cm/360 | ×0,70 | ×0,85 | ×1,15 | ×1,30 | ×1,50 | ×2,00 |
| --- | --- | --- | --- | --- | --- | --- |
| Vorher, große Ziele | 5,3 | 0,9 | 0,5 | 1,7 | 3,5 | 8,3 |
| Flick, kopfgroß | 3,5 | 0,6 | 0,4 | 1,3 | 2,9 | 7,4 |
| + Flash „manchmal“ | 4,0 | 0,7 | 0,5 | 1,6 | 3,4 | 8,4 |
| + Flash „oft“ | 4,8 | 0,9 | 0,6 | 1,8 | 3,9 | 9,4 |

Die Kurve bleibt flach um das Optimum (±15 % kosten 0,4–0,9 Punkte) und wird mit Flash an den Rändern etwas steiler, weil die Ausweichquote bei zu langsamer Sensitivity stark einbricht. Die Aussage „ein Bereich statt eines Punktes“ gilt unverändert.

### A3. Ist die Konfidenz kalibriert?

Alle Mischungen ohne „oft“ und beide Rauschstufen zusammen (160 Läufe je Rundenzahl; Klassen mit sehr wenigen Läufen, z. B. 30 Runden und 0–39 %, sind weggelassen):

| Runden | angezeigte Konfidenz | n | tatsächlich P(Verlust ≤ „vernachlässigbar“) | tatsächlich P(innerhalb ±15 %) | vorher (ASSESSMENT A3) |
| --- | --- | --- | --- | --- | --- |
| 8 | 0–39 % | 63 | 92 % | 49 % | 94 % / 39 % |
| 8 | 40–59 % | 97 | 89 % | 43 % | 95 % / 54 % |
| 15 | 60–79 % | 49 | 96 % | 57 % | 98 % / 62 % |
| 15 | 80–100 % | 26 | 96 % | 73 % | 100 % / 75 % |
| 30 | 60–79 % | 59 | 98 % | 54 % | 90 % / 68 % |
| 30 | 80–100 % | 55 | 95 % | 71 % | 100 % / 82 % |

Die Aussage „Verlust vernachlässigbar“ trifft weiter fast immer zu (in den gezeigten Klassen 89–98 %; 79 % bei 15 Runden und 0–39 %, n = 29), die Zahl bleibt konservativ. Als Aussage über „innerhalb ±15 % des wahren Wertes“ ist sie bei 30 Runden jetzt **leicht optimistisch** (80+ % angezeigt, 71 % tatsächlich, vorher 82 %). Das liegt innerhalb des Stichprobenfehlers der Zellen, ist aber die Richtung, in die Flash die Sache bewegt. Das 80-%-Intervall deckt die Wahrheit in 83–98 % der Läufe.

### A4. Wie viel ist Spieler, wie viel Setup?

* **Neu: die Flash-Wichtigkeit ist ein Setup-Parameter.** Das wahre Optimum desselben Spielers liegt bei 50,0 cm/360 (nur Flick), 46,3 („manchmal“) und 40,8 („oft“); bei Rauschen ×1,6 72,5 → 68,0 → 60,8. Der Finder folgt dem verschobenen Optimum (A1). Das ist gewollt, heißt aber: Die ausgegebene Zahl hängt jetzt auch davon ab, was der Spieler im Onboarding angibt.
* Zielgröße (×0,64 pro Verdopplung des Radius), Punktgewichte (unter 7 %) und Spielerrauschen: unverändert gegenüber ASSESSMENT A4.
* Die Flash-Annahmen (Pop-Delay 0,45–0,9 s, Randwinkel 10°) bestimmen die Ausweichquote und damit die Verschiebung; sie sind im Profil als `verified: false` markiert und wurden so gewählt, dass die simulierte Hand weder immer noch nie ausweicht. Mit realen Werten kann die Verschiebung deutlich anders ausfallen.

## B. Kurvenfinder

### B0. Was sich seit ASSESSMENT.md im Code geändert hat

Seit M9 (nach ASSESSMENT.md): Schwellen der Fehleranalyse neu kalibriert (Vertrauensboden 0,005, BIC 8, Bereich 1,02), blinde Stärkensuche als empfohlener Weg, Flash-Szenario. Die Fehleranalyse selbst und die Kalibrierrunden (Klick-Mix) sind durch das Flash-Szenario **nicht** verändert; geändert sind die Stärkensuche und der A/B-Test (Flash in der Hälfte der Runden, wenn die Flash-Wichtigkeit nicht „nie“ ist).

### B1. Basislinie: warum Flash nicht in die Fehleranalyse darf

Wanted-Gain (zentriert, log) einer Spielerin ohne Vorliebe, 30 Spieler × 10 Runden je Szenario, kopfgroße Ziele, Standard-Hand:

| Szenario | Bewegungen (verwendet) | Wanted-Gain am unteren Ende (ab 3 in/s) | am oberen Ende (bis ~4,4 in/s) | Spanne |
| --- | --- | --- | --- | --- |
| Flick | 8424 (1580) | +0,004 | −0,001 | ±0,006 |
| Switching | 7398 (2839) | +0,004 | −0,007 | ±0,007 |
| Flash-Rückkehr | 2378 (2333) | +0,021 | −0,036 | +0,021 … −0,036 |
| (Micro: 11 186 Bewegungen, keine über 3 in/s) | | | | |

Flicks und Switching sind flach, die Flash-Rückkehr nicht: ihr Fehlerbias hängt von Weglänge und Geschwindigkeit anders ab (Median-Fehlerverhältnis −3,0 % gegen −4,0 % bei Flick). Mit einer echten Vorliebe (−25 %) geht es von +0,037 bis −0,056, also trägt die Rückkehr tatsächlich Information, aber nicht trennbar vom Szenario-Effekt. Gepoolt sieht das aus wie eine Verstärkung, die mit der Geschwindigkeit sinkt.

Falsche Angebote bei 500 simulierten Spielern ohne Vorliebe (schnelle Hand, 30 Runden; Schwelle BIC 8):

| Kalibrierrunden | BIC-Gewinn 99. Perzentil | Maximum | falsche Angebote |
| --- | --- | --- | --- |
| Klick-Mix | −1,9 | 0,1 | 0 von 500 |
| Klick-Mix + Flash (25 %) | 3,1 | 3,3 | 0 von 500 |
| Flick + Flash (50 %) | 4,5 | **9,4** | **1 von 500** |
| nur Flash (15 / 30 Runden) | – | – | 5 von 7 und 6 von 6 *bewertbaren* Läufen |

Bei niedriger Sensitivity (90 cm/360, Standard-Hand) ergibt sich dasselbe (Maximum 8,4 bei 50 % Flash, 1 von 500). Der Schwanz der Nullverteilung wird durch Flash dicker; das Risiko ist klein, aber genau das, was die Schwelle verhindern soll. **Die App behält deshalb die Klick-Szenarien in der Kalibrierung.**

### B2. Tabelle schwacher Effekte (8, 15, 30 Runden), Legacy-Bedingungen

Angeboten / vom A/B-Test bestätigt, 30 Spieler je Zelle, große Ziele, Klick-Mix (so wie ASSESSMENT B3). Die Spalte „M9“ ist die Tabelle aus ASSESSMENT.md vor den Korrekturen, „jetzt“ ist der aktuelle Code (die Zahlen aus `ALGORITHMS.md` nach den Korrekturen sind reproduziert):

| wahrer Gain | 8 Runden M9 | 8 jetzt | 15 M9 | 15 jetzt | 30 M9 | 30 jetzt |
| --- | --- | --- | --- | --- | --- | --- |
| −50 % | 30 / 30 | 10 / 10 | 30 / 30 | 30 / 30 | 30 / 30 | 30 / 30 |
| −35 % | 30 / 27 | 30 / 28 | 30 / 24 | 30 / 28 | 30 / 24 | 30 / 28 |
| −25 % | 30 / 23 | 30 / 29 | 30 / 24 | 30 / 28 | 30 / 18 | 30 / 29 |
| **−15 %** | 20 / 0 | 17 / 15 | 30 / 1 | 21 / 18 | 30 / 0 | 30 / 28 |
| 0 % | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 1 / 0 | 0 / 0 |
| +15 % | 0 / 0 | 2 / 0 | 0 / 0 | 4 / 1 | 0 / 0 | 8 / 2 |
| +25 % | 0 / 0 | 10 / 3 | 8 / 5 | 19 / 3 | 13 / 11 | 30 / 1 |
| +35 % | 18 / 18 | 23 / 11 | 30 / 28 | 29 / 5 | 30 / 28 | 30 / 8 |
| +50 % | 29 / 28 | 30 / 9 | 30 / 29 | 30 / 9 | 30 / 27 | 30 / 8 |

(Die Zeile −50 % bei 8 Runden hat nur 10 Angebote; das stand schon so in der Tabelle in `ALGORITHMS.md`, eine Erklärung habe ich nicht untersucht.) Dieser Teil ist **vor** dem Flash-Szenario und danach identisch, er zeigt nur den Stand der Fehleranalyse. Die „bestätigt“-Spalte ist bei steigenden Kurven niedrig, weil die angepasste Kurve zu schwach ist (ASSESSMENT B2); dort hilft die Stärkensuche (B4).

### B3. Tabelle schwacher Effekte, kopfgroße Ziele, mit und ohne Flash

Angeboten / bestätigt (A/B-Test besser), 30 Spieler je Zelle, Spieler ohne Vorliebe (0 %) hatten in allen Zellen 0 Angebote und sind weggelassen. „Vorher“ = Klick-Mix in Analyse und A/B-Test. „Nachher (App)“ = Analyse mit Klick-Mix, A/B-Test mit Flash in der Hälfte der Paare. „Verworfen“ = Flash (25 %) auch in der Analyse.

**Schnelle Hand**

| Runden | Setup | −25 % | −15 % | +15 % | +25 % | +50 % |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | Vorher | 30 / 28 | 28 / 12 | 0 / 0 | 7 / 4 | 26 / 19 |
| | **Nachher (App)** | 30 / 27 | 28 / **26** | 0 / 0 | 7 / 4 | 26 / **6** |
| | Verworfen | 30 / 28 | 29 / 27 | 1 / 1 | 3 / 3 | 12 / 5 |
| 15 | Vorher | 30 / 27 | 29 / 16 | 6 / 5 | 19 / 15 | 30 / 20 |
| | **Nachher (App)** | 30 / 27 | 29 / **27** | 6 / 5 | 19 / 15 | 30 / **10** |
| | Verworfen | 30 / 28 | 30 / 28 | 1 / 1 | 7 / 4 | 28 / 19 |
| 30 | Vorher | 30 / 27 | 30 / 16 | 11 / 9 | 29 / 24 | 30 / 19 |
| | **Nachher (App)** | 30 / 26 | 30 / **24** | 11 / 8 | 29 / 24 | 30 / **11** |
| | Verworfen | 30 / 26 | 30 / 26 | 3 / 3 | 23 / 19 | 30 / 16 |

**Standard-Hand** (Analyse verwirft die einfachen Bewegungen als zu langsam; bei 8 Runden in allen drei Setups 0 Angebote)

| Runden | Setup | −25 % | −15 % |
| --- | --- | --- | --- |
| 15 | Vorher | 7 / 6 | 0 / 0 |
| | **Nachher (App)** | 7 / 7 | 0 / 0 |
| | Verworfen | **26 / 23** | 3 / 2 |
| 30 | Vorher | 27 / 23 | 0 / 0 |
| | **Nachher (App)** | 27 / 24 | 0 / 0 |
| | Verworfen | **30 / 28** | **15 / 10** |

Lesart (der Unterschied ist überall dort belastbar, wo er mehr als etwa 15 Punkte beträgt):

* **Fallende Kurven (−15 %) werden mit Flash-Paaren im A/B-Test viel häufiger bestätigt**: 12 → 26 (8 Runden), 16 → 27 (15), 16 → 24 (30) von je 30. Das Angebot bleibt gleich (28–30), nur die Bestätigung steigt: Flash-Runden machen den Nutzen der Kurve messbar. Ein klarer Gewinn.
* **Große steigende Kurven (+50 %) werden schlechter bestätigt**: 19 → 6, 20 → 10, 19 → 11 von 30. Die angepasste Kurve (zu schwach, siehe B2) hilft in Flash-Runden weniger, als sie in Klick-Runden hilft. Kleine steigende Effekte (+15 %, +25 %) bleiben unverändert. Ein Verlust, aber nur für den Weg über die Fehleranalyse; die Stärkensuche (B4) hat dieses Problem nicht, weil sie keine angepasste, sondern eine echte Kurvenstärke testet.
* **Flash in der Analyse** („Verworfen“) lässt die Standard-Hand überhaupt erst bewertbar werden (−25 % bei 15 Runden: 26 statt 7 Angebote), schadet der schnellen Hand bei steigenden Kurven (+25 % bei 15 Runden: 7 statt 19 Angebote) und erzeugt die Fehlalarme aus B1. Die Entscheidung, Flash aus der Analyse herauszulassen, bleibt damit richtig; der Nutzen bei der Standard-Hand ist ein Nebeneffekt davon, dass diese Hand ohne Flash nichts bewertet.
* Schädliche Angebote (A/B „schlechter“) kamen in diesen Zellen praktisch nicht vor (2 von 3240 Läufen).

### B4. Blinde Stärkensuche, mit und ohne Flash

24 Runden, 32 simulierte Spieler je Zelle, kopfgroße Ziele; gefundene Stärke (Median) und in wie vielen Läufen das Intervall „keine Kurve“ ausschloss (Spieler ohne Vorliebe: **falsch**, mit Vorliebe: richtig).

| Hand | Mix | ohne Vorliebe | +15 % | +25 % | −15 % | −25 % |
| --- | --- | --- | --- | --- | --- | --- |
| Standard | Klick-Mix | ×1,17 · **23/32** | ×1,39 · 31 | ×1,35 · 32 | ×0,93 · 13 | ×0,81 · 31 |
| | + Flash 25 % | ×1,09 · 8 | ×1,28 · 29 | ×1,34 · 30 | ×0,91 · 14 | ×0,74 · 29 |
| | Flash 50 % (App) | ×1,03 · **1** | ×1,27 · 29 | ×1,33 · 30 | ×0,87 · **26** | ×0,81 · 31 |
| Schnell | Klick-Mix | ×1,04 · **21/32** | ×1,40 · 32 | ×1,42 · 32 | ×0,93 · 28 | ×0,80 · 32 |
| | + Flash 25 % | ×1,06 · 13 | ×1,23 · 31 | ×1,45 · 32 | ×0,90 · 26 | ×0,78 · 31 |
| | Flash 50 % (App) | ×1,03 · **4** | ×1,19 · 32 | ×1,37 · 31 | ×0,87 · 31 | ×0,80 · 31 |

Vorher (große Ziele, ASSESSMENT/ALGORITHMS): ohne Vorliebe ×1,01, Intervall schließt in 5 von 16 Läufen zu Unrecht aus; +15 % → ×1,22, +25 % → ×1,45 (Rand), −15 % → ×0,89, −25 % → ×0,76.

Lesart: Unter realistischen (kopfgroßen) Zielen war die Stärkensuche **mit reinen Klick-Szenarien deutlich unspezifischer als gedacht** (zwei Drittel falsche Ausschlüsse). Mit Flash in der Hälfte der Runden fällt das auf 1–4 von 32, und die gefundene Stärke liegt bei +15 % näher an der Wahrheit (×1,19–1,27 statt ×1,39–1,40, wahr ×1,15). Der blinde A/B-Test am Ende bleibt trotzdem Pflicht (B6).

### B5. „Test-Kurve“-Button

Test-Kurve = natürliche Kurve, 15 % steigend, dann der blinde A/B-Test; 30 Spieler je Zelle; Ergebnis: besser / schlechter / kein Unterschied.

| Hand | A/B-Paare | ohne Vorliebe | wahre +15 % | wahre +25 % |
| --- | --- | --- | --- | --- |
| Standard | Klick-Mix | 0 / 0 / 30 | 28 / 0 / 2 | 29 / 0 / 1 |
| | Flash-Paare (App) | 0 / **15** / 15 | 23 / 0 / 7 | 29 / 0 / 1 |
| Schnell | Klick-Mix | 0 / 21 / 9 | 29 / 0 / 1 | 30 / 0 / 0 |
| | Flash-Paare (App) | 0 / **29** / 1 | 26 / 0 / 4 | 30 / 0 / 0 |

Vorher (Legacy, `AssessmentTest.trialCurve` neu gerechnet, große Ziele): ohne Vorliebe 25 von 30 „schlechter“, bei wahrer +15 % 17–19 „besser“, bei +25 % 8–15 „besser“.

Lesart: Mit Flash-Paaren wird der Button **strenger gegenüber Spielern ohne Vorliebe** (15–29 von 30 „schlechter“ statt 0–21) und verliert bei einer echten kleinen Vorliebe etwas Trefferquote (23–26 statt 28–29 „besser“). Das ist die richtige Richtung für einen Test, der nur schützen soll; er misst aber weiterhin eine ungewohnte Hand, deshalb ist „schlechter“ auch für eine Kurve möglich, an die man sich gewöhnen würde (ASSESSMENT B4). Der Button wählt seine Richtung weiterhin aus der gemessenen Verstärkung; die Spieler hier haben die richtige Richtung per Konstruktion, das ist also eine Obergrenze.

### B6. Blinder A/B-Test: Falschurteile und Trefferquote

Identische Arme (160 Läufe je Zelle), danach eine Kurve in der Stärke der wahren Vorliebe (60 Läufe je Zelle):

| Hand | A/B-Paare | identisch: besser / schlechter | wahre −25 %: besser | wahre +25 %: besser |
| --- | --- | --- | --- | --- |
| Standard | Klick-Mix | 1,3 % / 1,3 % | 62 % | 98 % |
| | Flash in der Hälfte | 2,5 % / 0,6 % | **100 %** | 97 % |
| Schnell | Klick-Mix | 1,9 % / 1,9 % | 100 % | 95 % |
| | Flash in der Hälfte | 1,9 % / 1,9 % | 100 % | 95 % |

Falsche Urteile ändern sich innerhalb des Stichprobenfehlers nicht (etwa 2 %); die Trefferquote steigt dort, wo die Klick-Paare schwach waren (Standard-Hand, −25 %: 62 → 100 %).

## E. Crosshair und Zielhöhen (M11, M12)

### E1. Zielhöhen: vorher voll 3D, nachher Kopfhöhe

**Vorher** ist die Verteilung der ersten Trainerversionen: jedes Ziel gleichverteilt im ±25°-Pitchfeld (Flick, Switching), Flash-Cues bis 30° hoch, Tracking mit halber Vertikalbewegung, keine Karte. **Nachher** ist der Profilstandard: Kamera auf Augenhöhe 1,6 m, Kopfmitte 1,65 m, 88 % der Ziele mit Standardabweichung 1,5° um Kopfhöhe, 12 % auf anderer Ebene (3–10°, 40 % davon unterhalb), Flash-Cues bis 12°, Tracking fast rein horizontal, ein Teil der Flick- und Switching-Ziele (30 %) an Ecken und Türöffnungen der Mini-Map. Die Höhen und Anteile sind **Annahmen** (Riot veröffentlicht keine Augen- oder Kopfhöhe, keine Statistik der Höhenunterschiede; `verified: false`). Kopfgroße Ziele, Standard-Hand mit Optimum 35/50/70 cm/360, Rauschen ×1,0. Reproduzierbar mit `VerticalExperimentsTest` (`AIMTUNE_REVIEW=1`, 30 Runden bzw. Spieler pro Zelle, Berichte `core/build/reports/harness/vertical-*.txt`).

**Beste Sensitivity der simulierten Spieler** (Maximum einer Parabel durch 9 Gitterpunkte von 20–160 cm/360, 30 Runden je Punkt; ein Wert hat etwa ±10 % Rauschen):

| Spieler | Szenario | vorher | nachher |
| --- | --- | --- | --- |
| 35 | Flick / Switching / Micro | 37,8 / 36,1 / 42,2 | 31,4 / 33,8 / 38,0 |
| 50 | Flick / Switching / Micro | 45,8 / 43,7 / 65,7 | 47,3 / 49,3 / 58,6 |
| 70 | Flick / Switching / Micro | 63,9 / 60,0 / 77,9 | 67,8 / 63,1 / 83,2 |
| 50 | Tracking / Flash („manchmal“) | 20,0 (Rand) / 34,9 | 23,9 / 36,1 |
| 70 | Tracking / Flash („manchmal“) | 30,2 / 41,5 | 36,0 / 45,5 |

Lesart: **keine einheitliche Verschiebung der empfohlenen Sensitivity**. Bei Flick, Switching und Micro liegen die Änderungen zwischen −17 % und +13 %, in beiden Richtungen und im Rauschen der Parabelanpassung. Tracking und Flash tendieren zu höheren cm/360 (langsamer): in den gezeigten Zeilen +3 bis +19 %, bei Spieler 35 liegt beides am Gitterrand (20 cm/360) und sagt nichts. Das ist der einzige Hinweis auf einen Trend, und er ist klein gegen das Rauschen. Das Ergebnis ist **nicht stabiler geworden**: die wahre Wirkung der Höhenverteilung auf das Optimum ist in der Simulation kleiner als das Rauschen. Der Grund ist plausibel: Die simulierte Hand hat in beiden Achsen dasselbe Rauschen, die Zielhöhe ändert nur, wie viele der Bewegungen auf der vertikalen Achse stattfinden.

**Woher die Fehler kommen** (Anteil des vertikalen Fehlers am Endpunktfehler der ersten Bewegung, Flick, über alle Sensitivities): vorher 43–47 %, nachher 34–43 %. Die Aufteilung ist also als Messgröße brauchbar (die Fehler eines Spielers aus Bewegungen mit größerem horizontalem Anteil sind überwiegend horizontal), aber der Absolutwert gilt nur für diese simulierte Hand und sagt nichts über Menschen.

**Sensitivity-Finder (Flick + Flash „manchmal“, 15 Spieler pro Zelle, Suchbereich 20–140):**

| | gemischte Wahrheit | Runden | gefunden (Median) | Fehlerfaktor Median / p90 | Konfidenz |
| --- | --- | --- | --- | --- | --- |
| vorher | 46,0 | 15 / 30 | 43,7 / 46,1 | 1,16 / 1,84 · 1,12 / 1,28 | 54 / 61 % |
| nachher | 48,6 | 15 / 30 | 42,6 / 44,5 | 1,20 / 1,52 · 1,15 / 1,43 | 45 / 64 % |

Median-Fehler und Konfidenz sind im Rahmen des Rauschens gleich (n = 15, ein p90 hat nur wenige Läufe). Der Finder ist nach der Änderung nicht besser und nicht schlechter.

**Fehleranalyse der Kurve (Klick-Mix, 30 Spieler pro Zelle, „angeboten (davon richtige Richtung)“):**

| Hand | Runden | Setup | 0 % | −15 % | +15 % | −25 % | +25 % | +50 % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Standard | 15 | vorher / nachher | 0 / 0 | 0 / 0 | 0 / 0 | 1 / 0 | 0 / 0 | 0 / 0 |
| Standard | 30 | vorher / nachher | 0 / 0 | 2 / 0 | 0 / 0 | 25 / 21 | 0 / 0 | 0 / 0 |
| schnell | 15 | vorher / nachher | 0 / 0 | 30 / 30 | 6 / 0 | 30 / 30 | 24 / 17 | 30 / 29 |
| schnell | 30 | vorher / nachher | 0 / 0 | 30 / 30 | 13 / 5 | 30 / 30 | 28 / 28 | 30 / 30 |

Keine falschen Angebote bei flachen Spielern (0 von 30 in allen vier Zeilen, wie vorher). Die Erkennung fallender Kurven bleibt gleich; **steigende schwache Vorlieben (+15 %, +25 % bei 15 Runden) werden mit Kopfhöhe seltener gefunden** (6 → 0 bzw. 24 → 17 von 30). Ich habe die Ursache nicht untersucht; naheliegend ist, dass bei überwiegend horizontalen Bewegungen weniger schnelle, lange Bewegungen mit großem Fehlersignal zusammenkommen. Das ist dieselbe Richtung, in die schon die Flash-Rückkehren die Analyse verschlechtert haben, und kein neuer Grund für die schon bestehende Entscheidung, die Analyse als experimentell zu führen.

**Stärkensuche (24 Runden, Flick + Flash, 15 Spieler pro Zelle, „Stärke im Median, falsch ausgeschlossen“):**

| Hand | Setup | 0 % | +15 % | +25 % | −15 % | −25 % |
| --- | --- | --- | --- | --- | --- | --- |
| Standard | vorher | ×1,03 (1/15) | ×1,23 (14/15) | ×1,33 (15/15) | ×0,86 (10/15) | ×0,79 (15/15) |
| Standard | nachher | ×1,03 (2/15) | ×1,20 (15/15) | ×1,36 (14/15) | ×0,89 (11/15) | ×0,78 (14/15) |
| schnell | vorher | ×1,05 (4/15) | ×1,17 (15/15) | ×1,45 (15/15) | ×0,88 (15/15) | ×0,75 (15/15) |
| schnell | nachher | ×1,01 (1/15) | ×1,19 (14/15) | ×1,38 (15/15) | ×0,88 (14/15) | ×0,78 (14/15) |

Gleich im Rahmen des Rauschens: flache Spieler werden weiter selten ausgeschlossen (1–4 von 15), echte Vorlieben werden gefunden und fast immer als „keine 100 %“ ausgeschlossen.

**Blinder A/B-Test (gleiche Arme: n = 120; echte Kurve: n = 60):**

| Hand | Setup | gleiche Arme: besser / schlechter | −25 % bestätigt | +25 % bestätigt |
| --- | --- | --- | --- | --- |
| Standard | vorher | 0,0 / 2,5 % | 97 % | 97 % |
| Standard | nachher | 1,7 / 0,8 % | 100 % | 100 % |
| schnell | vorher | 3,3 / 0,8 % | 98 % | 97 % |
| schnell | nachher | 3,3 / 1,7 % | 100 % | 97 % |

Falschurteile bleiben bei etwa 2–5 % insgesamt (unverändert; der Stichprobenfehler bei n = 120 liegt bei ±3 Punkten), echte Kurven werden weiter in 97–100 % bestätigt.

**Gesamtbild:** Die Höhenverteilung macht den Trainer realistischer (Ziele stehen auf dem Boden, die meisten Bewegungen sind horizontal, es gibt eine Karte mit Ecken und Türen), aber **sie ändert die Ergebnisse der Simulation nicht messbar**, außer dass steigende schwache Kurvenvorlieben in der Fehleranalyse bei 15 Runden schlechter erkannt werden. Das ist kein Beleg für Realismus: die simulierte Hand ist in beiden Achsen gleich, die Verteilung der Zielhöhen ist angenommen, und es gibt keine echten Aufnahmen, an denen man prüfen könnte, ob Menschen vertikal anders treffen. Was die Änderung leistet, ist, dass die Übungsbewegungen den Bewegungen im Spiel ähnlicher sind (Realismus der Aufgabe, nicht der Zahlen).

### E2. Crosshair

Das Crosshair ändert **keine Zahl**: es wird nur gezeichnet, nie in Metriken, Score oder Simulation verwendet (die simulierte Hand sieht kein Fadenkreuz). Es verbessert den **Realismus der Aufgabe**: Spieler zielen mit dem eigenen Valorant-Crosshair (Code importieren oder im Editor bauen, Größen in Bildschirmpixeln der nativen Auflösung, mehrere Profile). Geprüft ist die Geometrie gegen feste Referenzmaße und Bilder (`CrosshairLayoutTest`: Pixellängen, Lücken, Dicke, Outline) und der Code über 17 echte Profil­codes (`CrosshairCodecTest`: Code → Modell → Code ist identisch). **Nicht geprüft** ist der Vergleich mit dem Spiel selbst: das Format ist inoffiziell (Community-Parser, nicht von Riot), die RGB-Werte der Farbvoreinstellungen und die Rundung ungerader Dicken auf geraden Bildschirmen sind Annahmen (docs/CROSSHAIR.md, „Open points“). Das muss der Spieler einmal selbst prüfen: eigenen Code aus Valorant importieren und das Bild in gleicher Auflösung nebeneinanderlegen.

Folge für die Ampel: die neue Zeile „Zielverteilung (Höhe, Karte)“ bleibt 🟡; das Crosshair ist als eigene Zeile „Crosshair“ 🟡 (Format und Pixelgenauigkeit nicht gegen das Spiel geprüft).

## C und D: was sich nicht geändert hat

* **Realismus (ASSESSMENT C):** unverändert rot. Neu hinzu kommen die Flash-Annahmen (Flug vor dem Windup, Randwinkel, der Rest der Blickwinkel-Regel kommt nur aus Zweitquellen). Die belegten Windups (0,3–0,6 s, maximale Blendzeit 1,5–2,25 s) stammen von den Fähigkeits-Seiten des Valorant-Wikis, Patch-Stand mit Datum, nicht aus einem Riot-Dokument.
* **Simulierte Hand:** Bei kopfgroßen Zielen erreichen Bewegungen in der Simulation 3–5 in/s, das ist langsam gegenüber echten Flicks. Die Aussage „Flash hilft der Analyse bei der Standard-Hand“ (B3) ist deshalb vor allem ein Befund über die Simulation.
* **Zirkularität (D1):** Der simulierte Spieler dreht per Minimum-Jerk-Bewegung weg und reagiert auf den Pop; er hört die neuen Sounds nicht und passt sich nicht an. Alle Flash-Zahlen prüfen, ob die Mechanik und die Statistik zusammenpassen, nicht ob Menschen so reagieren.
* **Echte Daten (D4):** weiter der entscheidende Punkt. Der Forschungsexport enthält die Flash-Ereignisse; für die Fragen aus D4 sind Aufnahmen von 10 Spielern mit Flash-Runden jetzt aussagekräftiger.

## Handlungsoptionen

In der Reihenfolge, die ich empfehle (Aufwand: klein / mittel / groß):

1. **Flash-Zeiten im Spiel prüfen** (klein, du): Schießstand, Pop-Delay und Sichtbarkeit des Flugs messen, den Randwinkel beim Wegdrehen prüfen, dann `trainer.flash.verified` setzen. Das ist die größte offene Annahme im neuen Szenario und verschiebt das Optimum.
2. **Flash-Runden mit echter Maus spielen und beurteilen** (klein, du): Ist der Knall-Sound hilfreich, passt die Gegnerposition, ist 1,5 s Weißbild plausibel? Danach Standardwerte nachziehen.
3. **Bei 8 Runden keine Flash-Runden spielen** (klein, Code): Die Auswertung zeigt, dass Flash bei 8 Runden die Schätzung am stärksten verschlechtert (p90 94 % bei „oft“). Option: die ersten ~10 Runden eines Laufs Flick, danach erst Flash, oder „oft“ als Standard vermeiden (jetzt ist „manchmal“ Standard). Nutzen: vermeidet die groben Ausreißer; Preis: das Flash-Optimum wird erst später sichtbar.
4. **Stärkensuche mit Flash beibehalten und den A/B-Test nicht abkürzen** (nichts zu tun, nur festhalten): Die Suche allein ist selbst mit Flash bei 4 von 32 flachen Spielern falsch positiv; der A/B-Abschluss bleibt verpflichtend.
5. **Fehleranalyse nicht auf Flash ausdehnen** (nichts zu tun): Wer es doch will (die Standard-Hand wird dadurch bewertbar), müsste die Szenario-Unterschiede im Fehlerbias modellieren (Normierung je Szenario geht nicht, weil der Geschwindigkeitsgewinn genau aus dem Unterschied zwischen den Szenarien kommt). Aufwand: groß, Nutzen unklar.
6. **Steigende Kurven aus der Fehleranalyse: A/B mit Klick-Paaren für diesen Fall?** (mittel): Die +50-%-Bestätigung sinkt mit Flash-Paaren (19 → 6 bei 8 Runden). Option: nur bei Kurven, die die Analyse als *steigend* vorschlägt, Klick- und Flash-Paare mischen. Nutzen: kleine Zahl von Fällen; vorerst nicht nötig, weil die Stärkensuche der empfohlene Weg ist.
7. **Simulierte Hand an echten Aufnahmen kalibrieren** (groß, braucht Daten): 10 Spieler aufnehmen (ASSESSMENT Empfehlung 5), die Hand so anpassen, dass einfache Bewegungen bei kleinen Zielen realistische Geschwindigkeiten haben, und B3/B4 neu rechnen. Erst damit sind die Aussagen über die Geschwindigkeitsbereiche belastbar.
8. **Positionierung:** Der Text der App und der README sagen weiter „Bereich, kein Optimum“ und „Kurven experimentell“; dazu kommt: „Flash-Rückkehren sind ein Modell, kein Spielersatz“. (klein)
9. **Test-Kurve-Button umbenennen oder beide Richtungen anbieten** (klein): bleibt aus ASSESSMENT B4 offen; mit Flash-Paaren ist er strenger, aber seine Richtung kommt weiterhin aus der gemessenen Verstärkung.

## Was ich nicht beantworten kann

* Alles hängt an einer Handmodell-Annahme; relative Aussagen (Vorher/Nachher unter gleichen Bedingungen) sind belastbarer als absolute (Optimum in cm, Prozent in Spielerlauf).
* Ob echte Spieler Beschleunigungsvorlieben dieser Größe haben, ist unbekannt; die Effektstärken 15–50 % sind illustrativ.
* Ob die Flash-Mechanik (Pop-Delay, Randwinkel, Blendregel, Rückkehr-Position) dem Spiel entspricht, ist nur teilweise belegt.
* Prozentwerte haben ±15 Punkte Stichprobenfehler. Unterschiede, auf die ich mich oben stütze, liegen deutlich darüber (z. B. 23/32 gegen 1/32, 12/30 gegen 26/30); kleinere (z. B. 62 % gegen 65 %) behaupte ich nicht.
