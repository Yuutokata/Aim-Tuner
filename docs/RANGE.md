# M15 Range mode

Status: **plan approved (sensitivity 0.335, first setting Medium, armor off)** (step 1 of `docs/PROMPT-M15-range.md`). Nothing below is measured yet; every value in this file is a plan or a guess until `tools/range-re/out/range-spec.json` exists.

## Goal

A "Range mode" that rebuilds the function of Valorant's shooting range station **Skills Test** (25 bots per run, SCORE / REMAINING board, Challenge Easy / Medium / Hard, Bot Armor on/off, Infinite Ammo on/off). It becomes the default round type for the blind sensitivity fit and the curve search; flick and flash rounds stay as optional scenarios. It also closes R1b (head size, eye height and elevation measured instead of assumed).

## Hard rules

The repo is going public and the game runs under Vanguard.

- Never: read game memory; hook, inject into or debug the client; unpack, decrypt or extract game files, maps, models, textures or sounds; touch Vanguard; automate input into the game.
- Allowed sources only: the user's own recordings and screenshots, the user's manual measurements, public documentation with every source cited.
- Clean room: geometry, timing and behaviour are copied as closely as measured (lanes, distances, counter, bot positions and sizes, strafing, spawn timing, the 25-bot run). The look is inspired, not copied: same proportions, a warm sandstone and timber palette, glowing lane lines, a score board, all from original geometry and procedural textures. No Riot bot design, logo, font, HUD, texture or sound. The bot is an original humanoid with the measured head and body proportions, so hitboxes stay exact. In the UI it is called "Range mode".
- The REA skill is used only on recordings and docs, never on binaries or the running game.

## What gets measured

All values go to `tools/range-re/out/range-spec.json`, each with `source` (clip:frame range or URL), `n`, a spread and `verified`.

| Value | Clip | Method |
|---|---|---|
| Calibration of pixel to angle | 1 (slow 360° turn) | known yaw 0.07, sensitivity and DPI; `UnitChain` |
| Bots per run (expected 25), run start and end | 3 (full runs) | frame labelling, on-screen counter |
| Spawn positions as distance, yaw, elevation | 2 (static views) | bounding boxes → angles; distance only from floor markers or floor geometry, never from head size (that would be circular with R1b) |
| Arena geometry: counter height and position, lane width, back-wall distance, floor lines | 2, 4, 5 | floor markers and edges → angles → metres |
| Spawn intervals, bot lifetime per difficulty | 2, 3 | detection timestamps |
| Strafe speed, range, turn pattern | 2 | position tracking over time |
| Armored and unarmored hit rules | 3, 2 | what is visible; else `verified: false` |
| Head and body angular size per distance | 4 (head at known markers) | bounding boxes; closes R1b |
| Eye height (stand, crouch), head height | 5 | geometry from known marker distances |
| Fields of the end-of-run stats screen | 3 | cropped frames |

Conversion: 1920x1080, horizontal FOV 103, yaw 0.07 deg/count at sensitivity 1, 800 DPI. Inputs also noted per clip: aspect ratio and letterbox, crosshair centre. Recording sensitivity: 0.335 (confirmed by the user; 0.435 from the prompt is not used). Recordings and the fit use Infinite Ammo on. The Python `UnitChain` mirrors the Kotlin unit chain (`UnitChainTest` in `app/src/test`), with a parity test on shared sample values. Every public source is cited with its date.

## Decisions the review added

- **One setting first.** Measure and integrate a single setting (Medium, Bot Armor off) first; the other five are added after the gate passes.
- **Noise model.** A 25-bot run is one long, correlated observation. Before integration, decide whether a run feeds the optimizer as one value (score) or as per-bot samples, and check the repeat-round and confidence logic (`SensitivityOptimizer`) against it, including fatigue and learning inside a run.
- **Gate validity.** `SimulatedPlayer` and `NoiseModel` were tuned on flick targets. Strafing bots and kill timing need simulator parameters checked against the measured data; otherwise "no worse than flick-only" proves little.
- **Git hygiene.** Raw clips, frames and crops stay out of git (`tools/range-re/out/` frames and the input videos are ignored). Only derived numbers (`range-spec.json`, small CSVs) are committed.

