# Curve finder audit

Branch `audit/curve-finder`, 2026-10-22. Audit and plan only; no production code was changed.

Evidence files (all on this branch):

| File | What | How to regenerate |
|---|---|---|
| `docs/audit/forensics.txt` | Real data, copy of `%APPDATA%\AimTune\aimtune.db` from 2026-10-19 | `AIMTUNE_REAL_DB=<copy> ./gradlew.bat :app:test --tests "*CurveAuditForensics*"` (`app/src/test/.../audit/CurveAuditForensicsTest.kt`) |
| `docs/audit/audit-curve.txt` | Simulation, n = 24 seeds per row (level checks 8) | `AIMTUNE_REVIEW=1 ./gradlew.bat :core:test --tests "*CurveAuditTest*" --rerun-tasks` (`core/src/test/.../harness/review/CurveAuditTest.kt`) |
| `docs/audit/export.txt` | Export vs Raw Accel 1.7.1 formulas | `./gradlew.bat :storage:test --tests "*ExportAuditTest*"` (`storage/src/test/.../audit/ExportAuditTest.kt`) |
| `docs/audit/user-curve-table.txt` | The user's Raw Accel curve computed from the 1.7.1 source | Python script in the session scratchpad (formulas below) |
| `docs/audit/valorant-range-research.md` | Public sources on the Valorant shooting range | Web research, sources cited inside |

The real database was only read through copies; the original's SHA-256 (`9C500A24…DCE25`) was recorded before the audit.

---

## Kurzfassung (Deutsch)

**Was ist passiert?** Die exportierte Kurve (`natural`, decay 0.05, limit 0.70, also 30 % weniger Gain bei schnellen Bewegungen) stammt **nicht** aus „Letzte Analyse“, sondern aus **„Letzte Suche“**, der blinden Stärkesuche. Belege:
- Die Datei hat genau die Form der Suchkurven, und die Rekonstruktion aus deinen Daten ergibt exakt diese Kurve.
- „Letzte Analyse“ findet auf deinen Daten gar keine Kurve: Der gewünschte Gain ist in allen Geschwindigkeitsbereichen ≈ 1,00.

**Warum fällt die Kurve? Drei Ursachen greifen ineinander.**

1. **Du hast nicht mit 0,335 getestet, sondern mit ≈ 0,474** (34,4 cm/360).
   - Die Kurvensuche startet immer beim Optimum des Sensitivity-Finders (`AppContext.kt:271-275`). Das lag bei 0,494, mit nur 9 Runden und 17 % Konfidenz.
   - Deine Hand ist auf 0,75 × 0,335 plus eine steigende Kurve eingestellt. Gemessen an der getesteten Sensitivity sind das nur etwa 53–63 % des Gains, je nach Tempo.
   - Die Hand war also im Test um etwa den Faktor 1,6–1,9 zu „schnell“ eingestellt.
2. **Die Stärkesuche kennt keinen Gesamtfaktor.**
   - Sie spielt nur `natural(0.05, Stärke)` bei Faktor 1. „Weniger Gain bei schnellen Bewegungen“ ist damit die einzige Möglichkeit, insgesamt weniger Gain zu bekommen.
   - In der Simulation verlernt eine so angepasste Hand im Trainer die langsamen Bewegungen schnell, die schnellen aber kaum: Nach 15 Runden ist der Faktor ≈ 1,0 bei 3 in/s, aber noch ≈ 0,7 bei 20–50 in/s. Genau dort setzt eine fallende Kurve an.
   - In deinen Suchrunden sinkt der Score mit dem tatsächlich gelieferten Gain (r = −0,32, n = 50).
3. **„Letzte Suche“ exportiert ohne Bestätigung.**
   - Das Intervall der Suche reichte von 0,70 bis 1,75 und schloss „keine Kurve“ also nicht aus. Die Live-Suche hätte deshalb nichts angeboten.
   - Der einzige Blindtest dieser Kurve (Sitzung 13, 12 Paare) ergab „kein Unterschied“: −5,8 Punkte, Intervall −21 bis +9, eher schlechter.
   - „Letzte Suche“ bietet die Kurve trotzdem zum Export an (`StoredSearches.kt:19`).

**Bug, Designfehler oder echtes Signal?**
- **Bug:** Start bei einer unsicheren Sensitivity, und Export ohne bzw. gegen den Blindtest.
- **Designfehler:** Stärke und Gesamtniveau sind nicht getrennt, und die Anpassung der Hand wird nicht berücksichtigt.
- **Echtes Signal:** Dafür, dass du eine fallende Kurve willst, gibt es keinen Beleg. Der Blindtest war neutral bis negativ, und die Fehleranalyse ist flach.
- In der Simulation reproduziert ein Spieler mit deiner Anpassung und deinem Testniveau das Ergebnis:
  - In 50–58 % der Läufe endet die Suche fallend.
  - „Letzte Suche“ bietet in 92–96 % der Läufe eine Kurve an, und davon schadet etwa die Hälfte (Median −22 Punkte).

**Empfehlung, in dieser Reihenfolge:**
1. **Sofort (M16, klein):**
   - Export nur noch nach bestandenem Blindtest. „Letzte Suche“ und „Letzte Analyse“ dürfen nur einen Blindtest starten.
   - Kurventests laufen bei deiner eingestellten Spiel-Sensitivity, außer der Finder ist sicher.
   - Die Suche wird zweistufig: erst das Niveau, dann die Form. In der Simulation verschwinden damit alle fallenden Ergebnisse bei angepassten Spielern, und ein echter fallender Wunsch wird weiter zu 92 % gefunden.
   - Fallende Kurven nur mit starkem Beleg (Intervall-Obergrenze < 0,90).
