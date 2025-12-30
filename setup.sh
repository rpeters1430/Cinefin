#!/usr/bin/env bash
set -euo pipefail

JDK_MAJOR=21
OS_NAME=$(uname -s | tr '[:upper:]' '[:lower:]')
case "$OS_NAME" in
  linux*) HOST_OS="linux" ;;
  msys*|mingw*|cygwin*) HOST_OS="windows" ;;
  *)
    HOST_OS="linux"
    echo "⚠️  Unrecognized platform ($OS_NAME); defaulting to Linux tooling." >&2
    ;;
esac

# Small helper to surface missing tools with a CachyOS/Arch-friendly hint.
INSTALL_HINT="sudo apt-get install openjdk-${JDK_MAJOR}-jdk wget unzip zip git"
if command -v pacman >/dev/null 2>&1; then
  INSTALL_HINT="sudo pacman -S --needed jdk${JDK_MAJOR}-openjdk wget unzip zip git base-devel"
elif [ "$HOST_OS" = "windows" ]; then
  INSTALL_HINT="Install Java ${JDK_MAJOR} and tooling via winget or choco (e.g., winget install --id Microsoft.OpenJDK.${JDK_MAJOR} -e; winget install Git.Git 7zip.7zip). Ensure curl or wget and unzip are available."
fi

require_cmd() {
  local bin="$1"
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "❌ Missing dependency: $bin. Install with: $INSTALL_HINT" >&2
    exit 1
  fi
}

download() {
  local url="$1" dest="$2"
  if command -v wget >/dev/null 2>&1; then
    wget -q "$url" -O "$dest"
  elif command -v curl >/dev/null 2>&1; then
    curl -sSL "$url" -o "$dest"
  else
    echo "❌ Missing dependency: wget or curl. Install with: $INSTALL_HINT" >&2
    exit 1
  fi
}

require_cmd unzip
require_cmd zip
require_cmd java
require_cmd git

# Warn if Java is not the expected version
JAVA_VERSION_OUTPUT=$(java -version 2>&1 | head -n 1 || true)
if ! grep -qE '"'"${JDK_MAJOR}"'\.' <<<"$JAVA_VERSION_OUTPUT"; then
  echo "⚠️  Java ${JDK_MAJOR} recommended. Detected: $JAVA_VERSION_OUTPUT" >&2
fi

# ----- Android SDK + Build Tools -----
: "${TARGET_API:=35}"      # change to 36 if you want Android 16 preview
: "${BUILD_TOOLS:=35.0.0}" # latest stable
if [ "$HOST_OS" = "windows" ]; then
  DEFAULT_SDK_ROOT="${SDK_ROOT:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
  ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/.android}"
else
  DEFAULT_SDK_ROOT="${SDK_ROOT:-$HOME/android-sdk}"
  ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$DEFAULT_SDK_ROOT/.android}"
fi

SDK_ROOT="$DEFAULT_SDK_ROOT"
# If the default SDK_ROOT isn't writable (e.g., sandboxed $HOME), fall back to a local directory.
if ! mkdir -p "$SDK_ROOT" 2>/dev/null || ! touch "$SDK_ROOT/.rwtest" 2>/dev/null; then
  SDK_ROOT="$PWD/.android-sdk"
  mkdir -p "$SDK_ROOT"
  echo "ℹ️ Using SDK directory at $SDK_ROOT"
else
  rm -f "$SDK_ROOT/.rwtest" 2>/dev/null || true
fi
ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$SDK_ROOT/.android}"

export SDK_ROOT
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_HOME

TOOLS_ROOT="$SDK_ROOT/cmdline-tools"
TOOLS_LATEST="$TOOLS_ROOT/latest"
CMDLINE_OS_ID="linux"
if [ "$HOST_OS" = "windows" ]; then
  CMDLINE_OS_ID="win"
fi
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-${CMDLINE_OS_ID}-11076708_latest.zip"
SDK_MANAGER="$TOOLS_LATEST/bin/sdkmanager"
if [ ! -x "$SDK_MANAGER" ] && [ -x "${SDK_MANAGER}.bat" ]; then
  SDK_MANAGER="${SDK_MANAGER}.bat"
fi

if [ ! -x "$SDK_MANAGER" ]; then
  echo "Installing Android cmdline-tools..."
  mkdir -p "$TOOLS_ROOT"
  rm -f /tmp/cmdtools.zip
  download "$CMDLINE_TOOLS_URL" /tmp/cmdtools.zip
  unzip -q /tmp/cmdtools.zip -d "$TOOLS_ROOT"
  rm /tmp/cmdtools.zip
  mv "$TOOLS_ROOT/cmdline-tools" "$TOOLS_LATEST"
fi

export PATH="$TOOLS_LATEST/bin:$SDK_ROOT/platform-tools:$PATH"

# sdkmanager expects this file to exist; place it alongside the SDK to avoid $HOME permission issues.
if mkdir -p "$ANDROID_SDK_HOME" 2>/dev/null; then
  touch "$ANDROID_SDK_HOME/repositories.cfg" 2>/dev/null || true
fi

# Accept licenses and install required SDK components; handle sdkmanager closing stdin early.
echo "Accepting licenses..."
set +o pipefail
yes 2>/dev/null | "$SDK_MANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true
set -o pipefail

echo "Installing platform-tools, platform ${TARGET_API}, build-tools ${BUILD_TOOLS}..."
set +o pipefail
yes 2>/dev/null | "$SDK_MANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${TARGET_API}" \
  "build-tools;${BUILD_TOOLS}" || true
set -o pipefail

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
