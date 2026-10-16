# Valorant crosshair profile codes

AimTune draws the player's own Valorant crosshair in the trainer. This document records what the code format looks like, where that is known from, what is still an assumption and how the trainer draws it. Riot publishes no specification of the code, so everything here was reverse engineered by the community; the app keeps every key it does not understand, so a code survives a round trip through AimTune.

## Sources

- nk521, [ValorantRandomCrosshairGenerator](https://github.com/nk521/ValorantRandomCrosshairGenerator) (`valorant_crosshair.py`): a generator that writes complete codes. It is the most complete list of keys found, with the value ranges the author used when drawing random crosshairs (these are narrower than or equal to the game's ranges, which the generator never states).
- carloshenryck, [valorant-crosshair](https://github.com/carloshenryck/valorant-crosshair) (`src/constants/crosshairSubtitle.ts`, `crosshairInitialSettings.ts`): a code reader/preview with the names of the primary keys and the defaults of a fresh profile.
- sciencejiho, [valorant-crosshair](https://github.com/sciencejiho/valorant-crosshair) ("Crosshair Lab"): a lossless parser; it fixes the structure (section markers `P`, `A`, `S`, unknown keys and order are preserved) and lists the keys that scale with the resolution (`t z 0t 0l 0v 0o 1t 1l 1v 1o`, which confirms that sizes are in screen pixels).
- Example codes of professional players on [thespike.gg](https://www.thespike.gg/valorant/crosshairs/codes), [The Loadout](https://theloadout.com/valorant/crosshair-settings) and [Sportskeeda](https://www.sportskeeda.com/valorant/best-dot-crosshair-codes-valorant), used as the test set (`CrosshairCodecTest`).

Nothing was checked against the game itself yet: **please export two or three of your own crosshairs from Valorant** (Settings, Crosshair, "Copy profile code") and compare them with what the editor shows (see "Open points").

## Structure

A code is a list of values separated by `;`. It starts with a version (`0`), then key/value pairs, split into sections by a section marker:

```
0 ; s;1 ; P ; c;5 ; h;0 ; ... ; A ; ... ; S ; ...
  header     primary         ADS           sniper
```

- `0`: format version; AimTune writes `0` and keeps any other value it finds.
- Header keys before the first marker: `s;1` is the "advanced options" switch of the editor in the generator. Some codes also carry `c;1` here (a "use the primary crosshair for ..." flag, seen in a public example).
- `P` primary crosshair, `A` aim down sights (when the weapon scopes; if the section is missing Valorant uses the primary crosshair), `S` sniper scope.
- A key that is missing means the game's default. The game omits keys that have their default value, so most real codes are short.

## Keys of the primary (`P`) and ADS (`A`) sections

| Key | Meaning | Values | Default if missing | Source |
|---|---|---|---|---|
| `c` | color preset | 0-7: white, green, yellow-green, green-yellow, yellow, cyan, pink, red; `8` = custom color | 0 (white) | generator, reader |
| `u` | custom color | `RRGGBBAA` hex, used with `c;8` and `b;1` | | generator |
| `b` | custom color switched on | 0 / 1 | 0 | generator |
| `h` | outlines | 0 / 1 | 1 | reader (initial settings) |
| `t` | outline thickness | 1-6 (px) | 1 | generator |
| `o` | outline opacity | 0-1 | 0.5 | generator, reader |
| `d` | center dot | 0 / 1 | 0 | generator |
| `z` | center dot thickness | 1-6 (px) | 2 | generator |
| `a` | center dot opacity | 0-1 | 1 | generator |
| `f` | fade crosshair with firing error | 0 / 1 | | generator |
| `m` | override firing error (primary level) | 0 / 1 | | generator (meaning less certain) |

Inner lines use the prefix `0`, outer lines the prefix `1`; the keys are the same:

| Key | Meaning | Values | Default (inner / outer) |
|---|---|---|---|
| `Nb` | show the lines | 0 / 1 (`0b;0` hides them, nothing else is written then) | 1 / 1 |
| `Nt` | line thickness | 1-10 (px) | 2 / 2 |
| `Na` | line opacity | 0-1 | 0.8 / 0.35 |
| `No` | offset from the center to the start of the line | 0-20 (px; the game may allow more for outer lines) | 3 / 10 |
| `Nl` | line length (horizontal lines, and vertical ones unless `Ng`) | 0-20 (px) | 6 / 2 |
| `Nv` | line length of the vertical lines | 0-20 (px) | = `Nl` |
| `Ng` | vertical length set separately | 0 / 1 | 0 |
| `Nm` | movement error on | 0 / 1 | 0 |
| `Ns` | movement error multiplier | 0-3 | 1 |
| `Ne` | firing error multiplier (written when firing error is on) | 0-3 | 1 |
| `Nf` | firing error off (`Nf;0`) | 0 | |

The defaults of the table are those of a fresh profile in the reader's initial settings (inner 6 px long, 2 px thick, offset 3, opacity 0.8; outer 2 px, offset 10, opacity 0.35; outlines on at 0.5; dot off, 2 px).

## Sniper scope (`S`)

`c` (preset or `c;8` with `u`/`t` hex and `b;1`), `s` (center dot size), `o` (opacity), `d;0` (no dot). The meaning of `s` is unclear: generated codes use whole numbers, real codes carry values like `0.7`. AimTune does not draw the sniper scope; the section is kept as it is.

## Decisions for AimTune

- The trainer draws the **primary** crosshair. The ADS and sniper sections are parsed, preserved and exported unchanged; the editor edits the primary section only.
- Unknown keys, their order and the sections are preserved. A code that does not start with a version number and a `;` is rejected with an error message; a valid code with missing keys gets the defaults above; values outside the ranges are clamped when drawn and kept as they are in the code.
- **Movement and firing error are not drawn.** The trainer has no movement or shooting spread; the keys are stored and exported, and the editor shows them as switches without effect in the trainer.
- **Pixels.** Valorant sizes are screen pixels at the native resolution, independent of the window scale. The trainer draws the crosshair on the back buffer in pixels, never in design units. On an even-sized screen there is no center pixel, only a center between two pixels: an element of even thickness is centered on it, an element of odd thickness is centered on the pixel to the left of / above the center (the position is documented in `CrosshairLayout` and covered by a test). Whether Valorant rounds the same way is one of the open points.
- The outline is drawn outside each element, with the outline thickness and opacity; the dot is a square of the dot thickness.
- Colors: the presets are the colors listed above; the exact RGB values of the presets are assumptions (listed in `CrosshairColors`).

## Open points (to check in the game)

1. Export two or three crosshairs from Valorant and paste them into the editor; compare the look in the trainer with the game at the same resolution (pixel lengths, gaps, thickness, outline).
2. The meaning of `m` (primary level) and of the header `s;1` / `c;1`.
3. The range of the outer line offset and of the line lengths, and the exact RGB values of the color presets.
4. How Valorant places an odd-thickness element on an even-sized screen.
