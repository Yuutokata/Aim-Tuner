# M15 Range mode

Status: **plan approved; corrected after the first recordings** (see "Corrections after the first recordings"). Sensitivity 0.335. Nothing below is measured yet; every value in this file is a plan or a guess until `tools/range-re/out/range-spec.json` exists.

## Goal

A "Range mode" that rebuilds the function of Valorant's shooting range station **Skills Test** (the prompt assumed 25 bots per run and Easy / Medium / Hard; the recordings show the mode **Eliminate 100** with a TIME counter and a REMAINING counter, Bot Armor on and Infinite Ammo on, see below). It becomes the default round type for the blind sensitivity fit and the curve search; flick and flash rounds stay as optional scenarios. It also closes R1b (head size, eye height and elevation measured instead of assumed).

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

## Corrections after the first recordings

The user's first two clips (`clips/`, 1920x1080, 30 fps, 800 DPI, sensitivity 0.335; OBS probably scaled a 2560x1440 game down) show a different test than the prompt assumed:

- The panel reads **Challenge: Eliminate 100**, Bot Armor **enabled**, Infinite Ammo **enabled** ("Press F3 change settings"). There is no Easy / Medium / Hard in the clips, and no 25-bot run.
- The board counts REMAINING down from 100 and shows a TIME counter. A run therefore ends after 100 bots, not 25; the old "25 bots" is dropped until a source shows it.
- The user confirmed: the mode to copy is Eliminate 100, and the first setting is this one (armor on).
- 30 fps limits timing to about ±33 ms and makes fast strafes coarse; the scripts report that uncertainty. Other difficulty settings only come back if the user records them.
- Clips so far: 1 slow turn (14 s), 2 an Eliminate 100 run (67 s) with one crouch. There is no head-at-marker clip yet, so R1b stays open (head size can come from the turn clip's calibration only if a known distance is visible).

## First measurement pass (preliminary)

Scripts in `tools/range-re/`, run on the two clips (`out/turn.json`, `out/range-spec.json`; frames and detections are cached in the ignored `cache/`):

- **Turn clip:** the integrated image shift gives a total of 337° (peak 345°) for what looks like one full turn. The 6 % shortfall fits phase correlation losing large shifts at up to 300°/s, so the FOV 103 / width assumption is plausible but not proven.
- **Bot detection:** bots are found by their purple outline (OpenCV hue 128 to 168). Checked on a contact sheet of four frames: the boxes sit on bots. Far bots are small (about 1.5° tall).
- **Tracking is too weak:** 7 tracks in 67 s, but the counter fell by about 20 (100 to roughly 78 at 52 s). The tracker needs at least 12 frames per bot and loses short-lived or fast bots, so lifetime (median 0.9 s), gap between bots (median 5 s) and spawn bearing are biased and not usable yet. Strafe speed has only 2 tracks.
- **Not measurable from these clips:** head size per distance and eye height (no known distance markers, one crouch), run end, stats screen. Distances need floor markers, not head size.

Next for step 3: read the REMAINING counter per frame (cropped digits, template match) to count kills and timestamp them, lower the track minimum, and ask the user for a clip with a bot standing still at marked distances.

## Decisions the review added

- **One setting first.** Measure and integrate a single setting (Eliminate 100, Bot Armor on, Infinite Ammo on) first; other settings are added after the gate passes.
- **Noise model.** A 100-bot run is one long, correlated observation. Before integration, decide whether a run feeds the optimizer as one value (score) or as per-bot samples, and check the repeat-round and confidence logic (`SensitivityOptimizer`) against it, including fatigue and learning inside a run.
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

- **Sensitivity:** the user confirmed 0.335 for the recordings. The first setting is Medium, Eliminate 100, strafing bots, Bot Armor on (Speed Easy/Medium/Hard exists in the F3 panel according to valorant.fandom.com/wiki/Range; the recording kit captures all three).
- **Recording quality:** the measurements are only as good as the clips; colour-threshold detection can fail with armor, lighting or effects.
- **Invisible rules:** armor and hit rules may not be observable; they stay `verified: false`.
- **Lifetime and spawn timing** may depend on difficulty in ways static views do not show; full runs (clip 3) are the check.
- **Public docs** for the range may be thin or outdated.
- **Look:** a close likeness drifts toward copying; the bot and the props stay original.
- **Scope:** six settings exist; see "One setting first".

## Second measurement pass (clips of 2026-10-22, 1920x1080, 60 fps)

Preliminary, `verified: false`; details in `tools/range-re/out/range-summary.md` (git-ignored with the rest of `out/`).

- Panel (read from the frame): Speed Easy/Medium/Hard exists but none was selected; Streak Eliminate 100; Bots Strafe (other option: Reset); Bot Amount 1/5/10 (5 selected); Bot Armor on; Infinite Ammo on.
- Unshot bots do not despawn (one strafing bot stayed 44 s), so lifetime and spawn timing come from played runs only.
- Strafe: move-stop-move, moving 12 deg/s median (p25 8, p75 16; about 2.6 m/s if the bot is 12 m away, distance assumed from a 1.8 m bot), moving segments 0.36 s, stops 0.2 s, about half of the time stopped.
- Played run: a new bot about 1.8 s after the previous one (p25 1.25, p75 2.46, n = 19), a shot bot lives 1.4 s median; 23 kills in 39 s; bearing -51 to +81 deg; distance roughly 3-19 m (assumed bot height).
- Turn calibration: 351.5 deg per measured turn (97.6 %), FOV 103 at 1920 px consistent.
- Not measured: eye height (the eye-height clip is a normal run), head hitbox, HP. The user said the reconstruction need not be exact, so these stay assumptions from the profile.
