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
- `round(id, session_id, scenario, seed, cm360, dpi, blind, started_at_ms, start_nanos, duration_nanos, mouse_event_count, mouse_events BLOB, metrics_version)`: the raw mouse reports of a round are one blob (format version byte + deflated zigzag-varint stream of time delta, dx, dy; see `MouseEventCodec`), about 5 bytes per report before compression.
- `target_event(round_id, seq, type, time_offset_nanos, target_id, x, y, z, radius)`: `spawned`, `hit`, `miss`, times relative to the round start.
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

- `core/optimizer`: `GaussianProcess` (Matern 5/2, session offset covariance, hand-written `Cholesky`), `SensitivityOptimizer` (`suggestNext(history)` and `analyze(history)`, both pure functions of the observations and a seed) with `OptimizationResult` (optimum, 80 % interval, confidence, regret, recommendation). Maths in ALGORITHMS.md.
- `app/blind`: `BlindTest` holds one run: earlier rounds (`PastRound`), the rounds played now, and a background single-thread job that re-scores all rounds and runs the optimizer after every round (`step()`, `nextCm360()` wait for it only if called early). `BlindRun` adds the stored-session key and the config of the next round. `SampleBlindRun` feeds the screenshot tour.
- Flow: pre-test notice -> `AppContext.startBlindTest()` (search range from aim style and pad width, earlier rounds from `RoundProcessor.pastBlindRounds`) -> `TrainerScreen` with a `BlindRun` (one round at the optimizer's hidden cm/360, HUD "Round n / N", no debug overlay) -> `BetweenRoundsScreen` -> next round or `stopBlindTest` -> `ResultsScreen`. The menu's Results item analyses the stored rounds with `BlindTest.analyzeStored`.
- Storage: rounds are saved with `blind = true` and a `runKey`, so every run is one session row; `RoundRepository.blindFlickRounds(game, dpi)` collects the rounds of all sessions for merging.

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

Not covered yet: the Bayesian optimizer (M5), more scenarios (M6) and accel fitting (M7) do not exist, so the end-to-end test uses a baseline `SensitivityEstimator` (fixed grid, parabola through the best mean score). The optimizer plugs into the same interface, and the accel work will use `SimulatedPlayer.gainPreference`.

## Extension points

- `GameProfile`: add a JSON file in `games/` (see CONTRIBUTING.md).
- `Scenario`: implement the interface, register it; seeded RNG is supplied.
- `RawMouseSource`: implement `start()`, `stop()`, event callback/queue; focus gating is the caller's concern.
