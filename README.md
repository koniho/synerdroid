# Synerdroid

Synerdroid is an unofficial, open-source Android client compatible with Synergy. It is not affiliated with, endorsed by, or distributed by Synergy App Ltd (formerly Symless).

The project modernizes the archived `symless/synergy-android-7` client for Android 7 and later.

## Features

- Synergy core-protocol client tested with a Synergy 3 server
- Rootless pointer and navigation through Android Accessibility Service
- Optional Synerdroid on-screen keyboard and remote text input
- TLS 1.2/1.3 with exact SHA-256 server-certificate pinning
- Adjustable pointer speed and scroll direction
- Responsive light/dark interface and connection diagnostics

## Build

Requirements: JDK 17 and Android SDK 35.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Configure

Enable **Synerdroid input control** in Android Accessibility settings. In Synerdroid, enter a client screen name present in the Synergy server configuration, the server hostname or IP, port 24800, and the SHA-256 fingerprint of the TLS-enabled server certificate.

Synergy 3's “add by IP” flow expects another full Synergy 3 installation. Until native pairing is implemented, add Synerdroid to the server's text configuration.

## Security and privacy

Synerdroid sends and receives input directly over the local network. TLS connections require an exact certificate fingerprint and do not silently trust arbitrary self-signed certificates. The app currently includes no analytics, advertising, or telemetry.

## License and trademarks

This derivative is distributed under GNU GPL v2; see [COPYING](COPYING) and [NOTICE](NOTICE). “Synergy” is used only to describe protocol and product compatibility. Synergy and related marks belong to their respective owner. Synerdroid is an independent community project.
