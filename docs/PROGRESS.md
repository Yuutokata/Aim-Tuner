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
- [ ] GP + acquisition, noise, repeats, session offsets
- [ ] Blind test flow, confidence, multi-session merge
- [ ] Simulated player convergence tests

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

- Verification harness (see checklist): simulated player, headless runner, simulated and replay mouse sources, round log files, `--record`, fixture regression test, `:app:screenshots`. 161 tests pass. Model parameters were tuned by hand so the optimum is interior and flat-topped; convergence numbers are documented in ALGORITHMS.md. The accel and optimizer parts of the requested end-to-end test cannot exist yet.

- M4: game profiles (Valorant, CS2, custom yaw/FOV), search range, onboarding, pre-test notice, settings screen, UI skin from the design tokens, mouse gate (raw input only in the trainer while focused), screenshot tour. Launch options are now dev-only. 140 unit tests pass; I compared 8 rendered screens against the mockups but could not click through the app myself.
- M3: segmentation, metrics, score, SQLite storage + settings store, trainer integration.
- M2: core units/FOV/flick scenario, input module, LibGDX trainer, debug overlay.

## Next steps

1. User tests M4: `gradlew.bat :app:run` (use `AIMTUNE_DATA_DIR` to avoid touching real data). First launch should show onboarding (DPI, game + sensitivity with live cm/360°, aim style, pad). Then check the menu, Settings (save/reset, weight sliders, round length), the pre-test notice (pointer acceleration row, Raw Accel checkbox) and that the trainer runs at your current sensitivity with the game's FOV. Settings are saved to `%APPDATA%\AimTune\settings.json`.
2. Then ask whether to push M4, and start M5 (Bayesian optimizer, blind test flow, confidence, multi-session).

## Known bugs

- none known; M4 UI has only been checked through rendered screenshots.

## Open questions

- WM_INPUT `wParam` (RIM_INPUT vs RIM_INPUTSINK) semantics for a message-only window are unverified; it is counted but not used for gating (ADR 0002).
- Trainer FOV assumes the game's FOV value refers to a 16:9 window and keeps the vertical FOV fixed when the window aspect changes. Real per-game aspect behavior is verified in M4.

- Raw Accel cannot be detected (no query API, driver stores no readable settings), so the pre-test notice asks for a manual checkbox; the Windows pointer acceleration setting is auto-detected. Notice only, never enforced; "Open Raw Accel" button dropped.
- Game FOV values for Valorant and CS2 come from community sources, not official docs; the sensitivity decimals (3) are unverified against the games' accepted precision.
- Decided: "Consistency" is the spread (sample SD) of the signed first-movement error, lower is better (see ALGORITHMS.md). Chosen because it uses the same over/undershoot quantity as the optimizer and accel analysis and does not double-count time to hit. Revisit if sideways error turns out to matter.
- Mockups show a fixed "round 4 / 10" and "about 20 min"; the spec wants flexible length and stop-anytime. Plan: the 10 is a suggested session length, "Stop for today" always available, "Continue session" needs persisted session state (M3/M5).
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Apex, Overwatch 2 and Fortnite tiles are omitted until yaw/FOV are verified (Valorant, CS2 and a custom yaw/FOV tile ship).
- The simulated player is a model I made up (reaction, Fitts, noise terms); it checks that the pipeline recovers a known optimum, not that real humans behave this way. Revisit its parameters with real recordings (`--record`).
- Segmentation thresholds and the score's 0-100 mapping are starting values; revisit with real recordings and the simulated player (M5/M7).
