# Review

Review of the state after M8 (commit `d844477`), 2026-10-13. Scope: does the method find a good sensitivity, is the acceleration logic sound, is the trainer realistic for Valorant, code quality, release readiness. Nothing was fixed; this file is the list of what should be.

How the numbers were produced: the gated experiments in `core/src/test/.../harness/review/` (simulated player, up to 300 runs per row; run with `AIMTUNE_REVIEW=1 ./gradlew :core:test --tests '*Review*'`, reports in `core/build/reports/harness/review-*.txt`), `storage/src/test/.../review/RealDataReviewTest` (reads a copy of a real `aimtune.db` given in `AIMTUNE_REAL_DB`), and the permanent `app/.../UnitChainTest`. The 20 real rounds are the user's own first sessions (1 kHz mouse, 800 DPI, Valorant). Everything "on the simulated player" is true for the model in `SimulatedAimer` only; see "Method validity" for what that does and does not prove.

## Verdict

The plumbing is sound and the design is careful: the unit chain is exact, the Raw Input path delivers clean 1 kHz data, the Raw Accel export matches the 1.7.1 schema, the optimizer is conservative, and 268 tests pass. But **the result cannot be trusted yet**, for three reasons that are independent of how good the optimizer is:

1. **The trainer's targets are 2-3x too large for a head** (flick: 2.05° median angular diameter, a head at 10-20 m is about 0.6-1.2°). On the simulated player the optimum moves from 117 cm/360° (head-sized targets, r = 0.10 m) to 54 cm/360° (current, r = 0.25 m). Target size alone can move the answer by a factor of two.
2. **The "first movement" that feeds the consistency metric and the whole acceleration analysis is wrong in about 15 % of real movements** (a tiny tremor fragment or carry-over from the previous click is taken as the first reach, mean "reaction time" 85 ms, impossible for a human). The acceleration part has therefore never run on valid real data.
3. **The score has a built-in sensitivity preference** (consistency improves monotonically with lower sensitivity) and **the confidence ignores learning/fatigue**: with a learning drift the app reports 93 % while only 40 % of the runs are within x1.3 of the truth.

Acceleration: the false-"winner" rate of the blind A/B test is 12 % (5.3 % better, 7.0 % worse over 300 null runs), not the "1 of 30" that M7 documented, and the analysis offers a curve to 42 % of noisy players who have a flat gain. Treat any curve recommendation as a hypothesis until C2 is fixed and the A/B test is made stricter.

Recommended order: C1, C2, C3 and C4 first (they change the numbers), then the drift and accel items, then release work. The REVIEW tests double as the regression harness for those fixes.

## Status after the M9 fixes

Fixed in code, with tests (details in ALGORITHMS.md; the findings below are kept as they were found):

| Finding | Status |
| --- | --- |
| C1 targets too large | **Fixed**: targets, distances, flick-angle mix, tracking speed and stops come from the profile (`trainer` block, `TrainerGeometry`), sizes are angles; the values are still assumptions (`verified: false`, pre-test notice "Estimate") until measured in the game |
| C2 spurious first reaches | **Fixed** and checked on the 20 real rounds: first errors below -0.5 went from 15 % to 2.7 %, 1113 of 1114 movements have a first reach; stored metrics are recomputed from the raw events at startup (`METRICS_VERSION` 2) |
| C3 consistency bias | **Fixed**: consistency no longer enters the score (diagnostic only); optima of single metrics are interior, weight sets now give 41-44 cm/360° instead of 41-149 |
| C4 no validation on people | **Open**: needs recordings of other people (protocol in the Realism section); the new geometry also showed that the simulated player's optimum with head-sized targets is 143 cm/360°, i.e. at the edge of what a person can do, which is a property of the simulation and not a recommendation |
| M1 search range | **Fixed**: centred on the current value, pad width only a hint, plausibility check in onboarding |
| M2 drift | **Mostly fixed**: per-session trend in the model, interleaved design points, `driftDetected` caps the confidence at 60 % and the app says so; confidence under drift fell from 93 % to 46-63 %. The recommendation itself is still pulled by strong fatigue or learning (in the simulation the drift moves the best sensitivity itself) |
| M3 A/B false positives | **Fixed**: 5.3 % + 7.0 % became 2.0 % + 3.0 % in 500 null runs; flat noisy players are offered a curve in 6 % instead of 42 % (median instead of mean per speed bin); rising gain still confirmed in 57 of 60 |
| M4 speed measurement | **Fixed where it can be**: acceleration is refused without per-report timestamps; jitter/batching tests; 4 and 8 kHz mice and the fit-versus-driver speed estimator are untested and still differ (see ALGORITHMS.md) |
| M5 scenario mix | **Fixed**: flick uses the angle mix of the profile (59 % under 10°, 79 % under 20°) |
| M6 first shot | **Fixed**: accuracy is the first-shot accuracy |
| M7 FOV at other shapes | **Fixed by not guessing**: the trainer letterboxes to the profile's aspect (16:9), so the horizontal FOV is the game's |
| M8 click ordering | **Fixed** for Raw Input: the click is read from the same packet stream and shot at its own view |
| M9 long histories | **Fixed**: hyperparameters searched on the newest 150 rounds, one query for round metrics; 400 rounds take 0.3 s instead of 3.9 s |
| Q1-Q9 | Q1 (dropped counter in the debug overlay, fallback to GLFW when Raw Input cannot start), Q2 (message for a round without mouse input), Q4 (metrics refresh), Q5 (N+1), Q6 (duplicate comment), Q7, Q9 (pre-test row) fixed. Not changed: `GetRawInputBuffer` (per-message reading handles 8 k messages a second, and there was no 4/8 kHz mouse to test a buffered path), Q3 (needs a multi-monitor/scaling setup), Q8 (documented) |
| Release | README (install, use, method, limitations), `THIRD-PARTY-NOTICES.md`, Raw Accel link, SPEC and ALGORITHMS corrections, CI smoke test of the packaged app on tags. **Open**: loading the exported `settings.json` into Raw Accel, an installer test on a machine without a JDK (both need hardware or a clean machine) |

