# Architecture

## Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `core` | Units, event model, submovement segmentation, metrics, scoring, GP, acquisition, optimizer, accel analysis/fit, simulated player. Pure Kotlin, no LibGDX. | nothing |
| `games` | `GameProfile` interface, JSON loader, bundled profiles (`games/*.json`), converters. | `core` |
| `input` | `RawMouseSource` interface and `WindowsRawInputSource` (JNA, WM_INPUT, QPC). The GLFW fallback `GlfwMouseSource` lives in `app` because it needs the window handle. | `core` |
| `storage` | SQLite persistence (sessions, rounds, raw events, target events), settings JSON. | `core` |
| `app` | LibGDX application, Scene2D UI, 3D room, `Scenario` rendering, i18n, jpackage. | all |

Rules: `core` never imports from another module; `input`, `games`, `storage` never import each other; only `app` wires them together.

## Data flow

raw input (counts + QPC timestamps) → `MouseEvent` stream → round recorder (with `TargetEvent`s) → submovement segmentation → per-round metrics → normalized score → optimizer (GP over log cm360) → results (+ accel analysis in phase 2). Raw events and target events are persisted per round; metrics are recomputable from them.

## Threading

- Input thread: dedicated message-only window thread for Raw Input; pushes events into a lock-free queue, timestamped on arrival with QPC. The window reports focus to the source, which registers the mouse only while focused (see ADR 0002).
- Shared clock: the source exposes the `NanoClock` that stamps its events (QPC on Windows); the trainer uses the same clock for shots and round times.
- Render/game thread (LibGDX): drains the queue each frame, applies sensitivity to camera, evaluates scenario.
- Compute: optimizer and fitting run on a background coroutine dispatcher; results posted back to the render thread.
- Storage writes on an IO dispatcher.

## Storage (M3, implemented)

SQLite in `%APPDATA%\AimTune\aimtune.db` (override with `AIMTUNE_DATA_DIR`; Linux/macOS fall back to `$XDG_DATA_HOME/aimtune` or `~/.local/share/aimtune`), plus `settings.json` next to it. Schema versions are tracked with `PRAGMA user_version` and an ordered migration list in `Database.kt`; a database from a newer version is refused.

- `session(id, started_at_ms, game_id, dpi, kind)`: kind is `sensitivity` or `accel_ab`.
- `round(id, session_id, scenario, seed, cm360, dpi, blind, started_at_ms, start_nanos, duration_nanos, mouse_event_count, mouse_events BLOB, metrics_version, accel)`: the raw mouse reports of a round are one blob (format version byte + deflated zigzag-varint stream of time delta, dx, dy; see `MouseEventCodec`), about 5 bytes per report before compression.
- `target_event(round_id, seq, type, time_offset_nanos, target_id, x, y, z, radius)`: `spawned`, `hit`, `miss`, `waypoint` (moving targets; x, y, z = position), times relative to the round start.
- `metric(round_id, name, value)`: values derived when the round was saved (names in `toNamedValues()`); `metrics_version` says which definitions produced them. Metrics are always recomputable from the raw data. Scores are not stored: they are relative to all rounds and recomputed (see ALGORITHMS.md).

Rounds are analysed and saved by `RoundProcessor` on a background thread. If the database cannot be opened the trainer still runs and reports "not saved".

## App structure (M4)

- `AimTuneApp` (libGDX `Game`) wires services into an `AppContext`, which owns navigation (`showMenu/showStart/showOnboarding/showPreTest/showSettings/showTrainer`). Start shows onboarding when no complete setup exists, otherwise the pre-test notice.
- Screens extend `UiScreen` (Scene2D stage in design units, shared `UiHolder` skin and fonts); `TrainerScreen` hosts the 3D trainer.
- `SettingsHolder` holds the current `AppSettings` and saves through `SettingsStore`. `SetupDraft` is the editable, validated form model for onboarding and settings.
- `MouseGate` is the only path to mouse data: the raw source listens only while the window is focused and the active screen allows it (only the trainer).
- UI skin: `ui/Tokens` reads `docs/design/tokens.json` (copied into resources at build time), `ui/Drawables` renders rounded rects with an SDF shader, `ui/UiSkin` + `ui/Widgets` build styles and common layouts.
- Games: `games` loads `GameProfile` JSON (bundled + user files); `core.SearchRange` derives the optimizer range from aim style and pad width.
- `ScreenshotTour` (`--screenshots <dir>`) renders every screen with in-memory sample settings, for design review.

## Optimizer and blind test (M5)

