# Aether Android

Android application for running Aether censorship circumvention client on Android devices.

## Features

- Downloads the latest Aether binary from GitHub releases
- Runs an encrypted tunnel (MASQUE/WireGuard)
- Exposes a SOCKS5 proxy at `127.0.0.1:1819`
- Foreground service for reliable background operation
- Modern Material 3 design with dynamic color support

## Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Local Build
```bash
./gradlew assembleDebug
```
APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Signed Release Build
1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore release.jks -alias aether -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `local.properties` with:
   ```
   keystore.password=your_password
   key.password=your_password
   key.alias=aether
   ```

3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

### GitHub Actions (CI/CD)
On every push to `main` or `master`, GitHub Actions will:
1. Build a Debug APK
2. Upload it as an artifact

## Usage

1. Install APK on your Android device
2. Press **Start Aether**
3. The app downloads the binary (first run only)
4. Tunnel starts on `127.0.0.1:1819`
5. Configure your apps or device to use SOCKS5 proxy `127.0.0.1:1819`
6. Press **Stop Aether** to stop

## License

This project wraps [Aether](https://github.com/CluvexStudio/aether) by CluvexStudio. Please respect the original project's license.