## Findings

Severity: **critical** = the result or a recommendation is wrong or misleading; **major** = clearly degrades correctness or trust; **minor** = polish or risk that is contained.

### Critical

**C1. Targets are not defined by angular size and are too big (flick, switching).**
- Where: `core/.../scenario/FlickScenario.kt:8-15` (`targetRadius = 0.25`, distance 8-20 m, yaw ±50°, pitch ±25°), `SwitchingScenario.kt:8-18` (0.3 m, 8-18 m), `MicroScenario.kt:10-19` (0.15 m, 10-14 m), `TrackingScenario.kt:13-23` (0.5 m at 14 m). The profile has no target data (`games/GameProfile.kt`).
- Evidence: measured angular diameters (`review-geometry.txt`): flick p5/p50/p95 = 1.48° / 2.05° / 3.33°, switching 2.03° / 2.70° / 4.06°, micro 1.25° / 1.43° / 1.69°. A head-sized target (0.20 m effective diameter, an estimate, see "Realism") is 2.29° at 5 m, 1.15° at 10 m, 0.76° at 15 m, 0.57° at 20 m, 0.38° at 30 m: the flick targets are about 2.5x a head at the same distance. Simulated player, default weights, 17 sensitivities x 30 rounds (`review-bias.txt`): optimum 129 cm/360° at r = 0.08 m, 117 at 0.10, 75 at 0.15, **54 at 0.25 (current)**, 34.5 at 0.5, 25 at 1.0. About x0.64 per doubling of the radius. At r = 0.10 the simulated player needs 1.8 s per target instead of 1.3 s.
- Proposed fix: targets defined by angular size and distance distribution in the game profile (see "Realism"); the scene derives the sphere radius from angle and distance. Same for micro/switching/tracking. Keep the angular size independent of distance so a distance change cannot change difficulty.

**C2. The first-submovement definition picks spurious fragments on real data; the consistency metric and the accel analysis are built on it.**
- Where: `core/.../metrics/RoundMetrics.kt:118-125` (first submovement = first one whose peak lies between the spawn and the hit), `analysis/SubmovementSegmentation.kt` (start threshold 2 cm/s, minimum duration 30 ms, no amplitude or direction condition).
- Evidence (20 real rounds, 1114 click-scenario movements, `RealDataReviewTest`): **172 of 1114 (15 %) have a first error below -0.5**, at every distance (p10 of the signed error is -0.93 to -1.10 in all distance buckets from 0 to 40°+, medians about -0.02). Mean time from spawn to the first submovement start is 85 ms (a human reaction is 150+ ms), and the first fragment often has a peak speed of 0.9-1.0 in/s (the start threshold is 0.79 in/s) and 20-40 counts, e.g. `+451 ms, (-20, 2) counts` before a 1448-count flick at `+538 ms`; or a submovement at `+49 ms` in the opposite direction (carry-over from the previous click). Real errorSpread averages 0.46 over the 12 flick rounds (range 0.29-0.80), which these outliers dominate. `AccelAnalysis` drops errors below -0.8 only, so fragments between -0.8 and -0.5 still enter the speed bins.
- Proposed fix: the first reach is the first submovement that starts at least ~100 ms after the spawn (physiological minimum), points toward the target (cos > 0), and covers at least ~30 % of the target distance or exceeds a physical peak speed (e.g. 5 cm/s); fragments before it count as "pre-movement", not as the first reach. Re-validate with `RealDataReviewTest` (target: < 3 % with e < -0.5, reaction time p5 above 120 ms) and add a fixture of real rounds (`--record`) as a regression test. Because metrics are stored at save time, add a "recompute stored metrics from the stored mouse events" step and bump `METRICS_VERSION` (see Q4).

