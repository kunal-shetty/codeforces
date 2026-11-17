# Changelog

All notable changes to Upsolve. Versions follow `versionName` in `app/build.gradle`.

## [3.1] - 2026-11-17

### Added
- **LeetCode problem sheet**: the ✦ button opens AI Hints and Explain, using the LeetCode statement.
- **Pattern tracks** for LeetCode: 16 patterns with tiered goals (15 → 40 → 100 → 200 → 400) and a next-problems list from Easy to Hard.
- **Weekly coach on LeetCode**, using Easy/Medium/Hard counts and per-tag solves.
- **Trick cards**: flashcards from the third hint, the Explain "key trick" and your notes, with Leitner-box spacing.
- **Widget redesign**: progress ring, streak pill, next problem to solve again, cards due. Always dark.
- Explain now ends with the one key idea to remember.

### Removed
- The AI **Debug** tab. Open models weren't reliable enough at finding real bugs.

### Fixed
- LeetCode tag renames ("Graph Theory", "Union-Find") that left some tracks at 0.

## [3.0] - 2026-11-17

### Added
- Renamed the app from CF Daily to **Upsolve**.
- **AI coach** on Groq's free API: Hints, Debug, Explain and the weekly coach. Bring your own key.
- **Solve again from memory**: solved problems come back after about 2 and 6 weeks.
- **My template**: your own header on top of every generated template.

### Fixed
- Dark-mode contrast on pastel cards.

## [2.1] - 2026-11-17

### Added
- **C++ templates** for Codeforces, with the input already read, checked against the sample input.

## [2.0]

- Codeforces and LeetCode modes, daily picks, contests with reminders, problem search, stats, friends leaderboard, widget.
