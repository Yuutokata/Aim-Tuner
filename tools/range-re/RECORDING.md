# Recording kit for the Range measurements (M15)

One page. You record; scripts measure. Only your own recordings are used. Do not touch game files, memory or Vanguard, and do not share the clips in the repo (they stay in `tools/range-re/clips/`, which is git-ignored).

## Settings (write them into `clips/settings.txt` before recording)

- Resolution 1920x1080 native, fullscreen or borderless. Note aspect ratio and any letterbox or stretch.
- Valorant: horizontal FOV is fixed at 103; sensitivity **0.335**; mouse 800 DPI. Raw input as usual; do not change any of this between clips.
- Crosshair: note its type and size. Take one screenshot with the crosshair on a flat wall so the script can find the centre of the screen.
- Range setting for all clips: **Medium, Bot Armor off, Infinite Ammo on** (the other settings come later).
- Close overlays (FPS counters, Discord, notifications). Keep the in-game scoreboard and counter visible.

## OBS

- Canvas and output 1920x1080, 60 fps or more (120 is better for strafe speed), constant frame rate, lossless-ish quality (CQP 16 or higher bitrate), mp4 or mkv.
- Capture the game (game capture or display capture), no scaling, no filters, no webcam.
- Name clips as below; keep each clip under about 3 minutes.

## Clips

1. **`01-turn360.mp4`**: stand still at the counter, one slow, steady full 360° turn (a few seconds per turn), twice. This calibrates pixels to angle. Do it once at the Skills Test counter.
2. **`02-static-medium.mp4`**: start a Medium run (armor off) and do **not shoot**. Hold the view still on the lanes for the whole run, or until the bots stop coming. Repeat 3 times. This gives bot positions, spawn timing, lifetime and strafe.
3. **`03-run-<n>.mp4`**: 5 full runs played normally, with the end-of-run stats screen on screen for 3 seconds at the end. Say aloud or add a text note if a run was unusual.
4. **`04-head-<m>.mp4`**: a bot head (or any stationary head-sized target) at known distance markers. Stand at each marker you can find, aim at the head, and hold still for 2 seconds. Write the marker distances in `settings.txt`. For R1b you may instead follow the procedure in `docs/REVIEW.md` ("Realism (Valorant)"): move the crosshair across the head edge to edge and note the mouse distance.
5. **`05-eyeheight.mp4`**: a standing view and a crouched view from the same spot, looking at the same flat wall or floor marker, 3 seconds each.

## Also useful (optional)

- A screenshot of the Skills Test settings panel (Challenge, Bot Armor, Infinite Ammo) and of the score/remaining board.
- A tape measure style note: the counter height and the distance from the counter to the back wall, if the range has markers.

When you are done, tell me the folder. I will run the scripts in `tools/range-re/` on it and report medians and counts only.
