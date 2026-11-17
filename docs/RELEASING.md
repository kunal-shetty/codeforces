# Releasing

## One-time setup: a signing key

Android only installs an update if it's signed with the same key as the installed app, so every release must use **one** key. Keep it safe: lose it, and users have to uninstall (and lose their data) to move to a new one.

```bash
keytool -genkeypair -v -keystore upsolve.jks -alias upsolve -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upsolve.jks > upsolve.jks.b64      # macOS: base64 -i upsolve.jks -o upsolve.jks.b64
```

In the repository, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `UPSOLVE_KEYSTORE_BASE64` | contents of `upsolve.jks.b64` |
| `UPSOLVE_KEYSTORE_PASSWORD` | the keystore password |
| `UPSOLVE_KEY_ALIAS` | `upsolve` |
| `UPSOLVE_KEY_PASSWORD` | the key password |

Store `upsolve.jks` and the passwords somewhere safe outside the repo (a password manager), then delete the `.b64` file. `.gitignore` already blocks `*.jks`.

Without these secrets the Release workflow still publishes an APK, but it's debug-signed with a throwaway key, so it can't update an earlier install.

## Each release

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle`.
2. Move the notes from the top of `CHANGELOG.md` under the new version and today's date.
3. Commit, then tag and push:
   ```bash
   git tag v3.2.0
   git push origin main --tags
   ```
4. The **Release** workflow builds `Upsolve-v3.2.0.apk` and publishes the GitHub release. Check its notes and edit them if needed.

## Building a signed APK locally

```bash
UPSOLVE_KEYSTORE=/path/to/upsolve.jks UPSOLVE_KEYSTORE_PASSWORD=... UPSOLVE_KEY_ALIAS=upsolve UPSOLVE_KEY_PASSWORD=... \
  ./gradlew assembleRelease
```
