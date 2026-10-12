# Algorithms

Formulas and parameter reasoning. Sections marked *(draft)* are finalised in the milestone that implements them.

## Unit conversion

`cm360 = (360 × 2.54) / (DPI × sens × yaw)`; inverse `sens = (360 × 2.54) / (DPI × yaw × cm360)`. Example: 800 DPI, sens 0.249, yaw 0.07 → 914.4 / 13.944 ≈ 65.58 cm/360°. Sensitivity is rounded per the profile's format only on output.

## Search range *(draft)*

Table keyed by aim style giving a window around typical cm/360° values (initial defaults from the onboarding mockup: wrist 20-35, mixed 30-50, arm 45-80; to be tuned), capped above by pad width (a 360° turn must fit on the pad with margin). The current cm/360° is always included by widening the window if needed. Table lives in config, not code.

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

**Score.** Metrics of a round are `accuracy` (higher better), `time to hit` (lower better), `consistency` (lower better) and, from M6, `tracking` (higher better). Over all rounds given to the calculator, each metric gets a baseline (mean and sample SD) and a round's metric becomes `z = ±(x − mean) / SD` (sign flipped when lower is better). The metric score is `100 · Φ(z)` with Φ the normal CDF, and the round score is the weighted mean over the metrics that round has, with weights renormalized (defaults from the settings mockup: accuracy 40, time to hit 30, tracking 20, consistency 10). A metric with fewer than 3 rounds, or without variation, has no baseline and is ignored; a round with no usable metric has no score. No population priors are invented: the baseline is the user's own rounds, so scores are relative to the user and recomputed whenever rounds are added. The 0-100 mapping and baseline window are revisited in M5 when the optimizer consumes scores.

## Bayesian optimization *(draft, M5)*

Variable `x = ln(cm360)`. GP with Matern 5/2 kernel plus learned observation noise; Cholesky via EJML. Mean function with session offsets (learning/fatigue) or per-session normalization. Acquisition: Expected Improvement (UCB as alternative); occasional repeats of tested points to estimate noise. Blind order is randomized by the optimizer, not shown to the user.

## Confidence *(draft, M5)*

From the posterior: width of the credible interval of the argmax (sampling or Laplace), mapped to a 0-100% value, plus a recommendation whether more rounds are worthwhile (expected improvement of the next round vs. threshold).

## Multi-session merging *(draft, M5)*

All rounds of all sessions enter one GP with per-session offset terms, so results combine without letting day-to-day shifts bias the optimum.

## Accel analysis and fitting *(draft, M7)*

Signed endpoint error as a function of input speed (unit verified against Raw Accel, presumably counts/ms). Desired gain g(v): overshoot at speed v lowers gain, undershoot raises it. Fit Raw Accel modes (list verified in M7) with regularized least squares under caps; select the mode by cross-validation or AIC/BIC. Export only if the blind A/B shows a measurable gain.