- `core/optimizer`: `GaussianProcess` (Matern 5/2, session and scenario offset covariance, hand-written `Cholesky`), `SensitivityOptimizer` (`suggestNext(history)` and `analyze(history)`, both pure functions of the observations and a seed) with `OptimizationResult` (optimum, 80 % interval, confidence, regret, recommendation). Maths in ALGORITHMS.md.
- `app/blind`: `BlindTest` holds one run: earlier rounds (`PastRound`), the rounds played now, and a background single-thread job that re-scores all rounds and runs the optimizer after every round (`step()`, `nextCm360()` wait for it only if called early). `BlindRun` adds the stored-session key and the config of the next round. `SampleBlindRun` feeds the screenshot tour.
- Flow: pre-test notice -> `AppContext.startBlindTest()` (search range from aim style and pad width, earlier rounds from `RoundProcessor.pastBlindRounds`) -> `TrainerScreen` with a `BlindRun` (one round at the optimizer's hidden cm/360, HUD "Round n / N", no debug overlay) -> `BetweenRoundsScreen` -> next round or `stopBlindTest` -> `ResultsScreen`. The menu's Results item analyses the stored rounds with `BlindTest.analyzeStored`.
- Storage: rounds are saved with `blind = true` and a `runKey`, so every run is one session row; `RoundRepository.blindRounds(game, dpi)` collects the rounds of all sessions and scenarios for merging.

## Scenarios (M6)

- `core/scenario`: `Scenario` (`kind`, `targets`, `events`, `start`, `update`, `fire`), `ScenarioKind` (flick, micro, tracking, switching, flash; `create(duration, seed)`; `usesClicks`), the scenarios `FlickScenario`, `MicroScenario`, `SwitchingScenario`, `TrackingScenario`, `FlashScenario` (M10; also `Scenario.update(now, view)` and `Scenario.flash`, because it decides from where the player looks; `TargetEvent.FlashCue`/`FlashPop`; `FlashGeometry` in the profile; `core/metrics/FlashMetrics`; `FlashImportance` is the setting, see ALGORITHMS.md "Flash turn-away"), `TargetPath` (position of moving targets over time, rebuilt from `Spawned` + `Waypoint` events) and `ScenarioSchedule` (which scenario each blind round plays). `Scenario.update(now)` advances moving targets and is called by the trainer every frame before targets are read or a shot is fired; events carry their scheduled time, so the log does not depend on the frame rate.
- Heights (M11): `ElevationMix` (vertical distribution of the targets), `TrainerGeometry.eyeHeightMeters`/`headHeightMeters`/`elevation`/`arena`, `placeNearLevel` (flick and micro steps, flash returns) and `snapToFeature` (move a share of the flick and switching targets to a corner or doorway of `ArenaLayout`) in `Placement.kt`. The scene (`TrainerScene`) draws the floor at `-eyeHeight`, a ring wall at `ArenaLayout.wallDistanceMeters`, pillars at corners, door frames at doorways and a column under every head-sized target. `RoundMetrics` stores the horizontal and vertical endpoint error of the first movements (`METRICS_VERSION` 3); `AxisSummary` (app) turns them into the sentence on the results screen.
- `core/metrics/TrackingMetrics`: time on target and mean distance, sampled every 10 ms from the recording and the target path.
- `core/score/ScoreCalculator.scoreRounds(rounds, scenarios)` gives every scenario its own baselines.
- App: `Trainer` creates the scenario of its `TrainerConfig`; `TrainerScene` draws all visible targets at their own radius; `BlindRun` asks the `ScenarioSchedule` for the scenario of each round; `AppSettings.scenarios` (toggles in the settings screen) and the metric weights feed the schedule.
- Tests: `core/.../scenario/ScenariosTest`, `ScenarioScheduleTest`, `core/.../harness/ScenarioSweepTest` (report `scenario-sweeps.txt`), `MixedScenarioConvergenceTest` (report `mixed-scenarios.txt`), `app/.../harness/TrainerHarnessTest` (every scenario through the real trainer equals the headless runner).

## Acceleration (M7)

- `core/accel`: `AccelCurve` (`NoAccel`, `ClassicCurve`, `NaturalCurve`, `PowerCurve`, Raw Accel's Velocity formulas), `AccelProcessor` (per-report speed in in/s and the driver's time clamp), `AccelSpec` (one-line text form for storage), `AccelAnalysis` (movements -> wanted gain per speed bin), `fitAccel` with `NelderMead` (families, factor, BIC selection), `AccelAbPlan` and `evaluateAb` (blind A/B).
- `RoundRecording.accel` is the curve a round was played with. `ViewTrack` rebuilds the view through it (and exposes the per-report scales that the metrics' angular path uses), `Trainer` and `HeadlessRound` apply it per report, the database (schema version 2, column `round.accel`) and round logs keep it. Raw counts stay raw: the segmentation and the analysis always work on the hand's movement.
- `SimulatedAimer` plans with `SimulatedPlayer.gainPreference`, sees the view that results from the counts and the active curve, and so produces the errors the analysis looks for.
- `storage/RawAccelExport`: `settings.json` in the Raw Accel 1.7.1 layout, written to `<data dir>/exports/rawaccel/settings.json`, also copyable to the clipboard.
- `app/accel`: `AccelRun` is the state machine without UI (calibration, analysis, test, done; nothing in it reveals the condition of a test round), `AccelSession` plugs it into the trainer screen through `RoundSession` (shared with `BlindSession`), `SampleAccelRun` feeds the screenshot tour. Screens: `AccelIntroScreen` (starting sensitivity from the stored blind rounds, Raw Accel check), `AccelBetweenScreen`, `AccelAnalysisScreen`, `AccelResultScreen`. Acceleration rounds are stored as sessions of kind `accel_ab`, so the optimizer never sees them.
- Tests: `AccelCurveTest` (formulas, processor, spec), `AccelFitTest`, `core/.../harness/AccelPipelineTest` (analysis -> fit -> A/B against the simulated players, false-positive calibration, power), `RawAccelExportTest`, `AccelRunTest`, `RoundRepositoryTest` (stored curve, migration from version 1).

## Results, history and i18n (M8)

- `app/ui/ChartActor` draws a `ChartSpec` (grid, bands, dashed lines, dots, markers) with sprite-batch rectangles; `ResultCharts` builds the specs for the score chart and the wanted-gain chart as pure functions.
- `app/history/buildHistory` refits the optimizer on the rounds of each game and DPI up to each stored session; `HistoryScreen` loads it in the background.
- `app/i18n`: `I18n` wraps a LibGDX `I18NBundle` (`assets/i18n/messages.properties` English base, `messages_de.properties`), `Language` maps the stored code (`AppSettings.language`), `UiText` is a key with arguments for texts built outside screens (HUD labels). `UiScreen.t(key, args)` is the shortcut. Diagnostics (startup problems, exception messages, `AccelFitResult.reason`) stay English; `AccelFitResult.reasonCode` carries the translated reason. `BundlesTest` keeps both bundles in step and checks that every key in the code exists.

## Verification harness

Everything below runs without a human or a window (except the screenshots, which need a GPU context but a hidden window).

| Part | Where | What it does |
|---|---|---|
| `SimulatedPlayer`, `SimulatedAimer` | `core/sim` | Synthetic aimer (model in ALGORITHMS.md). Pure, seeded, never reads a clock. |
| `ManualClock` | `core/sim` | Virtual time, advanced by the driver. |
| `HeadlessRound` | `core/sim` | One round of a `Scenario` against a player on a 1000 Hz virtual clock; returns the `RoundRecording` the app would save. Used for sweeps (about 5 ms per 45 s round). |
| `SimulatedMouseSource` | `input` | The same aimer as a `RawMouseSource`: reads the active target, produces counts with timestamps; the driver calls `tick()` and `consumeClick()`. Lets the real `Trainer` be tested. |
| `RoundLogFile` | `storage` | One round as a JSON raw event log (settings, seed, mouse reports as the compact codec blob, target events; times relative to the round start). |
| `ReplayMouseSource` | `input` | Plays a log back at its recorded offsets and exposes the recorded shots (`consumeShots()`); with the recorded seed the trainer reproduces the round. |
| `--record <dir>` | `app` | Writes every finished real round as a log, so real sessions become fixtures (`RoundProcessor(exportDir)`). |
| `:app:screenshots` | `app` | Renders every screen with sample data into `app/build/screenshots/` (hidden window, in-memory settings) for comparison with `docs/design/`. |

Tests: `core/.../sim/HeadlessRoundTest` (aimer behaviour), `core/.../harness/PipelineConvergenceTest` (rounds -> segmentation -> metrics -> score -> estimator vs ground truth, with a convergence report in `core/build/reports/harness/`), `app/.../harness/TrainerHarnessTest` (the trainer with `SimulatedMouseSource` records exactly what `HeadlessRound` records; a saved log replays to the same round), `app/.../harness/RecordedFixtureTest` (every log in `app/src/test/resources/fixtures` replays to its recorded target events and keeps its pinned metrics).

Adding a real fixture: run the app with `--record <dir>`, play a round, copy the log into `app/src/test/resources/fixtures/` and pin its metrics in `RecordedFixtureTest`.

The end-to-end sensitivity test uses a baseline `SensitivityEstimator` (fixed grid, parabola through the best mean score) and the optimizer plugs into the same interface; the acceleration flow is tested end to end by `AccelPipelineTest` through `SimulatedPlayer.gainPreference`.

## Extension points

- `GameProfile`: add a JSON file in `games/` (see CONTRIBUTING.md).
- `Scenario`: implement the interface, add a `ScenarioKind` entry with its factory (take the seed in the constructor), say which score metrics it produces in `producedMetrics`, and teach the simulated aimer to play it.
- `RawMouseSource`: implement `start()`, `stop()`, event callback/queue; focus gating is the caller's concern.
