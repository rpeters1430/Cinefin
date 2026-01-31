# Jellyfin Android Version Bump and Build Script
# This script increments versionCode, updates versionName, and builds the app

# Set strict mode for better error handling
$ErrorActionPreference = "Stop"

# Define file paths
$buildGradlePath = "app\build.gradle.kts"
$projectRoot = $PSScriptRoot

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Jellyfin Android Version Manager" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Check if build.gradle.kts exists
if (-not (Test-Path $buildGradlePath)) {
    Write-Host "ERROR: Cannot find $buildGradlePath" -ForegroundColor Red
    Write-Host "Make sure you're running this script from the project root directory." -ForegroundColor Yellow
    exit 1
}

# Read the current build.gradle.kts file
Write-Host "Reading current version information..." -ForegroundColor Yellow
$buildGradleContent = Get-Content $buildGradlePath -Raw

# Extract current versionCode using regex
if ($buildGradleContent -match 'versionCode\s*=\s*(\d+)') {
    $currentVersionCode = [int]$matches[1]
    Write-Host "Current versionCode: $currentVersionCode" -ForegroundColor Green
} else {
    Write-Host "ERROR: Could not find versionCode in build.gradle.kts" -ForegroundColor Red
    exit 1
}

# Extract current versionName using regex
if ($buildGradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $currentVersionName = $matches[1]
    Write-Host "Current versionName: $currentVersionName" -ForegroundColor Green
} else {
    Write-Host "ERROR: Could not find versionName in build.gradle.kts" -ForegroundColor Red
    exit 1
}

# Calculate new versionCode
$newVersionCode = $currentVersionCode + 1
Write-Host "`nNew versionCode will be: $newVersionCode" -ForegroundColor Cyan

# Prompt for new versionName
Write-Host "`nEnter new versionName (or press Enter to keep '$currentVersionName'): " -ForegroundColor Yellow -NoNewline
$newVersionName = Read-Host

if ([string]::IsNullOrWhiteSpace($newVersionName)) {
    $newVersionName = $currentVersionName
    Write-Host "Keeping current versionName: $currentVersionName" -ForegroundColor Green
} else {
    Write-Host "New versionName will be: $newVersionName" -ForegroundColor Cyan
}

# Confirm changes
Write-Host "`n----------------------------------------" -ForegroundColor Cyan
Write-Host "Version Changes Summary:" -ForegroundColor Cyan
Write-Host "  versionCode: $currentVersionCode -> $newVersionCode" -ForegroundColor Yellow
Write-Host "  versionName: $currentVersionName -> $newVersionName" -ForegroundColor Yellow
Write-Host "----------------------------------------`n" -ForegroundColor Cyan

Write-Host "Do you want to proceed with these changes? (Y/N): " -ForegroundColor Yellow -NoNewline
$confirm = Read-Host

if ($confirm -ne 'Y' -and $confirm -ne 'y') {
    Write-Host "`nOperation cancelled by user." -ForegroundColor Red
    exit 0
}

# Update the build.gradle.kts file
Write-Host "`nUpdating $buildGradlePath..." -ForegroundColor Yellow

# Replace versionCode
$buildGradleContent = $buildGradleContent -replace "versionCode\s*=\s*\d+", "versionCode = $newVersionCode"

# Replace versionName
$buildGradleContent = $buildGradleContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newVersionName`""

# Write the updated content back to the file
Set-Content -Path $buildGradlePath -Value $buildGradleContent -NoNewline

Write-Host "Version numbers updated successfully!" -ForegroundColor Green

# Ask what to build
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Build Options" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan
Write-Host "1) Build debug APK (assembleDebug)" -ForegroundColor White
Write-Host "2) Clean build (clean)" -ForegroundColor White
Write-Host "3) Build signed release bundle for Play Console (bundleRelease)" -ForegroundColor White
Write-Host "4) Build signed release APK (assembleRelease)" -ForegroundColor White
Write-Host "5) Skip build" -ForegroundColor White
Write-Host "`nEnter your choice (1-5): " -ForegroundColor Yellow -NoNewline
$buildChoice = Read-Host

# Define output paths
$debugApkPath = "app\build\outputs\apk\debug\app-debug.apk"
$releaseApkPath = "app\build\outputs\apk\release\app-release.apk"
$releaseBundlePath = "app\build\outputs\bundle\release\app-release.aab"