**C3. The consistency metric favours low sensitivity by construction.**
- Where: `core/.../score/ScoreCalculator.kt` (`CONSISTENCY`), `RoundMetrics.kt:81` (`errorSpread` = SD of the signed error divided by the distance).
- Evidence (`review-bias.txt`, optimum by metric weights; default player / noisy player x1.6): accuracy only 41 / 69, time to hit only 44 / 65, **consistency only 149 / 145 (the edge of the grid)**, default 40/30/10 45 / 69, speed heavy 20/70/10 44 / 67, precision heavy 60/10/30 **65 / 139**. A relative-error SD falls monotonically as hand movements get larger (the fixed hand noise in degrees shrinks), so it has no interior optimum and pushes the result toward the slow end whenever it carries weight. At the default weight (10 %) the shift is small (45 vs 44), but the weights are user-editable and a precision-heavy setting moves the answer by 1.5-2x.
- Proposed fix: take the metric out of the sensitivity objective (keep it as a diagnostic), or normalise it per movement distance and de-trend it against cm/360°. Show in Settings that precision-heavy weights prefer low sensitivities, or fix the weights for the optimizer. Re-run `ReviewBiasTest` to confirm that every single-metric optimum is interior.

**C4. No validation on real humans; the simulated player decides what "correct" means.**
- Where: `core/.../sim/SimulatedAimer.kt`, `SimulatedPlayer.kt` (hand-tuned).
- Evidence: real rounds (12 flick rounds): mean time to hit 887 ms and 84 % accuracy; simulated player at its optimum: about 1300 ms and 100 % accuracy (the simulated aimer only clicks when on target, so accuracy carries almost no information and time to hit decides everything). The real error distribution has a heavy left tail the simulation does not model (C2). Calibration, convergence and accel numbers in this document are therefore consistency checks of the pipeline, not evidence about people.
- Proposed fix: record 3-5 blind sessions of at least two people over a sensitivity sweep (`--record`), fit the player parameters to them (reaction time, Fitts slope, click policy: humans click early), compare the sim's score curve shape to the real one, and only then quote convergence numbers.

### Major

**M1. The search range is asymmetric for typical users and the pad cap is the wrong rule.**
- Where: `core/.../units/SearchRange.kt:59-62` (`hi = minOf(hi, padWidthCm)`, then the current value only gets a 1.1 margin).
- Evidence (`review-range.txt`): the current value is always inside, but for this user (mixed, 32 cm pad, 65.6 cm/360°) the range is 24.6-72.2: the current value sits at 91 % of the log width, the upper end is only 10 % above it and the lower end 2.7x below; an optimum above 72 cm/360° cannot be found. Arm style with a 27 cm pad collapses to 20.8-27.0 (x1.30) and misses the whole arm window (45-80). A turn does not need to fit the pad (people lift the mouse; a 180° turn needs half), so capping at the pad width is not meaningful. With the current value near an end, `atRangeEdge` caps the confidence at 50 %, so the user sees "not sure" but cannot get a better answer without changing the setup.
- Proposed fix: centre the range on the current value in log space (e.g. current / 1.8 to current x 1.8, clipped to a hard 10-150 band), widen by the aim style table, and use the pad width only as a warning (a 180° turn needing more than the pad width). Extreme inputs (current 0.5 cm gives 0.45-32, current 3000 gives 24.6-3300) need a plausibility check in onboarding.

**M2. The confidence does not know about drift, and is not calibrated in either direction.**
- Where: `core/.../optimizer/GaussianProcess.kt` and `SensitivityOptimizer.kt:232-243` (session and scenario offsets only; no trend within a session; no warm-up exclusion).
- Evidence (30-round blind tests, 40 seeds, truth 58 cm/360°; `review-drift.txt`): no drift: median error x1.10, 90 % within x1.3, mean confidence 72 %. Fatigue 3 %/min: x1.14, 83 % within, bias +13 % (toward lower sensitivity). Fatigue 6 %/min: x1.37, **25 %** within, bias +37 %. Learning (noise x1.5 to x0.7): x1.19, 88 % within, **confidence 93 %**. Stronger learning (x2.0 to x0.6): x1.39, **40 % within, confidence still 93 %**. Multi-session merging is fine: a bad middle session, improving sessions and equal sessions give x1.08-1.13 and 88-90 % within. Without drift the confidence is conservative, not overconfident (next item).
- Why: after the first six random design rounds the optimizer repeats the sensitivities near the apparent best, so a time trend is confounded with sensitivity (late rounds cluster near the optimum).
- Proposed fix: add a monotone-in-time term (a linear trend in round index per session, or a warm-up discard of the first 1-2 rounds per session), randomise design-point order across the whole run (interleave a few design points late), and report the trend. Add the drift cases from `ReviewBiasTest` as a regression test with an upper bound on the confidence under drift.

