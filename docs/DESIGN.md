# Design

## Source of truth

- `docs/design/AimTune.pdf`: one tall page ("Design system and 12 screens (1920×1080). Built on Nocturne, adds a monospace numeric face and Okabe-Ito chart colors"). 14 frames in 2 columns × 7 rows, labelled 1a-1n.
- `docs/design/tokens.json`: the Nocturne tokens used below.

The UI follows both. Tokens are translated into a Scene2D skin in `assets/ui/`. Mockups are web designs; where Scene2D cannot reproduce something 1:1, the deviation is listed under "Deviations". All numbers in the mockups (43.3, 1.24, 41.9 …) are sample data, not defaults.

Re-rendering the PDF: `pdftoppm -r 150 -png docs/design/AimTune.pdf out`, then crop the 2×7 grid (frames are 1920×1080 scaled).

## Design system (frame 1a)

- **One accent** (`#9184d9`), used for outlines, selection and the primary data line. Buttons are outlined, no fills. Selected items (tiles, segmented options, list rows) get a dark accent tint (accent ramp 800) with an accent outline.
- Surfaces: bg `#161826`, surface `#232532` (cards, inputs, list rows), divider `rgba(233,233,237,0.16)`, text `#e9e9ed`; neutral and accent ramps 100-900.
- **Chart colors (Okabe-Ito, colorblind-safe):** A/baseline `#56B4E9`, B/candidate `#E69F00`, good `#009E73`, warning `#D55E00`, fit line `#b5abfc`, band `rgba(145,132,217,0.22)`. Series also differ by line style (baseline dashed `14 8`, candidate solid) and by label, never by hue alone.
- **Type:** Inter for all interface text, JetBrains Mono for all numbers, units and timers. Sizes at 1920×1080: display 64 / heading 56 (screen titles), title 32/500 (section titles), bodyLg 22, body 20/400 (minimum 16 px for interface text), label 14/500 uppercase with +10% tracking, mono numbers 44 / 96 / 150 (numberMd/Lg/Xl).
- **Spacing** 4-8-12-16-24-32-48-72, **radius** 4/8/14 (sm/md/lg), margin 72, header 88, footer 120.
- **Components:** primary button (outlined accent, accent text), secondary button (outlined neutral, light text), text button (accent text, e.g. Back), chips (Recommended = tinted fill, Session 14 = neutral fill, Blind test = outlined), segmented control (Wrist/Mixed/Arm), numeric input (mono value with a small unit suffix such as "DPI"), cards on surface color, list rows with a right-aligned mono value or key hint, slider with value label, progress bar.

## Screens and flow

```
Main menu ─ Start test ─▶ [first launch: Onboarding 1-4] ─▶ Pre-test notice ─▶ Rounds (HUD ⇄ Between rounds) ─▶ Results (Sensitivity | Acceleration)
          ├ Continue session ─▶ Rounds
          ├ Results ─▶ Results
          ├ History
          └ Settings
```

