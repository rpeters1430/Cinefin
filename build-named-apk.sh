#!/bin/bash
# Build and rename APK with version number

set -e

# Get version from build.gradle.kts
VERSION=$(grep 'versionName = ' app/build.gradle.kts | sed 's/.*versionName = "\(.*\)"/\1/')

echo "Building Jellyfin Android v${VERSION}..."

# Clean and build
./gradlew clean assembleDebug

# Copy and rename APK
cp app/build/outputs/apk/debug/app-debug.apk "app/build/outputs/apk/jellyfin-android-v${VERSION}-debug.apk"

echo ""
echo "✅ Build complete!"
echo "📦 APK: app/build/outputs/apk/jellyfin-android-v${VERSION}-debug.apk"
echo ""
echo "To install:"
echo "  adb install app/build/outputs/apk/jellyfin-android-v${VERSION}-debug.apk"
