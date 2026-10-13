# AimTune

Open-source Windows desktop app that finds a player's optimal mouse sensitivity (and optionally a Raw Accel curve) inside a built-in 3D aim trainer, not inside the game. It runs short blind aim rounds, fits a Gaussian process over log(cm/360°) with Bayesian optimization, and reports the result as a game sensitivity and cm/360°. Acceleration curves are only simulated in-app and exported as a Raw Accel `settings.json`. Nothing user-specific is hardcoded; all such values come from onboarding or settings.

## Session rule (mandatory)

Read `docs/PROGRESS.md` at the start of every session. Update it at the end of every task and before every commit (checklist, current milestone/task, last session, next steps, bugs, open questions).

## Hard anti-cheat rules

- Never interact with game processes: no injection, no reading/writing game memory, no input manipulation outside our own window, no automatic editing of game configs.
- Read mouse input only while our own window is focused.
- Accel curves are only simulated in-app and exported as a file for Raw Accel. No custom driver.

## Modules (details: `docs/ARCHITECTURE.md`)

- `core`: pure Kotlin logic (units, metrics, segmentation, GP, optimizer, accel fit, simulated player). No LibGDX, fully testable.
- `games`: `GameProfile` + JSON profiles in `games/`.
- `input`: `RawMouseSource` (JNA Raw Input, GLFW fallback).
- `storage`: SQLite + settings JSON in `%APPDATA%/AimTune`.
- `app`: LibGDX/Scene2D UI, scenarios rendering, i18n, packaging.

Dependency direction: `app` → `storage`/`input`/`games` → `core`. `core` depends on nothing else.

## Commands (Windows: `gradlew.bat`)

- Build + tests: `./gradlew build`
- Tests only: `./gradlew test`
- Run app: `./gradlew :app:run` (dev-only options in `LaunchOptions.USAGE`; `--screenshots <dir>` renders the UI screens to PNGs)
- Package installer: `./gradlew :app:jpackage` (added in M8)

## JDK setup

- Gradle 9.6.0 (wrapper) runs on the JDK in `JAVA_HOME`, currently JDK 25 (Gradle 9.1+ supports running on 25). Do not downgrade the wrapper below 9.1.
- The project targets Java 21 via `kotlin { jvmToolchain(21) }` (in `build-logic`). The foojay-resolver plugin in `settings.gradle.kts` downloads JDK 21 automatically when none is installed; no manual JDK 21 install needed.
- To check provisioning: `./gradlew build -Porg.gradle.java.installations.auto-detect=false`.

## Conventions

- Kotlin, JDK 21, Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, convention plugins in `build-logic`.
- Code and comments in English. i18n via LibGDX `I18NBundle` (en default, de).
- Internal unit is cm/360°; never store sensitivity in a game-specific unit.
- Never guess APIs or file formats (LibGDX, LWJGL, JNA, Raw Accel): check current docs or source first.
- Tests for all logic; the simulated player verifies optimizer and accel convergence.
- One commit per completed step, format `<type>: <description>`. Stop after each milestone for user testing.
- UI follows `docs/design/` (tokens → Scene2D skin in `assets/ui/`); note deviations in `docs/DESIGN.md`.
- Keep docs in sync: update ARCHITECTURE, ALGORITHMS, DESIGN when a milestone changes them.

## Docs

- `docs/PROGRESS.md`: status (source of truth)
- `docs/SPEC.md`: what the app does
- `docs/ARCHITECTURE.md`: how it is built
- `docs/ALGORITHMS.md`: the math
- `docs/DESIGN.md`: UI/UX
- `docs/adr/`: decision records
- `CONTRIBUTING.md`: contributing, how to add a game
