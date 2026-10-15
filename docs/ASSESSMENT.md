# AimTune: independent assessment (after M9)

Written 2026-10-15 as a skeptical outside evaluation, not a defence of the work. All numbers come from simulated players unless stated; the external facts come from `docs/research/EXTERNAL-EVIDENCE.md` (sources and their quality are listed there). Experiments: `core/src/test/.../harness/review/AssessmentTest.kt` (gated; run with `AIMTUNE_REVIEW=1 ./gradlew.bat :core:test --tests "*AssessmentTest.<name>"`, reports in `core/build/reports/harness/assess-*.txt`). Cells have 30-40 simulated players, so a percentage carries about +-15 points of sampling error (95 %, worst case); differences smaller than that are not claimed.

## Status after the fixes (same day)

The recommendations at the end were implemented and measured again; the findings below are kept as they were found. What changed and what the measurements say now:

| Recommendation | Done | Measured result |
| --- | --- | --- |
| 1 range, not a point | results screen shows the stretch in which the model scores within the negligible difference (`equivalentLowCm360`..`HighCm360`), with a text for "flat" (ratio 1.8 or more) | at 8 / 15 / 30 rounds the median stretch is 37-62 / 38-56 / 40-57 cm/360 for a true optimum of 49.5 |
| 2 hand model | `SimulatedPlayer.forHeadSizedTargets` (default hand scaled so that the optimum with head-sized targets is 50 instead of 156 cm/360), guard test, `assess-headtargets.txt` | finder on that hand: median error 18 / 16 / 9 %, within +-15 % in 40 / 48 / 73 %, same flat shape (+-15 % costs 0.4-0.6 points). Still not validated against people |
| 3 curve feature | marked experimental in the app and README; reaches under 3 in/s are not used; sensitivity factor bounded to +-10 %; thresholds recalibrated on 400 flat players (BIC 5, range 1.04); trial buttons for a rising and a falling curve | see below |
| 4 calibration rounds | default 15 (early analysis from 8) | |
| 7 positioning | README and results text say "range", "clean aim test is not a game", "experimental" | |

**Curve finder after the fixes** (30 players per cell; details in ALGORITHMS.md):
* The baseline of a player without preference is flat (measured wanted gain within +-0.006 at all speeds instead of +0.066 at 1.8 in/s); the artefact is gone, and with it the asymmetry and the harmful offers.
* No false offers in 360 flat players; A/B with no difference 1.7 % better, 3.3 % worse (300 runs).
* Falling gain: -25 % found and confirmed in 27-29 of 30 (15 rounds), -35 % and -50 % in all; -15 % and below not found. Rising gain: only +50 % is found in a third to a half of the runs (confirmed in a fifth); everything smaller is not found. **This is the honest detection limit: the automatic path finds only large preferences, and rising ones almost not at all**, because a hand that expects more gain moves slower and leaves a narrow speed range.
* The trial path now works as a hypothesis test: for a player without preference "better" in 0 of 60 runs (50 "worse", 10 "no difference"); for a true 15 % preference "better" in 17-21 of 30, for 25 % in 24 of 30. It still measures an unadapted hand.
* Curves are no longer harmful where they are offered (the best fit for a true -15 %, which is below the detection limit and not offered, would gain +6.1 points in the paired measurement against +4.7 for an oracle curve; its sensitivity factor is now 1.05 instead of 1.9), but for rising preferences the fitted curve captures only a tenth of an oracle curve's benefit (+0.5 to +0.9 points against +4 to +13).

