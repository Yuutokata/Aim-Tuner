# VALORANT Range bot tests - research (2026-10-22)

Finding: almost nothing quantitative is documented publicly. Qualitative structure is documented; all timings, speeds, distances and hitbox sizes are NOT.

| # | Item | Value | Source (date) | Conf. |
|---|------|-------|---------------|-------|
| 1 | F3 menu in Shooting Range | Speed: Easy/Medium/Hard; Streak: Eliminate 50 / Eliminate 100; Bots: Strafe / Reset; Bot Armor: On/Off; Infinite Ammo | https://valorant.fandom.com/wiki/Range (current, fetched 2026-10-22) | high (names), none (effects) |
| 1 | Speed meaning | "training bots spawn and despawn within a period of time depending on difficulty"; speed = how fast bots vanish (Easy for pure aim, Hard for reflex) | fandom Range; https://www.gamerevolution.com/guides/644326-valorant-practice-range-how-use-training (~2020) | medium |
| 1 | Bot count in test | Not documented. Streak target 50/100 kills. Bots appear "every few seconds" (vague) | gamerevolution / search summary | low |
| 1 | Spike plant/defuse drills | Exist (Ascent-B-like site) in Basic Training/Range; separate from aim tests | fandom Range | medium |
| 1 | Free-range bots | Patch 13.05: option for 10, 5, or 1 bots in Range practice mode, default 5, PC only | https://liquipedia.net/valorant/Patch_13.05 (2026-09-01) | high |
| 2 | Bot Armor toggle | On/Off exists. Armor = 50 on 100 HP (150 total) for players; bot values NOT documented. Vandal head 160 = one-shot; Vandal body vs armored 4 shots per damage calculators (generic player rules, not bot-verified) | fandom Range; https://www.valorant-spind.de/en/valorant-schadensrechner.html | bot HP: not documented; player rules: high |
| 3 | Strafe speed/distance/pattern/timing | NOT documented | - | - |
| 3 | Run speed 6.75 m/s etc. | Not verified by any source I fetched (came from the task prompt) | - | unverified |
| 4 | Spawn interval / lifetime per difficulty | NOT documented. Target-practice board distances: 5, 10, 20, 30, 50 m (selectable) | fandom Range | medium (board only) |
| 4 | Bot distances from player | NOT documented | - | - |
| 5 | Head hitbox size | NOT documented. Agents share identical hitbox size (Riot devs per MeinMMO); models like Jett smaller than hitbox, so hits register off-model | https://mein-mmo.de/en/valorant-popular-agent-jett-dies-faster-than-the-others-why,500318 ; vlr.gg thread https://www.vlr.gg/401413/valorant-hitboxes | medium |
| 5 | Head/body ratio | Fan estimate head ~13% of Jett standing height (reddit r/VALORANT pu9pbv, 2021; page blocked, snippet only) | low |
| 5 | Agent height / eye / crouch | NOT documented. Community Icebox-container pixel measurement: ~124.6 cm (implausible, 2023 post summarised by https://theglobalgaming.com/valorant/how-tall-are-agents). Lore heights 160-200 cm irrelevant in-game | very low |
| 5 | Unit->metre | NOT documented in sources found | - | - |
| 6 | Patches | v0.49 spawn-practice-bots moved from F3 to in-world switch; v1.02 kills refresh Jett blade/Reyna orbs; v4.08 bots respawned unarmed fix; v7.08 bot hitbox position reset fix on respawn; v8.01 Energy/Soul orbs from bots; v8.04 bot portraits/minimap icons; v9.02 new Range + tutorial on PC; v9.03-9.05 localisation/lighting; v11.10 bots show flash VFX; 13.05 bot count 10/5/1 default 5 | fandom Range update history; Liquipedia Patch 13.05 | high for listed items; none change speed/timing |

Community: speedrun.com Range categories (50/100 kills, Strafe/No Strafe), no per-weapon/armor categories; Reddit comment: Hard encourages flicking (secondhand).

## Not documented + how to measure
Record at 60+ fps (120 better), 0.335 @800 DPI, yaw 0.07 (cm/360 ~ 360/(0.335*0.07*800)*2.54 = 48.7 cm), FOV 103 horizontal. Frame time = 1/fps.
- Bot lifetime/spawn interval per difficulty: step through video frames, count frames from bot appearance to disappearance (lifetime) and from disappearance/kill to next spawn; 20+ samples per difficulty, don't shoot (lifetime) vs shoot (interval).
- Strafe speed/distance/pattern: don't shoot; film a bot, measure its angular travel in px across frames. Convert px to degrees via FOV (horizontal 103 deg over screen width, use atan mapping), then deg/s. Distance: use ranged bots with known target-board distances (5/10/20/30/50 m) as reference, or turn-angle method: rotate crosshair via known mouse counts (counts*0.07*0.335 deg... i.e. deg = counts*yaw*sens) to measure angular span; with distance d, span = 2 d tan(theta/2). Stop pattern: log frames where position delta = 0; direction-change intervals from the same log.
- Bot distance: stand next to the 5/10/20/30/50 m board, compare bot apparent head width in px vs board; or measure vertical angle subtended by head/body with the sensitivity-calibrated mouse sweep.
- Head hitbox: shoot with Infinite Ammo at a stationary bot (Reset mode) with small mouse steps (counts known) to find the edge where headshots stop; width = 2 d tan(delta/2). Repeat at 3 distances to separate size from distance error.
- Armor: count Vandal/Phantom/Sheriff shots to kill with armor on/off, body vs head, at close range (damage falloff on Phantom/Sheriff; use Vandal first).
- Height/eye height: compare against Range board distance and ground-level crosshair elevation angle; crouch via same method toggled. Units->metres: measure a known 5 m board distance in walk steps vs run speed over timed frames.
- Player movement speed facts (6.75 m/s run etc.): measure by timing run over the 50 m board distance.
