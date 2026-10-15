# Specification

## Goal

An open-source Windows desktop app that lets any player find their optimal mouse sensitivity and, optionally, an optimal acceleration curve in a built-in 3D aim trainer. Results are shown as a game sensitivity (Valorant first) and cm/360°, and optionally exported as a Raw Accel `settings.json`. The app is not tailored to a specific user; all user values come from onboarding or settings.

## Hard rules (anti-cheat safety)

1. No interaction with game processes: no injection, no game memory access, no input manipulation outside the app window, no automatic game config edits.
2. Mouse input is read only while the app window is focused.
3. Accel curves are simulated in-app and exported as a file for Raw Accel. No custom driver.

## Tech stack

Kotlin, JDK 21, Gradle Kotlin DSL; LibGDX (LWJGL3) with Scene2D; Raw Input via JNA with QPC timestamps (fallback GLFW raw motion) behind `RawMouseSource`; SQLite (xerial JDBC) in `%APPDATA%/AimTune`, settings as JSON (kotlinx.serialization); custom Gaussian process (own Cholesky, no linear algebra library, see ADR 0004); I18NBundle (en default, de); jpackage; GitHub Actions; MIT.

## Features

### Units and game converters
- Internal unit cm/360°: `cm360 = (360 × 2.54) / (DPI × sens × yaw)`.
- Games are `GameProfile`s (interface and/or JSON in `games/`): name, yaw (deg/count at sens 1), FOV with type (horizontal/vertical), aspect ratio behavior, sensitivity format and rounding.
- Valorant: yaw 0.07, FOV 103° horizontal (16:9), to be verified against current sources.
- New games without core changes. The trainer renders with the selected profile's FOV.

### Onboarding (editable later)
- DPI, game, current sensitivity → current cm/360° as start point.
- Aim style: wrist / mixed / arm. Mousepad: small / medium / large or width in cm.
- Derive a search range in cm/360° from a configurable table; pad width caps the upper bound; the current value is always inside the range.
- Notice (not enforced) that Windows "Enhance pointer precision" and active Raw Accel curves distort measurements.

### Scenarios
Rounds of about 30-60 s (configurable) in a plain 3D room with targets of a head's angular size, defined per game in its profile: Flick, Micro-adjust, Tracking, Target switching, Flash turn-away. Plugin-style `Scenario` interface; seeded for reproducibility.

**Flash turn-away.** Turning away from a flash and coming back quickly is a key use case in Valorant. A flash orb becomes visible inside the field of view and pops after a short delay; the player has to turn so far that the flash is beyond the edge of the screen (half the profile's horizontal FOV plus a margin) when it pops, then turn back and hit the head-sized enemy that appears near the original view direction. A player who still sees the flash is blinded (white screen) for its blind duration and sees the enemy only afterwards. Times, positions and the blind duration are in the `flash` block of the profile's `trainer` block (windups of the real abilities, 0.3-0.6 s; sources and what is assumed are listed in the profile and in docs/ALGORITHMS.md). A setting in onboarding (aim style step) and in the settings says how much flash dodging matters to the player (never / sometimes / often); it is the weight of the dodge metric and decides how often flash rounds are played.

### Measurement and metrics
Store raw events (counts + timestamps) and target events per round. Compute hit rate, time-to-target, signed endpoint error per movement, corrective movements, path efficiency, tracking time-on-target ratio and mean distance. Segment into submovements (also used for accel analysis). For flash rounds also the share of flashes the player had turned away from when they popped, the time to turn away and its peak speed. Combine accuracy (first shot), time to hit, tracking and the flash dodge rate into a normalized, weighted, per-user-baselined score; the spread of the first-movement error is shown as a diagnostic only because it cannot have an optimum of its own.

### Sensitivity optimization (phase 1)
Bayesian optimization over log(cm360) in the range; GP with noise model; EI or UCB; blind testing; repeated points for noise; session offsets and a per-session trend (learning, fatigue) are part of the model, a detected trend lowers the confidence; stop at any time with confidence value and recommendation whether more rounds are worthwhile; sessions merge into one result.

### Acceleration curve (phase 2, optional)
The blind search of the curve strength and its blind test also play flash turn-away rounds (half of their rounds) when flash dodging matters to the player; the error analysis of phase 1 plain rounds does not use them (docs/ALGORITHMS.md "Flash turn-away").

Base sensitivity is the found optimum. Signed endpoint error vs input speed from submovements gives a desired gain g(v). Fit the supported Raw Accel modes (verify list) with a weighted least squares fit on the median error per speed bin, pick the mode by an information criterion (BIC) against "no acceleration", constrain with caps. Verify via in-app blind A/B; export only if measurably better, else recommend no accel. Export Raw Accel `settings.json` (verify schema in a1xd/rawaccel) with the sens multiplier set so in-game sensitivity stays unchanged.

### Results view
Recommended sensitivity for the selected game, cm/360°, confidence; score vs cm/360° chart with uncertainty band; accel curve chart, A/B comparison, export; session history.

## Quality strategy
Unit tests for converters (800 DPI, sens 0.249 → about 65.6 cm/360°), GP, acquisition, segmentation, curve fitting. A simulated player (Fitts' law + noise + known optimal sensitivity and gain curve) verifies convergence. Separation: core, input, app, games.

## Milestones
See `docs/PROGRESS.md`.
