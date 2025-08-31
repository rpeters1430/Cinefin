#!/usr/bin/env pwsh
param()

$sdkDir = $env:ANDROID_SDK_ROOT
if (-not $sdkDir -or $sdkDir -eq "") {
  $sdkDir = $env:ANDROID_HOME
}

if (-not $sdkDir -or $sdkDir -eq "") {
  Write-Error "ERROR: ANDROID_SDK_ROOT or ANDROID_HOME is not set."
  exit 1
}

"sdk.dir=$sdkDir" | Out-File -FilePath local.properties -Encoding UTF8 -NoNewline
Write-Host "Wrote local.properties with sdk.dir=$sdkDir"

