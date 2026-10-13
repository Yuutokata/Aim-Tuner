# 0005: Packaging with jpackage directly

## Context
The plan named the Badass Runtime plugin for the installer. It wraps jlink and jpackage and has to work out the needed modules of a dependency set that includes LWJGL, JNA and SQLite natives, none of which are modular. Its compatibility with Gradle 9.6, Kotlin 2.4 and the JDK 21 toolchain could not be verified here.

## Decision
`./gradlew :app:jpackage` runs the toolchain JDK's own `jpackage` on the `installDist` jars, with a fixed list of runtime modules (`java.base`, `java.desktop`, `java.logging`, `java.management`, `java.naming`, `java.sql`, `jdk.unsupported`). The default type is `app-image` (a folder with `AimTune.exe` and its runtime, about 110 MB); `-PinstallerType=exe` or `msi` builds an installer and needs the WiX toolset. CI zips the app image on version tags.

## Consequences
- No third-party plugin to keep compatible; the task is 25 lines in `app/build.gradle.kts`.
- The module list is maintained by hand. A new dependency that needs another JDK module shows up as a startup failure of the packaged app; the screenshot tour (`AimTune.exe --screenshots <dir>`) is a quick check that the packaged runtime works.
- The packaged build was checked by running the tour from the app image; raw input and SQLite from the packaged build are still for a person to check.
