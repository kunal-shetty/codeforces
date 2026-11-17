# Contributing to Upsolve

Thanks for helping. Upsolve is a small, focused app, and this guide gets you from zero to a merged pull request.

- [Ways to help](#ways-to-help)
- [Set up](#set-up)
- [Project rules](#project-rules)
- [Where things live](#where-things-live)
- [Working on the template generator](#working-on-the-template-generator)
- [Working on AI features](#working-on-ai-features)
- [Pull requests](#pull-requests)
- [Releases](#releases)

## Ways to help

- **Report a bug**: open an issue with the *Bug report* form. Include the problem ID (e.g. `1741D` or `two-sum`) when it's about one problem.
- **Template misses**: a Codeforces problem whose template shows ⚠ or reads the input wrong is a great first issue. Use the *Template miss* form.
- **Ideas**: the *Feature request* form. Please say what problem it solves for someone practising.
- **Code**: issues labelled [`good first issue`](https://github.com/kunal-shetty/codeforces/labels/good%20first%20issue) or [`help wanted`](https://github.com/kunal-shetty/codeforces/labels/help%20wanted). Comment on the issue so nobody duplicates the work.

## Set up

You need **JDK 17+** and the **Android SDK** (Android Studio installs both).

```bash
git clone https://github.com/<you>/codeforces.git
cd codeforces
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

**Test on a real phone when you can** (USB debugging plus `adb`). Sync, notifications, exact alarms and the widget behave differently on emulators. Check light and dark mode (Profile → Appearance) and both sites (the pill in the top bar).

For AI features, get a free key at [console.groq.com/keys](https://console.groq.com/keys) and paste it in Profile → AI coach. **Never commit a key**, not even in a test.

## Project rules

These keep the app small, fast and consistent. A PR that breaks one needs a good reason, written in its description.

1. **No third-party libraries.** Pure Java 11 and Android XML. Networking is `HttpURLConnection`, JSON is `org.json`, storage is `SQLiteOpenHelper`. Don't add an AndroidX, Kotlin or network dependency.
2. **Colourful, low text.** Use cards, chips, icons and charts rather than paragraphs. Use the bottom sheet (`MainActivity.showSheet`) for choices and details, never `AlertDialog`. Short labels: "Swap", not "Swap this problem for another one".
3. **Light and dark.** Use colours from `res/values/colors.xml` (they have dark twins in `values-night/`). Don't hard-code text colours on themed surfaces; `R.color.text` is right on every pastel fill. The widget is the exception: it is always dark.
4. **Both sites.** If a feature makes sense on Codeforces and LeetCode, support both through `Mode.lc()`. If it doesn't, hide it cleanly on the other site.
5. **Offline-friendly.** Screens render from the database; network work runs on a background thread and refreshes the screen when it finishes. Never block the main thread.
6. **Private.** Nothing leaves the phone except calls to Codeforces, LeetCode and (with the user's key) Groq. No analytics.

### Code style

- 4 spaces, braces on the same line, about 160 columns max (see `.editorconfig`).
- Javadoc on classes and on anything non-obvious. A comment says *why*, not *what*.
- User-facing text is plain English, short, with no exclamation marks.
- Keep classes focused. One screen per `*Screen` class; reusable pieces (sheets, cards) get their own class.

## Where things live

| You want to... | Look at |
|---|---|
| Change how daily picks are chosen | `data/Recommender.java` |
| Add a database table or column | `Db.createExtras()` with `CREATE TABLE IF NOT EXISTS` / `addColumn`, then bump the version in the `Db` constructor |
| Call a new Codeforces endpoint | `data/CfApi.java` (has rate-limit retry) |
| Query more LeetCode data | `data/LcApi.java`, queries in `LcSync.java` |
| Add an AI feature | `data/Ai.java` (`ask`, `json`), UI patterns in `ui/ProblemSheet.java` and `ui/Coach.java` |
| Add a tab to the problem sheet | `ui/ProblemSheet.java`, `kinds` and `select()` |
| Change the widget | `ui/TodayWidget.java`, `res/layout/widget_today.xml` |
| Add a pattern track | the `TRACKS` table in `ui/Tracks.java` (LeetCode tag names are case-sensitive; check one with the GraphQL `topicTags` query) |

Database upgrades must never lose user data: only add tables and columns, and keep `onUpgrade` idempotent.

## Working on the template generator

`data/Template.java` turns a Codeforces *Input* section into C++ reading code. It has no Android dependencies, so you can test it on your computer.

```bash
cd tools/template-check
./fetch.sh            # downloads the sample problem pages into ./pages (not committed)
./run.sh              # compiles Template.java with Run.java and checks every page
./run.sh 1741_D       # also prints the generated code for one problem
```

`run.sh` prints `OK` for each problem whose generated code reads all of its sample input exactly, and a score at the end. **A template change must not lower the score.** Add the problem you fixed to `problems.txt` so it stays fixed. If you have `g++`, `./run.sh --compile` also checks that every generated template compiles.

## Working on AI features

- Keep prompts in the class that uses them, as constants, and ask for **JSON** when the UI needs structure (`Ai.ask(ctx, system, user, true)` then `Ai.json(reply)`).
- Assume open models drift: validate fields, cap list lengths, and pass text through `ProblemSheet.tidy()` to strip stray LaTeX and markdown.
- Cache answers in the `kv` table per problem, so opening one again is free and works offline.
- No spoilers by default: anything that reveals a solution sits behind a tap.
- Every AI screen needs a friendly no-key state (see `ProblemSheet.needKey()`).

## Pull requests

1. Fork the repo and create a branch: `feat/short-name`, `fix/short-name` or `docs/...`.
2. Keep each PR focused on one thing. Small PRs get reviewed fast.
3. Make sure `./gradlew assembleDebug` passes. CI runs it on every PR.
4. Fill in the PR template, with screenshots for any UI change, in light **and** dark mode.
5. Commit messages: short imperative subject (`Add trie track`, `Fix grid detection for "n lines of length m"`), then a body if the *why* isn't obvious.

A maintainer reviews it, may ask for changes, and squash-merges it. Thanks!

## Releases

Maintainers bump `versionCode` and `versionName` in `app/build.gradle`, add an entry to [CHANGELOG.md](CHANGELOG.md), and push a tag like `v3.2.0`. The *Release* workflow builds the APK and attaches it to a GitHub release. Signing setup and the full steps are in [docs/RELEASING.md](docs/RELEASING.md).

## Code of Conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). Be kind; everyone here is learning.
