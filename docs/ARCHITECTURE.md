# Architecture

## Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `core` | Units, event model, submovement segmentation, metrics, scoring, GP, acquisition, optimizer, accel analysis/fit, simulated player. Pure Kotlin, no LibGDX. | nothing |
| `games` | `GameProfile` interface, JSON loader, bundled profiles (`games/*.json`), converters. | `core` |
| `input` | `RawMouseSource` interface and implementations: `WindowsRawInputSource` (JNA, WM_INPUT, QPC) and `GlfwRawMouseSource` (fallback). | `core` |
| `storage` | SQLite persistence (sessions, rounds, raw events, target events), settings JSON. | `core` |
| `app` | LibGDX application, Scene2D UI, 3D room, `Scenario` rendering, i18n, jpackage. | all |

Rules: `core` never imports from another module; `input`, `games`, `storage` never import each other; only `app` wires them together.

## Data flow

raw input (counts + QPC timestamps) → `MouseEvent` stream → round recorder (with `TargetEvent`s) → submovement segmentation → per-round metrics → normalized score → optimizer (GP over log cm360) → results (+ accel analysis in phase 2). Raw events and target events are persisted per round; metrics are recomputable from them.

## Threading

- Input thread: dedicated message-only window thread for Raw Input; pushes events into a lock-free queue, timestamped on arrival with QPC.
- Render/game thread (LibGDX): drains the queue each frame, applies sensitivity to camera, evaluates scenario.
- Compute: optimizer and fitting run on a background coroutine dispatcher; results posted back to the render thread.
- Storage writes on an IO dispatcher.

## Storage schema (draft, finalised in M3)

`session(id, started_at, game, dpi, settings_json)`, `round(id, session_id, scenario, seed, cm360, blind, started_at, duration_ms, score)`, `mouse_event(round_id, t_us, dx, dy)`, `target_event(round_id, t_us, type, x, y, z)`, `metric(round_id, name, value)`. Settings: `settings.json` in `%APPDATA%/AimTune`.

## Extension points

- `GameProfile`: add a JSON file in `games/` (see CONTRIBUTING.md).
- `Scenario`: implement the interface, register it; seeded RNG is supplied.
- `RawMouseSource`: implement `start()`, `stop()`, event callback/queue; focus gating is the caller's concern.