**Follow-up on a more precise curve** (trust floor 0.02 to 0.005, BIC 8, range 1.02; numbers in ALGORITHMS.md): the error analysis now finds falling preferences of 15 % and rising ones of 25 % (offered 21 and 19 of 30 at 15 rounds), but for rising ones the fitted curve is far too weak (+0.5 points against an oracle +4 to +13) because the hand speeds it sees span only about 4-10 in/s; a second calibration with the first curve on does not repair it. A blind search over one parameter, the curve strength, with the optimizer that finds the sensitivity, recovers the strength well (true +15 % found as x1.22 with +9.5 points of real benefit, falling -25 % as x0.76) and finds all 80 simulated preferences, at the cost of about 24 rounds, and needs the blind A/B test as a final guard (a flat player's interval excluded "no curve" in 5 of 16 runs). Not implemented in the app yet.

Still open: recommendations 5 and 6 (they need other people and the game), the validation of any of this against people, and a wider hand-speed range for the rising case.

## Verdict in ten lines

1. **Sensitivity finder: works as a statistical instrument, unproven as a product.** In simulation it lands within about 8-19 % (median) of the true optimum at 8-30 rounds, and the score curve is so flat there that the cost of being 15 % off is below the noise of one round. Nobody has run it on a human whose true optimum is known, and the one controlled human study found in the literature says the real optimum is a broad band where 2x steps cannot be told apart.
2. **The number it prints is mostly a property of the setup.** Doubling the target radius moves the optimum by about x0.64; changing the score weights moves it by under 7 %. The absolute value is set by the target size and by the simulated hand model, neither of which is verified.
3. **With the Valorant-sized targets the simulated player's own optimum (143 cm/360 and still rising) lies outside what any human uses** (pros 33-65 cm/360, controlled study 20-80). So the harness no longer says anything about where a real person's optimum will fall; it only says the optimizer finds the optimum of whatever curve it is given.
4. **Curve finder: not fit to ship as it is.** It detects only large effects (about 35 % gain change reliably), it confuses a measurement artefact at low speeds with a falling-gain preference, and for a true -15 % preference it offers a curve with a sensitivity factor of 2-3 that the blind test then (correctly) rejects in 25-29 of 30 runs.
5. **The asymmetry (falling found, rising missed) is a property of the method, not luck:** the analysis of a player with no preference at all already shows a "falling gain" shape below 3 in/s that is as large as a 15 % preference.
6. **The "try a mild curve" button mostly answers "worse"** (27 of 30 flat players), picks its direction from the same artefact (a coin flip for a true +15 % preference), and measures an unadapted hand. It protects against bad curves; it does not find good ones.
7. **No external validation exists for any part:** no trainer-to-game transfer study, no acceleration study for first-person aiming, no prior art for Bayesian sensitivity finding (so also no benchmark).
8. **Compared with existing tools** (converters, PSA, a closed web finder) AimTune is the only one that tests blind and reports uncertainty. That is a real advantage in method, not in proven accuracy.
9. **Reasonable product positioning:** "finds a sensitivity range you perform equally well in, and tells you when it cannot tell", with the curve feature labelled experimental or removed until fixed.
10. **What only real human data can settle** is listed in section D4; that is the next investment, before any further algorithm work.

---

## A. Sensitivity finder

### A1. How precisely does it recover the optimum?

Truth = argmax of a smooth fit to a reference sweep (17 grid points x 24 rounds, flick scenario, 45 s rounds); search range 20-140 cm/360; 40 simulated players per cell; the default optimizer and weights. "True loss" is the score the player really loses by using the recommendation instead of the true best.

| noise | true optimum | rounds | error, median / p75 / p90 | within +-15 % | true loss mean (p90), points |
|---|---|---|---|---|---|
| x0.7 | 37.2 cm | 8 | 9 / 20 / 44 % | 60 % | 2.24 (5.02) |
| | | 15 | 11 / 23 / 41 % | 60 % | 1.78 (4.29) |
| | | 30 | 7 / 16 / 29 % | 70 % | 1.13 (3.03) |
| x1.0 | 46.0 cm | 8 | 19 / 31 / 40 % (bias +11 %) | 38 % | 1.27 (2.83) |
| | | 15 | 13 / 21 / 33 % (bias +9 %) | 63 % | 0.76 (2.18) |
| | | 30 | 8 / 16 / 21 % (bias +5 %) | 73 % | 0.39 (1.17) |
| x1.6 | 70.7 cm | 8 | 13 / 21 / 28 % (bias -7 %) | 50 % | 0.61 (1.39) |
| | | 15 | 12 / 19 / 26 % | 63 % | 0.49 (1.33) |
| | | 30 | 5 / 12 / 23 % | 78 % | 0.46 (1.03) |

In centimetres at noise x1.0 (46 cm): a median miss of about 9 cm at 8 rounds, 6 cm at 15, 4 cm at 30. Reading: **8 rounds is a coarse estimate (a bad one in one case of ten is 40 % off), 15 is usable, 30 is good**; more rounds beat the noise slowly. The non-monotone step at noise x0.7 (8 to 15 rounds) is within sampling error at n = 40. The early estimate is biased high by about 10 % at noise x1.0 with few rounds.

### A2. How flat is the curve? Does a "perfect" sensitivity mean anything?

Score points lost relative to the true optimum (same sweeps; one round's score has an SD of 2.4-2.8 points at noise x0.7 and x1.0; the x1.6 sweep reports 0.6 because scores are normalised over the pooled rounds, so the unit differs):

| change of cm/360 | x0.70 | x0.85 | x1.15 | x1.30 | x1.50 | x2.00 |
|---|---|---|---|---|---|---|
| noise x0.7 | 6.5 | 1.2 | 0.7 | 2.3 | 5.0 | 12.0 |
| noise x1.0 | 5.3 | 0.9 | 0.5 | 1.7 | 3.5 | 8.3 |
| noise x1.6 | 3.2 | 0.6 | 0.4 | 1.1 | 2.4 | 4.5 |

**+-15 % costs 0.4-1.2 points, which is half a single round's noise or less.** The curve is asymmetric (too fast hurts less than too slow in this model). A person cannot feel, and a 45-second round cannot measure, such a difference. This agrees with the controlled study (NVIDIA, CHI 2022): a 4x band of 20-80 cm/360 in which adjacent 2x steps were statistically inseparable (13 participants, stationary targets; read through a summarizer). **The honest product is a range plus a "stay within it" message, not a point value.** What AimTune can tell a person is mainly "you are far outside your range" (x0.7 costs 3-6 points, x2 costs 4-12), which is useful but different from what the UI headline suggests.

### A3. Is the reported confidence calibrated at the default round count?

Pooled over the three noise levels:

| rounds | stated confidence | n | actual P(true loss <= "negligible") | actual P(within +-15 %) |
|---|---|---|---|---|
| 8 | 0-39 % | 36 | 94 % | 39 % |
| 8 | 40-59 % | 84 | 95 % | 54 % |
| 15 | 60-79 % | 50 | 98 % | 62 % |
| 15 | 80-100 % | 28 | 100 % | 75 % |
| 30 | 60-79 % | 40 | 90 % | 68 % |
| 30 | 80-100 % | 61 | 100 % | 82 % |

The stated number is **conservative** against "loses no more than a tenth of the score spread" (the percent it states is far below the 94-100 % that happens) and roughly **matches** a stricter reading of "within +-15 % of the true sensitivity" (stated 40-59 %, actual 54 %; stated 80+ %, actual 75-82 %). The 80 % interval covers the truth in 80-93 % of runs at noise x0.7 and x1.0 (nominal) and 98-100 % at x1.6 (too wide). No overconfidence was found. Caveat: this is the simulator checking its own statistics; it shows the GP is internally consistent, not that the score measures what a person needs.

### A4. How much is the player, how much is the setup?

From the earlier review sweeps (`review-bias.txt`, flick, 17 x 30 rounds) and A1:

* **Score weights barely matter:** optimum 40.9 (accuracy only), 43.6 (time only), 43.6 (default), 43.6 (speed heavy), 43.6 (precision heavy): spread under 7 %. For a noisy player 64.8-68.7 (6 %).
* **Target size matters a lot:** radius 0.08 m 124 cm/360, 0.10 m 96, 0.15 m 72, 0.25 m 51, 0.50 m 32, 1.00 m 20 (the lower end of the range): about **x0.64 per doubling of the radius**. With the profile's head-sized targets (0.4-1.9 deg) the flick sweep is still rising at 160 cm/360 (score 29, 55, 69, 72, 74 at 30, 50, 80, 120, 160 cm).
* **Player noise matters:** x0.7, x1.0, x1.6 noise give optima of 37, 46 and 71 cm/360.

So the printed value is "the optimum of this player model with this target size", and the target size is an assumption. A +-20 % uncertainty in head size (0.2-0.3 m) moves the simulated optimum by about +-12 %, the same order as the finder's own error at 8-15 rounds; the bigger problem is the hand model (section C).

---

## B. Curve finder

### B1. Why are falling curves found and rising ones missed?

Pooled analysis of 40 players x 15 rounds (24-26 k movements per row), centred measured wanted log-gain per speed bin:

| true preference | measured at ~1.8 in/s | measured at ~2.8 in/s | measured at 8 in/s | median BIC gain (threshold 10) | offered |
|---|---|---|---|---|---|
| **none (0 %)** | **+0.066** | **-0.034** | -0.001 | -1.8 | 0 / 40 |
| +15 % | +0.032 (truth +0.039) | -0.055 (truth +0.024) | +0.026 (truth -0.028) | -1.6 | 0 / 40 |
| -15 % | +0.117 (truth -0.051) | +0.006 (truth -0.032) | -0.026 (truth +0.026) | 22.3 | 39 / 40 |
| +25 % | +0.010 | -0.066 | +0.039 | 8.9 | 9 / 40 |
| -25 % | +0.161 | +0.044 | -0.049 | 67.5 | 40 / 40 |

(The truth column is the centred wanted log-gain; its sign convention is that a player who wants more gain at speed shows a *negative* wanted gain at low speed relative to the average.)

**The player with no preference already shows a speed-dependent pattern below about 3 in/s** (+0.07 at 1.8 in/s), of the same size as the whole signal of a 15 % preference (+-0.04). That pattern has the shape of a falling-gain preference (high at low speed, decreasing), so it is **added to a falling preference and subtracted from a rising one**. A true +15 % comes out as a U shape that the fit cannot use; a true -15 % comes out inflated about 2x at the low end. This is a bias in the method (or in the simulated player's baseline reach error, which the method cannot tell apart from a preference), not chance and not a bug in sign handling: the sign conventions are right (the large cases, +-35-50 %, are found in the right direction in all runs).

I did not isolate the origin inside the simulator (candidates: the fixed hand noise acting on short reaches, the 3 % undershoot bias, the first-reach filter). In real people a systematic dependence of reach error on reach size is a well-known motor-control effect (short reaches overshoot, long ones undershoot); this is general knowledge that the research pass did not source, so treat it as a hypothesis. If it holds, **a real player will show a "falling gain" at low speed regardless of any preference**, and the app would offer curves for the wrong reason.

### B2. Why is falling -15 % offered in 25/25 runs but almost never confirmed?

Not because the blind test is underpowered. Direct measurement over 60 pairs per player, 20 players, median score difference of curve minus plain:

| true preference | oracle curve (shape of the truth) | the fitted curve, as the app uses it | fitted curve with factor forced to 1 | fitted factor (median) |
|---|---|---|---|---|
| -15 % | **+4.7** | **-18.7** | -27.8 | 1.89 |
| -25 % | +9.4 | +5.0 | +4.1 | 1.30 |
| +15 % | +4.1 | +1.7 | +1.8 | 0.98 |
| +25 % | +12.7 | +2.8 | +2.9 | 0.96 |
| +50 % | +9.5 | +4.5 | +4.7 | 0.94 |

(The standard error of one such measurement is 0.8-1.2 points, so the blind test would see any of these differences; the minimum effect of 2 points is not what blocks it.)

For -15 % the fit chose a `natural` curve with limit 0.3-0.55, decay 0.5-1.0 and **sensitivity factor 1.8-3.3**: total gain 1.3-1.5 at 1 in/s, 1.1 at 2 in/s, 0.98 above 4 in/s. In other words it fitted the low-speed artefact of B1 with a steep bump and a big overall factor, so the "curve" round runs at a sensitivity two to three times different from the plain round. The blind test measures that and says "worse" (25-29 of 30). **The test is right; the curve it was given is wrong.** At -25 % the fit is sensible (limit 0.69-0.75, factor 1.23-1.37, gain 1.2 falling to 0.92) but still delivers half of what an oracle curve would (+5.0 vs +9.4). For rising preferences the fit has the right shape (power curve, gain rising to 1.2-1.3) and a BIC gain of 5-16, mostly under the threshold of 10, so it is not offered; offered, it is better than nothing (+1.7 to +4.5) but far below the oracle.

Two further observations:
* The A/B design confounds the curve with a sensitivity change whenever the fitted factor is not near 1. A factor outside about 0.9-1.1 should never be applied, or it should be tested as a separate step.
* In the simulation a 15 % gain mismatch costs about 4 points, **more than a 15 % sensitivity error costs (0.4-1.2)**. That is a property of the simulated player, which expects a fixed gain and never adapts; real hands adapt to a constant gain (Krakauer et al. 2000: gain changes are learned and generalise), so both the harm of a mismatch and the benefit of a matching curve are probably much smaller in people.

### B3. What effect size is detected and confirmed?

30 players per cell, the full app pipeline (calibration, fit, blind test). "Confirmed" = blind test says better; "worse" = blind test says worse (the offer was harmful).

| true gain change | 8 rounds: offered / confirmed | 15 rounds | 30 rounds |
|---|---|---|---|
| -50 % | 30 / 30 | 30 / 30 | 30 / 30 |
| -35 % | 30 / 27 | 30 / 24 | 30 / 24 |
| -25 % | 30 / 23 | 30 / 24 | 30 / 18 (11 undecided) |
| **-15 %** | 20 / **0** (worse 14) | 30 / **1** (worse 25) | 30 / **0** (worse 29) |
| 0 % | 0 / 0 | 0 / 0 | 1 / 0 (worse 1) |
| +15 % | 0 | 0 | 0 |
| +25 % | 0 | 8 / 5 | 13 / 11 |
| +35 % | 18 / 18 | 30 / 28 | 30 / 28 |
| +50 % | 29 / 28 | 30 / 29 | 30 / 27 |

**Reliably detected and confirmed (>= 80 %): falling gain of 35 % or more at any calibration length; rising gain of 35 % or more from 15 rounds; 50 % at 8 rounds.** A change of 25 % is found (offered) in 0-43 % of cases for rising and 100 % for falling, but confirmed in 0-37 % (rising) and 60-77 % (falling); 15 % or less is not found (rising) or found with a harmful curve (falling). More calibration rounds help little (30 rounds finds +25 % in 37 % of runs, 15 rounds in 17 %) because the limit is the low-speed artefact and the BIC threshold, not noise alone. The default of 8 calibration rounds is below every budget in this table that behaves well for rising effects. The previous (M9) tables in `ALGORITHMS.md` used 15 rounds and one pair of effect sizes; this table replaces the impression they gave.

**How large are real human preferences?** Unknown. No study of acceleration versus raw input for first-person aiming was found; the only magnitude is from desktop cursor pointing (OS acceleration beat constant gain by up to 24 % in Casiez & Roussel 2011, abstract only), and the NVIDIA authors state cursor results do not transfer to first-person sensitivity. The claim that nearly all pros disable acceleration is unsourced. The effect sizes in this table (15-50 %) are therefore arbitrary. A real preference of 25-35 % would be detected only about half the time; one of 10-15 % never.

**Conclusion.** Useful only for players who want a strong curve, and for them only in the half of cases where the fit's shape is right. The feature should be **labelled experimental**, and these defects should be fixed before it is advertised as a result: (1) exclude or down-weight the lowest speed bins or model the baseline reach-size pattern from the plain data, (2) cap the sensitivity factor, (3) require a stronger fit than BIC 10 when only the lowest bins carry the signal, (4) never show "a curve fits how you aim" when the blind test would be run with a changed sensitivity.

### B4. The "Try a mild curve anyway" button

Trial curve = natural, 15 % gain change, direction taken from the sign of the measured wanted gain, then the same blind test. 30 players per row, calibration found no curve:

| true preference | calibration rounds | no curve found | trial direction right | better | worse | no difference |
|---|---|---|---|---|---|---|
| 0 % | 8 | 30 | 1 (29 chose falling) | 0 | **27** | 3 |
| 0 % | 15 | 30 | 0 (30 chose falling) | 0 | **27** | 3 |
| +15 % | 8 | 30 | 13 | 10 | 17 | 3 |
| +15 % | 15 | 30 | 15 | 11 | 15 | 4 |
| +25 % | 8 | 25 | 23 | 19 | 2 | 4 |
| +25 % | 15 | 24 | 21 | 17 | 3 | 4 |

(For true falling or large effects the calibration already offers a curve, so no trial occurs.)

* It does **not** mostly produce "no difference" (3-4 of 30). It mostly produces "worse" for a player without preference (27 of 30), because a 15 % curve really hurts a simulated player who never adapts. That is a clear answer but not a useful one.
* **Its direction is wrong or random.** For a flat player 29-30 of 30 trials chose the falling direction (the B1 artefact); for a true +15 % preference the rising direction was chosen in 43-50 % of runs, i.e. a coin flip. It only works when the preference is large enough that the calibration would have found the curve anyway (the +25 % row).
* It measures an **unadapted** hand. A blind test right after switching measures the switching cost, so it is biased towards "worse" for any curve, including ones a person would grow to prefer in days. Krakauer et al. support that gain changes are learned; no source gives the time scale.
* Verdict: the button gives honest negative answers but cannot discover a good curve. Rename it ("Check if a mild curve hurts you now") or hide it until direction selection and an adaptation phase exist. If kept, offer both directions in randomised order instead of picking one.

---

## C. Realism of the test environment

| aspect | trainer (profile `valorant.json`, `verified: false`) | researched value | gap |
|---|---|---|---|
| head size | 0.2 m, 6-30 m, so 0.4-1.9 deg (median 0.87) | **No hitbox figure found in any primary source.** Fan estimate head about 13 % of about 2 m = 0.26 m; community says hitboxes are shared by all agents | unknown; +-20 % plausible |
| angular head size from the research file | | 0.25 m head: 2.86 / 1.43 / 0.72 / 0.48 deg at 5 / 10 / 20 / 30 m | trainer omits duels under 6 m (over 2.4 deg) |
| distance mix | 6-30 m uniform-ish | **not found** (no quantified duel distances) | unknown |
| strafe speed | 3-6 m/s, 25 % stops | one unsourced aggregator figure: rifle about 5.4 m/s, knife 6.75; counter-strafe timing not found | unknown, plausible |
| FOV, yaw | 103 deg at 16:9 (letterboxed), 0.07 | repeated by many community sources, no Riot document | probably right, unverified |
| flick angle mix | 59 % under 10 deg, 79 % under 20 deg | no data | assumption |

**Effect on the recommendation:** head size +-20 % moves the simulated optimum by about +-12 % (x0.64 per doubling), inside the finder's own error. That is acceptable. **The unverified part that matters more is the hand model.** Under the profile's head-sized targets the simulated player's optimum is above 143 cm/360 and still rising, whereas the controlled study found a band of 20-80 cm/360 for targets of 0.57-9.15 deg, and Valorant pros cluster around 33-65 cm/360 (community data, mean eDPI about 247). A real human with 0.87 deg targets will not want 143 cm/360, so the simulated hand (fixed noise of 0.07 cm, 30 cm/s limit) is wrong for small targets by a large factor. Consequences: (1) every harness number above for "head-sized targets" is a statement about the simulator, (2) for a real person the score curve at these targets is probably flat across the band (as in NVIDIA) so the finder may have little to find, and (3) the app currently has no check that says "your curve is flat, any sensitivity in this band is equivalent", which is the most likely true result.

**Transfer to real play.** The only peer-reviewed trainer work found is a test-retest reliability study of KovaaK's tasks (Rogers et al. 2024, 10 players, ICC 0.95-0.995: the trainer is *repeatable*, not *predictive*) and a six-player Aim Lab-to-Valorant report of weak quality. Nothing shows that a trainer-optimal sensitivity is optimal in a game with movement, peeking, crosshair placement, pressure or fatigue. The NVIDIA authors list their own limits (stationary targets, no recoil or 180 degree turns). Confidence that evidence is thin: high. What transfers plausibly is the speed-accuracy trade-off on isolated aim; what is untested is everything else. The README should say "a sensitivity you perform equally well at in a clean aim test", not "your best sensitivity".

---

## D. Validity of the whole approach

**D1. Circularity.** The simulated player is a Fitts-style hand with signal-dependent noise plus fixed hand noise and a speed limit; the optimizer, the score and the segmentation were built and tuned on it. Passing the harness shows: the GP finds the maximum of a noisy curve, its uncertainty is internally consistent (A3), the segmentation and metric chain are numerically right, the false-positive rate of the A/B test is low (2.0 % / 3.0 % in 500 null runs). It does **not** show: that people have an interior optimum under the trainer's targets, that the score ranks sensitivities as in-game performance does, that the trainer's noise resembles a person's (it does not at small targets, see C), or that acceleration preferences exist in the form the simulator assumes. The B1 artefact is a concrete example of the circularity risk: the analysis and the simulator share a model of "reach error as a function of gain", so a weakness of the model is invisible to the harness until it shows up as the baseline pattern.

**D2. Learning and adaptation.** The simulator has fatigue but no learning. The literature found says: within a session practice effects are large (the NVIDIA study discarded the first half of each session), over eight sessions at the preferred setting the net change was under 15 % in either direction, and gain adaptation is learned and generalises (Krakauer 2000), but no source gives hours or days for mouse sensitivity. So a short session measures a transient; warm-up effects are as large as differences inside the flat band. AimTune's mitigations (interleaved design points, per-session trend, drift cap at 60 % confidence) are reasonable and tested on simulated drift (confidence under drift fell from 93 % to 46-63 %), but the *recommendation* is still pulled by strong drift, and no experiment measures adaptation to the recommended value.

**D3. Against existing approaches** (evidence quality in the research file; none has published validation):

| approach | does | AimTune compared |
|---|---|---|
| keep your sensitivity | nothing; given the flat optimum probably not much worse than a "better" value | the baseline AimTune must beat; the simulations suggest the gain is about 0-2 points for a person within 30 % of the optimum |
| converters (mouse-sensitivity.com, KovaaK's matcher) | convert between games, preserve cm/360 | different job; AimTune is not a converter |
| PSA | subjective A/B by feel from the current value | AimTune is blind, randomised, quantifies uncertainty (better); PSA is faster and needs no trainer; its known bias (too low) has no counterpart here |
| 3daimtrainer finder (closed, 2-5 min) | undisclosed formula | AimTune is open and documented; neither is validated |
| Bayesian / GP finders | none found | novel; also nothing to benchmark against |

AimTune is better in method (blind, repeated, interval and confidence, drift awareness, honest "cannot tell"), worse in friction (many minutes of trainer play) and unproven in outcome. It is the same as the alternatives in the one dimension that matters, external validation.

**D4. What only real human data can show** (cannot be answered by any further simulation):
1. Does a person have a detectable interior optimum under head-sized targets, or is the curve flat across 20-80 cm/360? (Run the finder on 10+ people twice, a week apart: test-retest agreement of the recommendation.)
2. Does the recommendation agree with what the person independently prefers or plays well at? (Blind comparison against their current value in a separate task.)
3. Does the optimum move with target size in people as it does in the simulator (x0.64 per doubling)?
4. How long does adaptation to a new sensitivity take, and does the optimum shift with it?
5. Do reach errors in people depend on speed or reach size when the gain is constant (the B1 artefact)? This decides whether the curve analysis can ever work. A recording of 10 players without any curve answers it directly with the existing analysis code.
6. Does any person's reach error show the signature of a gain preference once a known curve is applied (a positive control with a curve applied by the experimenter)?
7. The head size and the distances in Valorant itself (R1b).

---

## Recommendations, in order

1. **Do not present a point sensitivity as accurate.** Show the range of equivalent values from the model (where the curve is within the negligible difference), say that +-15 % is not distinguishable, and say "flat" when it is. (cheap; reuse `curve` and `negligibleDifference`.)
2. **Fix the hand-model gap before trusting any further harness work:** calibrate the simulated hand to the 20-80 cm/360 band at 0.5-2 deg targets (or document that the harness is a statistical check only). (medium)
3. **Curve feature:** label experimental; add a positive-and-negative control on the baseline artefact (analysis of a plain round set should show a flat pattern, so drop or model bins below about 3 in/s); cap the sensitivity factor at 0.9-1.1; never apply a factor in the A/B test; rename or hide the trial button; if kept, test both directions in random order. (medium)
4. **Raise the default calibration to 15 rounds** if the curve feature stays; the table in B3 shows 8 is worse for rising curves. (trivial)
5. **Real data:** collect 10 other players' plain-round recordings (needs only the existing Raw Input recording) and run the artefact check (D4 item 5) and test-retest (D4 item 1). This decides whether the curve analysis has a future and whether the finder is meaningful. (user task)
6. **Measure a head in Valorant's shooting range** (R1b) and replace the three assumed numbers; then say "verified" in the profile. (user task)
7. **Positioning text in README and results:** "equally good range in a clean aim test", transfer to games unproven. (trivial)

## What I could not answer, and how sure I am

* Everything marked "simulation" depends on a hand model that was shown to be wrong at small targets (C); relative statements (calibration, flatness shape, artefact) are more trustworthy than absolute ones (optimum values, error in cm).
* The origin of the B1 artefact inside the simulator is not isolated.
* The NVIDIA study and several papers were read only through abstracts or summaries (listed in the research file); the 24 % pointing figure and the 20-80 cm band are from there.
* Valorant hitbox size, movement speeds and duel distances: not found in any primary source.
* Real human acceleration preferences: no data; the effect sizes in B3 are illustrative.
* Percentages have +-15 points of sampling error; no claim here depends on a difference smaller than that.