2. **Danach (M17/M18):**
   - Raw Accel vollständig abbilden: Gain-Modus, alle Cap-Modi, Import. Heute kann die App dein Profil weder darstellen noch korrekt einlesen; der Import liest es still um bis zu 2,5 % falsch.
   - Neuer Modus „Bestehende Kurve verfeinern“: Kalibrierung mit deiner eigenen Kurve, lokale Blindsuche (Niveau, Cap, Übergang), Blindtest gegen deine Kurve, und ein Export, der dein restliches Profil unverändert lässt.
3. **Realismus:**
   - Strafende Ziele im Range-Modus und als Teil von Micro und Switching, aber nicht in der Fehleranalyse der Kurve. Die meisten Bot-Parameter von Valorant sind nicht dokumentiert und müssen gemessen werden (siehe Abschnitt 7).

**Bis dahin:** Lösche die exportierte `natural 0.70`-Datei bzw. nutze sie nicht, und bleib bei deinem eigenen Profil.

---

## Executive summary

**The falling curve came from "Last search", not from "Last analysis".**
- The exported `settings.json` (natural, decay 0.05, limit 0.70, Velocity, Output DPI 800) is the blind strength-search family at its lower range edge.
- Pooling the 50 stored search rounds reproduces it exactly (forensics B).
- The error analysis behind "Last analysis" finds no curve on the user's data: the wanted gain is 0.99–1.01 in all 10 bins.

**Root cause: a level mismatch, expressed through a one-parameter shape search, then exported without a guard.**

1. **The curve rounds were played at 34.4 cm/360 (Valorant 0.474), not at 0.335.**
   - `AppContext.startAccelRun` always starts from the sensitivity optimizer's optimum, here 33.05 cm/360 from 9 blind rounds at 17 % confidence.
   - Relative to that level, the user's hand is adapted to 0.75 × curve × 34.4/48.7 = 0.53–0.63 of the played gain.
2. **The strength search has no overall factor.**
   - Lowering the gain at speed is the only way it can lower the gain at all.
   - The simulated adapted hand quickly re-learns slow movements in the trainer but keeps the low high-speed gain (adaptation factor ≈ 1.0 at 3 in/s and ≈ 0.7 at 20–50 in/s after 15 rounds), which looks like a falling preference.
3. **"Last search" offers export whenever the best strength is a curve,** even when the interval includes "no curve" (here 0.70–1.75, 50 % confidence) and after the user's own blind A/B of that curve gave NO_DIFFERENCE (−5.8 points, CI −21.0 to +9.4).

**Reproduction.** A simulated player adapted like the user and tested at the user's real level ends falling in 50–58 % of strength searches. "Last search" offers a curve in 92–96 % of runs, and 50–58 % of those offers are harmful (median −22 points). The live path with its A/B test exports none of them.

**There is no evidence of a real falling preference.**

**Recommendation:**
- Ship a small safety milestone (M16):
  - export only after a passed blind A/B;
  - test curves at the user's configured sensitivity unless the finder is confident;
  - make the strength search two-stage (level first, then shape);
  - require an interval below 0.90 for falling curves.
- In simulation, the two-stage search removes every falling result for adapted and flat hands, while a true falling preference is still exported in 92 % of runs.
- Its cost is weaker detection of true rising preferences (100 % → 42 % exported), which needs one more gate before it ships.
- Then make the Raw Accel model complete (Gain mode, all cap modes, import that preserves the profile), and build the "refine an existing curve" mode on top of it.

---

## Findings