| Frame | Screen | Content |
|---|---|---|
| 1b | Main menu | Title "Find the sens you aim best with."; list: Start test (Enter, "new blind session · about 20 min"), Continue session (C, "session 14 · round 6 / 10 · 70 %"), Results (R), History (H), Settings (S). Right card "Last recommendation": cm/360° in numberXl mono, game sens, confidence, rounds. Profile summary line, footer "Open source · MIT", "Raw Accel export supported", version label. |
| 1c | Onboarding 1/4 DPI | Numeric input with presets 400/800/1600/3200, "Not sure?" info card (mouse vendor apps, "DPI"/"CPI"/"Sensitivity stages"). |
| 1d | Onboarding 2/4 game + sens | Game tiles (CS2, Valorant, Apex Legends, Overwatch 2, Fortnite, "Custom yaw…"), in-game sensitivity input with "Yaw · DPI (from step 1)" hint; live **current cm/360°** in numberXl with the formula `360 × 2.54 ÷ (DPI × sens × yaw)`. |
| 1e | Onboarding 3/4 aim style | Three cards: Wrist (typ. 20-35 cm/360°), Mixed (30-50), Arm (45-80), each with icon and one-line description; selected card has check mark. |
| 1f | Onboarding 4/4 mousepad | List Small ≈27×21, Medium ≈32×27, Large ≈45×40, Desk mat ≈90×40, Custom (width × height); "Fit check" panel draws the 360° distance against the pad width and says whether the current setting fits. |
| 1g | Pre-test notice | Status rows with state label: Windows "Enhance pointer precision" (OFF/ON), Raw Accel curve (ACTIVE → warning row with "Open Raw Accel"), Mouse input in AimTune (RAW). Buttons Back, Re-check, Start test (never blocked). |
| 1h | In-test HUD (blind) | Scenario name top-left, timer in mono with thin progress bar top-center, "Round 4 / 10" top-right, crosshair, head-sized targets (accent fill with darker ring), horizon line, "Esc · pause" bottom-left. **No sensitivity is shown.** |
| 1i | Between rounds | "Round 4 / 10 complete", headline ("Good round."), three stat cards (hits / accuracy / avg time to hit), note that sensitivity stays hidden until the test ends, confidence in numberXl with bar and an "85 % recommended" marker, "About 3 more rounds to reach 85 %.", buttons Next round / Stop for today. |
| 1j | Results · Sensitivity | Recommended game sens (numberXl) "in-game sensitivity at 800 DPI", cm/360° with credible range ("Range 38.4-46.1 · now 43.3 (-3 %)"), confidence card ("46 rounds · 5 sessions"), buttons "Copy 1.24 to clipboard" and "Continue testing". Chart: score (0-100) vs cm/360° with fitted line, 95% uncertainty band, round-result dots, dashed vertical line at the optimum with value label, legend. Tabs Sensitivity / Acceleration. |
| 1k | Results · Acceleration (curve) | Chart of sensitivity multiplier (0.8×-1.6×) vs hand speed (counts/ms), A dashed blue vs B solid orange. Chip "Curve B recommended · confidence 74 %". A/B score bars ("20 rounds each", B +2.7), difference 95% CI, Raw Accel parameter list (mode, input offset, acceleration, cap), "Export Raw Accel settings". |
| 1l | Results · Acceleration (none) | Same chart; chip "No clear difference", heading "No acceleration recommended", explanation, A/B bars with CI that includes 0, "Run more A/B rounds", export link disabled ("nothing to export"). |
| 1m | History | Line chart of recommended cm/360° per session, table (date, game, rounds, cm/360°, confidence, type = Sensitivity / Accel A/B), game filter (All / per game). |
| 1n | Settings | Language (English / Deutsch), game profile dropdown with yaw hint, mouse DPI, round length 30/45/60/90 s, **metric weights** sliders (accuracy 40, time to hit 30, tracking 20, consistency 10; total shown), Save / Reset to defaults. |

Keyboard: menu hotkeys as shown, Enter to confirm, Esc to pause/back, Tab navigation in all forms.

## Trainer visual style

Minimal, high contrast, low distraction: dark room, soft horizon, no decoration. Targets use the accent ramp (fill `#b5abfc`, ring accent 700), crosshair is a small neutral cross. HUD text is mono for numbers and sits in the 72 px margins, away from the center.

## Accessibility

- Colorblind-safe chart palette; series are also distinguished by dash style and labels; status uses text (OFF / ACTIVE / RAW) plus icon, not color alone.
- Interface text at least 16 px at 1920×1080; uppercase labels 14 px.
- Full keyboard navigation of menus and forms.

## i18n

LibGDX `I18NBundle`: `assets/i18n/messages.properties` (English default) and `messages_de.properties`. No user-visible strings in code. Layouts must tolerate German text length (about +30%). Numbers use the locale decimal separator for display only; stored values are locale-independent.

## Scene2D mapping

- UI stage uses a 1920×1080 virtual size with a fit/scaling viewport; the 3D room has its own viewport.
- Skin generated from `tokens.json` (colors, font sizes, radii) into `assets/ui/` (`uiskin.json` + atlas). Outlined buttons, cards and inputs use 9-patch drawables generated for radii 4/8/14.
- Fonts: Inter and JetBrains Mono bundled and rasterized to bitmap fonts (e.g. gdx-freetype; verify version and the fonts' licenses at M2/M8).
- Charts are custom actors drawn with `ShapeRenderer` (lines, filled band with alpha, dashed segments, dots), not Scene2D widgets.

## Deviations

| Mockup | Planned implementation | Reason |
|---|---|---|
| Label letter-spacing +10% | Approximated with spaced glyph layout for uppercase labels, or dropped | `BitmapFont` has no tracking |
| 2D circles as targets | Real 3D spheres, unlit accent color with an outline ring | The trainer is 3D; it must use the game's FOV |
| Icons (play, history, gear, hand …) | Line icons rendered into a texture atlas | Scene2D has no SVG; icon set and license to be confirmed |
| CSS hover/focus effects | Explicit hover, pressed, focus and disabled drawables (not shown in the mockups) | Scene2D needs them defined; designed to match the outlined style |
| Game tiles show CS2, Apex, Overwatch 2, Fortnite | Valorant ships first; other profiles are added only after yaw/FOV are verified | Spec: Valorant first, never guess values |
| Pad presets include Desk mat | Adopted (small / medium / large / desk mat / custom) | Extends the spec's three presets |

## Open design questions

See `docs/PROGRESS.md` ("Open questions"): pre-test system checks, "consistency" metric definition, session length vs "stop anytime", export file dialog.