## Module plan

| Part | Plan | Existing code |
|---|---|---|
| Profile | `trainer.range` in `games/valorant.json` with DTO, loader and tests; measured head and eye values replace the assumed ones (R1b), `verified` set where measured | `GameProfiles.kt` (private DTOs), `GameProfile.kt`, `GameProfilesTest.kt` |
| Scenario | `RangeScenario`, `ScenarioKind.RANGE`; seeded spawns from the spec; strafing via `TargetPath`; elevation from the measured distribution instead of `ElevationMix` when present | `core/scenario/{ScenarioKind,Scenario,FlickScenario,TargetPath,ElevationMix}.kt` |
| Arena | range-like layout from original geometry (counter, lanes with glowing lines, back wall, score board) using the measured lane width and distances | `ArenaLayout.kt`, `TrainerScene.buildArena` |
| Metrics | existing first reach, first-shot accuracy, time to hit, horizontal/vertical error; add time-to-kill per bot and a run score; bump `METRICS_VERSION` (3) only if stored metrics change | `RoundMetrics.kt`, `RoundRepository.kt` |
| Pipeline | RANGE in `ScenarioSchedule` as default; strength search and A/B use it (range rules like `MAX_WITH_FLASH`); menu entry "Range mode" with flow (a) blind sensitivity fit and (b) curve search, blind A/B, LUT export | `ScenarioSchedule.kt`, `MainMenuScreen`, `AppContext.startBlindTest/startAccelRun`, `AccelRun`, `StrengthSearch`, `StoredSearches` |
| Simulator | simulated player plays `RangeScenario` in `HeadlessRound`; `TrainerHarnessTest` covers it | `HeadlessRound.of`, `TrainerHarnessTest` |
| Gate | M14 `GateHarness` with the 4 player models; range must not raise flat false positives, and the sensitivity error must not be worse than flick-only; tables go to ALGORITHMS.md "M15" | `core/src/test/.../harness/review/` |

## Measurement tooling

`tools/range-re/` holds Python scripts (ffmpeg, OpenCV): frame extraction, bot detection by colour threshold or template match, bounding boxes to angles through `UnitChain`, tracking for strafe speed, spawn and despawn timestamps. Output is CSV and JSON in `out/`. Everything is cached on disk keyed by a hash of the input. Models see only summaries (median, IQR, counts). Haiku labelling is limited to crops of at most about 512 px and about 30 images per batch. If a script value and the public docs differ by more than 10 %, one Sonnet pass looks at about 5 crops before the user is asked. There is no Python in the repo yet, so the tooling is new.

## Phases

1. Plan (this file), then stop for approval.
2. `tools/range-re/RECORDING.md`: recording kit for the user (OBS, settings to note, crosshair centre, five clips).
3. Measurement scripts and `range-spec.json`.
4. REA pass: run state machine (start, spawn, kill or timeout, end), difficulty rules, hit and kill rules; guesses are marked `verified: false`.
5. Integration, one Sonnet subagent per part (table above), then the gate.
6. Docs (SPEC, ARCHITECTURE, ALGORITHMS, DESIGN, README clean-room note, PROGRESS) and the screenshot tour in EN and DE.

All work is on `feature/m15-range-mode`; nothing is merged without the user's OK. Model use: Sonnet for the work, Opus at most twice (this plan, the final spec check).

## Open risks

- **Sensitivity:** the user confirmed 0.335 for the recordings. The first setting is Medium with Bot Armor off.
- **Recording quality:** the measurements are only as good as the clips; colour-threshold detection can fail with armor, lighting or effects.
- **Invisible rules:** armor and hit rules may not be observable; they stay `verified: false`.
- **Lifetime and spawn timing** may depend on difficulty in ways static views do not show; full runs (clip 3) are the check.
- **Public docs** for the range may be thin or outdated.
- **Look:** a close likeness drifts toward copying; the bot and the props stay original.
- **Scope:** six settings exist; see "One setting first".
