# Recording kit for the Range measurements (M15)

You record, and the scripts measure. Only your own recordings are used.
- Do not touch game files, memory or Vanguard.
- Clips never go into git. They live in `tools/range-re/clips/`, which is git-ignored.

Plan for about 45 minutes in one sitting. Everything below happens in the Shooting Range (Practice → Shooting Range).

## 1. Before you start

### Raw Accel
Switch Raw Accel **off** for the whole recording session (profile with no acceleration and Output DPI = 800, or the "disable" switch). Switch it back on afterwards.
- The scripts turn mouse counts and pixels into angles with 0.335 @ 800 DPI, yaw 0.07, which is 48.7 cm/360 and 0.0187° per count.
- Your 0.75 output factor and the curve would break that.
- How the runs feel does not matter, because we measure the bots, not you.

### Valorant
| Setting | Value |
|---|---|
| Sensitivity | 0.335 |
| Scoped multiplier | unchanged |
| Resolution | 1920x1080 (or your native one, but then note it) |
| Display mode | fullscreen |
| Overlays | none (FPS counter, Discord, notifications off) |
| Crosshair | your normal one |

### OBS
- **Settings → Video:**
  - Base (canvas) **and** output resolution exactly equal to the game resolution, with no downscaling. The first clips looked downscaled from 2560x1440, so check this.
  - **60 fps** at least, **120 fps** if your PC manages it. The first clips were 30 fps, which is too coarse for strafes.
- **Settings → Output:** Recording format mkv or mp4, encoder NVENC/x264, rate control CQP 16-18 (or ≥ 40 Mbit/s).
- Use a **Game Capture** source only, with no filters and no webcam.

### settings.txt
Create `tools/range-re/clips/settings.txt` and fill it in:
```
game resolution: 1920x1080   display mode: fullscreen   monitor native: ...
OBS canvas/output: 1920x1080 / 1920x1080   fps: 60|120
sensitivity 0.335, dpi 800, Raw Accel: off
crosshair code: 0;...
notes per clip: (anything unusual, e.g. "clip 03-medium-2: died to nothing, paused 10 s")
```

## 2. The clips

Use exactly these file names (OBS: Settings → Advanced → filename format, or rename afterwards). Each clip should be under about 3 minutes; split it into `-1`, `-2` and so on if needed.

### Skills test (counter, F3 opens the settings panel)

| # | File | F3 settings | What you do |
|---|---|---|---|
| 0 | `00-panel-<speed>.png` | each setting you use | Screenshot (F12 in OBS, or Valorant's own) of the open F3 panel, so the scripts know the exact option names. |
| 1 | `01-turn360.mp4` | none needed | Stand at the counter. Make **two slow, steady full 360° turns to the right**, about 5 s per turn, then hold still 2 s. Calibrates pixels to angle. |
| 2 | `02-idle-easy.mp4`, `02-idle-medium.mp4`, `02-idle-hard.mp4` | Speed = Easy / Medium / Hard; Eliminate 100; Bots = **Strafe**; Bot Armor **on**; Infinite Ammo on | Start the test and **do not shoot and do not move the mouse**. Keep the crosshair on the middle of the lanes. Record **90 s** or until the run ends. Gives spawn interval, lifetime, positions and strafing per difficulty. |
| 3 | `02-idle-medium-noStrafe.mp4` | as above, Medium, Bots = the non-strafing option | Same as 2, 60 s. Tells strafing apart from spawn behaviour. |
| 4 | `03-run-medium-1.mp4` … `-3` | Medium, Eliminate 100, Strafe, Armor on | Play **3 full runs** normally. At the end, leave the stats screen visible for **3 s**. This is the target mode for AimTune. |
| 5 | `03-run-easy-1.mp4`, `03-run-hard-1.mp4` | Easy / Hard, otherwise as 4 | One full run each, with the stats screen at the end. |

### Free range (bot spawn switch at the distance boards: 5 / 10 / 20 / 30 / 50 m)

| # | File | Setting | What you do |
|---|---|---|---|
| 6 | `04-strafe-10m.mp4`, `04-strafe-20m.mp4` | 1 bot at 10 m (then 20 m), movement = **strafe**, armor on | Stand at the counter centre. Put the crosshair on the bot's start position and **hold still 40 s**, without shooting. Gives strafe speed, distance, stops and reversal timing. |
| 7 | `05-head-10m.mp4`, `-20m`, `-30m` | 1 bot, **not moving**, at 10 / 20 / 30 m | Crosshair just beside the head, hold still **3 s**. Then move **very slowly** horizontally across the head and back, tapping one shot about every 0.5 s (infinite ammo), about 10 s in total. Gives head size and where the headshot hitbox ends. |
| 8 | `06-hp-armor.mp4` | 1 bot, not moving, 10 m, Vandal | Armor **on**: kill it with body taps only (count out loud), then once with a head tap. Armor **off**: same again. Gives bot HP and armor. |
| 9 | `07-eyeheight.mp4` | 1 bot, not moving, 10 m | Standing: crosshair on the bot's feet 3 s, then on its head 3 s. Crouched: the same. Gives eye height and crouch height. |
| 10 | `08-runspeed.mp4` (optional) | knife out | Run in a straight line from the 5 m board to the 30 m board holding W, then the same with the Vandal. Gives run speed. |

If something in the game is called differently (for example the movement option of a single bot), take a screenshot of it and say what you picked in `settings.txt`.

## 3. Where to put it and what happens next

1. Put all files into `C:\Users\leo\IdeaProjects\Sensi-Finder\tools\range-re\clips\`, next to the two old clips. Do not delete those.
2. Tell me "clips are in".
3. I then:
   - re-run the turn calibration;
   - read the REMAINING counter per frame;
   - track the bots per difficulty;
   - measure head, HP and height;
   - write `tools/range-re/out/range-spec.json` with n, spread and `verified` per value.
4. You get medians and counts only; no frames leave your machine.
5. Then M15 continues with the behaviour spec (step 4) and the moving-target metrics.
