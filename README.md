# AimTune

Find your optimal mouse sensitivity (and optionally an acceleration curve) in a built-in 3D aim trainer, then convert it to your game's sensitivity and cm/360°. Open source, MIT licensed, Windows.

> Status: early development. See [docs/PROGRESS.md](docs/PROGRESS.md).

## Safety

AimTune never touches game processes, never reads input outside its own focused window, and never edits game configs. Acceleration curves are only exported as a file for [Raw Accel](https://github.com/a1xd/rawaccel).

## Build

```
./gradlew build
./gradlew :app:run
```

Requires JDK 21 (Gradle toolchain resolves it automatically).

## Documentation

- [Specification](docs/SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Algorithms](docs/ALGORITHMS.md)
- [Design](docs/DESIGN.md)
- [Contributing](CONTRIBUTING.md)

## License

MIT
