<#
.SYNOPSIS
    Setup script for Cinefin Android development on Windows.
    This script verifies/installs JDK 21, Android SDK, and configures local.properties.

.DESCRIPTION
    1. Checks for Java 21.
    2. Locates or installs Android SDK Command-line Tools.
    3. Installs required Android SDK components (API 35, Build-tools 35.0.0).
    4. Configures local.properties with the SDK path.
#>

$JDK_VERSION = "21"
$ANDROID_API = "35"
$BUILD_TOOLS = "35.0.0"

Write-Host "--- Cinefin Windows Setup ---" -ForegroundColor Cyan

# 1. Check for Java
Write-Host "[1/4] Checking Java..." -ForegroundColor Yellow
$javaBinary = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaBinary) {
    Write-Host "❌ Java not found. Please install JDK $JDK_VERSION." -ForegroundColor Red
    Write-Host "   Hint: winget install Microsoft.OpenJDK.$JDK_VERSION" -ForegroundColor Gray
    exit 1
}

$javaVersion = & java -version 2>&1 | Out-String
if ($javaVersion -notmatch "version `"$JDK_VERSION") {
    Write-Host "⚠️  Warning: Expected JDK $JDK_VERSION, but found a different version." -ForegroundColor Yellow
    Write-Host "Detected: $javaVersion" -ForegroundColor Gray
} else {
    Write-Host "✅ Java $JDK_VERSION detected." -ForegroundColor Green
}

# 2. Locate Android SDK
Write-Host "[2/4] Locating Android SDK..." -ForegroundColor Yellow
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) { $sdkPath = $env:ANDROID_SDK_ROOT }
if (-not $sdkPath) {
    $defaultPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultPath) {
        $sdkPath = $defaultPath
    }
}

if (-not $sdkPath) {
    Write-Host "❓ Android SDK not found. Would you like to install it to $HOME\Android\Sdk? (y/n)" -ForegroundColor Cyan
    $choice = Read-Host
    if ($choice -eq 'y') {
        $sdkPath = Join-Path $HOME "Android\Sdk"
        New-Item -ItemType Directory -Force -Path $sdkPath | Out-Null
    } else {
        Write-Host "❌ Setup aborted. Please set ANDROID_HOME environment variable." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Using SDK Path: $sdkPath" -ForegroundColor Gray
$env:ANDROID_HOME = $sdkPath
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkPath, "User")

# 3. Setup Command-line Tools & SDK Components
Write-Host "[3/4] Ensuring SDK components..." -ForegroundColor Yellow

$cmdLineToolsPath = Join-Path $sdkPath "cmdline-tools\latest\bin"
$sdkManager = Join-Path $cmdLineToolsPath "sdkmanager.bat"

if (-not (Test-Path $sdkManager)) {
    Write-Host "Installing Android Command-line Tools..." -ForegroundColor Cyan
    $tempZip = Join-Path $env:TEMP "cmdline-tools.zip"
    $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    
    Write-Host "Downloading tools from Google..." -ForegroundColor Gray
    Invoke-WebRequest -Uri $url -OutFile $tempZip
    
    $extractPath = Join-Path $sdkPath "temp_cmdline"
    Expand-Archive -Path $tempZip -DestinationPath $extractPath -Force
    
    $finalToolsPath = Join-Path $sdkPath "cmdline-tools"
    if (-not (Test-Path $finalToolsPath)) { New-Item -ItemType Directory -Path $finalToolsPath }
    
    # Google's zip has 'cmdline-tools' folder inside. We want it at 'cmdline-tools/latest'
    Move-Item -Path (Join-Path $extractPath "cmdline-tools") -Destination (Join-Path $finalToolsPath "latest") -Force
    Remove-Item -Path $extractPath -Recurse -Force
    Remove-Item -Path $tempZip -Force
}

Write-Host "Accepting licenses..." -ForegroundColor Gray
$null = "y`ny`ny`ny`ny`ny`ny`n" | & $sdkManager --licenses --sdk_root=$sdkPath

Write-Host "Installing Platform $ANDROID_API and Build-Tools $BUILD_TOOLS..." -ForegroundColor Gray
& $sdkManager --sdk_root=$sdkPath "platform-tools" "platforms;android-$ANDROID_API" "build-tools;$BUILD_TOOLS"

# 4. Configure local.properties
Write-Host "[4/4] Configuring local.properties..." -ForegroundColor Yellow
$scriptDir = Split-Path -Parent $PSCommandPath
$projectRoot = Split-Path -Parent $scriptDir
$localProperties = Join-Path $projectRoot "local.properties"

# In .properties files, backslashes must be escaped, and colons in paths often are too.
$sdkPathEscaped = $sdkPath.Replace('\', '\\').Replace(':', '\:')
$content = @()

if (Test-Path $localProperties) {
    $content = Get-Content $localProperties | Where-Object { $_ -notmatch "^sdk\.dir=" }
}

$newContent = @("sdk.dir=$sdkPathEscaped") + $content

# Check for GOOGLE_AI_API_KEY
$hasKey = $newContent | Where-Object { $_ -match "^GOOGLE_AI_API_KEY=" }
if (-not $hasKey) {
    $newContent += ""
    $newContent += "## Google AI API key for Gemini cloud fallback"
    $newContent += "## Get your key from: https://aistudio.google.com/apikey"
    $newContent += "GOOGLE_AI_API_KEY="
}

# Check for Signing Config
$hasKeystore = $newContent | Where-Object { $_ -match "^JELLYFIN_KEYSTORE_FILE=" }
if (-not $hasKeystore) {
    $newContent += ""
    $newContent += "## Release Signing Config (optional, for release builds)"
    $newContent += "JELLYFIN_KEYSTORE_FILE=C\:\\Users\\James\\Documents\\jellyfinryan-upload.jks"
    $newContent += "JELLYFIN_KEYSTORE_PASSWORD="
    $newContent += "JELLYFIN_KEY_ALIAS="
    $newContent += "JELLYFIN_KEY_PASSWORD="
} else {
    # If it exists, ensure it has the correct path (James' specific path)
    $newContent = $newContent | ForEach-Object {
        if ($_ -match "^JELLYFIN_KEYSTORE_FILE=") {
            "JELLYFIN_KEYSTORE_FILE=C\:\\Users\\James\\Documents\\jellyfinryan-upload.jks"
        } else {
            $_
        }
    }
}

$newContent | Set-Content -Path $localProperties -Encoding UTF8

# 5. Verify Build Environment
Write-Host "[5/5] Verifying build environment..." -ForegroundColor Yellow
$gradlew = Join-Path $projectRoot "gradlew.bat"
if (Test-Path $gradlew) {
    Write-Host "Running gradlew --version..." -ForegroundColor Gray
    Push-Location $projectRoot
    try {
        & .\gradlew.bat --version
        Write-Host "✅ Build environment verified!" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Gradle check failed, but SDK is setup. You might need to check your JDK installation." -ForegroundColor Yellow
    }
    Pop-Location
}

Write-Host "`n✅ Setup Complete!" -ForegroundColor Green
Write-Host "You can now build the project using: .\gradlew.bat assembleDebug" -ForegroundColor Cyan

