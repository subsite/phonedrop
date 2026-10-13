# PhoneDrop

Android app that turns the phone into an SFTP server on the local network, so a Linux PC can
browse and copy files with tools it already has: Nautilus/Dolphin, `sftp`, `scp`, `rsync`, `sshfs`.
No root required.

- Port **2222**, any username.
- Password login (optional, remembered): a fresh 8-character password is generated each session and
  shown in the app. Turn it off to allow key login only; it can be switched while the server runs.
- For permanent access, run `ssh-copy-id`, or add SSH public keys in the app (**SSH keys** button).
- Shares the Download folder by default, or any folder you pick (including all of storage).
- Runs as a foreground service (notification with a Stop button), so it keeps going with the screen off.
- Advertised over mDNS as `_sftp-ssh._tcp`, so it shows up under "Network" in file managers.

## Build

Open the project in Android Studio and press Run, with the phone connected over USB (with USB
debugging on) or an emulator running.

From a terminal (uses Android Studio's SDK and bundled JDK):

```sh
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # server tests (incl. real OpenSSH client if installed)
adb install -r app/build/outputs/apk/release/app-release.apk
```

Local builds are signed with the debug key and versioned from `git describe`. You can also copy the
APK to the phone and open it to install, after allowing installs from that source.

### Emulator

The emulator is behind NAT, so the address shown in the app isn't reachable from the PC. Forward
the port and connect to localhost instead (mDNS discovery won't work here):

```sh
adb forward tcp:2222 tcp:2222
sftp -P 2222 drop@localhost
```

## Releasing

Pushing a version tag makes GitHub Actions run the tests, build a signed APK and publish it as a
GitHub Release (`.github/workflows/release.yml`):

```sh
git tag v1.0.0 && git push origin v1.0.0
```

Tags must be `vMAJOR.MINOR.PATCH`. The version is shown under **About** in the app; versionCode is
`MAJOR*10000 + MINOR*100 + PATCH`, so every release must be higher than the last.

One-time setup: create a release key (keep it and its passwords backed up; without it, future
releases can't update installed copies):

```sh
keytool -genkeypair -keystore ~/keys/phonedrop-release.jks -alias phonedrop \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Fredrik Welander, O=SubSite"
```

Then add these repository secrets (Settings → Secrets and variables → Actions):
`SIGNING_KEYSTORE_BASE64` (output of `base64 -w0 ~/keys/phonedrop-release.jks`),
`SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`phonedrop`) and `SIGNING_KEY_PASSWORD`.

Release APKs and Android Studio builds have different signatures, so switching between them on one
device means uninstalling first.

## Use

1. Open PhoneDrop, tap **Grant access** and allow "All files access".
2. Tap **Start server**. The app shows the password and an address like `sftp://drop@192.168.1.23:2222`.
3. On the PC:

```sh
# File manager: paste the address into Nautilus (Ctrl+L) or Dolphin
sftp -P 2222 drop@192.168.1.23
scp -P 2222 photo.jpg drop@192.168.1.23:/
rsync -av -e 'ssh -p 2222' drop@192.168.1.23:/ ~/phone-downloads/
sshfs -p 2222 drop@192.168.1.23:/ ~/phone
avahi-browse -rt _sftp-ssh._tcp    # find the phone on the network
```

Paths are relative to the shared folder (`/` = Download by default).

### Passwordless login

With the server running and password login on, from the PC:

```sh
ssh-copy-id -p 2222 drop@192.168.1.23   # enter the session password once
```

Or copy `~/.ssh/id_ed25519.pub` to the phone (e.g. over SFTP with the password) and use
**SSH keys → Add from file…**, or paste the key text there.
Either way, the keys are listed there and can be removed.

Optional `~/.ssh/config` entry:

```
Host phone
    HostName 192.168.1.23
    Port 2222
    User drop
```

The host key persists across restarts, so `known_hosts` stays valid. It changes only if you
reinstall the app or clear its data.

## Notes

- Changing permissions/ownership is ignored (Android storage doesn't support it); copies still succeed.
- Only SFTP is exposed: no shell or command execution. The one exception is the command
  `ssh-copy-id` uses to install keys, which only adds to the app's key list.
- Some phones aggressively kill background apps. If the server stops with the screen off, set
  PhoneDrop's battery usage to "Unrestricted".

## License

Copyright (C) 2026 Fredrik Welander

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE).
