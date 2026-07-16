#!/bin/bash
# Build Script for Aether Android APK
# This script creates a debug APK using the Android SDK
#
# Requirements:
#   - Android SDK (set ANDROID_HOME environment variable)
#   - JDK 17 (set JAVA_HOME environment variable)
#   - Gradle 8.6+ (or use the wrapper)
#
# Usage:
#   chmod +x build-apk.sh
#   ./build-apk.sh debug       # Build debug APK
#   ./build-apk.sh release     # Build release APK (signed)
#   ./build-apk.sh clean       # Clean build artifacts
#
# Output:
#   Debug:   app/build/outputs/apk/debug/app-debug.apk
#   Release: app/build/outputs/apk/release/app-release.apk

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

verify_prerequisites() {
    if [ -z "$ANDROID_HOME" ]; then
        echo "[!] WARNING: ANDROID_HOME is not set. Trying default paths..."
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
            echo "    Found: $ANDROID_HOME"
        elif [ -d "/usr/lib/android-sdk" ]; then
            export ANDROID_HOME="/usr/lib/android-sdk"
            echo "    Found: $ANDROID_HOME"
        else
            echo "[-] ANDROID_HOME is not set and no default SDK found."
            echo "    Install Android SDK or set ANDROID_HOME environment variable."
            exit 1
        fi
    fi

    if [ -z "$JAVA_HOME" ]; then
        echo "[!] WARNING: JAVA_HOME is not set. Trying to find Java..."
        if command -v java &> /dev/null; then
            export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
            echo "    Using: $JAVA_HOME"
        else
            echo "[-] JAVA_HOME is not set and no Java found."
            echo "    Install JDK 17 or set JAVA_HOME environment variable."
            exit 1
        fi
    fi

    echo "[+] Prerequisites satisfied."
    echo "    ANDROID_HOME=$ANDROID_HOME"
    echo "    JAVA_HOME=$JAVA_HOME"
}

build_debug() {
    echo "[*] Building Debug APK..."
    ./gradlew assembleDebug
    echo "[+] Build successful!"
    echo "    APK: app/build/outputs/apk/debug/app-debug.apk"
}

build_release() {
    echo "[*] Building Release APK..."
    echo ""
    echo "    Ensure you have configured signing in app/build.gradle.kts"
    echo "    or set up a keystore before building release."
    echo ""
    ./gradlew assembleRelease
    echo "[+] Build successful!"
    echo "    APK: app/build/outputs/apk/release/app-release.apk"
    echo ""
    echo "NOTE: This APK is NOT signed. To sign it, run:"
    echo "  jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \\"
    echo "    -keystore <your-keystore.jks> \\"
    echo "    app/build/outputs/apk/release/app-release-unsigned.apk \\"
    echo "    <alias>"
    echo "  zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release.apk"
}

clean_build() {
    echo "[*] Cleaning build artifacts..."
    ./gradlew clean
    rm -rf app/build
    echo "[+] Clean complete."
}

# Main
case "${1:-debug}" in
    debug)
        verify_prerequisites
        build_debug
        ;;
    release)
        verify_prerequisites
        build_release
        ;;
    clean)
        clean_build
        ;;
    help|--help|-h)
        echo "Usage: $0 [debug|release|clean|help]"
        echo ""
        echo "  debug   - Build debug APK (default)"
        echo "  release - Build release APK"
        echo "  clean   - Clean build artifacts"
        echo "  help    - Show this help"
        echo ""
        echo "Prerequisites:"
        echo "  - ANDROID_HOME environment variable set to Android SDK path"
        echo "  - JAVA_HOME environment variable set to JDK 17 path"
        echo "  - For release builds, a valid keystore is required"
        exit 0
        ;;
    *)
        echo "Unknown action: $1"
        echo "Usage: $0 [debug|release|clean|help]"
        exit 1
        ;;
esac