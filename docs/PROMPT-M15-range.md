# M15 Range mode: rebuild the Valorant shooting range for fitting

Read CLAUDE.md, docs/PROGRESS.md, docs/ARCHITECTURE.md, docs/ALGORITHMS.md ("Realism", "Flash turn-away", "Vertical distribution", "M14") and docs/REVIEW.md (R1b) before anything else. This is milestone M15. Do not start coding until the plan in step 1 is approved.

## Branch

Start from a fresh branch off an up-to-date `main`: `git switch main && git pull && git switch -c feature/m15-range-mode`. All M15 work goes there, and nothing gets merged without the user's OK.

## Goal

Add a "Range mode" to AimTune: a functional rebuild of Valorant's shooting range **Skills Test**, the station at the counter where you start a bot run. These are the facts known from the user's screenshot:
- **Run:** 25 bots per run, with a SCORE / REMAINING board above the lanes.
- **Settings panel:** Challenge (Easy / Medium / Hard), Bot Armor (on/off) and Infinite Ammo (on/off), changed with F3.
- **Layout:** you shoot from a counter, and the bots stand on lanes marked by glowing floor lines.

Rebuild all difficulties and both armor settings. Range mode becomes the **default** round type for the blind sensitivity fit and the curve search. Flick and flash rounds stay as optional scenarios. The player plays bot runs there, and those runs feed the existing pipeline:
- the blind sensitivity optimizer (GP, M5/M9),
- the blind curve-strength search and curve finder v2 (bounded LUT, M14),
- the blind A/B test and the Raw Accel export.

The point is realism: the targets, distances, spawn timing, strafing and elevation should match the real range closely enough that the fitted sensitivity and curve transfer to the game. This also closes R1b, the open measurement of head size, eye height and elevation.

## Hard rules for the reverse engineering

The repo is going public, and the game runs under Vanguard.

**Never do any of the following:**
- read game memory,
- hook, inject into or debug the client,
- unpack, decrypt or extract game files, maps, models, textures or sounds,
- touch Vanguard,
- automate input into the game.

Any of these risks a ban, breaks Riot's terms, and would put Riot's assets into a public repo.

**Allowed sources:**
- the user's own screen recordings and screenshots,
- the user's manual measurements in the range,
- public documentation (Valorant Fandom/wiki, patch notes, community measurements), with every source cited.

**This is a clean-room functional copy.** Geometry, timing and behaviour are copied as closely as the measurements allow: lane layout, distances, the counter's height and position, bot positions, sizes, strafing, spawn timing and the 25-bot run.

The look is **inspired by, not copied from** the range:
- **Allowed:** the same overall proportions, a warm sandstone and timber palette, similar lighting, glowing lane lines and a score/remaining board. All of it is built from original geometry and procedural or self-made textures.
- **Not allowed:** Riot's bot design, logos, fonts, HUD, textures or sounds, and no traced copies of the map's props.

The bot gets its own humanoid design with the same head and body proportions as measured, so the hitbox sizes stay exact. In the UI it is called "Range mode".

Use the REA (reverse-engineer-anything) skill only on these allowed inputs. Its job is to turn recordings and docs into a behaviour spec. It must never be pointed at game binaries or the running game.

## Keep it cheap (model routing and token budget)

The user is on the **Pro plan**, so Opus is scarce.

- **Main session:** Sonnet.
- **Opus:** use it at most twice — once to review the plan in step 1, once for the final spec check against the measurements. Keep both passes short.
- **Implementation:** Sonnet subagents, one per module. Each gets a tight brief, with file paths and the spec section it needs, and nothing else.
- **Bulk measurement:** deterministic scripts, not LLM vision.
  - Python with ffmpeg and OpenCV extracts frames and detects bots, for example by colour threshold or template match.
  - The scripts measure bounding boxes in pixels and convert them to angles with the known FOV and resolution. Write a `UnitChain` helper for this; the yaw is 0.07.
  - They track positions over time for strafe speed, and log spawn and despawn timestamps.
  - Output goes to CSV and JSON in `tools/range-re/out/`.
- **Haiku subagents:** only for cheap labelling the scripts cannot do, such as "which frame does the run start", "is this bot armored", or reading on-screen text on a few cropped frames.
  - Send downscaled crops (at most about 512 px), never full 1080p frames or whole videos.
  - Allow at most about 30 images per labelling batch.
