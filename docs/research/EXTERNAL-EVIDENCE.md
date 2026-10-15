# External evidence for AimTune

Compiled 2026-10-15 for a skeptical assessment. Labels: **[P]** peer-reviewed or preprint by researchers, **[O]** official or project primary documentation, **[C]** community or secondary (unverified). "Not found" means searched and not located, not that it does not exist. Several PDFs (Casiez 2008, the NVIDIA paper full text, PMC) could not be read in full by the fetch tool; claims from them rest on abstracts or a summarizer's reading and are marked.

---

## 1. Valorant geometry

### 1.1 What was found

| Item | Finding | Source / label |
|---|---|---|
| Head hitbox radius or height | **Not found.** No official or datamined number located. | - |
| Player height | Community: all agents are about 2 m (one source says 1.96 m) so that headshot height is consistent. Riot's creative director reportedly said agents are equally tall for gameplay reasons. | [C] https://win.gg/news/all-valorant-agents-have-different-heights-whos-the-tallest-question-mark/ , https://theglobalgaming.com/valorant/how-tall-are-agents |
| Head size relative to body | Fan estimate: head about 1/7.5 of body height (about 13 %), i.e. about 0.26 m if 2 m tall. Model-based, not a hitbox measurement. | [C] https://libredd.it/r/VALORANT/comments/pu9pbv/i_calculated_the_size_of_the_head_compared_to_the/ |
| Shared hitboxes across agents | Riot devs reportedly told a journalist hitboxes should be the same size for all agents. Players report model/hitbox mismatches (e.g. Jett's model smaller than hitbox). Early beta hitboxes were larger than models; Riot later shrank them. | [C] https://mein-mmo.de/en/valorant-popular-agent-jett-dies-faster-than-the-others-why,500318 , https://www.vlr.gg/post/4086164/valorant-hitboxes , https://www.gamepressure.com/S013-amp.asp?ID=7135 |
| Crouch | One report says the hitbox shifts when crouching (no numbers). | [C] win.gg article above |
| Horizontal FOV | Fixed 103 deg at 16:9, no FOV slider. Attributed to Riot dev "Classick" by guides; no primary Riot document found. 4:3 reportedly gives about 71 deg. | [C] https://www.mouse-sensitivity.com/forums/topic/8438-valorant-locked-fov/ , https://www.ggrecon.com/guides/how-to-change-your-fov-in-valorant/ |
| Yaw | 0.07 deg per count at sensitivity 1. No official Riot statement found. | [C] https://www.tabnews.com.br/OVitor/como-calcular-edpi-e-cm-360-a-matematica-da-sensibilidade-entre-jogos , https://www.recharge.com/blog/en-gb/valorant-sensitivity-converter-cs2-apex-ow2-fortnite |
| Movement speeds | Only one unsourced aggregator figure (knife 6.75 m/s, rifle about 5.4 m/s, attributed to the wiki). Walk/crouch speeds and counter-strafe timing: **not found** from a primary source. Relyks (pro) reportedly found accuracy retained below about 30 % of max speed (single-player test). Pros disagree on whether counter-strafing exists. | [C] https://www.ggrecon.com/articles/valorant-shooting-accuracy , https://thespike.gg/valorant/beginner-guides/is-counter-strafing-in-valorant-slower-than-csgo |
| Engagement distances (map sizes, duel ranges, weapon ranges) | **Not found** in a quantified, citable form. A guide only notes head level differs between 5 m and 30 m due to perspective. | [C] https://gameplayking.com/blog/valorant-headshot-percentage-science |

### 1.2 cm/360 computation

`cm/360 = 360 / (dpi * sens * 0.07) * 2.54`. Example: 800 dpi, sens 0.4 gives 360/(800*0.4*0.07) = 16.07 in = 40.8 cm. [C] formula, internally consistent with 0.07 yaw. Whether the engine applies exactly 0.07 with no rounding or per-axis difference was not verified.

### 1.3 Angular head size (own computation, assumptions explicit)

Formula: `angle = 2 * atan(w / (2 d))`, w = head width, d = distance, target facing the player. Head width is **assumed**, not sourced: 0.20 / 0.25 / 0.30 m (0.25 m is roughly 13 % of 2 m per the fan estimate). Real hitboxes may be larger than the visible head and non-spherical.

| d | w = 0.20 m | w = 0.25 m | w = 0.30 m |
|---|---|---|---|
| 5 m | 2.29 deg | 2.86 deg | 3.43 deg |
| 10 m | 1.15 deg | 1.43 deg | 1.72 deg |
| 20 m | 0.57 deg | 0.72 deg | 0.86 deg |
| 30 m | 0.38 deg | 0.48 deg | 0.57 deg |

Hand-scale reading: at 45 cm/360, one degree is 0.125 cm, so a 0.25 m head at 20 m (0.72 deg) is about 0.9 mm of hand travel, or about 10 counts at 0.07 deg per count. The NVIDIA study (section 3) used target widths of 0.57 to 9.15 deg, so Valorant long-range heads are at or below its smallest target.

**Bottom line for AimTune:** Head size, engagement distances and movement numbers needed to tie trainer targets to Valorant are mostly unverified; only 0.07 yaw, 103 deg FOV and about 2 m height are widely repeated, all community-sourced. A claim that trainer target sizes "match Valorant" cannot currently be backed with a primary source. Confidence: **low**.

---

## 2. Mouse acceleration and performance

### 2.1 Pointing literature (desktop cursor, not first-person)

- Casiez & Roussel, UIST 2011, "No more bricolage!" [P]. Introduces EchoMouse and libpointing to characterize and replicate OS transfer functions. Reports the default functions of Windows, OS X and Xorg improved performance **by up to 24 % over constant gain**. Abstract: https://lilloa.univ-lille.fr/handle/20.500.12210/27978 ; DOI 10.1145/2047196.2047276 (https://dblp.dagstuhl.de/pid/95/2264-1.html). Not read in full.
- Casiez, Vogel, Balakrishnan, Cockburn, Human-Computer Interaction 23(3):215-250, 2008 [P]. Compares constant gain with pointer acceleration at several gain levels on desktop and large high-resolution displays, with a model of how acceleration changes motor-space movement. Only the abstract framing was retrieved; numeric results **not read** (PDF https://Www.Gwern.net/doc/design/2008-casiez.pdf was not extractable). DOI 10.1080/07370020802278163. A secondary source says higher gain reduces accuracy for small targets (unverified for this paper).
- Nancel et al.: the retrieved paper (CHI 2011 mid-air pan-and-zoom, https://hal.archives-ouvertes.fr/hal-00559171) is **not** about transfer functions. A course slide deck attributes to Nancel et al. (2013) that with a good acceleration function users acquire targets accurately on a large display with small input space (secondary: https://www.lri.fr/~fanis/courses/ISI2014/slides/input.pdf). The primary Nancel transfer-function paper was **not found/read**.
- Hanada et al., UIST 2021 [P, abstract only]: transfer functions should be defined in physical units and adapted to hardware: https://hal.archives-ouvertes.fr/hal-03322657 . Relevant to AimTune's use of cm/360.

### 2.2 FPS / first-person

- **No controlled study of acceleration versus raw input for first-person aiming was found.** Searches returned vendor blogs and forum posts only.
- The NVIDIA CHI 2022 / IEEE ToG 2023 study used linear mapping only; acceleration was not a condition. It discusses why competitive players avoid acceleration (flicks rely on repeatable open-loop motion) and states first-person sensitivity (deg per mm) is not convertible to pointer CD gain [P]: https://ar5iv.labs.arxiv.org/html/2203.12050 , https://research.nvidia.com/publication/2023-07_mouse-sensitivity-first-person-targeting-tasks
- Community: "about 99 % (or over 95 %) of CS2/Valorant pros disable acceleration" appears repeatedly **without a source** [C, unverified]. The prosettings.net Valorant table has no acceleration field.
- A month-long Raw Accel self-test reported by a blog claims acceleration may help erratic targets at the cost of consistency; no method or numbers [C, unverified]: https://www.geeky-gadgets.com/using-mouse-acceleration-in-fps-games-for-more-accurate-aim/
- Raw Accel docs describe the math (sensitivity = f(v)/v, gain = f'(v)); modes Linear, Classic, Power, Natural, Jump, Synchronous, LUT; recommend gain caps and offsets. The Synchronous style is the authors' "most correct" choice based on the claim that we perceive speed and sensitivity changes proportionally; the guide cites no experiments for this [O]: https://github.com/RawAccelOfficial/rawaccel/blob/master/doc/Guide.md . Latest release found: v1.7.1 (2025-07-27) via `gh api`. Vanguard compatibility is asserted only by guides [C]: https://www.thespike.gg/valorant/tips/is-raw-accel-bannable-in-valorant ; Riot's own statement **not found**.
- Aggregate data on Raw Accel curve values used by competitive players: **not found** (only generic guides and single pro setting pages).

### 2.3 Size of preferred gain variation with speed
No FPS data found. In pointing, the up-to-24 % benefit above is the best-supported magnitude (cursor tasks).

### 2.4 Plain verdict
- Desktop pointing: well-designed acceleration helps, up to roughly 24 % versus constant gain (Casiez & Roussel).
- First-person aiming: **no good evidence either way**. The community consensus (consistency, muscle memory) leans against acceleration but is anecdotal. Do not extrapolate the pointing benefit to flick-based FPS play; the NVIDIA authors say the metrics are not interchangeable.

**Bottom line for AimTune:** Optimizing an acceleration curve in a trainer rests on pointing research that may not transfer, plus community belief that pros avoid it. Treat the accel feature as exploratory and claim no measured benefit. Confidence: **medium** that FPS evidence is absent; **low** on any magnitude.

---

## 3. Mouse sensitivity and aim performance

### 3.1 Key study
Boudaoud, Spjut, Kim (NVIDIA), "Mouse Sensitivity Effects in First-Person Targeting Tasks", CHI 2022 / arXiv:2203.12050 [P]; extended journal version IEEE Transactions on Games 2023 (https://research.nvidia.com/publication/2023-07_mouse-sensitivity-first-person-targeting-tasks). Details from the ar5iv HTML (https://ar5iv.labs.arxiv.org/html/2203.12050), read via a summarizer, not verbatim:
- 13 analyzed participants (15 recruited), 12 male, 1 female, ages 11-33. Stationary targets, click task, 8 sessions of 500 trials (first half of each session discarded as warm-up).
- Tested 160, 80, 40, 20, 10, 5 cm/360 (0.225-7.2 deg/mm) plus each person's preferred setting (often 30 cm/360).
- **Optimal band 0.45-1.8 deg/mm, about 20-80 cm/360 (4x range)**; adjacent settings inside the band were not statistically separable. Completion time and throughput differed significantly across all six (F=111.7, F=275.6). Hit rate differed only at the highest sensitivity (5 cm/360).
- Low-difficulty targets favored higher sensitivity, high-difficulty (small, far) targets lower sensitivity (not quantified in the summary).
- Percentage performance differences between settings were **not reported in what I could read**; figures were not accessible.
- Preferred-setting performance between first and last sessions changed by under 15 % of completion time, in either direction.
- Stated limitations: tiny sample, stationary targets, no recoil/scope/180-degree turns, one mouse and display, about 100 mm travel without lifting.
- The abstract notes community recommendations vary 10x or more.

### 3.2 Flatness near the optimum
Within 20-80 cm/360, adjacent steps (a factor of 2 apart) were not separable, so +-15-20 % is **well inside the resolution of that experiment**. This supports a flat optimum, but it is one small study of group means; it cannot show whether individual optima differ within the band. Aim Lab's blog says most tactical-shooter players sit at 30-60 cm/360 [vendor/C]: https://aimlabs.com/articles/?p=4179 (snippet-level).

### 3.3 Pro sensitivity (secondary data)
- prosettings.net Valorant list (about 100 rows as tabulated by a summarizer, possibly incomplete): mean eDPI about 247, median about 224, range 128-1208 [C]: https://prosettings.net/lists/valorant/ . With 0.07 yaw: eDPI 224 gives 58.3 cm/360; 247 gives 52.9; 280 gives 46.7; 200 gives 65.3; 400 gives 32.7.
- Guides quote avg about 44 cm/360 and eDPI 200-400 [C]: https://noping.com/blog/best-mouse-sensitivity-and-dpi-for-valorant . These do not agree exactly (mean 44 vs 53-58 cm); say the bulk spans about 33-65 cm with outliers beyond.
- No Valorant study linking pro sensitivity to performance. NVIDIA's abstract cites pro sensitivity spread of up to 8x within one title [P, summary].
- Aim Lab/KovaaK's published large-dataset sensitivity-versus-accuracy analyses: **not found**.

**Bottom line for AimTune:** One small controlled study supports a broad flat optimum (about 20-80 cm/360); Valorant pros cluster around 33-65 cm. Nothing shows individual optima can be identified to +-15 %, so a sharp optimum from a short session is likely noise-dominated. Confidence: **medium** for the broad band, **low** for within-band precision.

---

## 4. Learning and adaptation

- Krakauer, Pine, Ghilardi, Ghez, J Neurosci 20(23):8916-8924, 2000 [P]: https://pmc.ncbi.nlm.nih.gov/articles/PMC6773094 . Per the abstract (read via Europe PMC), learning a **gain** change (extent errors) yields a scaling factor that generalizes to new distances and directions after training on one target, whereas learning a **rotation** needs multiple directions and does not transfer across directions. So gain adaptation is comparatively fast and general; hour/day time courses were **not extracted**.
- Visuomotor-rotation work shows partial retention at 24 h and faster relearning (rotation, not gain; snippets only): https://pmc.ncbi.nlm.nih.gov/articles/PMC2672910 , https://yourreview.journals.yorku.ca/index.php/yourreview/article/view/40661
- NVIDIA study: strong within-session practice effect (first half of each session discarded); across 8 sessions at the preferred setting, no significant net change, about half slightly better and half slightly worse, under 15 % [P, summary]. No multi-day sensitivity-switching experiment was found. A secondary summary says the authors found no lasting "hard-wired" muscle memory for sensitivity in all players (unverified).
- Speed-accuracy trade-off is the authors' mechanism for the optimum. Fatigue effects: not found in a primary FPS source.

Inference (mine, not sourced): a short session measures a not-yet-adapted state; warm-up and order effects (up to about 15 %) are as large as differences inside the flat band, so discarding early rounds and counterbalancing order are needed, and testing widely different sensitivities in one session confounds adaptation with preference.

**Bottom line for AimTune:** Motor-learning research supports that gain changes are learned and generalize, but no source gives a reliable adaptation duration for mouse sensitivity, so a short blind session measures a transient. Warm-up discarding is supported by the one FPS study that did it. Confidence: **medium** on direction, **low** on time scales.

---

## 5. Existing approaches

| Tool | What it does | Evidence |
|---|---|---|
| PSA (Perfect Sensitivity Approximation) | Iterative A/B: start at current sens, try lower and higher, keep what feels more controllable, repeat. Subjective feel only. | No paper or spec found. Forum criticism: staying fixed on one spot biases toward too-low sensitivity; assumes a fixed 360-degree sweep regardless of pad size [C]: https://www.mouse-sensitivity.com/forums/topic/5237-psa-method-accuracy-vs-feel/ , https://www.hunted.space/product/psa-calculator-2 |
| mouse-sensitivity.com | Converts sensitivity between games preserving cm/360; does not find an optimum. | Forum-level only [C]: https://www.mouse-sensitivity.com/forums/topic/8356-when-converting-kovaaks-to-another-game-there-is-not-a-sensitvity-scale-for-cod/ |
| KovaaK's Sensitivity Matcher | Free GPL script by KovaaK converting sensitivity between 3D games; no optimization. Users dispute DPI handling [C]: https://alternativeto.net/software/kovaak-s-sensitivity-matcher/about |
| KovaaK's / Aim Lab built-in finder | **Not found**: no documented built-in optimum-finding feature. Aim Lab gives conversion and rule-of-thumb ranges (30-60 cm/360) [vendor]. Aim Lab support page returned 403 (not read). |
| 3daimtrainer.com Sensitivity Finder | 2-5 min browser aim test, then recommends a sensitivity. Page discloses no scoring formula or validation; only "4M users" [vendor claim]: https://www.3daimtrainer.com/sensitivity-finder/ |
| Bayesian/GP sensitivity optimization | **Not found** in literature or public tools; NVIDIA used fixed-grid user studies. AimTune's GP over log(cm/360) appears novel, which also means no external validation. |

**Bottom line for AimTune:** Existing tools are converters or subjective heuristics with no published validation; none claims a measured optimum backed by evidence. AimTune's approach is novel but unvalidated against any benchmark. Confidence: **medium** (search breadth was limited).

---

## 6. Transfer from aim trainers to real games

- Rogers et al., Front Sports Act Living 6, 2024, DOI 10.3389/fspor.2024.1309991 [P]: KovaaK's reliability pilot, 10 FPS esports players, two identical trials 3-5 days apart, 4 tasks (Micro/Macro Flicking, Strafe Tracking, Wall Peeking); ICC 0.947-0.995. Macro Flicking improved significantly between trials (practice effect). It shows **test-retest reliability, not validity versus in-game performance**. Abstract via Europe PMC; PMC page was blocked: https://pmc.ncbi.nlm.nih.gov/articles/PMC10925653/
- Filipino Valorant study, IEEE 2021 [P, weak]: about 6 players, Aim Lab daily for a week before playing Valorant; higher average damage per round reported; no randomized control evident from snippets: https://ieeexplore.ieee.org/document/9436822/
- Aim Lab focus-of-attention study, PLoS ONE 2023, 37 players: an in-trainer experiment, not a transfer test: https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10368276/
- Coaching blogs say flicking/tracking plausibly transfer but admit no large clean trial exists [C]: https://cognitivetrain.com/aim-training-fps-transfer/ . A "70/30" rule found is unsourced.
- Correlation of trainer score with in-game stats (K/D, headshot %, rank): **not found**.

**Bottom line for AimTune:** Trainer measurements are reliable for the same task, but there is little to no good evidence that they predict in-game aim or that a trainer-optimal sensitivity is in-game optimal. State this limitation plainly in docs and results. Confidence: **high** that evidence is thin; **medium** that it is truly absent given limited search.

---

## Source quality table

| Source | Type | Used for |
|---|---|---|
| Boudaoud, Spjut, Kim arXiv 2203.12050 / IEEE ToG 2023 | peer-reviewed + preprint; read via summarizer | sens range, flatness, adaptation, accel discussion |
| Casiez & Roussel UIST 2011 (Lille record, dblp) | peer-reviewed, abstract only | accel benefit up to 24 % |
| Casiez et al. HCI 2008 | peer-reviewed, abstract only | CG vs PA framing |
| Hanada et al. UIST 2021 (HAL) | peer-reviewed, abstract only | physical-unit transfer functions |
| Nancel et al. CHI 2011 (HAL); course slides | peer-reviewed / secondary | checked; not about transfer functions |
| Krakauer et al. J Neurosci 2000 | peer-reviewed, abstract-level | gain vs rotation learning |
| Visuomotor rotation papers (PMC2672910, York review) | peer-reviewed / student journal, snippets | retention at 24 h |
| Rogers et al. Front Sports Act Living 2024 | peer-reviewed, abstract | trainer reliability |
| Filipino Valorant IEEE 2021; PLoS ONE 2023 | peer-reviewed, snippets | trainer studies |
| Raw Accel Guide.md and `gh api` releases | official project docs | curve math, modes, version |
| prosettings.net Valorant list | community, summarizer tabulation | pro eDPI |
| noping, tabnews, recharge, mouse-sensitivity.com forum | community / commercial | yaw 0.07, cm/360 formula, FOV 103, PSA critique |
| win.gg, theglobalgaming, vlr.gg, mein-mmo, gamepressure, reddit | community / media | height, hitbox sameness |
| ggrecon, thespike.gg, geeky-gadgets, cognitivetrain | community / media | movement, Raw Accel legality, accel claims, transfer |
| hunted.space, 3daimtrainer, alternativeto, Aim Lab blog | vendor / community | existing tools |

Gaps: Riot primary docs (hitbox, speeds, ranges, FOV, yaw) not found; Casiez 2008 and Nancel transfer-function results not read; no FPS acceleration experiment; no Bayesian sensitivity prior art; no trainer-to-game validity study.
