# Security policy

## Supported versions

Only the latest release gets fixes. Please update before reporting.

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Report them privately with GitHub's [private vulnerability reporting](https://github.com/kunal-shetty/codeforces/security/advisories/new). Include:

- what the problem is and what it lets someone do,
- the steps to reproduce it, with the app version and Android version,
- a suggested fix, if you have one.

You'll get a reply within 7 days. Once a fix ships, we'll credit you in the release notes unless you'd rather we didn't.

## What's in scope

- Leaking the user's Groq API key (logs, backups, other apps, unexpected hosts).
- Sending data anywhere other than Codeforces, LeetCode and Groq.
- Exported components (receivers, widget, activity) that other apps can abuse.
- Injection through data the app reads from Codeforces or LeetCode (problem names, statements) into SQL, intents or anything else that runs.

## How user data is handled

- **Groq key:** stored in the app's private `SharedPreferences` and sent only, as a Bearer token, to `https://api.groq.com`. It is never logged.
- **Everything else** (handles, solves, notes, cards) is in app-private SQLite and never leaves the device, except the public handle sent to Codeforces or LeetCode to fetch the user's own public data.
- The app has no servers, accounts or analytics of its own.

## For contributors

- Never commit API keys, keystores or `local.properties`; `.gitignore` covers the usual ones.
- Use parameterised SQL (`?` placeholders), never string concatenation, for anything that comes from the network or the user.
- Network code uses HTTPS only.
