# Design

## Source of truth

`docs/design/AimTune.pdf` (screen exports) and `docs/design/tokens.json` (Nocturne theme). The UI follows them: tokens are translated into a Scene2D skin in `assets/ui/`. Mockups are web designs; where Scene2D cannot reproduce something 1:1, the deviation is listed under "Deviations" below.

> Status: `AimTune.pdf` has not been reviewed yet (the PDF could not be rendered in the current environment). Screen layouts below are the planned structure; align them with the PDF before M2 UI work.

## Tokens (summary)

- Colors: bg `#161826`, surface `#232532`, text `#e9e9ed`, accent `#9184d9`; neutral and accent ramps 100-900; divider `rgba(233,233,237,0.16)`.
- Chart colors (colorblind-safe): baseline `#56B4E9`, candidate `#E69F00`, good `#009E73`, warning `#D55E00`, fit `#b5abfc`, band `rgba(145,132,217,0.22)`. Never rely on color alone: baseline dashed `14 8`, candidate solid.
- Type: Inter (heading/body), JetBrains Mono (numbers); sizes label 14 … numberXl 150; label letter-spacing 0.1em.
- Spacing 4/8/12/16/24/32/48/72, radius 4/8/14, layout 1920×1080 with margin 72, header 88, footer 120.

## Screens and flow

onboarding (game, DPI, sensitivity, aim style, pad, notice) → test (blind rounds, minimal HUD) → results (recommendation, score chart with band, confidence, history) → optional accel (curve chart, A/B, export).

## Trainer visual style

Minimal, high contrast, low distraction: plain room, head-sized targets with strong contrast to the background, small crosshair, no sensitivity shown during blind rounds.

## Accessibility

Colorblind-safe palette (above) plus dash/shape differences; text at least 14 px at 1080p; keyboard navigation in menus.

## i18n

LibGDX `I18NBundle`; `assets/i18n/messages.properties` (English default) and `messages_de.properties`. No user-visible strings in code.

## Deviations from the mockups

None recorded yet.