**M3. Blind A/B false-positive rate is 12 %, not 3 %; the analysis stage offers curves to noisy flat players.**
- Where: `core/.../accel/AccelAb.kt:102` (`DEFAULT_ALPHA_PER_LOOK = 0.02`, looks at pairs 4-10), `AccelFit.kt:50` (BIC gain 10, range 8 %).
- Evidence (`review-accel.txt`): plain against plain, 300 runs of up to 10 pairs: 'curve better' **16 (5.3 %)**, 'curve worse' **21 (7.0 %)**, undecided 263 (the M7 test used 30 runs: 1 of 30, which cannot distinguish 3 % from 12 %). Per direction this is about the planned one-sided 5 %, but a verdict in either direction means a 'winner' is announced in 1 of 8 runs where there is nothing to find; the documented 3 % was luck of a small sample. Flat players: no curve offered in 100 of 100 runs at noise x1 (15 and 8 rounds), 1 of 60 at 80 cm/360°; **at noise x1.6, 42 of 100 are offered a curve** (the A/B test then calls it worse in 31, undecided 11, better 0). Rising-gain players: offered 60 of 60, confirmed 56; falling-gain players: offered 60 of 60, confirmed 59, and in all of them the curve falls with speed (sign conventions are right).
- Proposed fix: per-look level 0.01 (about 2.5 % per direction) or an O'Brien-Fleming boundary, require min 6 pairs, and a minimum effect size (e.g. 2 score points) in addition to the interval; make the BIC threshold depend on the measured noise (permutation null: shuffle speeds, refit, require the real BIC gain above the 95th percentile). Re-run the null with at least 500 runs and put the rate in ALGORITHMS.md.

