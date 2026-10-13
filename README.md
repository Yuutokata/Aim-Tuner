# AimTune

Find your optimal mouse sensitivity (and optionally an acceleration curve) in a built-in 3D aim trainer, then convert it to your game's sensitivity and cm/360°. Open source, MIT licensed, Windows.

> Status: early development. See [docs/PROGRESS.md](docs/PROGRESS.md).

![Results: recommended sensitivity with the score chart](docs/screenshots/results.png)

## What it does

- Runs short **blind** aim rounds in a built-in 3D trainer (flick, micro-adjust, tracking, target switching) at sensitivities chosen by a Bayesian optimizer, so you never see which one you are playing.
- Reports the best sensitivity as your game's value and as cm/360° with a credible range and a confidence, and keeps a history of sessions.
- Optionally looks for a speed-dependent curve in how your first movements miss, lets you test it blind against plain sensitivity, and exports a [Raw Accel](https://github.com/a1xd/rawaccel) `settings.json` only if the curve is clearly better.
- English and German.

| Menu | Acceleration analysis |
| --- | --- |
| ![Menu](docs/screenshots/menu.png) | ![Acceleration analysis](docs/screenshots/accel-analysis.png) |

| History | Blind round |
| --- | --- |
| ![History](docs/screenshots/history.png) | ![A blind round](docs/screenshots/blind-running.png) |

The screenshots are rendered with sample data by `./gradlew :app:screenshots` (`:app:screenshotsDe` for German).

![Settings in German](docs/screenshots/settings-de.png)

## Safety

AimTune never touches game processes, never reads input outside its own focused window, and never edits game configs. Acceleration curves are only exported as a file for [Raw Accel](https://github.com/a1xd/rawaccel).

## Build

```
./gradlew build
./gradlew :app:run
```

On first launch a four-step setup asks for your mouse DPI, game and current sensitivity, aim style and mousepad size; everything is stored in `%APPDATA%\AimTune\settings.json` and can be changed in Settings. Click starts a round, Esc aborts it, F3 toggles the debug overlay. Developer options (`--seed`, `--source`, `--no-vsync`, `--screenshots <dir>`, `--language <code>`, `--record <dir>`) are listed in `LaunchOptions.USAGE`.

Finished rounds are analysed (first-movement error, corrections, path efficiency) and stored in a local SQLite database in `%APPDATA%\AimTune` (set `AIMTUNE_DATA_DIR` to use another folder).

To package the app with its own Java runtime: `./gradlew :app:jpackage` writes `app/build/jpackage/AimTune/AimTune.exe` (add `-PinstallerType=exe` or `msi` for an installer; that needs the WiX toolset). See [ADR 0005](docs/adr/0005-jpackage-without-plugin.md).

Gradle itself runs on whatever JDK `JAVA_HOME` points to (Gradle 9.6.0 supports JDK 17 up to 25). The project compiles for Java 21 through a Gradle toolchain, and the foojay resolver plugin downloads JDK 21 automatically if it isn't installed, so you don't need to install JDK 21 yourself.

## Documentation

- [Specification](docs/SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Algorithms](docs/ALGORITHMS.md)
- [Design](docs/DESIGN.md)
- [Contributing](CONTRIBUTING.md)

## License

MIT