| # | Severity | Finding | Evidence | Effect on results |
|---|---|---|---|---|
| F1 | **critical** (bug) | Curve runs start at the sensitivity optimizer's optimum whatever its confidence, not at the user's in-game sensitivity, and the UI does not show this as a change of sensitivity. | `app/.../AppContext.kt:271-275` (`base = stored.config.copy(cm360 = result.optimumCm360)`). Real data: optimum 33.05 cm/360 = 0.494, CI 21.2–46.8 cm, conf 17 %, 9 rounds (forensics G). Curve sessions s7–s16 at 32.9–34.4 cm/360 (forensics A). | Tests a hand at 1.4× its usual sensitivity, and about 1.6–1.9× the gain it is adapted to. Every curve result is measured at the wrong level. |
| F2 | **critical** (bug) | "Last search" offers export and copy whenever the best strength is a curve, even if the interval includes 1 and even if a completed blind A/B was negative. "Last analysis" also exports untested. | `app/.../accel/StoredSearches.kt:18-19` (`offer`) vs `:22` (`clear`). `AccelLastSearchScreen.kt:32,38`, `AccelLastAnalysisScreen.kt:45,55`, `AccelFitScreen.kt:127-139`. Real: interval 0.70–1.75, excludesPlain false, A/B s13 NO_DIFFERENCE −5.79 (forensics B, C). Sim "last" column: FLAT 63 % offered, 42 % harmful; ADAPTED_HIGH 92 % offered, 50 % harmful, median −21.9 (audit-curve 1b, 3). | This is the path that produced the user's file. The live search and the A/B both rejected the curve. |
| F3 | **major** (design) | The strength search has one parameter (natural limit) at factor 1. Any wish for a different overall level can only show up as a falling or rising shape. | `core/.../accel/CurveStrength.kt:57-69` (`strengthAccel`, `sensitivityFactor = 1.0`). Real: corr(ln strength, ln effective gain) = 0.88; score vs ln(effective gain) slope −39.5, r = −0.32, negative in all four scenarios (forensics E7). Sim: two-stage (level first) removes all falling results for adapted players (audit-curve 3, O4). | Level errors (F1, adaptation) become falling curves. |
| F4 | **major** (design) | Hand adaptation is not part of the design. A hand used to a curve re-adapts slow movements within a session but keeps its high-speed calibration, which creates a transient "falling" preference. Searches are pooled over days (3 sessions here), mixing adaptation states. | Sim adaptation factor (ADAPTED_HIGH): at start 0.62/0.63/0.66/0.70 at 3/20/30/50 in/s; after 15 plain rounds 1.00/0.73/0.68/0.70 (audit-curve 3). Pooling: `StoredSearches.pool`, 50 rounds from s12, s13, s14 (forensics B). | Strength 0.70 matches the remaining high-speed factor of about 0.7. The finder measures the aftereffect, not a preference. |
| F5 | **major** (gap) | The app cannot represent Raw Accel's Gain form, the `in_out` and `input` cap modes, or Output DPI as a base. `parse` silently misreads such files, and the export writes a new profile that drops everything else. | Velocity only: `core/.../accel/AccelCurve.kt:9-14`. Export hardcodes Gain false and cap mode output: `storage/.../RawAccelExport.kt:165,179`. `parse` (`:71-87`) reads the user's file as Velocity, cap 1.6: 1.2205 vs 1.2520 at 62.5 in/s, 1.4050 vs 1.3825 at 100 in/s (export.txt §4). Lost fields: export.txt §4–5. | The user's own curve cannot be tested, refined or re-exported. An import would be up to 2.5 % wrong without warning. |
| F6 | **major** (design) | The optimizer exploits the range edge: 26 of 50 search rounds are at strength ≤ 0.80 (22 at exactly 0.70) and only 2 are plain. The "plain" baseline rests on 2 rounds, both flash. | forensics B (per-strength counts), E4 (scenario mix per band). | The edge result rests on a thin comparison and on the scenario mix. |
| F7 | **major** (design) | The error analysis cannot separate distance from speed. Real corr(ln distance, ln speed) is 0.76 (calibration) and 0.91 (search rounds). Within distance quintiles, speed still correlates with first error (mean r 0.43). A 5 % range effect makes the analysis offer rising curves to most flat players. | forensics E1. Sim FLAT_RANGE: legacy offers 100 %, exports 50 %; bounded exports 8 % with 38 % harmful offers (audit-curve 1a). | Not the cause here (real analysis flat). Restricting to rising shapes would not stop these false offers, because they are rising. |
| F8 | minor | The calibration covers only 3.0–21.5 in/s (p5–p95 3.2–12.9). 58 % of the distance moved is below 5 in/s, while the flash rounds of the search move 47 % of their distance at 20–40 in/s. | forensics D, E7 speed distributions. | The error analysis is blind where FPS curves act (flicks, flash turns). |
| F9 | minor | Onboarding says `aimStyle: WRIST`, pad 32 cm; the user says arm aiming on a normal pad. | `%APPDATA%\AimTune\settings.json` (copy). Search range: `searchRange(aimStyle, cm360, pad)` in AppContext. | Shifts the sensitivity search range and the plausibility checks. |
| F10 | minor | Export details: the device DPI is written as an int (`RawAccelExport.kt:53`, at most 0.1 % effect for a non-integer DPI). An uncapped classic writes `Cap / Jump.y = 0`, whose meaning in Raw Accel is unverified. `ALGORITHMS.md:279` says "10 pairs", but the code has 12 (`AccelAb.kt:42`). | export.txt §1–2. | Negligible for this case. |
| F11 | minor (harness gap) | The simulated strength search plays no flash rounds (the app plays 50 %), and the simulated player is not validated against real players (R10 open). | `GateHarness.gateStrength`, CurveAuditTest notes. | Simulation rates are indicative, not exact. |
| — | not a cause | Report timestamps are clean: no zero or negative intervals; median 1.01 ms (forensics E5). Gross first error is near 0 (+0.9 %, forensics F). | | |
| — | not a cause | Within-session drift is present but mixed: s13 54.2 → 43.6 (first vs last third), s12 34.9 → 48.7 (forensics E6). | | Adds noise. It has no consistent direction. |

**What is right.** The export check found these parts correct:
- What the A/B test plays equals what is exported for every family the app produces (classic including falling caps, natural, power, lut, strength curves), with the Raw Accel 1.7.1 formulas re-implemented from the JSON fields. The worst error is 4e-8, which is float rounding (export.txt §1).
- The speed axis matches Raw Accel (counts·1000/DPI/ms).
- Both lut ends are held flat, as in Raw Accel (`common/accel-lookup.hpp`, 1.7.1).

---

## Root cause of the falling curve

### What the user actually ran (forensics A–D, G)
- **Sensitivity finder:** 11 rounds in 4 sessions (9 blind, mostly flick). Optimum 33.05 cm/360 = Valorant 0.494, CI 0.349–0.771, confidence 17 %. The user's 0.335 (48.74 cm/360) is just outside the interval.
- **Curve sessions:**
  - s5/s6 at 46.5 cm/360.
  - s7–s14 at 32.9–34.4 cm/360, which is Valorant 0.474–0.495.
  - s16 at 33.1 cm/360.
  - The setup copy says 0.335, but the trainer ran at the optimizer's value (F1).
