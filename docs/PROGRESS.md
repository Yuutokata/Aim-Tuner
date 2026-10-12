# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M1 Project setup
- **Task:** done, waiting for user review

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
- [ ] Verify versions (LibGDX, LWJGL3, JNA) and `RAWMOUSE` layout
- [ ] `RawMouseSource` interface + JNA WM_INPUT source (QPC timestamps, focus gating)
- [ ] GLFW raw motion fallback
- [ ] 3D room + crosshair, FOV from game profile
- [ ] Flick scenario, fixed sensitivity, debug overlay

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

- Plan approved. Wrote documentation set, branch renamed to `main`, Gradle multi-module + CI added. `gradlew build` passes with JDK 21 (JAVA_HOME was not set in the shell; used `~/.jdks/ms-21.0.12.1`). Later: JAVA_HOME is now JDK 25; verified the build on Gradle 9.6.0 + JDK 25 with toolchain 21 provisioned by foojay (auto-detect disabled), documented in CLAUDE.md and README.

## Next steps

1. User reviews M1. Then M2: verify LibGDX/LWJGL3/JNA versions and RAWMOUSE layout, build raw input + room + flick.

## Known bugs

- none

## Open questions

- Pre-test notice (frame 1g) shows Windows pointer precision and Raw Accel status plus an "Open Raw Accel" button. Not in the spec. Reading the Windows setting is read-only and safe; detecting an active Raw Accel profile and launching Raw Accel need a source-verified approach (M4/M7). Neither touches game processes. Keep "notice only, never enforced".
- Metric weights in Settings include "Consistency" (40/30/20/10 with accuracy, time to hit, tracking). The spec defines weights per scenario and has no consistency metric. Define it (e.g. spread of signed endpoint error) in ALGORITHMS.md at M3/M6.
- Mockups show a fixed "round 4 / 10" and "about 20 min"; the spec wants flexible length and stop-anytime. Plan: the 10 is a suggested session length, "Stop for today" always available, "Continue session" needs persisted session state (M3/M5).
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Mockup game tiles (CS2, Apex, OW2, Fortnite) need yaw/FOV verification before they ship; Valorant first.
- Exposed vs plain JDBC for storage (decide in ADR 0003 at M3).
