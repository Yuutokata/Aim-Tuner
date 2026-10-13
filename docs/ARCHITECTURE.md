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

## Extension points

- `GameProfile`: add a JSON file in `games/` (see CONTRIBUTING.md).
- `Scenario`: implement the interface, register it; seeded RNG is supplied.
- `RawMouseSource`: implement `start()`, `stop()`, event callback/queue; focus gating is the caller's concern.