- **Searches:** s12 (18 rounds), s13 (24 search rounds plus a 12-pair A/B), s14 (8 rounds, kind `accel_search`). 50 % flash in each.
- **Export (2026-10-18 20:15 CEST):**
  - No search round started after 18:15 UTC, so the pooled state at export equals today's: best 0.70, CI 0.70–1.75, confidence 50 %, excludesPlain false, offer natural 0.70.
  - The live search screen would not have offered it.
  - "Last search" did, labelled "unclear".
- **The only A/B of that curve** (s13, 12 pairs): NO_DIFFERENCE, mean −5.79 points, CI −20.96 to +9.37.
- **"Last analysis"** (13 plain rounds from s7 and s16, 429 movements): no curve (`NOT_BETTER_THAN_NONE`). The ten bins:

| Speed (in/s) | 3.2 | 3.9 | 4.7 | 5.3 | 6.4 | 7.0 | 7.9 | 9.0 | 10.5 | 13.1 |
|---|---|---|---|---|---|---|---|---|---|---|
| n | 42 | 43 | 43 | 43 | 43 | 43 | 43 | 43 | 43 | 43 |
| Wanted gain | 0.988 | 0.988 | 1.010 | 1.014 | 0.994 | 0.999 | 1.003 | 1.002 | 1.012 | 0.987 |

  - Best family: natural (BIC gain −6.8, far below 8).
  - The family fits: classic −9.2, natural −6.8, power −6.9.

### The gain the user's hand is used to (Raw Accel 1.7.1, from source)

The user's profile is classic, Gain form, m = 2, inputOffset 10, cap mode in_out (62.5 in/s, 1.6), Output DPI 600 / device DPI 800. Its parameters from the 1.7.1 source:
- `acceleration` = 0.3/52.5 = 0.005714, derived from the cap point: `accel-classic.hpp` l.89-103 and l.165-168.
- In Gain form the cap's y is the **slope**, not the multiplier. Beyond the cap the multiplier is 1 + 21.75/x … → 1.6 (l.145-150).
- The final factor is the curve × output DPI / device DPI (`rawaccel.hpp` l.412-413, 429; `driver/driver.cpp` l.376).

| Speed (in/s) | ≤10 | 20 | 30 | 50 | 62.5 | 100 |
|---|---|---|---|---|---|---|
| Multiplier | 1.000 | 1.029 | 1.076 | 1.183 | 1.252 | 1.383 |
| × 0.75 vs plain 0.335 | 0.750 | 0.771 | 0.807 | 0.887 | 0.939 | 1.037 |
| × 0.75 × 34.4/48.7 vs the played 0.474 | 0.530 | 0.545 | 0.570 | 0.627 | 0.663 | 0.733 |

So even the user's "rising" curve is below plain 0.335 up to about 85 in/s, and it is about half the played gain at the speeds where most trainer movement happens.

### How each candidate cause contributes

| Candidate | Size in the user's data | Verdict |
|---|---|---|
| Level mismatch (F1 + adaptation) | Played gain 1.6–1.9× the adapted gain. Score falls with effective gain: slope −39.5 per ln unit, r = −0.32, all scenarios negative. | **Main cause** (bug + design). |
| Shape-only search (F3) | corr(ln strength, ln effective gain) 0.88. The ≤ 0.80 band delivers effective gain 0.84 against 1.0 for plain. | **Main mechanism** (design). |
| Adaptation aftereffect (F4) | Not measurable directly in real data. In sim, ≈ 0.7 high-speed factor left after 15 rounds, which is what strength 0.70 delivers. | Strong contributor (design). Model-based. |
| Unguarded export (F2) | Interval includes 1, and the A/B was −5.8. | **Why it reached the user** (bug). |
| Edge exploitation (F6) | 22 of 50 rounds at 0.70, 2 plain rounds. | Amplifier. |
| Range effect (distance ↔ speed) | r 0.76/0.91; within-distance r(speed, error) 0.43. | Affects only the error analysis, which was flat. Not a cause here. |
| Slow-reach artefact | 234 of 665 reaches < 3 in/s (dropped by the analysis). Mean error −0.04 (< 3) vs +0.04 (3–6). | Handled by the 3 in/s floor. Not a cause. |
| Undershoot of rushed reaches | Error per speed quintile +0.077 … +0.063 (calibration). Search rounds −0.024 → +0.036 with speed. | Small. It points to **overshoot** at speed, not undershoot, which fits a too-high gain. Not a cause of the falling shape. |
| Scenario mix / flash | Flash is 11 of 26 rounds at ≤ 0.80 and 2 of 2 plain rounds. Flash moves 47 % of its distance at 20–40 in/s, where the natural curve acts. | Contributor: the comparison against plain is flash-only. |
| Timestamp quality | 0 % zero intervals, p99 21 ms. | Not a cause. |
| Fatigue / drift | Mixed directions per session. | Noise. |
| Real falling preference | A/B −5.8 (CI −21..+9). Error analysis flat. | **No evidence.** |

### Simulation (audit-curve.txt)

The ADAPTED hand is flat but adapted to 0.75 × the user's curve, tested at plain. ADAPTED_HIGH is adapted to 0.53 × the curve, the user's real level. Strength search 0.70–1.75 unless noted, 24 seeds per row.

