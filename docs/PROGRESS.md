# Progress

Single source of truth for project status. Update at the end of every task and before every commit.

## Current

- **Milestone:** M11 Realistic target heights and M12 Valorant crosshair (in progress, M11 first); M10 Flash turn-away (implemented, waiting for the user's hands-on test); M9 Review fixes (implemented, waiting for the user's hands-on test); M8 Results, i18n, packaging is implemented and waiting for the user's hands-on test
- **Task:** fixes are in (see M9 below and docs/REVIEW.md status table). The target sizes are assumptions until R1b. M7's second hands-on part (analysis, blind test, Raw Accel import) is still open, and per the review the acceleration results should not be trusted before R2.

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
- [x] Hands-on test by the user: raw counts, timestamps, feel of sensitivity, focus gating (text sharpness fixed afterwards)

### M3 Metrics, segmentation, SQLite
- [x] Event model: `RoundRecording`, `ViewTrack` (view rebuilt from raw counts)
- [x] Submovement segmentation (synthetic-reach tests)
- [x] Flick metrics (endpoint error, corrections, path efficiency) + weighted per-user normalized score
- [x] SQLite schema, migrations, compact mouse-event blob, round repository (ADR 0003)
- [x] Settings JSON store (`AppSettings`, atomic save)
- [x] App records rounds, analyses and stores them in the background, shows the analysis on the round-complete screen
- [x] Hands-on test by the user: finish a round, check the analysis lines and that the database appears

### M4 Game profiles, converters, onboarding, settings
- [x] `GameProfile` + JSON loader, Valorant and CS2 (FOV values from community sources, see profile notes)
- [x] Converters (+ Valorant 800 DPI / 0.249 ≈ 65.6 cm/360° test)
- [x] Search-range table (aim style x pad width), `SetupDraft` validation
- [x] Windows pointer acceleration read (`SystemParametersInfo`)
- [x] Scene2D skin from `tokens.json`, screens: menu, onboarding (4 steps), pre-test notice, settings
- [x] Screenshot tour (`--screenshots <dir>`) for design review
- [ ] Hands-on test by the user: first launch goes through onboarding, settings persist, trainer uses the game's FOV

