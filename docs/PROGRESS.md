# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M1 Project setup
- **Task:** done, waiting for user review

## Checklist

### M1 Project setup
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
- [ ] Onboarding + search-range table + settings screen

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
- [ ] Results view + charts + history
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

- `docs/design/AimTune.pdf` can now be rendered (poppler installed). Screen layouts in DESIGN.md are not yet derived from it; review it before M2 UI work.
- `.idea/` files are staged; suggest unstaging (global rule: no editor config unless asked).
- Exposed vs plain JDBC for storage (decide in ADR 0003 at M3).