| Model | Ends falling | At low edge | Live offer (falling) | Exported after A/B | "Last search" offer | … harmful | Median "last" benefit |
|---|---|---|---|---|---|---|---|
| FLAT | 8 % | 0 % | 38 % (4 %) | 0 % | 63 % | 42 % | −2.2 |
| ADAPTED | 29 % | 4 % | 25 % (4 %) | 8 % rising | 92 % | 29 % | +13.1 |
| ADAPTED (0.70–1.45) | 46 % | 0 % | 21 % (8 %) | 4 % | 79 % | 46 % | −9.8 |
| ADAPTED_HIGH | 50 % | 29 % | 29 % (21 %) | 0 % | 92 % | 50 % | −21.9 |
| ADAPTED_HIGH (0.70–1.45) | 58 % | 13 % | 38 % (29 %) | 0 % | 96 % | 58 % | −24.8 |
| FALLING (true −30 %) | 100 % | 71 % | 100 % (100 %) | 100 % | 100 % | 0 % | +60.3 |
| RISING (true +30 %) | 0 % | 0 % | 100 % (0 %) | 100 % | 100 % | 0 % | +49.3 |

**Level check (12-pair paired benefit, 8 seeds).** For ADAPTED_HIGH:

| Arm | Benefit | Positive seeds |
|---|---|---|
| Constant factor 0.70, no curve | +8.4 | 8/8 |
| User curve at 0.53 | +6.4 | |
| Rising natural 1.3 at factor 0.6 | +6.6 | 8/8 |
| Falling natural 0.70 at factor 1 | −3.7 | 3/8 |

This hand wants a **lower level**, not a falling shape. The falling curve is the closest the search can get, and a blind A/B does not confirm it.

**Sensitivity finder in the simulation.** It moves an adapted hand toward lower sensitivity: ADAPTED_HIGH recommends ×1.42 cm/360, ADAPTED ×1.08 and FLAT ×0.95. The user's real finder went the other way (0.494). The real result is low-confidence (17 %, 9 rounds), and the simulated player is unvalidated (F11), so this stays an open question (Q2).

---

## Options and recommendation

### Task 4: encoding the FPS intent

All options were measured on the same seeds (audit-curve.txt §2 and §3). "Exported" means offered and A/B ACCEL_BETTER at α 0.01; "harmful" means A/B worse or paired benefit < 0.

| Option | Effect on adapted / flat hands | Detection of true preferences | Effort | Risk |
|---|---|---|---|---|
| O1 rising-or-flat strength range (1.00–1.75) | No falling at all. ADAPTED 88 % offered, 33 % exported, 4 % harmful. | RISING 100 %. **FALLING 4 % offered, 0 % exported**: falling becomes impossible. | S | Breaks "falling still possible". Flat players pile at 1.00 (75 % at the edge). |
| O1e rising-only fit families (error analysis) | No change; the error analysis never offered falling in any model. | FALLING lost. | S | Useless against the real false offers (F7), which are rising. |
| O2 asymmetric interval (falling only if the upper end < 0.90) | ADAPTED_HIGH harmful offers 21 % → 4 %; ADAPTED 4 % → 0 %; FLAT 29 % → 25 %. | FALLING 100 %, RISING 100 % unchanged. | S | Few. |
| O2e falling needs 2× BIC (error analysis) | No change (no falling offers to filter). | Unchanged. | S | Harmless but has no effect. |
| O3 falling needs a 4-point A/B effect | No measurable change. | Unchanged. | S | No effect at 12 pairs. |
| O4 two-stage: level first (sensitivity search at this setup), then strength at that level | **0 % falling** for ADAPTED, ADAPTED_HIGH and ADAPTED_RANGE. ADAPTED_HIGH 63 % exported (+31.5), 0 % harmful offers. | FALLING still 92 % exported. **RISING drops to 42 % exported** (the level stage absorbs part of a rising preference). | M | Lower rising power. Two searches mean more rounds (≈ 20 + 24). |
| O5 mandatory blind A/B before any export | Exported harmful curves: 0 % in every row. "Last search" exports would fall from 63–96 % to the A/B column. | Unchanged; costs 12–24 more rounds per curve. | S | None. This is the cheapest large win. |
| Prior on strength centred at 1 | Not measured: the optimizer has no prior-mean option, so this was not emulated (assumption: similar to O2, with more tuning). | — | M | Tuning on an unvalidated simulator. |
| Base at the user's in-game sensitivity (F1 fix) | Not simulated separately. ADAPTED (correct level) vs ADAPTED_HIGH (wrong level) shows the effect: low-edge results 4 % vs 29 %, harmful last offers 29 % vs 50 %. | — | S | None. |

**Recommendation:** O5 + F1 fix + O2 now (M16), then O4 behind a gate that must restore rising detection.

- **Two ideas for the O4 gate:**
  - Use the user's own sensitivity as the level stage instead of a new search.
  - Search level and strength jointly in 2-D.
- **FPS intent.** Rising is the default expectation. Falling stays possible, but needs O2's stronger interval and a passed A/B.
- **Copy for the UI:** "Falling curves are unusual for FPS; only offered when the evidence is strong".

### Task 5: export correctness and safety

- **Tested equals exported** for every family the app can produce (export.txt §1). Keep the cross-check test permanently (move `ExportAuditTest` §1 into `RawAccelExportTest`).
- **The user's file:**
  - It cannot be represented today (F5).
  - Representing it exactly needs Gain forms for classic and natural, `in_out` and `input` caps, Output DPI and device DPI kept separately, and the speed axis scaled by device DPI.
  - A Velocity lut can approximate it (max error 0.23 % with 32 points, 0.004 % with 257, export.txt §4). That is good for simulation, but it must not be the export, because it would replace the user's editable classic parameters.
