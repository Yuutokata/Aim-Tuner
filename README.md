# AimTune

Find your optimal mouse sensitivity (and optionally an acceleration curve) in a built-in 3D aim trainer, then convert it to your game's sensitivity and cm/360°. Open source, MIT licensed, Windows.

> Status: early development. See [docs/PROGRESS.md](docs/PROGRESS.md).

![Results: recommended sensitivity with the score chart](docs/screenshots/results.png)

## Install

1. Download `AimTune-windows.zip` from the latest release and unzip it anywhere.
2. Start `AimTune\AimTune.exe`. It brings its own Java runtime; nothing else has to be installed.
3. Windows may show a SmartScreen warning because the program is not code-signed yet: choose "More info", then "Run anyway".

Settings and your rounds are stored in `%APPDATA%\AimTune` (set `AIMTUNE_DATA_DIR` to use another folder). Deleting that folder resets everything.

## How to use it

1. **Set up once.** Enter your mouse DPI, your game and its current sensitivity, how you aim and the size of your pad. Switch off "Enhance pointer precision" in Windows and any Raw Accel curve before you test.
2. **Start a test.** Rounds of 45 seconds: click the targets as fast and accurately as you can. After every round you see how many you hit and how sure the program is; the sensitivity of the round is never shown. Play 15 to 30 rounds, ideally on two different days; "Stop for today" is always available.
3. **Read the result.** The Results screen shows the recommended sensitivity as your game's value and as cm/360°, a range, and a confidence. Type the value into your game and aim with it for a few days before judging.
4. **Optional: acceleration.** The Acceleration menu looks for a speed-dependent curve in how your first movements miss, tests it blind against your normal sensitivity, and only offers a Raw Accel `settings.json` if it is clearly better. Most people will be told that no curve is clearly better, which is a normal answer.

## How it works

- **Blind rounds.** The program plays short aim rounds at different sensitivities and hides which one. That way your expectation cannot decide the result.
- **A score per round.** Each round gets a score from how often your first shot hit and how fast you hit, compared with your own other rounds. The score is relative to you; it is not a rating.
- **A model of the score against sensitivity.** From the scores the program fits a smooth curve over sensitivity (a Gaussian process on cm/360°, on a log scale) and picks the next sensitivity to try where it learns the most. Rounds from earlier days are included and each day may have a different level; a steady change within a day (warming up, getting tired) is detected and lowers the confidence.
- **Confidence.** It is the chance that no other sensitivity in the tested range is noticeably better than the recommended one. It is not a promise about your game.
- **Targets.** The targets have the angular size of a head at typical distances, defined per game in `games/*.json`.

## Limitations

- **The target sizes are estimates.** Riot publishes no head size or movement speeds, so the Valorant profile uses a head of about 0.2 m seen from 6 to 30 m. The pre-test notice says so until the values are measured in the game (see [docs/REVIEW.md](docs/REVIEW.md)). The recommended sensitivity depends on target size.
- **Checked against a simulated player, not against many people.** The method recovers the best sensitivity of a simulated player in most runs, but how well real people behave like it is not measured yet.
- **A result is a good starting point, not a proof.** Two sensitivities close to each other are practically equal; the range is part of the answer. Fatigue and learning can move the result; play on more than one day.
- **Acceleration is experimental.** The exported Raw Accel file follows the 1.7.1 settings format but has not been loaded into Raw Accel by the author yet. The analysis needs a mouse report rate of 1000 Hz or more and Windows Raw Input.
- **Windows only**, and the window should be 16:9 or will be letterboxed to it (the FOV of the game at other shapes is unknown).

## What it does

- Runs short **blind** aim rounds in a built-in 3D trainer (flick, micro-adjust, tracking, target switching) at sensitivities chosen by a Bayesian optimizer, so you never see which one you are playing.
- Reports the best sensitivity as your game's value and as cm/360° with a credible range and a confidence, and keeps a history of sessions.
- Optionally looks for a speed-dependent curve in how your first movements miss, lets you test it blind against plain sensitivity, and exports a [Raw Accel](https://github.com/RawAccelOfficial/rawaccel) `settings.json` only if the curve is clearly better.
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

AimTune never touches game processes, never reads input outside its own focused window, and never edits game configs. Acceleration curves are only exported as a file for [Raw Accel](https://github.com/RawAccelOfficial/rawaccel).

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
