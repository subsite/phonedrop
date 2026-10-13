# PhoneDrop

Android app (Kotlin + Jetpack Compose) that runs an SFTP server on the phone so a Linux PC can transfer files over the LAN with standard tools (Nautilus/Dolphin `sftp://`, `sftp`, `scp`, `rsync`, `sshfs`). No root. It's sideloaded, not published on Play, so `MANAGE_EXTERNAL_STORAGE` is intentional. License: GPL-3.0-or-later.

## Layout

`app/src/main/java/fi/subsite/phonedrop/`:
- `server/SshServerFactory.kt` builds the Apache MINA SSHD server. Keep it free of Android APIs so the JVM unit tests can use it.
- `server/SftpServerService.kt` is the foreground service (`specialUse`). It owns the server, the Wi-Fi lock, the wake lock and mDNS, and holds the live password.
- `server/CommandHandler.kt` is the only exec/shell handling. It accepts `exit` and the `ssh-copy-id` install command; everything else is rejected.
- `server/LenientFileSystemAccessor.kt` ignores chmod/chown/utimes failures, because Android's FUSE storage rejects them.
- `server/AuthorizedKeys.kt` holds the keys, stored in OpenSSH format in the app's private `filesDir/authorized_keys`.
- `server/ServerState.kt` is a process-wide `StateFlow` shared by the service and the UI.
- `data/Settings.kt` (SharedPreferences) stores the shared root (default Download) and the password-login toggle.
- `ui/` holds the Compose screens. Navigation is a simple `showKeys` boolean in `MainActivity`, with no nav library.

## Behaviour to preserve

- Port 2222. Any username is accepted. The displayed username is `drop` (`SftpServerService.USERNAME`).
- Password login is optional and its on/off state is remembered. A fresh 8-character password is made on every start and every time it's switched on. The password is read through a provider lambda, so it can be toggled while the server runs.
- The password is shown only in the app, masked by default. Never put it in the notification.
- SFTP only: never add a shell or general exec.
- Keys are never read from shared storage, because other apps could write there.
- The folder picker is the in-app `FolderPickerDialog`. Don't use SAF `OpenDocumentTree`, because Android 11+ blocks the storage root and Download in it.

## Constraints

- `minSdk 26` (MINA needs `java.nio.file`). Watch for newer JDK APIs that Android lacks, e.g. `InputStream.readNBytes` needs API 33. Run lint.
- Only `material-icons-core` is a dependency. Add other icons as vector drawables in `res/drawable/`; don't pull in icons-extended.
- MINA on Android needs `user.home` set to `filesDir` and the BC provider disabled; see `prepareMina()`.
- Version comes from the git tag: CI passes `-PappVersion` and local builds use `git describe` (see the top of `app/build.gradle.kts`). It's shown in `AboutDialog` via `BuildConfig.VERSION_NAME`. Never hard-code it.
- Release signing uses the `SIGNING_*` env vars (CI secrets). Without them it falls back to the debug key. Never commit keystores.

## Releasing

Pushing a `vX.Y.Z` tag runs `.github/workflows/release.yml`, which tests, builds a signed APK and creates a GitHub Release. The remote is `git@github-subsite:subsite/phonedrop.git`; `github-subsite` is an SSH host alias on the user's machine. The user pushes from the host.

## Build & test

The user builds and runs the app from Android Studio on the host, with an emulator or a phone.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The unit tests in `SshServerFactoryTest` run the real server on localhost. They also drive the OpenSSH `sftp`, `scp` and `ssh-copy-id` clients when those are installed.

### In the Claude Code container

- JDK and SDK live outside the repo: `~/jdk` and `~/android-sdk`. They disappear when the container is rebuilt; reinstall them with Temurin 17 and cmdline-tools → `platforms;android-35`, `build-tools;35.0.0`.
- `local.properties` belongs to Android Studio on the host (`sdk.dir=/home/fredde/Android/Sdk`) and is gitignored. Don't leave it changed. Swap it temporarily:

```sh
cp local.properties /tmp/lp.host && echo "sdk.dir=$HOME/android-sdk" > local.properties
JAVA_HOME=~/jdk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -q
cp /tmp/lp.host local.properties
```

- There's no KVM or emulator in the container. On-device checks are done by the user.
- Ignore the lint warnings about newer dependency versions and DataExtractionRules.
