# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M8 Results, i18n, packaging (implemented, waiting for the user's hands-on test)
- **Task:** results charts, history, i18n and packaging done; next README screenshots and CONTRIBUTING game guide check. M7 is pushed, its second hands-on part (analysis, blind test, Raw Accel import) is still open.

## Checklist

### M1 Project setup
- [x] Design PDF reviewed, DESIGN.md aligned (frames 1a-1n)
- [x] Branch renamed to `main`
- [x] CLAUDE.md
- [x] docs/SPEC, ARCHITECTURE, ALGORITHMS, DESIGN, PROGRESS
- [x] ADRs 0001-0004
- [x] README, CONTRIBUTING skeleton, LICENSE (MIT)
- [x] Gradle multi-module (`core`, `games`, `input`, `storage`, `app`), version catalog, `build-logic`
- [x] Remove root `src/`
- [x] GitHub Actions (build/test on windows-latest, dist zip artifact on tags; jpackage installer comes in M8)

### M2 Raw input + room + flick
- [x] Verify versions (LibGDX 1.14.2, LWJGL 3.3.3 via backend, JNA 5.19.1) and `RAWMOUSE` layout (ADR 0002)
- [x] `RawMouseSource` interface + JNA WM_INPUT source (QPC timestamps, focus gating)
- [x] GLFW raw motion fallback (`GlfwMouseSource`, in `app`)
- [x] 3D room + crosshair, FOV from launch options (game profiles arrive in M4)
- [x] Flick scenario (seeded, tested), fixed sensitivity, debug overlay
- [x] Hands-on test by the user: raw counts, timestamps, feel of sensitivity, focus gating (text sharpness fixed afterwards)

### M3 Metrics, segmentation, SQLite
- [x] Event model: `RoundRecording`, `ViewTrack` (view rebuilt from raw counts)
- [x] Submovement segmentation (synthetic-reach tests)
- [x] Flick metrics (endpoint error, corrections, path efficiency) + weighted per-user normalized score
- [x] SQLite schema, migrations, compact mouse-event blob, round repository (ADR 0003)
- [x] Settings JSON store (`AppSettings`, atomic save)
- [x] App records rounds, analyses and stores them in the background, shows the analysis on the round-complete screen
- [x] Hands-on test by the user: finish a round, check the analysis lines and that the database appears

### M4 Game profiles, converters, onboarding, settings
- [x] `GameProfile` + JSON loader, Valorant and CS2 (FOV values from community sources, see profile notes)
- [x] Converters (+ Valorant 800 DPI / 0.249 ≈ 65.6 cm/360° test)
- [x] Search-range table (aim style x pad width), `SetupDraft` validation
- [x] Windows pointer acceleration read (`SystemParametersInfo`)
- [x] Scene2D skin from `tokens.json`, screens: menu, onboarding (4 steps), pre-test notice, settings
- [x] Screenshot tour (`--screenshots <dir>`) for design review
- [ ] Hands-on test by the user: first launch goes through onboarding, settings persist, trainer uses the game's FOV