### Verification harness (cross-cutting, requested after M4)
- [x] `SimulatedPlayer`/`SimulatedAimer` (Fitts' law, signal-dependent noise, reaction time, configurable optimum and gain preference, fatigue) + `ManualClock` + `HeadlessRound`
- [x] `SimulatedMouseSource` (trainer driven by the simulated player records exactly what `HeadlessRound` records)
- [x] Headless end-to-end test: rounds -> segmentation -> metrics -> score -> baseline estimator vs ground truth, many seeds and noise levels, convergence report
- [x] `RoundLogFile` raw event logs, `ReplayMouseSource`, `--record <dir>`, fixture regression test
- [x] `:app:screenshots` task renders every screen offscreen to `app/build/screenshots/`
- [ ] Optimizer (M5), more scenarios (M6) and accel fitting (M7) plug into the harness when they exist; the end-to-end test then asserts the recovered optimum/curve against the same ground truth

### M5 Bayesian optimizer
- [x] GP over ln(cm360) (Matern 5/2, noise, per-session offsets, own Cholesky, ADR 0004 amended), expected improvement, design and repeat rounds
- [x] Credible interval, loss-based confidence, "more rounds worthwhile" recommendation and rough rounds-to-target
- [x] Simulated player convergence tests (optimizer vs grid baseline, calibration of the confidence)
- [x] Blind test flow in the app: hidden sensitivity, "Round n / N" HUD, between-rounds screen with confidence, "Stop for today"
- [x] Multi-session merge: every run is its own stored session; earlier blind rounds of the same game and DPI enter the model
- [x] Minimal results screen (recommended sensitivity, cm/360 with range, confidence, copy to clipboard); chart comes in M8
- [x] Hands-on test by the user: works, the sensitivity switches and feels more right after a few rounds; after 5 rounds in 2 sessions 44 % confidence and "about 13 more rounds"; the recommendation was almost exactly the user's current Valorant sensitivity (the first value entered had been a bit lower)

### M6 More scenarios
- [x] `Scenario.update`, `ScenarioKind`, waypoint events and `TargetPath` for moving targets
- [x] Micro-adjust, tracking (time on target, no clicks), target switching (three targets) with tests
- [x] Reach and time-to-hit windows for several targets, tracking metric, `scoreInputs` with tracking
- [x] Per-scenario score baselines; score combination: weights decide the score and the share of rounds each scenario gets (`ScenarioSchedule`)
- [x] Simulated player plays all four scenarios; trainer, HUD, between-rounds and settings screens (scenario toggles) know them; storage keeps waypoints and tracking metrics
- [x] Mixed-scenario convergence test and report; optimizer: scenario offsets, edge-of-range flag with capped confidence
- [x] Hands-on test by the user (first try): the result still matched their sensitivity; tracking was too fast and changed direction too often; confidence stayed at 19 % after 9 rounds in 3 sessions (about 39 more rounds estimated) with all four scenarios on
- [x] Fixes: tracking slowed down (6-22 degrees per second, segments 1.2-2.8 s, radius 0.5 m); default is now flick only, other scenarios are opt-in toggles in Settings
- [x] Second hands-on test: not reported separately; M6 was pushed on the user's request after the fixes

### M7 Accel
- [x] Verify Raw Accel modes, units, settings.json schema (read from a1xd/rawaccel v1.7.1 source; findings in ALGORITHMS.md "Acceleration")
- [x] `core/accel`: `ClassicCurve`, `NaturalCurve`, `PowerCurve` (Velocity form, Raw Accel formulas) and `AccelProcessor` (per-report speed in in/s with the driver's time clamp); tests against values computed from the C++ formulas
- [x] Accel in the pipeline: `RoundRecording.accel`, view rebuilt with the curve, trainer and headless runner apply it, storage (schema version 2) and round logs keep it
- [x] Simulated player wants a speed-dependent gain (`gainPreference`, in in/s; it plans with it and gets what the curve gives, sees its true view) so the analysis has something to find
- [x] Error-vs-speed analysis (`AccelAnalysis`: wanted gain per speed bin, normalized to the average movement; all click scenarios pooled, because flicks alone cover too narrow a speed range)
- [x] Curve fitting (`fitAccel`: classic, natural, power with a free overall factor) + model selection against "no accel" (BIC gain of 10 and at least 8 % gain change)
- [x] Blind A/B (`AccelAbPlan`, `evaluateAb`): pairs of rounds with the same scenario and targets in counterbalanced order, paired t interval with a Pocock-style per-look level; calibrated on simulated rounds with no difference (1 of 30 false "better", 0 "worse"); the end-to-end `AccelPipelineTest` shows analysis -> fit -> A/B on the simulated players
- [x] Raw Accel export (`RawAccelExport`: settings.json in the 1.7.1 layout, Velocity form, factor in the output DPI); checked against the required properties and validation rules read from the source, but not loaded into Raw Accel itself (see open questions)
- [x] Accel screens in the app (menu "Acceleration": intro with the Raw Accel check, calibration and test rounds through `RoundSession`, analysis, verdict with export and copy)
- [x] Hands-on test, first part: calibration feels like normal aiming; stopped after 5 of 15 rounds, so the fit, A/B test and export are still untested by the user
- [ ] Hands-on test, second part: analysis result, blind test and Raw Accel import

### M8 Results, i18n, packaging
- [x] Chart actor (`ChartActor`/`ChartSpec`: grid, bands, dashed lines, dots, markers), score chart on the results screen, wanted-gain chart on the accel analysis screen (no results tab, see DESIGN.md)
- [x] History screen (menu H): per-session recommendation (refit on the rounds up to that session, background), chart, table of the newest sessions, game filter; accel A/B sessions listed without a recommendation
- [x] i18n en/de (I18NBundle, 250+ keys, language select in Settings, `BundlesTest`, `:app:screenshotsDe`); diagnostics and exception texts stay English
- [x] Packaging: `:app:jpackage` (JDK jpackage with a trimmed runtime, app image by default, installer with `-PinstallerType`; ADR 0005 explains why not the Badass plugin); the app image runs the screenshot tour, CI zips it on tags. Raw input and database from the packaged build are untested
- [x] README screenshots (`docs/screenshots`, rendered by the tour), feature list and packaging notes; CONTRIBUTING "add a game" existed since M1, "add a language" added

### M9 Review fixes (from docs/REVIEW.md; the user asked for "M8 – Review fixes", M8 was already taken by Results/i18n/packaging, so this is M9). Approved and implemented, not yet committed
Order: the four items that change the numbers first (R1-R4), then the rest.
- [x] R1 (C1, M5) Realism (values are assumptions until measured in the game, see the remaining item below): target angular size, distance and flick-angle distribution, strafe speed and scenario weights move into the `GameProfile` JSON (`verified` flag and sources per field); the scenarios take them from the profile; default round = one scenario with a mixed distance distribution; measure the real head size in the Valorant range (procedure in REVIEW.md) and replace the assumed values; document the sources in ALGORITHMS.md "Realism"
- [ ] R1b (user) Measure a head in the game's shooting range (procedure in REVIEW.md, Realism), put the values into `games/valorant.json` and set `trainer.verified` to true
- [x] R2 (C2) First-movement definition: first reach starts >= ~100 ms after the spawn, points to the target, covers >= ~30 % of the distance or exceeds a minimum peak speed; check on the real rounds (`RealDataReviewTest`: < 3 % with error < -0.5); add a real-round fixture (`--record`); "recompute stored metrics from the stored events" with `METRICS_VERSION` bump (Q4)
- [x] R3 (C3) Remove consistency from the sensitivity objective (or make it distance-normalised and de-trended); `ReviewBiasTest` single-metric optima must be interior; warn in Settings about weights
- [x] R4 (M1) Search range centred on the current value (log scale), pad width only a warning, plausibility check for extreme onboarding values
- [x] R5 (M2) Within-session trend / warm-up discard in the optimizer; interleave design points; drift regression test with a cap on the confidence under drift
- [x] R6 (M3) A/B test stricter (per-look 0.01 or O'Brien-Fleming, min 6 pairs, minimum effect size), permutation-based BIC threshold for the fit; null test with >= 500 runs; update ALGORITHMS.md numbers
- [x] R7 (M4) Accel only (GetRawInputBuffer and 4/8 kHz not done, see REVIEW.md status) with the Raw Input source; same speed estimator for the fit and the driver simulation; jitter/batching tests; check 4/8 kHz with `GetRawInputBuffer` (Q1)
- [x] R8 (M6, M8) First-shot accuracy metric; clicks read from the same raw packet stream and shot at the view of the click time
- [x] R9 (M7) Valorant FOV (letterboxed to the profile aspect instead of measuring) at other aspect ratios: measure or fix to a 16:9 viewport, `aspectBehavior` in the profile
- [ ] R10 (C4) Record real sessions of 2+ people over a sweep, fit/validate the simulated player against them, re-quote the convergence numbers
- [x] R11 (M9) Cache optimizer hyperparameters / cap the history refit, one query for rounds + metrics (Q5)
- [x] R12 Minor (except Q3, Q8, GetRawInputBuffer): Q1-Q3, Q6-Q9 of REVIEW.md
- [x] R14 Detection power for weak effects (`ReviewAccelTest.detectionPower`, result in ALGORITHMS.md): curves of +-15 % are mostly not found or not confirmed
- [x] R15 "Try a mild curve anyway" on the analysis screen (`trialAccel`, `AccelRun.startTrial`), same blind test, result marked as trial
- [ ] R13 Release (done: README, THIRD-PARTY-NOTICES, link, SPEC/ALGORITHMS, CI smoke test; still open: load the exported settings.json into Raw Accel, installer test without a JDK): README for users (install, use, method in plain words, limitations), THIRD-PARTY-NOTICES, Raw Accel link, SPEC/ALGORITHMS corrections, CI smoke test of the packaged app, load the exported `settings.json` into Raw Accel, test an installer on a machine without a JDK
- [x] Review done: `docs/REVIEW.md`, review experiments (`core/.../harness/review`, `storage/.../review`, gated by `AIMTUNE_REVIEW` / `AIMTUNE_REAL_DB`), permanent `UnitChainTest`

### M10 Flash turn-away (requested: turning away from flashes in Valorant and back quickly is a key use case)
- [x] Research of the real flash timings (Fandom ability pages, PC): windups Curveball 0.6 s, Blindside 0.6 s, Breach about 0.5 s, Guiding Light 0.3 s, KAY/O telegraph 0.3 s; max blind 1.5-2.25 s; the visible flight before the windup is not documented (assumption, marked unverified in `games/valorant.json`)
- [x] `FlashScenario` (cue inside the FOV, pop after 0.45-0.9 s, dodged if the flash is beyond half the horizontal FOV + 10 degrees from the view at the pop, otherwise 1.5 s white-out, then a head-sized enemy near the original view direction), `FlashGeometry` in the profile, events `FlashCue`/`FlashPop`, `Scenario.update(now, view)` and `Scenario.flash`
- [x] Metrics: dodge rate, turn time, turn peak speed; the return hit is an ordinary movement (time to hit counts from the end of the white-out, so blindness is paid for by the dodge metric only)
- [x] Score: `ScoreMetric.FLASH_DODGE`, `FlashImportance` (never / sometimes / often = weight 0 / 15 / 35 = 0 / 18 / 33 % of the rounds next to flicks); setting in onboarding (aim style step) and in the settings; default is flick + flash at "sometimes"
- [x] Simulated player turns away and comes back (`flashTurnReactionMs`, `flashReturnReactionMs`, `flashTurnMarginDegrees`); trainer draws the orb and the white-out; storage keeps the new events (no schema change), research export has them
- [x] Curve analysis with the returns (experiments in `FlashExperimentsTest`, results in ALGORITHMS.md "Flash turn-away"): flash rounds help the error analysis only for a hand too slow to judge by itself, make rising gains harder to find and fatten the false-offer tail (1 of 500 flat players at 50 % flash), flash only gives false offers. Decision: the error analysis keeps the click scenarios. The blind strength search and the blind A/B test do use flash rounds (half of their rounds, `AccelRun(flash = true)` unless importance is "never"): flat players are no longer sent to "a curve stands out" in two thirds of the runs (23/32 -> 1/32), real preferences are found as before or better, the A/B test keeps 2 % false verdicts and confirms 95-100 %
- [x] Guard tests: `FlashScoreTest` (the dodge rate alone peaks at the fast end, the combined optimum is inside the range, "often" stays within 45 % of the return-only optimum), `FlashScenarioTest`, `TrainerHarnessTest` (the trainer records flash rounds like the headless runner)
- [x] Docs: SPEC, ALGORITHMS, ARCHITECTURE, DESIGN, CONTRIBUTING, README
- [ ] Open (user): check the flash timings in the game (the visible flight before the windup and the exit margin are assumptions, `trainer.flash.verified` in `games/valorant.json`), play flash rounds on the real mouse and judge the scenario (pop delay, enemy position, white-out)

### M11 Realistic target heights and mini-map (requested: targets floated anywhere in a sphere; Valorant fights are on one level)
- [x] Research: no separate vertical sensitivity in Valorant found (pitch = yaw 0.07, `pitchMultiplier` not needed so far); eye and head height are not published (assumed 1.6 m / 1.65 m, `verified: false`)
- [x] `ElevationMix` + `eyeHeightMeters`/`headHeightMeters` in `TrainerGeometry` and the profile JSON (`trainer.elevation`): bulk near head level (spread 1.5 degrees), 12 % off level 3-10 degrees (40 % of them below); `uniform` switches back to the old field for comparisons
- [x] Placement: flick/micro steps take their pitch from the mix (`placeNearLevel`), switching draws pitch from the mix, tracking is almost purely horizontal, flash cue elevation 12 degrees and the returning enemy near head level
- [x] Horizontal and vertical endpoint errors in the metrics (`METRICS_VERSION` 3, old rounds are recomputed), shown on the results screen ("most of your misses are vertical")
- [x] Mini-map (floor, ring wall, corner pillars, door frames, columns under the heads) in the trainer scene; `trainer.arena` in the profile, 30 % of flick and switching targets stand at a corner or doorway
- [x] Re-run the simulation experiments (`VerticalExperimentsTest`): no measurable change of the numbers except weaker detection of rising weak preferences at 15 rounds; results in ALGORITHMS.md "Vertical distribution" and section E of ANALYSE.md (E2 crosshair follows with M12)
- [ ] Open (user): measure head height and the elevation of enemies in the shooting range (R1b), judge the mini-map

### M12 Valorant crosshair (requested: players aim differently with an unfamiliar crosshair)
- [x] `docs/CROSSHAIR.md`: code format with sources (community generator and parsers, no Riot spec), the keys, ranges and defaults, what 17 real codes show, decisions and open points
- [x] `core/crosshair`: `CrosshairCode` (lossless), `Crosshair` (typed primary section, `fromCode`/`applyTo`/`toCode`), `CrosshairLayout` (pixel rectangles); movement/firing error are parsed and kept, not drawn
- [x] Pixel-exact `CrosshairRenderer` on the back buffer in all scenarios (checked on a screenshot: 6 px lines, 2 px thick, offset 3 around (960, 540)), `CrosshairScreen` with named profiles (new, duplicate, delete, rename, select), import/export of codes, live preview on a bright, dark or trainer background, menu entry C; profiles are stored in the settings (`crosshairs`, `activeCrosshair`)
- [x] Tests: round trips of 17 real codes (raw and through the typed model), invalid codes, layout reference sizes and pictures, settings round trip; screenshot tour step `crosshair` (EN/DE)
- [ ] Open (user): export two or three of your own crosshairs from Valorant, paste them into the editor and compare the rendered crosshair side by side with the game at the same resolution (pixel lengths, gaps, outline, colors); check the meaning of `m`, header `s`/`c`, a custom color without `c;8`, rounding of odd thickness on an even screen (docs/CROSSHAIR.md "Open points")

## Last session

- Exit button on the main menu (footer, bottom right, "Exit (Esc)"; Esc already left the app). "Last search" now analyses the searches of all sessions at the setup of the newest one together (same DPI, sensitivity within 3 %, newest 120 rounds; `StoredSearches.pool`), because the strengths are played at the same sensitivity and more rounds tell the optimizer more. On the user's real data: 42 rounds from 2 sessions, best strength 0.70 (edge of the range), range 0.70 to 0.83, confidence 50 %, the same answer as the newest search alone. Rounds of different days also differ in how the player played; nothing corrects for that yet. Open: start a new search with the earlier rounds as a head start (fewer new rounds), like the calibration does.

- Last search (found when the "Last analysis" screen showed no curve for the user's real data: the 18-round run that gave the curve was the blind strength search, not the calibration, and "Last analysis" only analyses plain rounds): the acceleration intro has a "Last search" button (`AccelLastSearchScreen`). `StoredSearches` reads the strength back from each stored round (the search plays `NaturalCurve(CurveStrength.DECAY, strength)`, no curve is strength 1), runs `StrengthSearch` again over the stored score inputs and shows the strength card, and when the range excludes "no curve" the curve with the same export, copy and blind-test buttons as "Last analysis" (shared in `AccelFitScreen`). New sessions of the search get their own kind `accel_search` (`AccelSession.sessionKind` follows the phase); older ones hold the search and the test that followed in one `accel_ab` session, which is split heuristically (the end where rounds come in plain/curve pairs of one strength is the test; at least 16 rounds with 4 distinct strengths before it make a search). Checked on a copy of the user's real database: newest search 24 rounds, best strength 0.70 (the edge of the range, 30 % less gain at high speed), range 0.70 to 0.84, confidence 50 %. Plain rounds of search sessions now also count for the pooled calibration. Not done: a list of older searches; the heuristic can be wrong for a session whose search ended inside a pair-like pattern.

- Fullscreen (requested): F11 switches between the window (1280x720) and fullscreen in the display's current mode; the choice is saved as `AppSettings.fullscreen` and applied at the next start. F11 is ignored during a running round (a window change would lose the focus and abort it) and in the screenshot tour. Only the settings round trip is tested; the switch itself needs a try in the real window (multi-monitor, the mouse gate after the switch, the UI scale after the resize). No Settings tile for it yet.

- Last calibration (requested after a real 18-round run that found a good curve): the acceleration intro has a "Last analysis" button (`AccelLastAnalysisScreen`, shared curve card and reason texts in `AccelFitScreen`). It takes the setup of the newest plain acceleration round (`RoundRepository.latestPlainAccelSetup`), reads the stored plain rounds at that sensitivity (`RoundProcessor.lastCalibration`, same 3 % / 90 rounds as the pooled calibration) and fits them again, so the curve or the reason and the wanted-gain chart are available on any day without a new run. The numbers can differ slightly from the end of the run, because plain rounds of the A/B test and of other days count too. When a curve fits, the screen also offers "Export for Raw Accel" and "Copy settings.json" without a blind test first (the user wanted to export a curve they like); a caution says the blind test has not confirmed it. Not stored: the fit itself, no per-day breakdown, no list of older fits. The screen also starts the blind A/B test of that curve ("Test this curve blind": `AccelRun(startWithTest = curve)` begins in the TEST phase, `AppContext.startAccelTest`), at the DPI and sensitivity of the stored rounds; it needs the Raw Accel check of the intro and a mouse with report timestamps, otherwise the button is disabled with a hint. The result screen is the usual one (export only if better). Screenshot step `accel-last`.

- Calibration over several days (requested: play on different days to strengthen the curve result): a new acceleration run loads the plain-sensitivity click rounds of earlier `accel_ab` sessions at the same DPI and a sensitivity within 3 % (`RoundRepository.plainAccelRoundIds`, newest 90, `RoundProcessor.priorCalibration` analyses them again from the stored raw events) and adds their movements to the error analysis (`AccelRun.prior`). The run then asks for fewer new rounds (15 less the earlier ones, at least 3) and the between-rounds text says how many earlier rounds count. Not pooled: the blind strength search and the A/B test, which stay per run. Tests: `RoundRepositoryTest`, `AccelRunTest`, `RoundProcessorTest`. Open: judge it in the real app; a run started with the blind search does not look for earlier rounds; stored rounds from a different sensitivity are ignored, not rescaled.

- M13 Update check: `Version` (core), `UpdateChecker` (GitHub releases/latest, every failure including 404 of a private repository gives null and shows nothing), `UpdateService` (once a day, last find kept in the settings), hint with key U in the main menu, switch in Settings and `--no-update-check`, version of the build from `version.properties` (`-PappVersion`), `java.net.http` added to the packaged runtime. Tests: `VersionTest`, `UpdateCheckerTest` (local HTTP server incl. 404/403/429/500/odd JSON/no server), `UpdateServiceTest`. Open (user): after the repo is public, tag a newer version and see the hint in an older packaged app; not tried against the real GitHub API.

- Settings layout: the new hit-sound row pushed the footer (Back, Reset, Save) half out of the window; both sound switches now share one row ("Sounds": Flash sounds, Hit sound as toggle tiles), checked in the EN and DE screenshots.

- Release workflow (`.github/workflows/release.yml`): pushing a tag `vX.Y.Z` builds and tests, packages the app image with its own runtime (version from the tag via `-PappVersion`), smoke-tests it without Java, tries the `.exe` installer (needs WiX; the release still goes out with the zip if it fails) and publishes a GitHub release with generated notes. `ci.yml` now only builds and tests on pushes and pull requests. Not run yet: the first tag will show whether the runner has WiX. Release with `git tag v0.1.0 && git push origin v0.1.0`.

- Flash timing: the pop delay is now 0.5-0.65 s (was 0.45-0.9 s), matching the windups of the common flashes (Flashpoint/Arc Rose 0.5 s, Curveball/Blindside 0.6 s) after the player found the flash too slow. KAY/O (1.0/1.6 s) and Skye (up to 2 s) are slower and not modelled; blind stays 1.5 s (flashes blind 1.5-2.25 s). The simulation numbers for the flash scenario in ALGORITHMS.md were made with the old delay and are not re-run. Open: judge the new pace in the real app.

- Hit sound: every hit plays a short synthesized sound (click, glass-like ping, low thump; `HitSoundSynth`) whose pitch climbs a pentatonic scale with a streak of hits and starts over after a miss or a new round (`HitCombo`, `HitSounds`); switchable in Settings ("Hit sound", default on, `AppSettings.hitSound`); `Trainer.onShot` reports every shot, also clicks from the raw packet stream. Tests: `HitSoundTest`. Open (user): listen to it in the real app and say whether the character, volume and the climbing pitch feel right (tracking has no hits, so no sound there).

- M11 and M12: realistic target heights and a minimal map (targets at head height with a small off-level share, floor at eye height, corners and doorways, horizontal and vertical endpoint error in the metrics and on the results screen; simulation before/after in ANALYSE.md section E: no measurable change of the numbers except weaker detection of rising weak preferences at 15 rounds), and the Valorant crosshair (code import/export, editor, profiles, pixel-exact rendering). Open: head and eye height in Valorant's range (R1b), the crosshair compared with the game, plus the older open items below.

- Flash turn-away scenario (M10): a new scenario (flash visible, turn away until it is beyond the screen edge before it pops, turn back, hit the enemy; white-out when blinded), a dodge-rate score metric with an importance setting (never / sometimes / often) in onboarding and settings, flash rounds in the blind strength search and A/B test, documented with the simulation results in ALGORITHMS.md. Cue sounds (alert, rising whine, pop, ringing when blinded; synthesized, switchable in the settings) and a layout fix of the results screen (the buttons were pushed out of the window by the "equal scores" text) came after the first hands-on look. Unverified assumptions: the visible flight before the windup (pop delay 0.45-0.9 s) and the 10 degree exit margin. Earlier: - Blind curve-strength search in the app (recommended way in the acceleration intro): 24 rounds with curve strengths between x0.7 and x1.45 chosen by the sensitivity optimizer, result screen with strength, credible range and confidence, then the blind A/B test before any export; the error analysis stays as the second way. Analysis thresholds recalibrated (trust floor 0.005, BIC 8, gain range 1.02; see ALGORITHMS.md). Open: try it on the real mouse and judge how the curves feel, adaptation over several days, a longer search with a wider strength range if the result sits at the edge.

- Research export: History > "Share data for research" writes all stored rounds (raw mouse counts, target events, sensitivity, metrics; no names, paths or clock times) to one `.json.gz` in the data folder for sending to researchers (docs/RESEARCH-EXPORT.md, `ResearchExport`, `ShareDataScreen`, tests; 28 real rounds = 2 MB, under a second). Public datasets checked: none has target positions and raw trajectories (see chat/ASSESSMENT); the Aim Lab study (St Andrews, CC BY 4.0) has only per-participant summaries incl. cm/360 (n = 29 with sensitivity, median 23 cm/360).

- Assessment recommendations 1-4 and 7 implemented (docs/ASSESSMENT.md, "Status after the fixes"): range of equivalent sensitivities on the results screen, head-target simulated hand preset, curve analysis without reaches under 3 in/s and with a bounded factor and recalibrated thresholds (no false offers in 360 flat players; only falling gains of 25 % or more and rising of 50 % are found), trial buttons for both directions, curve feature marked experimental, default calibration 15 rounds, README positioning. Open: 5 (recordings of other players), 6 (measure a head in Valorant), a wider speed range for rising preferences.

- Independent assessment (docs/ASSESSMENT.md, evidence in docs/research/EXTERNAL-EVIDENCE.md, experiments in `AssessmentTest`): the sensitivity finder is statistically sound in simulation but its optimum is flat (+-15 % is below one round's noise) and tied to unverified target size and hand model; the curve finder detects only about 35 % gain changes, mistakes a low-speed baseline artefact for a falling preference, and offers curves with sensitivity factors of 2-3 for a true -15 %; the trial button picks its direction by the same artefact. Open tasks: see the recommendations at the end of ASSESSMENT.md (not started).

- Power test and trial curve: the detector misses rising gains of 15 % or less (0 of 25 offered) and finds 25 % in about half the runs; the analysis screen now offers a mild trial curve for the blind test when none was found. Known weakness: the A/B test cannot confirm a 15 % curve reliably; default of 8 calibration rounds is below what the tests used (15).

- M9 fixes (R1-R9, R11, most of R12/R13; see REVIEW.md "Status after the M9 fixes"): profile-defined target geometry (angular sizes, flick-angle mix, strafe speed, stops) and letterboxed 16:9 trainer picture; first reach must be a reach (real data: bad first errors 15 % to 2.7 %) with automatic recompute of stored metrics; consistency out of the score, first-shot accuracy in; search range centred on the current value; per-session trend and interleaved design points in the optimizer with a drift warning; median speed bins and a stricter A/B test (null: 2 % better / 3 % worse in 500 runs, noisy flat players offered a curve in 6 %); acceleration only with per-report timestamps; clicks read from the raw packet stream and shot at their own view; README for users, THIRD-PARTY-NOTICES, CI smoke test. Open: R1b (measure a head in the game), R10 (validation with other people), Raw Accel import test, installer on a clean machine.
- Review (REVIEW.md, no fixes): unit chain exact (new `UnitChainTest`); on the simulated player the optimum depends on target size (x0.64 per doubling of the radius, 117 cm/360 at head size vs 54 now), consistency favours low sensitivity, drift is not modelled (confidence 93 % at 40 % correct); on 20 real rounds 15 % of first movements are tremor fragments; A/B announces a winner in 12 % of null runs; search range cut off above the current value for small pads. Raw input timing from the real mouse is clean (1 kHz, no zero intervals); the packaged app image starts.

- M8: `ChartActor` and the score chart on the results screen (fitted line, 95 % band, round dots, optimum marker), the wanted-gain chart on the acceleration analysis, the History screen (menu H, recommendation after each session, game filter), English and German texts through `I18NBundle` with a language select in Settings, `:app:jpackage` (app image with a trimmed runtime, CI zip on tags), README screenshots. 268 tests pass (1 skipped). All screens checked through screenshots in both languages; the packaged app image runs the screenshot tour. Not checked with a real mouse: History with real data, the language switch in Settings, raw input and SQLite from the packaged build.
- M7: `core/accel` (Raw Accel curve models and per-report processor from the verified 1.7.1 source, analysis of error against speed, fits of classic/natural/power with BIC selection against no acceleration, blind A/B with a per-look corrected t test), accel in the round pipeline (recordings, view, trainer, storage schema 2, round logs), the simulated player expecting a speed-dependent gain, the Raw Accel `settings.json` export, the Acceleration menu with calibration, analysis, blind test and verdict screens, `RoundSession` shared with the blind test. 252 tests pass. On the simulated players the flow offers a curve to every rising-gain player, none of the flat ones, confirms it in 92 % of runs (median 5 pairs) and calls a harmful curve worse every time; a first version of the A/B test called a non-existent difference better in 40 % of runs because it looked after every pair without correcting for it, fixed with a per-look level and calibrated (1 of 30). The screens are only checked through screenshots and tests, not with a real mouse, and the exported file was not loaded into Raw Accel.
- M6: micro-adjust, tracking and target switching (`core/scenario`), tracking metric, per-scenario baselines, `ScenarioSchedule` (weights decide how often each scenario is played), simulated aimer for all four, scenario toggles in settings, trainer/HUD/between-rounds support, storage for waypoints and tracking metrics. 213 tests pass. Findings on the simulated player: scenarios have different best sensitivities (flick about 49, switching about 59, tracking 55-85, micro the edge of the range), so the mix finds a weighted compromise; mixed tests are less certain than flick-only ones for the same number of rounds (69 % within x1.3 of the blend after 30 rounds vs 88 % for flick only). The new screens are only checked through screenshots and tests, not with a real mouse.
- M5: `core/optimizer` (GP, expected improvement, interval, confidence), `BlindTest`/`BlindRun` in `app/blind`, blind trainer HUD, between-rounds and results screens, menu "Results" item, stored-round merging. 181 tests pass. The blind flow as a whole (real mouse, clicking through the screens) is untested by me; the optimizer and `BlindTest` are tested with the simulated player and the screens by screenshots.
- Verification harness (see checklist): simulated player, headless runner, simulated and replay mouse sources, round log files, `--record`, fixture regression test, `:app:screenshots`. 161 tests pass. Model parameters were tuned by hand so the optimum is interior and flat-topped; convergence numbers are documented in ALGORITHMS.md. The accel and optimizer parts of the requested end-to-end test cannot exist yet.

- M4: game profiles (Valorant, CS2, custom yaw/FOV), search range, onboarding, pre-test notice, settings screen, UI skin from the design tokens, mouse gate (raw input only in the trainer while focused), screenshot tour. Launch options are now dev-only. 140 unit tests pass; I compared 8 rendered screens against the mockups but could not click through the app myself.
- M3: segmentation, metrics, score, SQLite storage + settings store, trainer integration.
- M2: core units/FOV/flick scenario, input module, LibGDX trainer, debug overlay.

## Next steps

0. User tests M9 with `gradlew.bat :app:run`: the targets are now head-sized and much smaller (expect low accuracy at first), the pre-test notice says "Estimate", the between-rounds screen may warn about drift. Then R1b: measure a head in Valorant's range and set `verified`.

1. User tests M8 with `gradlew.bat :app:run` (or the app image from `:app:jpackage`): Results (R) with the chart, History (H) after a few sessions, Settings → Language (Deutsch/English, applies at once, kept on Save), the packaged `AimTune.exe` (does it start, does raw input work, does it create the database).
2. Still open from M7: the second hands-on part (analysis result, blind test, Raw Accel import of `settings.json`).
3. Then decide about pushing M8 and what comes after the milestones (pooling the A/B test over days, mixed-scenario confidence, refit with the curve on, see Open questions).

## Known bugs

- see docs/REVIEW.md: C1-C4 (targets too large, spurious first movements, consistency metric bias, no validation on real people), M1-M9. M4 UI has only been checked through rendered screenshots.

## Open questions

- WM_INPUT `wParam` (RIM_INPUT vs RIM_INPUTSINK) semantics for a message-only window are unverified; it is counted but not used for gating (ADR 0002).
- Trainer FOV assumes the game's FOV value refers to a 16:9 window and keeps the vertical FOV fixed when the window aspect changes. Real per-game aspect behavior is verified in M4.

- Raw Accel cannot be detected (no query API, driver stores no readable settings), so the pre-test notice asks for a manual checkbox; the Windows pointer acceleration setting is auto-detected. Notice only, never enforced; "Open Raw Accel" button dropped.
- Game FOV values for Valorant and CS2 come from community sources, not official docs; the sensitivity decimals (3) are unverified against the games' accepted precision.
- Decided: "Consistency" is the spread (sample SD) of the signed first-movement error, lower is better (see ALGORITHMS.md). Chosen because it uses the same over/undershoot quantity as the optimizer and accel analysis and does not double-count time to hit. Revisit if sideways error turns out to matter.
- Rounds per test: 10 is a suggested length ("Round 4 / 10", afterwards just "Round 12"), "Stop for today" is always available. "Continue session" is not built: a new run merges with the stored rounds automatically, which covers the use. The mockup menu entry is skipped.
- The confidence definition (chance that the estimate loses at most 0.2 x the score spread against the true best) and the 85 % target are my choice; revisit with real data. The scores of real humans may be flatter or noisier than the simulated player, then confidence will stay lower.
- Scenario geometry (target sizes, distances, tracking speeds 12-40 degrees per second, three targets for switching) is my first guess, not taken from a game; revisit after real play.
- Tracking has no click or hold: the on-target time is measured from the crosshair alone. Real tracking drills usually need the fire button held; this was left out to keep the input simple.
- Default is flick only. With all four on, rounds are spread over four kinds and a scenario needs 3 rounds of its own before its rounds count (until then they are a neutral 50), so confidence stalled in the user's test (19 % after 9 rounds). Idea if mixed tests should work: score rounds of a scenario without a baseline against the pooled baseline of the same metrics, or play scenarios in blocks.
- In the simulation micro-adjust prefers the lowest sensitivity of the range; if real players show the same, a mixed result can end at the edge of the range (the app then says so and caps the confidence).
- Blind rounds are only comparable per game and DPI; changing the game profile (FOV) starts a separate data set.
- Export Raw Accel settings needs a save location: LibGDX has no file dialog (options: native dialog via LWJGL tinyfd, or fixed folder). Decide at M7.
- Apex, Overwatch 2 and Fortnite tiles are omitted until yaw/FOV are verified (Valorant, CS2 and a custom yaw/FOV tile ship).
- The simulated player is a model I made up (reaction, Fitts, noise terms); it checks that the pipeline recovers a known optimum, not that real humans behave this way. Revisit its parameters with real recordings (`--record`).
- Raw Accel export: the file follows the 1.7.1 layout and validation rules read from the source but has not been loaded into Raw Accel; the user's test is the first real check. Export location is a fixed folder in the data directory plus a clipboard button, because LibGDX has no file dialog (decided at M7).
- The acceleration analysis assumes real players show a systematic under- or overshoot that depends on hand speed. The simulated player is built that way; real data may be flatter, in which case the app will say "no curve is clearly better", which is the intended outcome. Thresholds (20 movements per bin, BIC gain 10, 8 % gain change, factor-2 speed range, 2 % per-look level of the A/B test) are my choices, tuned on the simulation.
- The curve acts per mouse report while a player plans at the peak speed, so even a perfect fit removes only part of the error; the A/B test measures what is left. A refit with the curve switched on (second iteration) is not built.
- A short A/B test cannot capture a player getting used to a new curve over days; the counterbalanced order only spreads the effect over both conditions.
- Segmentation thresholds and the score's 0-100 mapping are starting values; revisit with real recordings and the simulated player (M5/M7).