- **The unconfirmed exports ("Last search", "Last analysis"):**
  - They should not export. They should show the curve and offer only "Test this curve blind".
  - **Guard if they stay:**
    - only when the interval excludes 1 (`clear`);
    - only when no stored A/B of the same curve was NO_DIFFERENCE or worse;
    - a confirmation dialog;
    - a filename that says "untested".
- **Export safety:**
  - Write a new profile next to the user's profiles, merged into a copy of their file. Never write a lone replacement.
  - Never touch Raw Accel's own folder.
  - Show a diff of the changed fields before saving.

### Task 6: new mode "Refine my curve"

**UI flow (Acceleration menu → "Refine my Raw Accel curve")**

1. **Import.**
   - LibGDX has no file dialog (PROGRESS open question), so accept either a paste of the `settings.json` text from the clipboard or a file the user drops into `%APPDATA%/AimTune/imports/`.
   - Pick the profile if there are several.
   - Show the parsed parameters, the curve chart (multiplier and gain vs in/s, × Output/device DPI), and a self-check: the app's values at reference speeds against the formula table.
   - Refuse, with a clear reason, any settings the trainer cannot reproduce exactly. For v1 these are:
     - by-component mode;
     - non-zero smoothing half-lives;
     - an input speed cap;
     - anisotropic domain or range stretches;
     - vertical parameters that differ from horizontal;
     - jump, synchronous and power-with-gain modes.
   - Warn about rotation and snapping (simulated, see below).
2. **Setup.**
   - Enter the in-game sensitivity (default from settings, 0.335).
   - The trainer plays **sensitivity × the imported curve × Output/device DPI**, exactly what the game gets.
   - The UI states that Raw Accel itself must be off while training.
