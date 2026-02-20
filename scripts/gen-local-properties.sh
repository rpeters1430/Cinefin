#!/usr/bin/env bash
set -euo pipefail

# Generates a local.properties pointing to an Android SDK.
# Priority: ANDROID_SDK_ROOT > ANDROID_HOME

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [ -z "${SDK_DIR}" ]; then
  echo "ERROR: ANDROID_SDK_ROOT or ANDROID_HOME is not set." >&2
  echo "Set ANDROID_SDK_ROOT to your SDK path, then rerun." >&2
  exit 1
fi

echo "sdk.dir=${SDK_DIR}" > local.properties
echo "Wrote local.properties with sdk.dir=${SDK_DIR}"

