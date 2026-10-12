# Algorithms

Formulas and parameter reasoning. Sections marked *(draft)* are finalised in the milestone that implements them.

## Unit conversion

`cm360 = (360 × 2.54) / (DPI × sens × yaw)`; inverse `sens = (360 × 2.54) / (DPI × yaw × cm360)`. Example: 800 DPI, sens 0.249, yaw 0.07 → 914.4 / 13.944 ≈ 65.58 cm/360°. Sensitivity is rounded per the profile's format only on output.

## Search range *(draft)*

Table keyed by aim style giving a window around typical cm/360° values (initial defaults from the onboarding mockup: wrist 20-35, mixed 30-50, arm 45-80; to be tuned), capped above by pad width (a 360° turn must fit on the pad with margin). The current cm/360° is always included by widening the window if needed. Table lives in config, not code.

## Submovement segmentation *(draft, M3)*

Smooth speed from counts/timestamps, find movement onsets/offsets by speed thresholds with hysteresis, split further at local speed minima that follow a distinct re-acceleration. Each submovement records duration, peak speed, path length and signed endpoint error relative to target direction/distance. Thresholds are parameters, tuned on synthetic data.

## Metrics and score *(draft, M3/M6)*

Hit rate, time-to-target, signed overshoot/undershoot ratio, corrective submovement count, path efficiency (straight distance / path length), tracking time-on-target ratio and mean distance. Each metric is z-normalized against the per-user baseline; the score is a weighted sum with configurable weights.

## Bayesian optimization *(draft, M5)*

Variable `x = ln(cm360)`. GP with Matern 5/2 kernel plus learned observation noise; Cholesky via EJML. Mean function with session offsets (learning/fatigue) or per-session normalization. Acquisition: Expected Improvement (UCB as alternative); occasional repeats of tested points to estimate noise. Blind order is randomized by the optimizer, not shown to the user.

## Confidence *(draft, M5)*

From the posterior: width of the credible interval of the argmax (sampling or Laplace), mapped to a 0-100% value, plus a recommendation whether more rounds are worthwhile (expected improvement of the next round vs. threshold).

## Multi-session merging *(draft, M5)*

All rounds of all sessions enter one GP with per-session offset terms, so results combine without letting day-to-day shifts bias the optimum.

## Accel analysis and fitting *(draft, M7)*

Signed endpoint error as a function of input speed (unit verified against Raw Accel, presumably counts/ms). Desired gain g(v): overshoot at speed v lowers gain, undershoot raises it. Fit Raw Accel modes (list verified in M7) with regularized least squares under caps; select the mode by cross-validation or AIC/BIC. Export only if the blind A/B shows a measurable gain.
