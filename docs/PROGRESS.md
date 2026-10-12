# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M2 Raw input + room + flick
- **Task:** implemented and committed; waiting for the user's hands-on test on Windows (see Next steps)

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
- [ ] Hands-on test by the user: raw counts, timestamps, feel of sensitivity, focus gating

### M3 Metrics, segmentation, SQLite
- [ ] Event model, submovement segmentation
- [ ] Metrics + normalized score
- [ ] SQLite schema + settings JSON

### M4 Game profiles, converters, onboarding, settings
- [ ] `GameProfile` + JSON loader, Valorant (verify yaw/FOV)
- [ ] Converters (+ Valorant 800 DPI / 0.249 ≈ 65.6 cm/360° test)
- [ ] Onboarding (4 steps per DESIGN.md) + search-range table + pre-test notice + settings screen

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

- Fixed blurry HUD text: replaced the scaled default bitmap font with FreeType-rendered Inter / JetBrains Mono at exact pixel size (HUD layout now in framebuffer pixels). Not yet checked by eye.
- M2: added core units/FOV/view angles/seeded flick scenario/round summary, input module (RawMouseSource, WindowsRawInputSource, QpcClock), app (LibGDX trainer, HUD, debug overlay, GLFW fallback). 39 unit tests pass; the app starts and the raw source starts, but real mouse input and the visuals have not been exercised yet.
- Earlier: plan approved. Wrote documentation set, branch renamed to `main`, Gradle multi-module + CI added. `gradlew build` passes with JDK 21 (JAVA_HOME was not set in the shell; used `~/.jdks/ms-21.0.12.1`). Later: JAVA_HOME is now JDK 25; verified the build on Gradle 9.6.0 + JDK 25 with toolchain 21 provisioned by foojay (auto-detect disabled), documented in CLAUDE.md and README.

## Next steps

1. User tests M2: `./gradlew :app:run --args="--dpi <your dpi> --cm360 <value> --fov 103"`. Check in the F3 overlay: source is "Windows Raw Input (JNA)", event rate matches the mouse polling rate while moving, the view turns one full 360 for `cm360` cm of mouse travel, no events count while the window is unfocused (alt-tab), Esc/focus loss aborts a round.
2. Then M3: event model, submovement segmentation, metrics, SQLite.

## Known bugs

- none known; M2 has not been tested with real mouse input yet.

## Open questions

- WM_INPUT `wParam` (RIM_INPUT vs RIM_INPUTSINK) semantics for a message-only window are unverified; it is counted but not used for gating (ADR 0002).
- Trainer FOV assumes the game's FOV value refers to a 16:9 window and keeps the vertical FOV fixed when the window aspect changes. Real per-game aspect behavior is verified in M4.

- Pre-test notice (frame 1g) shows Windows pointer precision and Raw Accel status plus an "Open Raw Accel" button. Not in the spec. Reading the Windows setting is read-only and safe; detecting an active Raw Accel profile and launching Raw Accel need a source-verified approach (M4/M7). Neither touches game processes. Keep "notice only, never enforced".
- Metric weights in Settings include "Consistency" (40/30/20/10 with accuracy, time to hit, tracking). The spec defines weights per scenario and has no consistency metric. Define it (e.g. spread of signed endpoint error) in ALGORITHMS.md at M3/M6.
- Mockups show a fixed "round 4 / 10" and "about 20 min"; the spec wants flexible length and stop-anytime. Plan: the 10 is a suggested session length, "Stop for today" always available, "Continue session" needs persisted session state (M3/M5).
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Mockup game tiles (CS2, Apex, OW2, Fortnite) need yaw/FOV verification before they ship; Valorant first.
- Exposed vs plain JDBC for storage (decide in ADR 0003 at M3).
