# Synergy for modern Android

An experimental Synergy client for Android 7+ updated from the archived
`symless/synergy-android-7` project.

## What changed

- Targets Android API 35 and builds with current Android Gradle tooling.
- Replaces root-only `/dev/uinput` access with an Android Accessibility Service.
- Adds TLS 1.2/1.3 with SHA-256 server-certificate pinning.
- Uses a responsive, scrollable light/dark UI.
- Shows persistent connection diagnostics and previous Java crash details.
- Saves the last values entered without supplying a default server address.

## Accessibility limitations

Android's public Accessibility API can perform taps, swipes, scrolling, global
navigation, and basic text editing. It cannot provide exact rootless emulation
of arbitrary hardware key events, mouse hover, or every desktop shortcut.

## Build

Requirements:

- JDK 17 or newer
- Android SDK 35

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Configure

Enable **Synergy input control** in Android Accessibility settings. In the app,
enter a client screen name that is present in the server's Synergy config, the
server hostname or IP, port 24800, and the SHA-256 fingerprint printed by the
TLS-enabled Synergy server.

The server must allow the chosen client screen name. Synergy 3's "add by IP"
flow expects another full Synergy 3 installation, so custom Android clients
should instead be added to the server's text configuration.

## Security

TLS connections require an exact SHA-256 certificate fingerprint. The client
will not silently trust an arbitrary self-signed certificate.

## License

The original project is GPL-licensed; see [COPYING](COPYING).
