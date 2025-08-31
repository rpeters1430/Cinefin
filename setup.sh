#!/usr/bin/env bash
set -euo pipefail

# ----- Android SDK + Build Tools -----
: "${TARGET_API:=35}"      # change to 36 if you want Android 16 preview
: "${BUILD_TOOLS:=35.0.0}" # latest stable
: "${SDK_ROOT:=$HOME/android-sdk}"

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"

TOOLS_ROOT="$SDK_ROOT/cmdline-tools"
TOOLS_LATEST="$TOOLS_ROOT/latest"

if [ ! -x "$TOOLS_LATEST/bin/sdkmanager" ]; then
  echo "Installing Android cmdline-tools..."
  mkdir -p "$TOOLS_ROOT"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
  unzip -q /tmp/cmdtools.zip -d "$TOOLS_ROOT"
  rm /tmp/cmdtools.zip
  mv "$TOOLS_ROOT/cmdline-tools" "$TOOLS_LATEST"
fi

export PATH="$TOOLS_LATEST/bin:$SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null
yes | sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${TARGET_API}" \
  "build-tools;${BUILD_TOOLS}"

# ----- Project wiring -----
# Write local.properties next to a detected Gradle wrapper.
PROJECT_DIR="$PWD"
if [ ! -f "$PROJECT_DIR/gradlew" ] && [ -f "/workspace/gradlew" ]; then
  PROJECT_DIR="/workspace"
fi

if [ -f "$PROJECT_DIR/gradlew" ]; then
  chmod +x "$PROJECT_DIR/gradlew"
  echo "sdk.dir=$SDK_ROOT" > "$PROJECT_DIR/local.properties"
fi

echo "✅ Android SDK ready (API $TARGET_API, Build-Tools $BUILD_TOOLS)"
