# Contributing

## Workflow

- Read [docs/PROGRESS.md](docs/PROGRESS.md) first and keep it updated.
- One logical change per commit, format `<type>: <description>` (feat, fix, refactor, docs, test, chore, perf, ci).
- Logic in `core` needs tests. Run `./gradlew build` before opening a PR.
- Respect the anti-cheat rules in [CLAUDE.md](CLAUDE.md): no game process access, no input outside the app window, no custom drivers.

## How to add a new game

Games are JSON profiles and need no code changes. Bundled profiles live in `games/src/main/resources/games/` and are listed in `index.json`; users can drop their own files in `<data dir>/games/*.json` (picked up on start).

```json
{
  "id": "cs2",
  "name": "Counter-Strike 2",
  "yaw": 0.022,
  "fov": { "degrees": 90.0, "type": "horizontal", "aspect": { "width": 4, "height": 3 }, "scaling": "hor_plus" },
  "sensitivity": { "decimals": 3 },
  "sources": ["https://..."],
  "notes": "Anything a reviewer should know."
}
```

- `yaw`: degrees per count at sensitivity 1.
- `fov`: the value the game shows, whether it is `horizontal` or `vertical`, and the aspect ratio it refers to. `scaling` is `hor_plus` (vertical angle kept when the window gets wider).
- `sensitivity.decimals`: how many decimals the game accepts.

Verify yaw and FOV against a current, reliable source and cite it in `sources` and the PR. Never guess values.

## How to add a language

1. Copy `app/src/main/resources/assets/i18n/messages.properties` to `messages_<code>.properties` and translate the values (UTF-8, keep the `{0}` placeholders, no leading or trailing spaces).
2. Add the language to `Language` in `app/i18n/I18n.kt` (code, name, locale). `BundlesTest` fails until every key is translated.
3. Check the screens with `./gradlew :app:screenshots` after pointing `--language` at the new code.
