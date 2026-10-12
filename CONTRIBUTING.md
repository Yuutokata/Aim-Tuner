# Contributing

## Workflow

- Read [docs/PROGRESS.md](docs/PROGRESS.md) first and keep it updated.
- One logical change per commit, format `<type>: <description>` (feat, fix, refactor, docs, test, chore, perf, ci).
- Logic in `core` needs tests. Run `./gradlew build` before opening a PR.
- Respect the anti-cheat rules in [CLAUDE.md](CLAUDE.md): no game process access, no input outside the app window, no custom drivers.

## How to add a new game

*(Final format is documented once the `GameProfile` loader exists, in M4.)*

Games are JSON profiles in `games/` and need no core code changes. A profile provides:

- `name`
- `yaw`: degrees per count at sensitivity 1
- `fov`: value and type (`horizontal` or `vertical`), and how it adapts to aspect ratio
- `sensitivity`: format and rounding (decimals)

Verify yaw and FOV against a current, reliable source and cite it in the PR.
