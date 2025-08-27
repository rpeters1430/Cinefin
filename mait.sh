#!/usr/bin/env bash
set -euo pipefail

export ANDROID_SDK_ROOT=$HOME/android-sdk
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH

yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null
yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platform-tools"
