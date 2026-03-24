#!/usr/bin/env bash
set -euo pipefail

JDK_MAJOR=21

OS_UNAME="$(uname -s)"
OS_NAME="$(printf "%s" "$OS_UNAME" | tr '[:upper:]' '[:lower:]')"

# Host OS detection
case "$OS_NAME" in
  linux*) HOST_OS="linux" ;;
  darwin*) HOST_OS="macos" ;;
  msys*|mingw*|cygwin*) HOST_OS="windows" ;;
  *)
    HOST_OS="linux"
    echo "⚠️  Unrecognized platform ($OS_UNAME); defaulting to Linux tooling." >&2
    ;;
esac

# Small helper to surface missing tools with platform-friendly hints.
INSTALL_HINT="sudo apt-get install openjdk-${JDK_MAJOR}-jdk wget unzip zip git"
if command -v dnf >/dev/null 2>&1; then
  INSTALL_HINT="sudo dnf install -y java-${JDK_MAJOR}-openjdk java-${JDK_MAJOR}-openjdk-devel wget unzip zip git"
elif command -v pacman >/dev/null 2>&1; then
  INSTALL_HINT="sudo pacman -S --needed jdk${JDK_MAJOR}-openjdk wget unzip zip git base-devel"
elif [ "$HOST_OS" = "macos" ]; then
  INSTALL_HINT="brew install openjdk@${JDK_MAJOR} wget unzip zip git"
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

# ----- macOS: ensure Homebrew basics (optional auto-install) -----
if [ "$HOST_OS" = "macos" ]; then
  if ! command -v brew >/dev/null 2>&1; then
    echo "❌ Homebrew not found. Install it from https://brew.sh and re-run." >&2
    exit 1
  fi

  # Try to ensure core tools exist (non-fatal if already installed)
  # We do not force-link Java here; we just ensure it's installed.
  brew update >/dev/null 2>&1 || true
  brew install wget unzip zip git "openjdk@${JDK_MAJOR}" >/dev/null 2>&1 || true

  # If java is missing but brew installed it, hint how to expose it
  if ! command -v java >/dev/null 2>&1; then
    echo "⚠️  Java is installed via Homebrew but not on PATH."
    echo "    Run one of these then re-run:"
    echo "      sudo ln -sfn \"$(brew --prefix)/opt/openjdk@${JDK_MAJOR}/libexec/openjdk.jdk\" /Library/Java/JavaVirtualMachines/openjdk-${JDK_MAJOR}.jdk"
    echo "    OR add to shell profile:"
    echo "      export PATH=\"$(brew --prefix)/opt/openjdk@${JDK_MAJOR}/bin:\$PATH\""
    # continue; require_cmd java below will stop if still missing
  fi
fi

require_cmd unzip
require_cmd zip
require_cmd java
require_cmd git

# Warn if Java is not the expected version
JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1 || true)"
if ! grep -qE "\"${JDK_MAJOR}\." <<<"$JAVA_VERSION_OUTPUT"; then
  echo "⚠️  Java ${JDK_MAJOR} recommended. Detected: $JAVA_VERSION_OUTPUT" >&2
fi

# ----- Android SDK + Build Tools -----
: "${TARGET_API:=35}"      # change to 36 if you want Android 16 preview
: "${BUILD_TOOLS:=35.0.0}" # latest stable

if [ "$HOST_OS" = "windows" ]; then
  DEFAULT_SDK_ROOT="${SDK_ROOT:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
  ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/.android}"
elif [ "$HOST_OS" = "macos" ]; then
  # Android Studio default location on macOS:
  DEFAULT_SDK_ROOT="${SDK_ROOT:-$HOME/Library/Android/sdk}"
  ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$HOME/.android}"
else
  DEFAULT_SDK_ROOT="${SDK_ROOT:-$HOME/android-sdk}"
  ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$DEFAULT_SDK_ROOT/.android}"
fi

SDK_ROOT="$DEFAULT_SDK_ROOT"

# If the default SDK_ROOT isn't writable, fall back to a local directory.
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

# cmdline-tools zip OS id
CMDLINE_OS_ID="linux"
if [ "$HOST_OS" = "windows" ]; then
  CMDLINE_OS_ID="win"
elif [ "$HOST_OS" = "macos" ]; then
  CMDLINE_OS_ID="mac"
fi

CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-${CMDLINE_OS_ID}-11076708_latest.zip"

SDK_MANAGER="$TOOLS_LATEST/bin/sdkmanager"
if [ ! -x "$SDK_MANAGER" ] && [ -x "${SDK_MANAGER}.bat" ]; then
  SDK_MANAGER="${SDK_MANAGER}.bat"
fi

if [ ! -x "$SDK_MANAGER" ]; then
  echo "Installing Android cmdline-tools..."
  mkdir -p "$TOOLS_ROOT"

  # Use a per-OS tmp path
  TMP_ZIP="${TMPDIR:-/tmp}/cmdtools.zip"
  rm -f "$TMP_ZIP" 2>/dev/null || true

  download "$CMDLINE_TOOLS_URL" "$TMP_ZIP"
  unzip -q "$TMP_ZIP" -d "$TOOLS_ROOT"
  rm -f "$TMP_ZIP" 2>/dev/null || true

  # Google’s zip contains cmdline-tools/; we want cmdline-tools/latest/
  rm -rf "$TOOLS_LATEST" 2>/dev/null || true
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
  LOCAL_PROPERTIES_FILE="$PROJECT_DIR/local.properties"
  TMP_FILE="$(mktemp)"

  if [ -f "$LOCAL_PROPERTIES_FILE" ]; then
    # Strip BOM, convert CRLF to LF, and remove existing sdk.dir
    sed -i '1s/^\xEF\xBB\xBF//' "$LOCAL_PROPERTIES_FILE" 2>/dev/null || true
    sed -i 's/\r$//' "$LOCAL_PROPERTIES_FILE" 2>/dev/null || true
    grep -v '^sdk\.dir=' "$LOCAL_PROPERTIES_FILE" > "$TMP_FILE" || true
  fi

  {
    echo "sdk.dir=$SDK_ROOT"
    cat "$TMP_FILE" 2>/dev/null || true
  } > "$LOCAL_PROPERTIES_FILE"

  if ! grep -q '^GOOGLE_AI_API_KEY=' "$LOCAL_PROPERTIES_FILE"; then
    {
      echo ""
      echo "## Google AI API key for Gemini cloud fallback"
      echo "## Get your key from: https://aistudio.google.com/apikey"
      echo "GOOGLE_AI_API_KEY="
    } >> "$LOCAL_PROPERTIES_FILE"
  fi

  rm -f "$TMP_FILE"
fi

echo "✅ Android SDK ready (API $TARGET_API, Build-Tools $BUILD_TOOLS)"