- **Never** put raw video, full frame dumps or large CSVs into any model context. Show summaries: medians, IQR and counts.
- **Cache everything:** extracted frames, detections and labels go to disk with a hash of the input, so a rerun costs nothing.
- **Escalate only on disagreement:** if a script measurement and the public docs differ by more than 10 %, have one Sonnet pass look at about 5 crops before asking the user.

## Steps

### 1. Plan (Sonnet drafts, one short Opus review)
Write `docs/RANGE.md` with:
- what to measure, and from which recording;
- the module plan below, mapped to existing classes;
- open risks.

Then stop for approval.

### 2. Recording kit for the user
Write `tools/range-re/RECORDING.md`, a one-page checklist covering:
- OBS at native resolution, 60 fps or more;
- the in-game settings to note: resolution, aspect and letterbox, FOV, sensitivity, DPI;
- the crosshair centre;
- the clips to record:
  1. a slow 360° turn for calibration;
  2. a static view of each Skills Test setting (Easy, Medium and Hard, each with armor off and on) with no shooting, to get bot positions, lifetime and strafe;
  3. a few full runs played normally;
  4. a head at known distance markers, for R1b;
  5. a crouch and a stand, to get the eye height.

### 3. Measurement scripts (Sonnet writes, scripts run)
Put them in `tools/range-re/`. Produce `out/range-spec.json` containing:
- bot count per run;
- spawn positions as distance, yaw and elevation distributions;
- spawn intervals;
- bot lifetime per difficulty;
- strafe speed, range and turn pattern;
- armored and unarmored hit rules, as far as they are visible;
- head and body angular size per distance;
- eye height and head height;
- the end-of-run stats screen fields.

Every value gets `source` (clip:frame range, or URL), `n`, a spread, and `verified`.

### 4. REA pass
Use the REA skill to turn the scripts' output and the public docs into a behaviour spec in `docs/RANGE.md`:
- the run state machine: start, spawn, kill or timeout, end;
- the difficulty rules;
- what counts as a hit or a kill.

Mark anything that is guessed as `verified: false`.

### 5. Integration (Sonnet subagents, one per item)
- **Profile:** add `trainer.range` to `games/valorant.json`, with a schema, a loader and tests. Replace the assumed head and eye values (R1b) with the measured ones and set `verified` where measured.
- **Scenario:** add `RangeScenario` (`ScenarioKind.RANGE`) in `core/scenario`. Spawns come from the spec, seeded. Strafing reuses `TargetPath`. Elevation comes from the measured distribution instead of `ElevationMix` when present.
- **Arena:** extend `trainer.arena` (M11) to a range-like layout made of original geometry, using the measured lane width, distances and back wall.
- **Metrics:** use the existing first reach, first-shot accuracy, time to hit and horizontal/vertical error; add a time-to-kill per bot and a run score. Bump `METRICS_VERSION` only if stored metrics change.
- **Pipeline:**
  - Range runs are a scenario in `ScenarioSchedule`, so the blind optimizer, the strength search (with `MAX_WITH_FLASH`-style range rules) and the A/B test can use them.
  - Add a menu entry "Range mode" with two flows:
    - (a) blind sensitivity fit,
    - (b) curve search, then blind A/B, then LUT export.
- **Simulated player:** the simulated player plays `RangeScenario` in `HeadlessRound`, and `TrainerHarnessTest` covers it.
- **Gate:** use the M14 `GateHarness` with the 4 player models. Range rounds must not raise flat false positives, and the sensitivity error must not get worse than flick-only. Put the tables in ALGORITHMS.md under "M15".

### 6. Docs and progress
Update SPEC, ARCHITECTURE, ALGORITHMS, DESIGN, README (the clean-room note) and PROGRESS (the M15 checklist and the open user tasks). Run the screenshot tour in EN and DE.

## Answers from the user (fill in before starting)

- **Range mode to copy:** the complete Skills Test (25 bots, SCORE/REMAINING board).
- **Difficulties / modes:** Easy, Medium and Hard, each with Bot Armor off and on; Infinite Ammo on.
- **Recording setup:**
  - 1920x1080 native;
  - Valorant sensitivity: 0.435 (the user has also mentioned 0.335; confirm before measuring);
  - 800 DPI;
  - FOV fixed at 103.
- **Range mode default for fitting:** yes, the default. Flick and flash stay optional.
- **Budget / plan limits:** Pro plan, so Sonnet does the work and Opus at most twice.
- **Look:** proportions and atmosphere like the range, built from original assets with no Riot assets or designs.
- **Branch:** `feature/m15-range-mode` off a fresh `main`.
