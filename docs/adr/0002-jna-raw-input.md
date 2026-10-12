# 0002: JNA Raw Input with GLFW fallback

## Context
Sensitivity measurements need per-report mouse counts with high-resolution timestamps, independent of the OS cursor and frame rate. GLFW raw motion is delivered during frame polling, so its timestamps are quantized to the frame rate.

## Decision
Read Windows Raw Input (WM_INPUT) via JNA on a dedicated thread that owns a message-only window and message loop. Each report is stamped on arrival with QueryPerformanceCounter. Hide it behind `RawMouseSource`; the GLFW raw-motion implementation (`GlfwMouseSource`, in `app` because it needs the window handle) is the fallback.

Details verified against Microsoft Learn (winuser.h docs):

- **Focus rule (hard anti-cheat rule).** A message-only window never has focus, so it must register with `RIDEV_INPUTSINK`, which also delivers input while another app is in the foreground. To keep "read only while our window is focused", the application reports focus to the source: the device is registered on focus gain and removed (`RIDEV_REMOVE`, null target) on focus loss, and any report arriving while unfocused is dropped without being queued.
- **One registration per process.** Only the window passed to the last `RegisterRawInputDevices` call receives mouse input. No other component in the process may register the mouse while `WindowsRawInputSource` runs. libGDX 1.14.2 does not enable GLFW raw mouse motion (checked in the backend source), and `GlfwMouseSource` is never used together with the JNA source.
- **Timestamps.** WM_INPUT carries no hardware timestamp, so the time is the arrival time on the input thread. Reports are read one per WM_INPUT message (no buffered draining) so arrival times are not batched.
- **JNA coverage.** JNA 5.19.1 wraps the window/message functions but not `RegisterRawInputDevices`, `GetRawInputData` or `QueryPerformanceCounter`; those are declared in `Win32Raw.kt`. JNA's `WinUser.HWND_MESSAGE` constant failed with error 1400 on 64-bit, so the parent handle is built as `(HWND)-3` explicitly.
- **Struct layout (x64).** RAWINPUTHEADER is 24 bytes; the RAWMOUSE packet is 48 bytes with `lLastX`/`lLastY` at offsets 36/40. A unit test builds packets byte by byte and checks the JNA mapping decodes them.

Not yet verified on hardware: WM_INPUT `wParam` (RIM_INPUT vs RIM_INPUTSINK) for a message-only window. It is counted for diagnostics only and not used for gating.

## Consequences
- Accurate arrival timing for submovement analysis, independent of the frame rate.
- Windows-specific native struct handling to maintain.
- Other OSes can still run the app through the fallback with frame-quantized timing.
- Nothing is queued while the window is unfocused, in line with the anti-cheat rules.
