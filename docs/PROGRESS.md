# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M4 Game profiles, converters, onboarding, settings
- **Task:** implemented and committed locally (not pushed); waiting for the user's hands-on test (see Next steps). M2 and M3 hands-on tests passed and are pushed.

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
- [ ] Hands-on test by the user: play a short blind test, check that the sensitivity is never shown, the confidence and results make sense

### M6 More scenarios
- [ ] Micro-adjust, tracking, target switching
- [ ] Score combination with configurable weights

### M7 Accel
- [ ] Verify Raw Accel modes, units, settings.json schema
- [ ] Error-vs-speed analysis, gain function
- [ ] Curve fitting + model selection
- [ ] In-app simulation + blind A/B
- [ ] Raw Accel export

### M8 Results, i18n, packaging
- [ ] Results view (sens + accel tabs) + charts + history + copy-to-clipboard
- [ ] i18n en/de
- [ ] jpackage installer
- [ ] README screenshots, CONTRIBUTING "add a game"

## Last session

- M5: `core/optimizer` (GP, expected improvement, interval, confidence), `BlindTest`/`BlindRun` in `app/blind`, blind trainer HUD, between-rounds and results screens, menu "Results" item, stored-round merging. 181 tests pass. The blind flow as a whole (real mouse, clicking through the screens) is untested by me; the optimizer and `BlindTest` are tested with the simulated player and the screens by screenshots.
- Verification harness (see checklist): simulated player, headless runner, simulated and replay mouse sources, round log files, `--record`, fixture regression test, `:app:screenshots`. 161 tests pass. Model parameters were tuned by hand so the optimum is interior and flat-topped; convergence numbers are documented in ALGORITHMS.md. The accel and optimizer parts of the requested end-to-end test cannot exist yet.

- M4: game profiles (Valorant, CS2, custom yaw/FOV), search range, onboarding, pre-test notice, settings screen, UI skin from the design tokens, mouse gate (raw input only in the trainer while focused), screenshot tour. Launch options are now dev-only. 140 unit tests pass; I compared 8 rendered screens against the mockups but could not click through the app myself.
- M3: segmentation, metrics, score, SQLite storage + settings store, trainer integration.
- M2: core units/FOV/flick scenario, input module, LibGDX trainer, debug overlay.

## Next steps

1. User tests M5: `gradlew.bat :app:run` (use `AIMTUNE_DATA_DIR` for a scratch data folder, `--record <dir>` saves every round as a log). Start test, play 10-12 rounds: the sensitivity should differ between rounds without being shown anywhere (also not with F3), the between-rounds screen shows hits/accuracy/time and a confidence, "Stop for today" shows the result. Then restart the app and use Results (R) on the menu: the stored rounds should give the same recommendation; a second test run should merge with the first (session count 2).
2. Please report: does the recommended sensitivity feel right, are the rounds too similar or too jumpy, and is the confidence plausible.
3. Then ask whether to push M5, and continue with M6 (more scenarios, score combination).

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
- Blind rounds are only comparable per game and DPI; changing the game profile (FOV) starts a separate data set.
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Apex, Overwatch 2 and Fortnite tiles are omitted until yaw/FOV are verified (Valorant, CS2 and a custom yaw/FOV tile ship).
- The simulated player is a model I made up (reaction, Fitts, noise terms); it checks that the pipeline recovers a known optimum, not that real humans behave this way. Revisit its parameters with real recordings (`--record`).
- Segmentation thresholds and the score's 0-100 mapping are starting values; revisit with real recordings and the simulated player (M5/M7).
