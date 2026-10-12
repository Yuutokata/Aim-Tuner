# 0002: JNA Raw Input with GLFW fallback

## Context
Sensitivity measurements need per-report mouse counts with high-resolution timestamps, independent of the OS cursor and frame rate. GLFW raw motion is frame-polled and has no per-report timestamps.

## Decision
Read Windows Raw Input (WM_INPUT) via JNA on a dedicated thread with a message-only window, timestamping each report with QueryPerformanceCounter. Hide it behind `RawMouseSource`; provide a GLFW raw-motion implementation as fallback. Input is read only while our window is focused.

## Consequences
- Accurate event timing for submovement analysis.
- Windows-specific native struct handling to maintain and verify against current docs.
- Other OSes can still run the app through the fallback with lower timing quality.
- Nothing is read outside our own window, in line with the anti-cheat rules.