### Verification harness (cross-cutting, requested after M4)
- [x] `SimulatedPlayer`/`SimulatedAimer` (Fitts' law, signal-dependent noise, reaction time, configurable optimum and gain preference, fatigue) + `ManualClock` + `HeadlessRound`
- [x] `SimulatedMouseSource` (trainer driven by the simulated player records exactly what `HeadlessRound` records)
- [x] Headless end-to-end test: rounds -> segmentation -> metrics -> score -> baseline estimator vs ground truth, many seeds and noise levels, convergence report
- [x] `RoundLogFile` raw event logs, `ReplayMouseSource`, `--record <dir>`, fixture regression test
- [x] `:app:screenshots` task renders every screen offscreen to `app/build/screenshots/`
- [ ] Optimizer (M5), more scenarios (M6) and accel fitting (M7) plug into the harness when they exist; the end-to-end test then asserts the recovered optimum/curve against the same ground truth

### M5 Bayesian optimizer
- [x] GP over ln(cm360) (Matern 5/2, noise, per-session offsets, own Cholesky, ADR 0004 amended), expected improvement, design and repeat rounds
- [x] Credible interval, loss-based confidence, "more rounds worthwhile" recommendation and rough rounds-to-target
- [x] Simulated player convergence tests (optimizer vs grid baseline, calibration of the confidence)
- [x] Blind test flow in the app: hidden sensitivity, "Round n / N" HUD, between-rounds screen with confidence, "Stop for today"
- [x] Multi-session merge: every run is its own stored session; earlier blind rounds of the same game and DPI enter the model
- [x] Minimal results screen (recommended sensitivity, cm/360 with range, confidence, copy to clipboard); chart comes in M8
- [x] Hands-on test by the user: works, the sensitivity switches and feels more right after a few rounds; after 5 rounds in 2 sessions 44 % confidence and "about 13 more rounds"; the recommendation was almost exactly the user's current Valorant sensitivity (the first value entered had been a bit lower)

### M6 More scenarios
- [x] `Scenario.update`, `ScenarioKind`, waypoint events and `TargetPath` for moving targets
- [x] Micro-adjust, tracking (time on target, no clicks), target switching (three targets) with tests
- [x] Reach and time-to-hit windows for several targets, tracking metric, `scoreInputs` with tracking
- [x] Per-scenario score baselines; score combination: weights decide the score and the share of rounds each scenario gets (`ScenarioSchedule`)
- [x] Simulated player plays all four scenarios; trainer, HUD, between-rounds and settings screens (scenario toggles) know them; storage keeps waypoints and tracking metrics
- [x] Mixed-scenario convergence test and report; optimizer: scenario offsets, edge-of-range flag with capped confidence
- [x] Hands-on test by the user (first try): the result still matched their sensitivity; tracking was too fast and changed direction too often; confidence stayed at 19 % after 9 rounds in 3 sessions (about 39 more rounds estimated) with all four scenarios on
- [x] Fixes: tracking slowed down (6-22 degrees per second, segments 1.2-2.8 s, radius 0.5 m); default is now flick only, other scenarios are opt-in toggles in Settings
- [x] Second hands-on test: not reported separately; M6 was pushed on the user's request after the fixes

### M7 Accel
- [x] Verify Raw Accel modes, units, settings.json schema (read from a1xd/rawaccel v1.7.1 source; findings in ALGORITHMS.md "Acceleration")
- [x] `core/accel`: `ClassicCurve`, `NaturalCurve`, `PowerCurve` (Velocity form, Raw Accel formulas) and `AccelProcessor` (per-report speed in in/s with the driver's time clamp); tests against values computed from the C++ formulas
- [x] Accel in the pipeline: `RoundRecording.accel`, view rebuilt with the curve, trainer and headless runner apply it, storage (schema version 2) and round logs keep it
- [x] Simulated player wants a speed-dependent gain (`gainPreference`, in in/s; it plans with it and gets what the curve gives, sees its true view) so the analysis has something to find
- [x] Error-vs-speed analysis (`AccelAnalysis`: wanted gain per speed bin, normalized to the average movement; all click scenarios pooled, because flicks alone cover too narrow a speed range)
- [x] Curve fitting (`fitAccel`: classic, natural, power with a free overall factor) + model selection against "no accel" (BIC gain of 10 and at least 8 % gain change)
- [x] Blind A/B (`AccelAbPlan`, `evaluateAb`): pairs of rounds with the same scenario and targets in counterbalanced order, paired t interval with a Pocock-style per-look level; calibrated on simulated rounds with no difference (1 of 30 false "better", 0 "worse"); the end-to-end `AccelPipelineTest` shows analysis -> fit -> A/B on the simulated players
- [x] Raw Accel export (`RawAccelExport`: settings.json in the 1.7.1 layout, Velocity form, factor in the output DPI); checked against the required properties and validation rules read from the source, but not loaded into Raw Accel itself (see open questions)
- [x] Accel screens in the app (menu "Acceleration": intro with the Raw Accel check, calibration and test rounds through `RoundSession`, analysis, verdict with export and copy)
- [x] Hands-on test, first part: calibration feels like normal aiming; stopped after 5 of 15 rounds, so the fit, A/B test and export are still untested by the user
- [ ] Hands-on test, second part: analysis result, blind test and Raw Accel import

### M8 Results, i18n, packaging
- [x] Chart actor (`ChartActor`/`ChartSpec`: grid, bands, dashed lines, dots, markers), score chart on the results screen, wanted-gain chart on the accel analysis screen (no results tab, see DESIGN.md)
- [x] History screen (menu H): per-session recommendation (refit on the rounds up to that session, background), chart, table of the newest sessions, game filter; accel A/B sessions listed without a recommendation
- [x] i18n en/de (I18NBundle, 250+ keys, language select in Settings, `BundlesTest`, `:app:screenshotsDe`); diagnostics and exception texts stay English
- [x] Packaging: `:app:jpackage` (JDK jpackage with a trimmed runtime, app image by default, installer with `-PinstallerType`; ADR 0005 explains why not the Badass plugin); the app image runs the screenshot tour, CI zips it on tags. Raw input and database from the packaged build are untested
- [x] README screenshots (`docs/screenshots`, rendered by the tour), feature list and packaging notes; CONTRIBUTING "add a game" existed since M1, "add a language" added

## Last session

- M8: `ChartActor` and the score chart on the results screen (fitted line, 95 % band, round dots, optimum marker), the wanted-gain chart on the acceleration analysis, the History screen (menu H, recommendation after each session, game filter), English and German texts through `I18NBundle` with a language select in Settings, `:app:jpackage` (app image with a trimmed runtime, CI zip on tags), README screenshots. 268 tests pass (1 skipped). All screens checked through screenshots in both languages; the packaged app image runs the screenshot tour. Not checked with a real mouse: History with real data, the language switch in Settings, raw input and SQLite from the packaged build.
- M7: `core/accel` (Raw Accel curve models and per-report processor from the verified 1.7.1 source, analysis of error against speed, fits of classic/natural/power with BIC selection against no acceleration, blind A/B with a per-look corrected t test), accel in the round pipeline (recordings, view, trainer, storage schema 2, round logs), the simulated player expecting a speed-dependent gain, the Raw Accel `settings.json` export, the Acceleration menu with calibration, analysis, blind test and verdict screens, `RoundSession` shared with the blind test. 252 tests pass. On the simulated players the flow offers a curve to every rising-gain player, none of the flat ones, confirms it in 92 % of runs (median 5 pairs) and calls a harmful curve worse every time; a first version of the A/B test called a non-existent difference better in 40 % of runs because it looked after every pair without correcting for it, fixed with a per-look level and calibrated (1 of 30). The screens are only checked through screenshots and tests, not with a real mouse, and the exported file was not loaded into Raw Accel.
- M6: micro-adjust, tracking and target switching (`core/scenario`), tracking metric, per-scenario baselines, `ScenarioSchedule` (weights decide how often each scenario is played), simulated aimer for all four, scenario toggles in settings, trainer/HUD/between-rounds support, storage for waypoints and tracking metrics. 213 tests pass. Findings on the simulated player: scenarios have different best sensitivities (flick about 49, switching about 59, tracking 55-85, micro the edge of the range), so the mix finds a weighted compromise; mixed tests are less certain than flick-only ones for the same number of rounds (69 % within x1.3 of the blend after 30 rounds vs 88 % for flick only). The new screens are only checked through screenshots and tests, not with a real mouse.
- M5: `core/optimizer` (GP, expected improvement, interval, confidence), `BlindTest`/`BlindRun` in `app/blind`, blind trainer HUD, between-rounds and results screens, menu "Results" item, stored-round merging. 181 tests pass. The blind flow as a whole (real mouse, clicking through the screens) is untested by me; the optimizer and `BlindTest` are tested with the simulated player and the screens by screenshots.
- Verification harness (see checklist): simulated player, headless runner, simulated and replay mouse sources, round log files, `--record`, fixture regression test, `:app:screenshots`. 161 tests pass. Model parameters were tuned by hand so the optimum is interior and flat-topped; convergence numbers are documented in ALGORITHMS.md. The accel and optimizer parts of the requested end-to-end test cannot exist yet.

- M4: game profiles (Valorant, CS2, custom yaw/FOV), search range, onboarding, pre-test notice, settings screen, UI skin from the design tokens, mouse gate (raw input only in the trainer while focused), screenshot tour. Launch options are now dev-only. 140 unit tests pass; I compared 8 rendered screens against the mockups but could not click through the app myself.
- M3: segmentation, metrics, score, SQLite storage + settings store, trainer integration.
- M2: core units/FOV/flick scenario, input module, LibGDX trainer, debug overlay.

## Next steps

1. User tests M8 with `gradlew.bat :app:run` (or the app image from `:app:jpackage`): Results (R) with the chart, History (H) after a few sessions, Settings → Language (Deutsch/English, applies at once, kept on Save), the packaged `AimTune.exe` (does it start, does raw input work, does it create the database).
2. Still open from M7: the second hands-on part (analysis result, blind test, Raw Accel import of `settings.json`).
3. Then decide about pushing M8 and what comes after the milestones (resume of an interrupted calibration, mixed-scenario confidence, refit with the curve on, see Open questions).

## Known bugs

- none known; M4 UI has only been checked through rendered screenshots.

## Open questions

- WM_INPUT `wParam` (RIM_INPUT vs RIM_INPUTSINK) semantics for a message-only window are unverified; it is counted but not used for gating (ADR 0002).
- Trainer FOV assumes the game's FOV value refers to a 16:9 window and keeps the vertical FOV fixed when the window aspect changes. Real per-game aspect behavior is verified in M4.

- Raw Accel cannot be detected (no query API, driver stores no readable settings), so the pre-test notice asks for a manual checkbox; the Windows pointer acceleration setting is auto-detected. Notice only, never enforced; "Open Raw Accel" button dropped.
- Game FOV values for Valorant and CS2 come from community sources, not official docs; the sensitivity decimals (3) are unverified against the games' accepted precision.
- Decided: "Consistency" is the spread (sample SD) of the signed first-movement error, lower is better (see ALGORITHMS.md). Chosen because it uses the same over/undershoot quantity as the optimizer and accel analysis and does not double-count time to hit. Revisit if sideways error turns out to matter.
- Rounds per test: 10 is a suggested length ("Round 4 / 10", afterwards just "Round 12"), "Stop for today" is always available. "Continue session" is not built: a new run merges with the stored rounds automatically, which covers the use. The mockup menu entry is skipped.
- The confidence definition (chance that the estimate loses at most 0.2 x the score spread against the true best) and the 85 % target are my choice; revisit with real data. The scores of real humans may be flatter or noisier than the simulated player, then confidence will stay lower.
- Scenario geometry (target sizes, distances, tracking speeds 12-40 degrees per second, three targets for switching) is my first guess, not taken from a game; revisit after real play.
- Tracking has no click or hold: the on-target time is measured from the crosshair alone. Real tracking drills usually need the fire button held; this was left out to keep the input simple.
- Default is flick only. With all four on, rounds are spread over four kinds and a scenario needs 3 rounds of its own before its rounds count (until then they are a neutral 50), so confidence stalled in the user's test (19 % after 9 rounds). Idea if mixed tests should work: score rounds of a scenario without a baseline against the pooled baseline of the same metrics, or play scenarios in blocks.
- In the simulation micro-adjust prefers the lowest sensitivity of the range; if real players show the same, a mixed result can end at the edge of the range (the app then says so and caps the confidence).
- Blind rounds are only comparable per game and DPI; changing the game profile (FOV) starts a separate data set.
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Apex, Overwatch 2 and Fortnite tiles are omitted until yaw/FOV are verified (Valorant, CS2 and a custom yaw/FOV tile ship).
- The simulated player is a model I made up (reaction, Fitts, noise terms); it checks that the pipeline recovers a known optimum, not that real humans behave this way. Revisit its parameters with real recordings (`--record`).
- Raw Accel export: the file follows the 1.7.1 layout and validation rules read from the source but has not been loaded into Raw Accel; the user's test is the first real check. Export location is a fixed folder in the data directory plus a clipboard button, because LibGDX has no file dialog (decided at M7).
- The acceleration analysis assumes real players show a systematic under- or overshoot that depends on hand speed. The simulated player is built that way; real data may be flatter, in which case the app will say "no curve is clearly better", which is the intended outcome. Thresholds (20 movements per bin, BIC gain 10, 8 % gain change, factor-2 speed range, 2 % per-look level of the A/B test) are my choices, tuned on the simulation.
- The curve acts per mouse report while a player plans at the peak speed, so even a perfect fit removes only part of the error; the A/B test measures what is left. A refit with the curve switched on (second iteration) is not built.
- A short A/B test cannot capture a player getting used to a new curve over days; the counterbalanced order only spreads the effect over both conditions.
- Segmentation thresholds and the score's 0-100 mapping are starting values; revisit with real recordings and the simulated player (M5/M7).