**M4. Speed for the curve depends on timestamps; the fallback path and high polling rates break it.**
- Where: `core/.../accel/AccelProcessor.kt:22-26` (per-report speed, time clamp 0.0625-100 ms), `app/.../input/GlfwMouseSource.kt:83` (timestamps taken in the frame's callback), `core/.../analysis/SubmovementSegmentation.kt` (1 ms bins, 7-bin average) versus `AccelAnalysis` (uses the smoothed peak).
- Evidence (`review-speed.txt`, one 30° reach at 800 DPI, true peak 19.3 in/s): 1 kHz ideal: per-report max 20.0, smoothed peak 19.3; 1 kHz with ±0.3 ms jitter: 35.0 / 22.0; 8 kHz with ±0.05 ms jitter: 40.0 / 19.6; **1 kHz with timestamps batched per 8 ms frame: 320 in/s (16x, the time clamp)**, smoothed peak 22.0; batched per 16.7 ms: 320 / 46.4. Real data from the Raw Input path (1 kHz mouse): inter-event time p5/p25/p50/p75/p95 = 0.97/0.99/1.00/1.02/3.00 ms, no zero intervals in 583 983 events, so this mouse is fine; 4 and 8 kHz mice are untested. The curve is fitted against the smoothed peak but applied by the driver (and the in-app simulation) per report, which are different quantities as soon as the timestamps are noisy.
- Proposed fix: accel simulation, calibration and A/B only with the Raw Input source (disable them for `GlfwMouseSource` with a message); use GetRawInputBuffer or the hardware timestamp-free batching rule and fall back to 1-ms-bin counts at 4/8 kHz; compute the fit speed with the same estimator the driver (and `AccelProcessor`) uses, or smooth the per-report speed the way Raw Accel's "input speed smoothing" would (the export writes 0). Add jitter/batching cases to `AccelProcessorTest`.

**M5. Scenario mix does not represent Valorant, and the default is flick only.**
- Where: `core/.../scenario/ScenarioKind.kt`, `storage/AppSettings.kt` (`DEFAULT_SCENARIOS = [flick]`).
- Evidence (`review-geometry.txt`): distance between consecutive flick targets p10/p50/p90 = 21° / 40° / 72°, **0 % below 10°, 9 % below 20°**; the real rounds show the same (72 % of movements start more than 20° from the crosshair). Micro is 4-9° but off by default. Valorant engagements are mostly small corrections near the crosshair plus occasional flicks; flick-only measures large-flick speed, which is the case where high sensitivity helps most.
- Proposed fix: weighted target-distance distribution in the profile (see "Realism"); make the default round a single scenario with a mixed distance distribution (about 60 % under 10°, 30 % 10-30°, 10 % over 30°) instead of four scenarios that need 3 rounds each before they count.

**M6. Scoring does not model first-shot precision.**
- Where: `core/.../scenario/RoundSummary.kt`, `ScoreCalculator.kt` (accuracy = hits / all clicks, time to hit = until the hit, no concept of a first shot).
- Evidence: a target needing two clicks costs one miss (accuracy) and the extra time (time to hit), which approximates but does not equal "first-shot headshot rate", and spamming is not capped.
- Proposed fix: per-target first-shot hit (yes/no) as the accuracy metric, with time to the first shot (not the first hit); count a second click as a miss of the target.

**M7. Valorant FOV at other aspect ratios is unverified and probably not Hor+.**
- Where: `games/GameProfile.kt:15` (Hor+ only), `app/TrainerScene.kt:24` (fixed vertical FOV for any window shape), `games/src/main/resources/games/valorant.json`.
- Evidence: 103° horizontal at 16:9 is confirmed by several community sources and by `UnitChainTest` (projection edge test). For 4:3 one source says about 71° horizontal; the Hor+ rule gives 86.6° (`UnitChainTest` prints 21:9 → 117.6°, 4:3 → 86.6°). No source found for ultrawide behaviour (one forum thread says the game forces 16:9). Neither profile has a primary source (Riot documents no FOV).
- Proposed fix: render the trainer in a fixed 16:9 viewport for Valorant (letterbox), or measure the game's FOV on the user's resolution (see "Realism") and store an `aspectBehavior` in the profile; say in the UI which assumption is used.

**M8. A click is applied to a view that has moved on since the click.**
- Where: `app/.../Trainer.kt:93-101` (`fire()` applies all pending mouse input first, then shoots at the render-thread time), `TrainerScreen.kt:50-58` (LibGDX `touchDown`).
- Evidence: the click carries no timestamp and mouse reports that arrived after it (up to one frame, 8-17 ms) are applied before the shot. During a flick the hand moves 10-20 in/s: up to 0.1-0.3 in = 80-240 counts, i.e. 2-6° at 47 cm/360°, in the worst case (the hand is usually slowing down at the click). Time to hit is quantised to the frame.
- Proposed fix: read the button state from the same `RAWMOUSE` packets (`usButtonFlags`) so clicks and movement are one ordered, timestamped stream, and shoot at the view of the click's timestamp.

**M9. Long histories and the optimizer cost.**
- Where: `app/history/History.kt:30-48` (refits all rounds up to each session), `SensitivityOptimizer.kt:232` (450 fits per call, O(n³) each).
- Evidence (`review-range.txt`): analyze plus suggestNext takes 0.1 s at 30-100 rounds, 0.5 s at 200, 3.9 s at 400. The history repeats this per session, so 1000 stored rounds in 100 sessions take minutes. `ADR 0004` assumes "a few hundred rows".
- Proposed fix: cache the hyperparameters per game/DPI (fit once, reuse), or cap the history refit at the last 300 rounds, and run `RoundRepository.rounds()` with one join instead of a metric query per round.

### Minor

**Q1.** `WindowsRawInputSource.kt:152-171` reads one packet per message with `GetRawInputData`; `GetRawInputBuffer` is the intended path at 4-8 kHz. Overflow and unfocused drops are counted (`droppedOverflow`, `droppedUnfocused`) but never shown. `AimTuneApp.createSource` does not fall back to GLFW when the Raw Input window cannot be created.
**Q2.** Absolute-position devices (RDP, touch, some VMs) are dropped silently (`RawInputParser.kt:24`); the app should say so when a round has no events.
**Q3.** Focus loss aborts the round and releases the cursor (`TrainerScreen.kt:98-101`, `AimTuneApp.kt:31-35`): correct. Not testable here, hence untested: multi-monitor, Windows display scaling (the HUD works in design units, raw input does not depend on scaling), cursor release on alt-tab in fullscreen.
**Q4.** `METRICS_VERSION` is written (`RoundRepository.kt:42`) and never read. Changing a metric definition (C2) leaves old stored metrics stale. Add a recompute step.
**Q5.** `RoundRepository.rounds()` runs one metric query per round (N+1); `blindRounds` compares `dpi` with `==` on doubles.
**Q6.** Duplicate stacked KDoc comments above `AccelFitResult` (`AccelFit.kt:29-30`); `AccelFitResult.reason` carries English text next to `reasonCode` (the app translates the code, so the string is dead for the UI).
**Q7.** Onboarding accepts any positive sensitivity and DPI up to 100 000 (`SetupDraft.kt`); no plausibility warning (see M1).
**Q8.** Unknown scenarios with no baseline are fed to the optimizer as a neutral 50 (`BlindTest.kt:121`); documented, but it dilutes mixed tests.
**Q9.** `GameProfile.sources` are strings; no profile marks a value as unverified. Add `verified: false` flags so the UI can show a hint.

### Anti-cheat rules (CLAUDE.md)

Confirmed by search and by reading the input module: no process, window or memory APIs (`OpenProcess`, `ReadProcessMemory`, `FindWindow`, hooks, `SendInput`, `SetCursorPos` do not appear), the only Win32 calls are `RegisterRawInputDevices`, `GetRawInputData`, `SystemParametersInfoW` (read-only, `SPI_GETMOUSE`), `QueryPerformanceCounter/Frequency` and the window functions for the app's own message-only window. Mouse input is read only while the trainer screen is shown and the window is focused (`MouseGate`; the registration is removed on focus loss and events that still arrive are dropped before queuing). One thing to state in the README: the raw input registration uses `RIDEV_INPUTSINK` because a message-only window can never have focus; it is removed whenever the app loses focus.

## Method validity

All numbers here are on the simulated player (`SimulatedAimer`), see C4. Truth is a smooth fit (degree 4) of a reference sweep of 17 sensitivities x 24 rounds per player.

**Convergence** (80 seeds per cell, range 20-140 cm/360°; fraction of runs whose estimate is within x1.3 of the truth / true loss at most the reported "negligible difference"):

| Case | 8 rounds | 12 | 20 | 30 |
| --- | --- | --- | --- | --- |
| optimum 50 | 75 % / 94 % | 86 % / 95 % | 94 % / 98 % | 95 % / 99 % |
| optimum 35 | 84 % / 88 % | 85 % / 86 % | 94 % / 95 % | 95 % / 98 % |
| optimum 80 | 85 % / 99 % | 83 % / 99 % | 85 % / 99 % | 89 % / 99 % |
| noisy player (x1.6) | 76 % / 90 % | 75 % / 89 % | 80 % / 89 % | 83 % / 90 % |

Mean true loss at 30 rounds: 0.45-1.14 score points (the score curve spans about 50 points).

**Confidence calibration** (all cases pooled; what actually happened in runs that reported a given confidence):

| Rounds | Reported confidence | Runs | Loss ≤ negligible | Within x1.3 | Interval covers truth |
| --- | --- | --- | --- | --- | --- |
| 12 | 40-59 % | 139 | 91 % | 81 % | 88 % |
| 12 | 60-79 % | 127 | 94 % | 84 % | 89 % |
| 12 | 80-100 % | 90 | 97 % | 92 % | 87 % |
| 20 | 80-100 % | 123 | 97 % | 94 % | 87 % |
| 30 | 60-79 % | 148 | 95 % | 92 % | 90 % |
| 30 | 80-100 % | 156 | 99 % | 93 % | 83 % |

So without drift the confidence is **conservative**: "80 %" means the estimate loses at most the negligible difference in 97-99 % of the cases and is within x1.3 in about 93 %. The 80 % credible interval covers the truth in 80-99 % of the runs, i.e. slightly over-covering (80-96 % at 30 rounds). The exception is a truth at the edge of the range (narrow range 30-50 with the optimum at 50): coverage 41-70 %, correctly flagged by `atRangeEdge` and capped at 50 %. Under learning drift the confidence is **overconfident** (M2). The confidence is a loss-based number, not the probability that the interval covers the optimum; label it accordingly in the UI ("chance that nothing noticeably better exists").

**Edge behaviour and few data points:** the GP reverts to its mean outside the data (no runaway extrapolation); below 10 rounds the confidence is capped at 50 %, and the reported mean confidence at 8 rounds is 38-48 % against 75-99 % actual success, so few rounds are not overconfident here. A slope that ends at the range edge is flagged; that works, but see M1 for how often the range puts a real optimum at the edge.

**Blind test leaks:** none found in the UI. The HUD shows "Round n / N" and no debug overlay (`Hud.kt:31`, F3 disabled in sessions), the between-rounds screen shows confidence and a note about the range edge (which reveals only that the best value is near an end), the log line prints the seed but not the sensitivity, no sound. The order is a seeded shuffle for the first six rounds and EI afterwards; repeats replay the best point, so a user who feels the sensitivities can recognise repeats. The sensitivity is felt by definition; the blind test only removes the expectation. `--record` round logs contain cm360 (dev-only).

**Fatigue/learning/multi-session:** see M2. Multi-session merging does not bias the result; within-session trends do.

**Score weights:** see C3. A sensitivity-preferring metric, not just noise, decides the answer when the weights change.

**Acceleration** (`review-accel.txt`, `accel-pipeline.txt`):

| Check | Result |
| --- | --- |
| Flat player, 15 calibration rounds, 100 seeds | curve offered in 0 |
| Flat player, 8 rounds, 100 seeds | 0 |
| Flat player, noise x1.6, 100 seeds | **42 offered** (31 judged worse, 11 undecided, 0 better) |
| Rising gain 1→1.5, 60 seeds | offered 60, confirmed better 56, undecided 4; curve rises with speed in 60 |
| Falling gain 1→0.7, 60 seeds | offered 60, confirmed better 59; curve falls with speed (sign conventions correct) |
| A/B null, 300 runs, ≤10 pairs | better 5.3 %, worse 7.0 %, undecided 87.7 % |

Causality is modelled correctly: calibration rounds are played without a curve (`AccelRun.kt` `accel = null`), speeds are computed from raw counts (pre-curve), the curve is applied to input speed in `Trainer` and `HeadlessRound`, and the A/B test compares plain at the recommended sensitivity with the curve at that sensitivity divided by the fitted factor, which is also what the export does (output DPI = DPI x factor, in-game sensitivity unchanged). A refit with the curve on is not built (documented). What is not trustworthy is the first-movement input (C2) and the speed estimate (M4).

**Raw Accel export:** checked against `RawAccelOfficial/rawaccel` v1.7.1 (`wrapper/wrapper.cpp`, `common/rawaccel-base.hpp`, `rawaccel.hpp`, fetched from the tag): all `JsonProperty` names the export writes (`Gain / Velocity`, `Cap / Jump`, `Cap mode`, `Input speed calculation parameters` and its three half-life names, `Output DPI`, the three ratio names, `Degrees of rotation`, `Degrees of angle snapping`, `Input Speed Cap`, the device config names) match, `Output DPI / 1000 * (1000 / DPI)` is the multiplier the driver applies (`rawaccel.hpp:412,429`), the half-lives default to 0, and `NORMALIZED_DPI = 1000` as assumed. The file has still not been loaded into Raw Accel itself (needs Windows with the driver, still an open item from M7). The repository moved from `a1xd/rawaccel` to `RawAccelOfficial/rawaccel`; README and docs link the old name.

## Realism (Valorant)

### What could be researched and what could not

I searched for primary sources and found none for the geometry. Riot publishes no hitbox, head-size or movement-speed numbers, and the wikis that might list them blocked automated access (Fandom returned 402/403). Only the following is established; everything else is an **assumption that must be checked by the user before it goes into the profile**.

| Item | Value | Source / status |
| --- | --- | --- |
| Yaw | 0.07 °/count at sensitivity 1 | in the profile, used by the community converters (csdb.gg); correct in `UnitChainTest` |
| FOV | 103° horizontal at 16:9 (about 70.5° vertical), locked | several community sources ([afkgaming](https://afkgaming.com/esports/guide/can-you-change-fov-in-valorant), [mouse-sensitivity.com thread](https://www.mouse-sensitivity.com/forums/topic/8438-valorant-locked-fov/)); not Riot |
| Hitbox size | the same for all agents | community statement, a developer remark relayed by [MeinMMO](https://mein-mmo.de/en/valorant-popular-agent-jett-dies-faster-than-the-others-why,500318); some models are smaller than their hitbox ([vlr.gg thread](https://www.vlr.gg/401413/valorant-hitboxes)) |
| Head proportion | about 13 % of the standing body height (visual estimate on the Jett model) | [Reddit measurement](https://libredd.it/r/VALORANT/comments/pu9pbv/i_calculated_the_size_of_the_head_compared_to_the/), not extracted data |
| Beta hitboxes | head hitboxes were larger than the models and were reduced later | [win.gg](https://win.gg/news/explaining-the-hit-registration-and-latency-problems-in-valorant/) |
| Agent height, head hitbox in cm | **not found** | assumption below |
| Run/walk/crouch speed in m/s | **not found** (Riot's support page is qualitative only) | assumption below |
| Engagement distances | **not found** | assumption below |

### Working assumptions (to be replaced by measurements)

- Effective head hitbox diameter 0.15-0.25 m, central estimate 0.20 m (agent about 1.8 m x 13 % = 0.23 m tall, narrower than tall).
- Typical duel distances 5-30 m, median 12-15 m (long angles up to 40 m; close corners 3-8 m).
- Tangential strafe speed 3-6 m/s. Angular speed = v / d: at 10 m 17-34°/s, at 14 m 12-25°/s, at 20 m 9-17°/s; a counter-strafe stops within about 0.1-0.2 s.

**A way to get the real numbers without any game access** (consistent with the anti-cheat rules; it uses the mouse only): in Valorant's shooting range, move the crosshair from one edge of a bot's head to the other and measure the mouse travel in cm at your known cm/360° (angle = travel / cm360 x 360°), at 2-3 distances the range offers. The same for the strafe: time a bot's crossing of a known angle. Put the values into the profile.

### Gap between the trainer and these values

| Quantity | Trainer now | Head / Valorant (assumed) | Gap |
| --- | --- | --- | --- |
| Flick target diameter | 2.05° median (1.5-3.3°) | 0.6-1.2° at 10-20 m | 2-3x too large |
| Switching target diameter | 2.7° (2.0-4.1°) | same | 3-4x |
| Micro target diameter | 1.43° (1.25-1.7°) | same | about 1.3-2x |
| Tracking target | 1 m diameter at 14 m = 4.1° | a body 0.5-0.9 m wide at 14 m = 2-3.7° | 1.1-2x |
| Flick distances | median 40°, p90 72°, 0 % below 10° | mostly < 15°, rare > 40° | wrong mix |
| Micro share | opt-in, 4-9° | the dominant case | wrong default |
| Tracking speed | 6-22°/s in 1.2-2.8 s straight segments at 14 m | 12-25°/s at 14 m with sudden stops and reversals | slower, no stop-and-go |
| Scoring | accuracy = hits/clicks, time to hit | first-shot head accuracy | approximated (M6) |

The simulated player shows why this matters (C1): the size of the target moves the optimum by x0.64 per doubling; with 0.10 m radius targets the same player would be recommended 117 cm/360°, with 0.25 m radius 54 cm/360°.

### Profile fields to add (task list in PROGRESS.md)

`targets.diameterDegrees` (distribution, min/max), `targets.distanceMeters` (min/max), `engagement.flickAngleDegrees` (bucket weights: 0-5, 5-10, 10-20, 20-40, 40+), `movement.strafeSpeedMetersPerSecond` (min/max), `movement.stopProbability`, `scenarioWeights` (flick, micro, switching, tracking), and a `verified` flag plus `sources` per field. CS2 and custom profiles get defaults with `verified: false`. Document the sources in `docs/ALGORITHMS.md` ("Realism").

## Code quality

- **Tests:** all 268 existing tests pass on a clean run (core 138 in about 270 s, app 72, storage 35, input 14 with 1 skipped on non-Windows, games 9); the review added 4 permanent `UnitChainTest` tests and 9 gated experiment tests that are skipped unless `AIMTUNE_REVIEW` / `AIMTUNE_REAL_DB` is set (281 tests in total, 10 skipped, 0 failing). The full suite takes about 5 minutes because the simulation harness runs inside `core:test`; consider a `harness` source set that runs on CI nightly.
- **Unit chain** (`UnitChainTest`, new, passing): one raw count turns the trainer view by exactly `sens x yaw` (1e-12 degrees, Valorant and CS2 at 400-3200 DPI), the pitch has the same scale with the mouse-down-looks-down sign, `DPI x cm360 / 2.54` counts make exactly one 360° turn (within half a count), and the horizontal FOV edge falls on the screen edge for both profiles (the camera construction is replicated in the test, because `TrainerScene` needs a GL context). The trainer uses the exact cm/360° while the user types a sensitivity rounded to 3 decimals: at most 0.2 % at 0.249.
- **Segmentation:** only tested on synthetic data until now; on real data see C2. Real rounds produce 123 submovements per 45 s round, median duration 229 ms, median peak speed 5 in/s (p95 20 in/s).
- **Coverage:** see the section below.
- **Dead code and duplication:** Q6; the four scenario classes duplicate the spawn/hit/miss code (`FlickScenario`, `MicroScenario`, `SwitchingScenario`); fine at this size.
- **Storage:** migrations are versioned and guarded against newer schemas; a corrupt or unwritable database is reported at startup and the app continues without saving (`AimTuneApp.openDatabase`); a corrupt settings file is reported and not overwritten (`SettingsStore`). Not covered: a corrupt mouse blob in a stored round (decode throws `IllegalArgumentException`, only used by fixtures), Q4, Q5, M9.
- **i18n:** `BundlesTest` guarantees that en and de have the same keys; diagnostics, exception messages and `AccelFitResult.reason` stay English (Q6).
- **Thread safety:** the queue, counters and the focus flag are concurrent or volatile, everything else in `WindowsRawInputSource` is confined to the input thread, `RoundProcessor` and `BlindTest` use single-thread executors; no problem found.
- **Frame pacing:** vsync is on by default (`--no-vsync`), idle FPS 30; the trainer reads the mouse from the queue once per frame, so input latency is one frame plus the vsync wait; no frame-time statistics are recorded (M8).

## Release readiness

- **Installer / clean machine:** `./gradlew :app:jpackage` produced an app image (112 MB, own trimmed runtime). I ran `AimTune.exe --screenshots` from it with `JAVA_HOME` unset: it started, opened the Raw Input source and rendered 25 screens, exit 0. Not verified: the SQLite native library and raw input with a real mouse inside the packaged build (needs a person at the machine), an `.exe`/`.msi` installer (needs WiX), and a machine without any JDK. CI only packages on tags and does not start the result.
- **README:** has features, build and safety; missing for users: installation (download, unzip, SmartScreen warning because the exe is unsigned), a short "how to use", the method in plain language (blind rounds, why cm/360°, what confidence means), and honest limitations (simulated validation only, Windows only, Raw Accel export untested in the driver, accel and consistency caveats from this review). The Raw Accel link is outdated (`RawAccelOfficial/rawaccel`).
- **CONTRIBUTING:** has "add a game" and "add a language"; it should mention the new target and engagement fields and the `verified` flag once they exist.
- **Licences:** project MIT. Dependencies: LibGDX/LWJGL (Apache-2.0 / BSD-3), JNA (dual LGPL-2.1 or Apache-2.0, use Apache), sqlite-jdbc (Apache-2.0, bundles SQLite, public domain), kotlinx-serialization and Kotlin (Apache-2.0), Inter and JetBrains Mono (SIL OFL, licence files are bundled in `assets/fonts`), FreeType natives (FreeType licence). All compatible with MIT. Missing: a `THIRD-PARTY-NOTICES` file in the distribution. No per-file licence headers (not required for MIT; the repository does not use them).
- **Docs vs code:** `docs/SPEC.md` still says "custom Gaussian process with EJML" (ADR 0004 amended: own Cholesky), "head-sized targets" (not true, C1), "fatigue/learning handled via session/time offsets" (only session offsets, M2) and "regularized least squares ... cross-validation" (BIC with a free factor); `docs/ALGORITHMS.md` says the A/B false-positive rate was calibrated at 1 of 30 (M3); the README links the old Raw Accel repository.

## Coverage

JaCoCo over a full `gradlew test` (measured with a Gradle init script, no build file was changed; the gated review experiments are skipped in this run):

| Module | Lines | Branches | Main gaps |
| --- | --- | --- | --- |
| `core` | 98 % (1519/1553) | 81 % | none that matter; the harness exercises the optimizer, simulator, accel and scenarios |
| `storage` | 95 % (510/538) | 78 % | corrupt-blob and migration-failure paths |
| `games` | 92 % (88/96) | 73 % | custom-profile error paths |
| `input` | 69 % (143/208) | 57 % | `WindowsRawInputSource` message loop and registration (only a skipped/real-device test), `SimulatedMouseSource` |
| `app` | **23 %** (588/2553) | 28 % | all Scene2D screens (`OnboardingScreen` 0/246, `SettingsScreen` 0/157, `UiSkin` 0/164, `Hud` 0/115, `AppContext` 0/122, `ChartActor` 0/85); they are only checked through screenshots |

The untested parts are exactly the ones that talk to the real world (raw input, UI state, navigation). `core` is well covered but its tests check agreement with the simulated player, not with people (C4). Suggested additions: a `Trainer` test with click/move ordering (M8), `AccelProcessor` jitter and batching cases (M4), a real-round fixture for the segmentation (C2), `AppContext` navigation tests with a fake `Game`.
