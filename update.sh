#!/usr/bin/env bash
set -euo pipefail

JDK_MAJOR=21
OS_NAME=$(uname -s | tr '[:upper:]' '[:lower:]')

case "$OS_NAME" in
  linux*) HOST_OS="linux" ;;
  darwin*) HOST_OS="macos" ;;
  msys*|mingw*|cygwin*) HOST_OS="windows" ;;
  *)
    HOST_OS="linux"
    echo "⚠️  Unrecognized platform ($OS_NAME); defaulting to Linux tooling." >&2
    ;;
esac

install_linux() {
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y "openjdk-${JDK_MAJOR}-jdk" wget unzip zip git
  elif command -v pacman >/dev/null 2>&1; then
    sudo pacman -Syu --needed "jdk${JDK_MAJOR}-openjdk" wget unzip zip git base-devel
  else
    echo "❌ Unsupported Linux package manager. Install JDK ${JDK_MAJOR}, git, wget, unzip, and zip manually." >&2
    exit 1
  fi
}

install_macos() {
  if ! command -v brew >/dev/null 2>&1; then
    echo "❌ Homebrew is required on macOS. Install it from https://brew.sh and re-run this script." >&2
    exit 1
  fi

  brew update
  brew install "openjdk@${JDK_MAJOR}" wget unzip zip git
  brew install --cask android-commandlinetools

  JAVA_BIN_ROOT="$(brew --prefix)/opt/openjdk@${JDK_MAJOR}/bin"
  ANDROID_CMDLINE_ROOT="$(brew --prefix)/share/android-commandlinetools"

  echo ""
  echo "➡️  Add Homebrew OpenJDK to your PATH if it is not picked up automatically:"
  echo "    export PATH=\"${JAVA_BIN_ROOT}:\$PATH\""
  if [ -d "$ANDROID_CMDLINE_ROOT" ]; then
    echo "➡️  Android command-line tools installed via Homebrew. Add them to your PATH:"
    echo "    export PATH=\"${ANDROID_CMDLINE_ROOT}/bin:\$PATH\""
  fi
}

install_windows() {
  echo "ℹ️  On Windows, install Java ${JDK_MAJOR}, git, wget/curl, unzip, and zip via winget or chocolatey." >&2
}

echo "Detected host: $HOST_OS"
case "$HOST_OS" in
  linux) install_linux ;;
  macos) install_macos ;;
  windows) install_windows ;;
esac

if [ -x "./setup.sh" ]; then
  echo ""
  echo "✅ System prerequisites installed. Run ./setup.sh to download or update the Android SDK."
fi
