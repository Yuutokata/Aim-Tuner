# 0001: LibGDX over Compose Desktop

## Context
The app needs a 3D aim trainer with a stable frame loop and exact FOV control, plus a small UI (onboarding, charts, results).

## Decision
Use LibGDX (LWJGL3 backend) for rendering and Scene2D for UI. Charts are drawn with Scene2D/ShapeRenderer.

## Consequences
- Direct control of camera, FOV and frame timing; GLFW is available for the raw-mouse fallback.
- UI is less rich than Compose; web mockups need adaptation (see DESIGN.md deviations).
- Logic stays in `core` without UI dependencies, so the UI toolkit stays replaceable.