switch ($buildChoice) {
    '1' {
        Write-Host "`nBuilding debug APK..." -ForegroundColor Yellow
        & .\gradlew.bat assembleDebug

        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n========================================" -ForegroundColor Green
            Write-Host "  Build Successful!" -ForegroundColor Green
            Write-Host "========================================`n" -ForegroundColor Green

            if (Test-Path $debugApkPath) {
                $fullPath = Resolve-Path $debugApkPath
                $fileSize = (Get-Item $debugApkPath).Length / 1MB
                Write-Host "Debug APK location:" -ForegroundColor Cyan
                Write-Host "  $fullPath" -ForegroundColor White
                Write-Host "  Size: $([math]::Round($fileSize, 2)) MB`n" -ForegroundColor White
            }
        } else {
            Write-Host "`nBuild failed with exit code $LASTEXITCODE" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    }
    '2' {
        Write-Host "`nCleaning build..." -ForegroundColor Yellow
        & .\gradlew.bat clean

        if ($LASTEXITCODE -eq 0) {
            Write-Host "`nClean completed successfully!" -ForegroundColor Green
        } else {
            Write-Host "`nClean failed with exit code $LASTEXITCODE" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    }
    '3' {
        Write-Host "`nBuilding signed release bundle (AAB) for Play Console..." -ForegroundColor Yellow
        Write-Host "NOTE: This requires signing credentials to be configured.`n" -ForegroundColor Cyan

        & .\gradlew.bat bundleRelease

        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n========================================" -ForegroundColor Green
            Write-Host "  Build Successful!" -ForegroundColor Green
            Write-Host "========================================`n" -ForegroundColor Green

            if (Test-Path $releaseBundlePath) {
                $fullPath = Resolve-Path $releaseBundlePath
                $fileSize = (Get-Item $releaseBundlePath).Length / 1MB
                Write-Host "Release Bundle (AAB) location:" -ForegroundColor Cyan
                Write-Host "  $fullPath" -ForegroundColor White
                Write-Host "  Size: $([math]::Round($fileSize, 2)) MB`n" -ForegroundColor White
                Write-Host "This file is ready to upload to Google Play Console!" -ForegroundColor Green
            }
        } else {
            Write-Host "`nBuild failed with exit code $LASTEXITCODE" -ForegroundColor Red
            Write-Host "`nCommon issues:" -ForegroundColor Yellow
            Write-Host "  - Missing signing credentials in gradle.properties or environment variables" -ForegroundColor White
            Write-Host "  - Keystore file not found" -ForegroundColor White
            Write-Host "  - Incorrect keystore password" -ForegroundColor White
            exit $LASTEXITCODE
        }
    }
    '4' {
        Write-Host "`nBuilding signed release APK..." -ForegroundColor Yellow
        Write-Host "NOTE: This requires signing credentials to be configured.`n" -ForegroundColor Cyan

        & .\gradlew.bat assembleRelease

        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n========================================" -ForegroundColor Green
            Write-Host "  Build Successful!" -ForegroundColor Green
            Write-Host "========================================`n" -ForegroundColor Green

            if (Test-Path $releaseApkPath) {
                $fullPath = Resolve-Path $releaseApkPath
                $fileSize = (Get-Item $releaseApkPath).Length / 1MB
                Write-Host "Release APK location:" -ForegroundColor Cyan
                Write-Host "  $fullPath" -ForegroundColor White
                Write-Host "  Size: $([math]::Round($fileSize, 2)) MB`n" -ForegroundColor White
            }
        } else {
            Write-Host "`nBuild failed with exit code $LASTEXITCODE" -ForegroundColor Red
            Write-Host "`nCommon issues:" -ForegroundColor Yellow
            Write-Host "  - Missing signing credentials in gradle.properties or environment variables" -ForegroundColor White
            Write-Host "  - Keystore file not found" -ForegroundColor White
            Write-Host "  - Incorrect keystore password" -ForegroundColor White
            exit $LASTEXITCODE
        }
    }
    '5' {
        Write-Host "`nSkipping build. Version numbers have been updated." -ForegroundColor Green
    }
    default {
        Write-Host "`nInvalid choice. Skipping build. Version numbers have been updated." -ForegroundColor Yellow
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Script Complete" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan
