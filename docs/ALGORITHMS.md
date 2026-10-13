# Algorithms

Formulas and parameter reasoning. Sections marked *(draft)* are finalised in the milestone that implements them.

## Unit conversion

`cm360 = (360 × 2.54) / (DPI × sens × yaw)`; inverse `sens = (360 × 2.54) / (DPI × yaw × cm360)`. Example: 800 DPI, sens 0.249, yaw 0.07 → 914.4 / 13.944 ≈ 65.58 cm/360°. Sensitivity is rounded per the profile's format only on output.

## Search range (M4, implemented)

`SearchRangeTable.DEFAULT` gives a window per aim style (wrist 20-35, mixed 30-50, arm 45-80 cm/360°, from the onboarding mockup; to be tuned). `searchRange()` then: (1) caps the upper bound by the pad width (a 360° turn must fit), (2) keeps the hi/lo ratio at least 1.3, (3) widens the window so the current cm/360° lies inside with a 1.1 margin. The current value is therefore always searchable. The table is a plain data class so it can move into config later.

## Submovement segmentation (M3, implemented)

Input: the round's mouse reports (counts, timestamps) and the DPI. Code: `core/analysis/SubmovementSegmentation.kt`.

1. **Speed profile.** Counts are summed into 1 ms bins, the x and y bin vectors are smoothed with a centered 7-bin moving average (smoothing the vectors, not the magnitude, avoids rectifying noise), and the speed is the vector length in counts/ms.
2. **Thresholds in physical units.** Speeds are compared in cm/s so they do not depend on DPI: a movement starts when the speed exceeds 2 cm/s and ends after the speed has stayed below 1 cm/s for 30 ms (hysteresis). The start is moved back to where the speed last exceeded the lower threshold. Movements shorter than 30 ms are dropped. At 800 DPI one count per ms is 3.2 cm/s, so isolated single-count jitter never reaches the start threshold after smoothing.
3. **Splitting.** Inside a movement, the local maxima and minima of the speed form an alternating peak/valley list. A valley is a real split only if its speed is at most 40 % of the lower neighbouring peak, both neighbouring peaks are at least twice the start threshold, and both resulting submovements last at least 40 ms. The valley that violates this most is removed first, merging it with the lower neighbouring peak, until none is left. This is the usual velocity-minimum decomposition of aimed movements into a ballistic phase and corrections.
4. **Output per submovement:** start, peak time, end, summed `dx`/`dy`, path length (sum of per-report step lengths) and peak speed in counts/ms (the unit Raw Accel uses, to be verified in M7).

The defaults are `SegmentationParams`; they were checked on synthetic minimum-jerk reaches (single flick, diagonal reach, two separate movements, a correction that overlaps the first reach, a slow smooth movement, 1 kHz vs 8 kHz). They are starting values and are re-tuned against real recordings and the simulated player in M5/M7.

## Metrics and score (M3, implemented for flick)

Code: `core/metrics/RoundMetrics.kt`, `core/score/ScoreCalculator.kt`.

**View reconstruction.** The view starts at yaw 0, pitch 0 at round start and is rebuilt from the recorded counts and the active `cm360` (`ViewTrack`), so every metric can be recomputed from the stored raw data.

**Per movement** (target spawn to the hit on it; targets under 2° from the crosshair at spawn are skipped):

- Target offset `T` on the screen plane in degrees: yaw difference scaled by cos(target pitch), pitch difference. `D0 = |T|`.
- First submovement = the first one whose peak lies between spawn and hit. Its view displacement `d` is measured the same way. **Signed endpoint error** `e = (d·T/|T| − D0) / D0`: `> 0` overshoot, `< 0` undershoot. This is the quantity the accel analysis (M7) uses together with the submovement's peak speed.
- Corrective submovements = submovements in the window minus one.
- Path efficiency = `D0 / (angular path travelled between spawn and hit)`, capped at 1.
- Time to target = spawn to hit; reaction = spawn to the first submovement's start.

The cos(pitch) scaling is a small-angle approximation; targets are within ±25° pitch, where the error is a few percent at most.

**Per round:** accuracy = hits / shots, mean time to hit, mean signed and mean absolute first-movement error, mean corrective submovements, mean path efficiency, and `errorSpread` = sample standard deviation of the signed first-movement errors.

**Consistency** (the metric shown with weight 10 in the settings mockup, not in the original spec) is defined as `errorSpread`; lower is better. It measures how repeatable the first movement is, independent of its average bias.

**Score.** Metrics of a round are `accuracy` (higher better), `time to hit` (lower better), `consistency` (lower better) and, from M6, `tracking` (higher better). Over all rounds given to the calculator, each metric gets a baseline (mean and sample SD) and a round's metric becomes `z = ±(x − mean) / SD` (sign flipped when lower is better). The metric score is `100 · Φ(z)` with Φ the normal CDF, and the round score is the weighted mean over the metrics that round has, with weights renormalized (defaults from the settings mockup: accuracy 40, time to hit 30, tracking 20, consistency 10). Baselines are kept per scenario when the scenarios are given (a micro round is easier than a flick round; pooled, they would only measure the scenario). A metric with fewer than 3 rounds (in that scenario), or without variation, has no baseline and is ignored; a round with no usable metric has no score. No population priors are invented: the baseline is the user's own rounds, so scores are relative to the user and recomputed whenever rounds are added. The 0-100 mapping and baseline window are revisited in M5 when the optimizer consumes scores.

## Verification harness: simulated player (implemented)

Used to test the pipeline without a human. Per target the aimer waits a reaction time (normal, at least 120 ms), makes a minimum-jerk primary reach, waits a feedback delay (140 ms), and corrects until its remaining error is inside the target's angular radius, then clicks after a click delay; a hand twitch at the click can turn a marginal hit into a miss. All randomness comes from one seeded generator.

For a reach of amplitude `A` degrees at cm/360 `c` (with `c_eff = c / optimumScale / gainPreference(speed)`):

- endpoint error per axis: `sigma = noiseLevel * fatigue * sqrt((signalNoise * A)^2 + (handNoiseCm * 360 / c_eff)^2)`. The second term is the fixed hand precision seen in degrees: it hurts at high sensitivity (small `c`). First reaches also fall short by `undershootBias`.
- duration: `(a + b * log2(1 + A / W) + handCm / maxHandSpeed) * fatigue`, with `handCm = A * c_eff / 360` and `W` the target's angular width. The last term hurts at low sensitivity (large `c`). Corrections use 60 % of the Fitts term.
- `fatigue = 1 + fatiguePerMinute * minutes of session time` scales noise and all times.
- Counts are the rounded position along the reach, reported only when they change, so quantization and event timing look like a real sensor.

The two terms give an interior optimum. Because everything depends on `c / optimumScale`, `withOptimum(c*)` moves the optimum to `c*` exactly (up to count quantization). With the default parameters the score peaks at about 50 cm/360 with a flat top (within 2 points from roughly 38 to 73 cm); `noiseLevel` also moves the optimum, so cases with other noise levels take their ground truth from a dense reference sweep (17 grid points x 30 rounds, parabola through the best three points) instead of the configured value. `PipelineConvergenceTest` checks that the sweep peak matches the configured optimum.

Measured with the baseline estimator (9 grid points x 4 rounds = 36 rounds, 20-140 cm/360, 8 seeds): within a factor 1.3 of the truth in 88-100 % of the runs, median error factor 1.06-1.18 (see `core/build/reports/harness/baseline-convergence.txt`). That is the number the optimizer has to beat with fewer rounds. These tolerances describe this simulated player; they say nothing about real humans.

## Bayesian optimization (M5, implemented in `core/optimizer`)

**Model.** `x = ln(cm360)`. Round scores (0-100, computed over all rounds together, see "Metrics and score") are standardized and modelled as `y = f(x) + offset(session) + noise` with a Matern 5/2 prior on `f`. The session offset (a better or worse day) is integrated out by adding a "same session" covariance `tau^2` to the kernel matrix, so rounds of many sessions give one estimate of `f` without a day-to-day shift biasing the optimum. Hyperparameters (lengthscale 0.12-0.7 times the log width of the search range, signal variance, noise variance 0.15-1.2, session variance 0 / 0.1 / 0.4, scenario variance 0 / 0.4) are picked from a small grid by marginal likelihood. The noise floor is deliberately not tiny: with few rounds a near-zero noise estimate gives an overconfident fit. Cholesky is hand-written (ADR 0004 amendment).

**Choosing the next sensitivity** (`suggestNext`, a pure function of the history and a seed, so a test can be stopped and continued):
1. Rounds 1-6: a seeded, shuffled, stratified design over the log range (hidden random order).
2. Afterwards: expected improvement over the best posterior mean on a 121-point grid, excluding values within 3 % of the previous round's.
3. Repeats: from round 8 on, if no point has been tested twice in a session, or every 6th round, the tested point with the best posterior mean is replayed, so the noise is observable.

**Result** (`analyze`, from 3 rounds on): the argmax of the posterior mean refined by a parabola, plus 400 posterior draws of `f` on the grid. From the draws:
- the central 80 % interval of the best cm/360 ("Range 38-46"),
- the expected regret (score points lost by choosing the estimate instead of the true best),
- the confidence: the share of draws in which the estimate loses at most `negligible = max(0.5, 0.2 * sd of the round scores)` points against that draw's best. Below 10 rounds, or when the best value lies within 2 % of the log width of an end of the searched range, it is capped at 50 %; the result then carries `atRangeEdge`.
More rounds are recommended while the confidence is under 85 %; "about N more rounds" assumes the failure share shrinks like 1/rounds and is only a rough guide.

**Why this confidence and not the interval width.** Scores are flat near the optimum (the simulated player loses under 2 points between 38 and 73 cm/360), so the argmax interval stays wide however many rounds are played, while any value in it is practically as good. The loss-based number answers the question that matters: could a different value be noticeably better. The interval is still shown.

**Measured on the simulated player** (12 seeds, 28 rounds, tolerance x1.3 around the truth): 67-83 % of runs within tolerance, against 67-92 % for the 36-round grid baseline, so the optimizer reaches about the same accuracy with 22 % fewer rounds, not a dramatic gain (`core/build/reports/harness/optimizer-convergence.txt`). Calibration over 120 runs: runs reporting 80 % or more confidence were within x1.3 of the truth 96 % of the time, 60-80 % confidence 78 %, below 40 % only 57 %.

## Scenarios, score combination and the schedule (M6)

**Scenarios.** Flick: one target at a time, at least 15 degrees from the previous one. Micro-adjust: radius 0.15 m at 10-14 m, each target 2.5-9 degrees from the previous one. Target switching: three targets visible, a hit replaces only that one (at least 14 degrees from the others and from the hit one). Tracking: one target of radius 0.5 m at 14 m moving in straight segments of 1.2-2.8 s at 6-22 degrees per second (the first version, 12-40 degrees per second in 0.5-1.4 s segments, was too fast and twitchy for a person to follow) inside a +-35 / +-15 degree field; no clicks. Speeds and headings of the segments are stratified (spread evenly over their ranges in random order), so every round is about equally hard; without this the path alone moved the on-target time by as much as the sensitivity did.

**Movements with several targets.** A reach runs from the later of the target's spawn and the previous hit to the hit on it, and time to hit is measured the same way. With one target at a time this is the spawn, as before.

**Tracking metric.** The round is sampled every 10 ms from the first spawn; the crosshair is on target when its angle to the target centre is at most the target's angular radius. `tracking = on-target samples / samples` (higher is better, feeds `TRACKING`); the mean angle is stored as `tracking_mean_distance_deg`. Tracking rounds have no hits, so they only produce the tracking metric; the other scenarios produce accuracy, time to hit and consistency.

**Score combination.** The round score is the weighted mean of the metrics the round has (see "Metrics and score"), against the baselines of its scenario. The weights also decide how often a scenario is played, because a round only measures its own scenario: the weight of each metric is split among the enabled scenarios that produce it, and a scenario's share of the rounds is the sum of its metrics' weights. With the default weights and all scenarios on, tracking gets 20 % of the rounds and flick, micro and switching 26.7 % each. A scenario whose metrics all weigh 0 is not played. The plan is smooth weighted round robin, so scenarios are spread evenly and the plan for a round index never changes. The optimizer sees one pooled set of scores, so it maximizes the weighted average of the scenarios' score curves.

**Optimizer changes for mixed scenarios.** Observations carry a group (the scenario) and the kernel has a "same group" term like the session term, chosen by marginal likelihood. In the simulation the fit never chose it, because the per-scenario scores already have the same mean; it is cheap insurance. Lengthscale factor 1.2 was dropped: with it the fit became almost linear and in one of 16 mixed runs the optimum ran to the edge of the range with high confidence. A best value at the edge is now flagged and its confidence capped, because a slope only shows that the optimum may lie outside the range.

**Simulated player, new scenarios.** Switching: the aimer goes for the visible target nearest to its aim and keeps it; a target that was already visible is re-acquired after `reacquireMs` (110 ms) instead of the full reaction time. Tracking: every 40 ms the aimer sees the target as it was `trackingLagMs` (150 ms) ago, estimates its velocity, anticipates `trackingPrediction` (80 %) of the motion during the delay plus half a reach, and commands `trackingGain` (70 %) of the remaining error as an 80 ms minimum-jerk reach. The command is limited by `trackingMaxHandSpeedCmPerS` (4 cm/s, which hurts a high cm/360 on fast segments) and perturbed by signal-dependent noise plus `trackingNoiseShare` (2x) times the flick hand noise (which hurts a low cm/360). These parameters are hand-tuned for a clear optimum, like the flick model.

**Measured on the simulated player** (45 s rounds, 13 grid points, 12 rounds each, `scenario-sweeps.txt`): flick scores peak at about 49 cm/360, target switching at about 59, tracking at about 64 (on target 0.29 at 20 cm/360, 0.66 at 53-73, 0.33 at 140). Micro-adjust keeps improving up to the largest value of the range (precision on tiny targets, no travel-time penalty), so its optimum is the edge. A blind test of 30 rounds with all four scenarios and the default weights (16 seeds, `mixed-scenarios.txt`) ended within a factor 1.3 of the weighted blend of the four curves (65 cm/360) in 94 % of the runs, with a median loss against the blend of 0.6 score points (with the faster first tracking version: 69 %). Flick only: 88 % within 1.3 (8 seeds). Mixed tests are less certain than flick-only tests for the same number of rounds, because each scenario gets only a fraction of them, and a scenario needs 3 rounds of its own before its rounds have a score (until then they count as a neutral 50 and carry no information). With the real app a user with 9 rounds in 3 sessions and all four scenarios on saw 19 % confidence, which is why flick only is the default. These numbers describe the simulated player only.

## Multi-session merging (M5)

Rounds with the same game and DPI from all sessions enter one fit; each blind-test run is its own session for the offset term. Scores are recomputed over all of them every time. See `SensitivityOptimizerTest` (merged sessions with a 10 point worse second day).

## Acceleration (M7)

**What was verified in Raw Accel** (a1xd/rawaccel v1.7.1, read from `common/*.hpp`, `driver/driver.cpp` and `wrapper/wrapper.cpp`; nothing here was guessed):

- Modes: classic, jump, natural, synchronous, power, lut (lookup), noaccel. AimTune fits and exports classic, natural and power; synchronous, jump and lookup are not used.
- Speed: per mouse report, `|counts| * (1000 / DPI) / time` in inches per second, where `time` is the time since the previous report clamped to 0.0625-100 ms (`DEFAULT_TIME_MIN/MAX`, the first report after a pause sees the longest time). At 1000 DPI one count per ms is one inch per second. The counts of the report are multiplied by the curve's value, then by `output DPI / 1000 * 1000 / device DPI`, and the driver carries fractions to the next report.
- "Gain / Velocity": `false` (Velocity, `LEGACY` in the code) means the curve is the sensitivity multiplier at that speed; `true` (Gain) means it describes the gain (derivative) and uses other formulas. AimTune simulates and exports the Velocity form so that what was tested is what is exported.
- Formulas (Velocity form, cap mode "output"): classic `1 + sign * min(a^(e-1) * (v - offset)^e / v, |cap - 1|)` (a cap below 1 makes it fall from 1 toward the cap); natural `limit' * (1 - (offset - exp(rate * (offset - v)) * (offset - v)) / v) + 1` with `limit' = limit - 1` and `rate = decay / |limit'|`; power `min((scale * v)^n + constant / v, cap)` above the speed where it reaches the output offset, the offset below it. `AccelCurveTest` pins values computed from these formulas.
- Validation (`rawaccel-validate.hpp`): every parameter is checked whatever the mode (acceleration, scale, gamma, decay rate, limit, sync speed positive, motivity > 1, classic exponent > 1, power exponent > 0, smooth in 0..1, offsets and caps not negative), so an export carries valid defaults for the unused ones.
- `settings.json` layout: Json.NET names taken from `wrapper.cpp` (`DriverConfig`: version, defaultDeviceConfig, profiles, devices; `Profile` marks every property as required; `AccelArgs` property names such as "Gain / Velocity", "Cap / Jump", "Cap mode"). The device DPI must be set (it normalizes the speed unit), and the output DPI of the profile is the device DPI times the sensitivity factor, so the in-game sensitivity stays as it is.
- Not verified: the exported file was not loaded into Raw Accel itself (no Windows driver here). It is checked against the required properties and the validation rules read from the source; if Raw Accel rejects it, that is a bug to report.

**What the analysis looks for.** A player aims with the gain they expect at a given hand speed. If the mouse gives less than that at some speed, first movements fall short there; if it gives more, they overshoot. The signed endpoint error of the first submovement (see "Metrics and score") against its peak speed therefore shows how the wanted gain changes with speed. A movement that landed at `1 + e` times its distance wanted `1 / (1 + e)` times the gain it got.

1. Samples: first movements of rounds without acceleration, (peak speed in in/s, `e`), movements that landed at under 0.2 or over 3 times the distance are dropped. All click scenarios are pooled: flicks alone cover too narrow a speed range (under a factor 2 between the bins), micro-adjust adds the slow end.
2. Bins: equal-count bins of speed (20 movements at least, 4-10 bins), per bin the mean log ratio and its standard error. The wanted log gain is the negative of it, normalized so that its average over all movements is 0: it says how the wanted gain changes with speed, not what the sensitivity should be (that is the optimizer's job).
3. Fit: each family (classic with exponent and cap, natural, power with output offset 1) is fitted to the bin values with weights `1 / (se^2 + 0.02^2)`, with a free overall factor (Raw Accel curves start at 1 and the factor moves into the output DPI), by Nelder-Mead from several starts. A fitted curve is recommended only if its BIC beats "no acceleration" by 10 points (BIC = misfit + parameters * ln(bins)) and it changes the gain by at least 8 % across the measured speeds. Too few movements or too narrow a speed range are reported as such.

**Blind A/B test.** The fitted total gain is `factor * curve(v)`. The plain condition runs at the recommended cm/360, the curve condition at cm/360 divided by the factor with the curve on. Rounds come in pairs with the same scenario and the same seed (identical targets) in counterbalanced order (groups of two pairs, plain-curve then curve-plain, each group's pattern chosen by the seed), up to 10 pairs. All rounds are scored together (the usual score), each complete pair gives a difference, and a one-sided t bound on the mean difference decides after every pair from the fourth: curve better if the lower bound is above 0, worse if the upper bound is below 0, no measurable difference if undecided after 10 pairs. Looking after every pair inflates false positives, so each look uses a one-sided level of 2 % (Pocock style, t quantile from the normal quantile with a Cornish-Fisher correction), calibrated on simulated rounds: with no real difference the curve was called better in 1 of 30 runs and worse in 0 (a first version with 10 % per look called it better in 12 of 30). Only a verdict "better" offers the export.

**Simulated player.** `gainPreference(v)` is the gain the player expects at the peak hand speed `v` (in/s) of a movement (1 at low speeds). The aimer plans its hand movement with that gain (found by three rounds of iteration, since speed depends on the movement), the mouse delivers the gain of the active curve (1 without one), and the aimer sees the resulting view, so a mismatch shows up as under- or overshoot and extra corrections. Time and noise use the delivered gain. A player that expects a gain rising from 1 to 1.5 undershoots by about 25 % without a curve and loses a fifth of its hits; the matching natural curve takes most of that back (a curve acts per report while the player plans at the peak speed, so a little undershoot remains).

**Measured on the simulated players** (`core/build/reports/harness/accel-pipeline.txt`, `accel-ab-power.txt`, `accel-ab-calibration.txt`): 15 calibration rounds (flick, switching, micro in turn) at 43-55 cm/360 offered a curve to every rising-gain player (60 of 60, mostly the power family, factor about 0.95, gain change 1.16-1.19) and to none of 10 flat players; the blind test confirmed it in 55 of 60 runs (92 %), median 5 pairs, the rest undecided after 10 pairs; a curve that gets in a flat player's way was called worse in 10 of 10 runs within 4-5 pairs. With fewer calibration rounds the simulated rising-gain players were still offered a curve in 20 of 20 runs from 5 rounds on (18 of 20 with 3) and flat players in at most 1 of 20, which is why the app plays 8 rounds and lets you analyse from 5. These numbers describe the simulated player only: real hands may not show errors this systematic, and a real player also adapts to a new curve over many rounds, which a short A/B test cannot capture (the counterbalanced order only spreads that effect over both conditions).