3. **Warm-up / calibration** (8–10 rounds, user's curve on, not scored for the search).
   - Lets the hand settle into its usual state.
   - Collects speed coverage, including flash rounds, and a baseline score per scenario.
4. **Local blind search, one dimension at a time** (12–16 rounds each, the existing 1-D optimizer on a pseudo-scale like `CurveStrength`):
   - **D1 level:** Output DPI × k, k ∈ [0.85, 1.18], log scale.
   - **D2 high-speed gain:** cap y (in Gain form, the slope at and beyond the cap), ±25 %.
   - **D3 transition:** inputOffset (0–2× its value), or for natural the decay rate.
   - Optionally D2 × D3 jointly later (needs a 2-D GP; ADR 0004's GP is 1-D in ln cm/360).
   - Each stage starts from the best value of the previous one.
   - The user can skip stages.
5. **Confirm.** Blind A/B **candidate vs the user's current curve** (both arms curved; the control arm is the imported curve, not plain), α 0.01 per look, ≥ 6 pairs, ≥ 2 points, max 12 pairs.
6. **Export.**
   - Take the original JSON tree, change only the edited fields (in both whole and vertical parameter blocks), and rename the profile to `<name> (AimTune)` or add it as a second profile.
   - Keep comment keys, devices and every other field byte-for-byte where possible.
   - Write to `exports/rawaccel/`, never into Raw Accel's folder.
   - Show the diff.

**Data model**

- `core/accel`:
  - Add `RawAccelArgs` (mode, gain flag, all curve parameters, cap x/y, cap mode) and the matching curve classes from the 1.7.1 source:
    - `ClassicGain` and `ClassicVelocity` with in/out/io caps;
    - `NaturalGain` and `NaturalVelocity` with an offset;
    - lut with the gain flag.
  - Add `RawAccelProfile` (Output DPI, device DPI, Y/X ratio, rotation, snapping) with a `multiplier(speed)` and a 2-D `apply(dx, dy, dt)` for rotation and Y/X.
  - Each formula gets a test against values computed from the source (pattern: `AccelCurveTest`).
- `storage`:
  - `RawAccelImport` keeps the raw `JsonObject` for a lossless round trip, plus the typed view.
  - `RawAccelExport.patch(original, changes)`.
  - Rounds store the full curve spec (`AccelSpec` gets a versioned encoding of the new classes; schema version 3).
- `app`:
  - `RefineRun` (phases IMPORT, CALIBRATION, SEARCH(D1..D3), TEST, DONE).
  - `AbCondition` generalised to "control curve vs candidate curve".

**Optimizer setup**
- Reuse `SensitivityOptimizer` per stage over a pseudo-scale, as `CurveStrength` does: one parameter in log space, 12–16 rounds, session offsets on.
- Stored stage rounds are pooled only within the same imported profile hash and sensitivity.

**Harness validation**
- **New player models:**
  - REFINE_NULL: hand adapted to the user curve, preference = exactly that curve.
  - REFINE_LEVEL±15 %: wants Output DPI ×0.87 or ×1.15.
  - REFINE_CAP±25 %: wants cap y ±25 %.
  - REFINE_RANGE: the null model with rangeBias 0.05.
- **Gates:**
  - REFINE_NULL: changes exported in ≤ 5 % of runs (500-run null).
  - Level ±15 %: found with the right sign in ≥ 70 % of runs, and A/B-confirmed in ≥ 50 %.
  - Cap ±25 %: report only, no threshold until real data exists.
  - No export with negative paired benefit.
  - `RawAccelProfile` reproduces the user's file to ≤ 1e-9 relative.

### Task 7: realism (moving targets, Range mode)

**Where strafing targets help:**
- **Micro-adjust:** yes. In Valorant, micro corrections are mostly on a target that strafes and stops.
- **Target switching:** yes. Strafe then stop, as in the range.
- **Flick:** a minority (≤ 30 %), with targets that move and then **stop before or around the expected reach end**, the counter-strafe pattern.
- **Flash:** the returning enemy may strafe.
- Moving targets go into the **score-based** paths: the sensitivity GP, the strength and refine searches, and the A/B test.

**Where they would hurt:**
- **The curve error analysis:**
  - A moving target adds a lead/prediction error that grows with target speed and reach time. That is a new speed-correlated term, on top of the range effect (F7).
  - Keep the error analysis on static targets or on targets that are stationary during the whole first reach.
  - Tag such reaches and filter by the tag.
- **Tracking:** already moves.

**Metrics:**
- Endpoint error and first reach must be measured against the target position **at the end time of the submovement** (`TargetPath.positionAt(t_end)`). Today they use the spawn position (`RoundMetrics.kt:201-246`, `directionAngles(spawn.target)`).
- Add a target-relative error (component along the target's motion = lead error, perpendicular = aim error).
- The first-reach rule (≥ 25 % of the distance, ≥ 120 ms) uses the distance to the target at reach start.
- Hits and kills need `fire` on moving targets (tracking ignores shots today).

**Baselines:**
- Moving variants get their own scenario ids (`micro-strafe`, `switch-strafe`), so the per-scenario z-score baselines (`ScoreCalculator.kt:76-95`) are not mixed.
- They need ≥ 3 rounds before they count, as now.

**Simulated player:**
- `clickStep` must aim at a predicted position.
- Reuse the tracking controller's lag and prediction (`trackingLagMs` 150 ms, `trackingPrediction` 0.8, `SimulatedAimer.kt:183-228`): reach to x(t + reaction + reach duration) with prediction error, then correct.
- Add a counter-strafe stop model: the player can wait for the stop.
- Fit both to real `--record` clips before quoting any gate numbers.

**Effect on the finders:**
- **Sensitivity finder:** moving targets favour slightly higher sensitivity for correction speed. Expect the optimum to shift. Re-run `PlateauGateTest` with a mixed schedule.
- **Curve finder:** score-based searches gain realism. The error analysis must exclude moving reaches, or the range and lead confounds grow.

**Range mode (M15).** Research status (docs/audit/valorant-range-research.md; docs/RANGE.md; tools/range-re/out/range-spec.json):

| Parameter | Value | Source | Status |
|---|---|---|---|
| Test options (F3 menu) | Speed Easy/Medium/Hard; Streak Eliminate 50/100; Bots Strafe/Reset; Bot Armor on/off; Infinite Ammo | valorant.fandom.com/wiki/Range (fetched 2026-10-22) | Documented (names only, not effects) |
| Speed meaning | Bots spawn and despawn "within a period of time depending on difficulty" | Fandom Range; gamerevolution (~2020) | Qualitative only |
| Bot count | Eliminate 50 or 100 (the user's clip: 100, REMAINING counter) | Fandom; user clip | Documented / measured |
| Free-range bot count option | 10 / 5 / 1 bots, PC, default 5 | liquipedia.net/valorant/Patch_13.05 | Documented (not the skills test) |
| Board / bot distances | 5, 10, 20, 30, 50 m (target board and single bot) | Fandom Range | Documented, unverified |
| Bot lifetime | Median 0.93 s (n = 7, biased tracker, Eliminate 100, armor on, difficulty unknown) | range-spec.json | Measured, preliminary |
| Gap between bots | Median 5.0 s (n = 6, biased) | range-spec.json | Measured, preliminary |
| Strafe speed / pattern / stop timing | — | none | **Not documented**; 2 tracks only |
| Spawn timing per difficulty | — | none | **Not documented** |
| Bot HP / armor | Players: 100 HP + 50 armor; Vandal headshot 160 one-shots. Bots: assumed the same | valorant-spind.de calculator | Bots **not documented** |
| Head hitbox, agent / eye / crouch height | All agents share one hitbox size (Riot devs via MeinMMO); no numbers | mein-mmo.de | **Not documented** |
| Run speed 6.75 m/s | — | not confirmed by any fetched source | **Not documented** |

The Fandom page shows a **Speed: Easy/Medium/Hard** option. That contradicts RANGE.md's "no Easy/Medium/Hard in the clips": it is likely a menu setting the clips did not show. The record kit should capture it (Q4).

**Measurement plan.** Extend `tools/range-re/RECORDING.md`. Record at ≥ 60 fps, 0.335 @ 800 DPI, FOV 103, yaw 0.07 (48.7 cm/360).
- **Lifetime and interval:** per Speed setting, ≥ 20 bots unshot (lifetime) and ≥ 20 kill → next-spawn gaps. Read the REMAINING counter per frame for timestamps.
- **Strafe:** Strafe mode, a bot at the 10 m and 20 m boards, unshot. Track pixel x per frame and convert to deg/s and m/s with the board distance. Reversal and stop frames give the pattern.
- **Head hitbox:** Reset mode, a stationary bot at 10/20/30 m. Step the crosshair across the head edge in single mouse counts (0.0187° per count) until headshots stop. Width = 2·d·tan(Δ/2).
- **HP / armor:** Vandal shots to kill (body and head) with armor on and off.
- **Eye height and crouch:** the pitch to the head and feet of a bot at a known board distance, standing and crouched.

---

## Proposed milestones

M15 (Range mode) is already in progress and blocked on recordings. The curve fixes come first because they protect users now.

### M16 Curve finder safety (S, about 1–2 sessions)
- [ ] Remove export and copy from "Last search" and "Last analysis"; keep only "Test this curve blind" (`AccelFitScreen.addCurveActions`).
- [ ] "Last search" offers only when `clear`, and never when a stored A/B of the same curve was NO_DIFFERENCE or worse.
- [ ] Curve runs start at the user's configured in-game sensitivity. They use the optimizer's value only if its confidence is ≥ a threshold and the user picks it; the start screen shows the cm/360 and game sensitivity that will be played.
- [ ] Falling strength needs an interval upper end < 0.90 (O2).
- [ ] Docs: ALGORITHMS (strength search, guards, 12 pairs), SPEC, PROGRESS.
- **Acceptance:**
  - Unit tests for the guards.
  - The forensics test on the real DB copy shows no export offer for the user's data.
- **Harness:** CurveAuditTest §1b/§3 re-run.
  - Exported harmful = 0 in all models.
  - FALLING exported ≥ 95 %.
  - RISING exported ≥ 95 %.

### M17 Raw Accel model complete + import (M, about 2–3 sessions)
- [ ] Gain and Velocity forms for classic and natural.
- [ ] Cap modes in / out / io.
- [ ] Natural input offset.
- [ ] lut with the gain flag.
- [ ] Output DPI and device DPI kept separate (speed axis via device DPI).
- [ ] Rotation and Y/X ratio in the trainer.
- [ ] All formulas cite `a1xd/rawaccel` v1.7.1 file:line.
- [ ] Lossless import (raw JSON tree + typed view) with explicit refusals for unsupported settings.
- [ ] Patch export that keeps the profile; diff screen.
- **Acceptance:**
  - The user's file is reproduced to ≤ 1e-9 relative at 0.5–200 in/s.
  - Import → export with no change is byte-identical up to JSON formatting.
  - Real Raw Accel load test by the user, including the open lut test.
- **Harness:** ExportAuditTest becomes permanent and covers every mode and cap mode.

### M18 Refine my curve (L, about 3–5 sessions)
- [ ] `RefineRun` (import, warm-up with the user's curve, D1 level, D2 cap gain, D3 transition, A/B vs the user's curve, patch export).
- [ ] Storage schema 3 for the full curve spec.
- [ ] Screens EN/DE and screenshot steps.
- **Acceptance:** the user completes D1 and A/B on their own profile.
- **Harness gates:**
  - REFINE_NULL ≤ 5 % exported (500 runs).
  - Level ±15 %: right sign ≥ 70 %, confirmed ≥ 50 %.
  - 0 exports with negative benefit.

### M19 Two-stage strength search for "from plain" (M)
- [ ] Level stage, then strength (O4), with the level taken from the user's sensitivity or a new search.
- [ ] Optionally a joint 2-D search.
- **Gate:**
  - RISING exported ≥ 80 % (today 42 % with O4, 100 % without).
  - FALLING ≥ 90 %.
  - ADAPTED and ADAPTED_HIGH falling exports 0 %.
  - FLAT harmful exports 0 %.
- Includes flash rounds in the simulated strength search (closes F11's harness gap).

### M15 Range mode (resume; split)
- [ ] M15a: moving-target metrics:
  - error at the submovement end time;
  - lead vs aim components;
  - hits on moving targets;
  - aimer prediction and counter-strafe stop;
  - `micro-strafe` and `switch-strafe` scenarios with their own baselines;
  - the error analysis filters moving reaches.
  - **Gate:** static scenarios unchanged (bit-identical metrics); moving reaches never enter `movementSamples`; sensitivity GP on a mixed schedule within the PlateauGate bounds.
- [ ] M15b: recordings per the extended kit (user).
- [ ] M15c: `RangeScenario`, arena, profile block from `range-spec.json` with `verified` flags.
- [ ] M15d: Range as the default round for the sensitivity and curve searches.
  - **Gate:** FLAT harmful exports 0 %; RISING/FALLING detection not worse than flick+flash.

### Housekeeping (S, can go with M16)
- [ ] Onboarding aim style and pad check with the user (F9).
- [ ] Device DPI as a number, not an int (F10).
- [ ] RANGE.md "Open risks" and RECORDING.md still say "Medium, armor off" (stale).

---

## Open questions for you

1. **Path.** Did you export from the "Last search" screen (it says "unclear")? Everything points to it, because "Last analysis" has no curve on your data.
2. **Sensitivity.** Did you notice the curve rounds ran at about 0.47 and not 0.335? The finder's own result is 0.494 with 17 % confidence. Do you still consider 0.335 (with Raw Accel, effective about 65 cm/360 at slow speed) your real sensitivity? The answer decides whether M16 should start curve runs at your setting by default.
3. **Aim style.** Onboarding says wrist and a 32 cm pad; you said arm on a normal pad. Which is right? What is the pad size?
4. **Range recordings.** Can you record Speed Easy/Medium/Hard (the F3 menu has them according to the wiki) and a Strafe-mode bot at 10 and 20 m, at ≥ 60 fps?
5. **Falling curves.** Should they stay possible in "from plain" with strong evidence (recommended), or be hidden behind an "advanced" switch?
6. **Refine mode.** Is changing your Output DPI (level) acceptable, or should the level stay fixed so only the shape is refined?
7. **Unsupported Raw Accel settings.** Do you use any of by-component mode, smoothing, anisotropic stretches or a different vertical curve on another profile? If not, v1 can refuse them.
8. **The exported natural 0.70 file.** Please do not load it. Should it be deleted from `%APPDATA%\AimTune\exports\rawaccel\`? (I did not touch it.)
