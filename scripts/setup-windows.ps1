# Setup script for Cinefin Android development on Windows
$JDK_VERSION = "21"
$ANDROID_API = "37"
$BUILD_TOOLS = "36.0.0"

Write-Host "`n--- Cinefin Windows Setup ---" -ForegroundColor Cyan

# 1. Check for Java
Write-Host "[1/5] Checking Java..." -ForegroundColor Yellow
$javaBinary = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaBinary) {
    Write-Host "Java not found. Please install JDK $JDK_VERSION." -ForegroundColor Red
    exit 1
}

$javaVersion = & java -version 2>&1 | Out-String
if ($javaVersion -notmatch "version `"$JDK_VERSION") {
    Write-Host "Warning: Expected JDK $JDK_VERSION, but found a different version." -ForegroundColor Yellow
} else {
    Write-Host "Java $JDK_VERSION detected." -ForegroundColor Green
}

# 2. Locate/Prepare Android SDK
Write-Host "[2/5] Locating Android SDK..." -ForegroundColor Yellow
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) { $sdkPath = $env:ANDROID_SDK_ROOT }
if (-not $sdkPath) {
    $defaultPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultPath) { $sdkPath = $defaultPath }
}
if (-not $sdkPath) {
    $sdkPath = Join-Path $HOME "Android\Sdk"
    Write-Host "Android SDK not found. Installing to $sdkPath..." -ForegroundColor Cyan
    if (-not (Test-Path $sdkPath)) {
        New-Item -ItemType Directory -Force -Path $sdkPath | Out-Null
    }
}
Write-Host "Using SDK Path: $sdkPath" -ForegroundColor Gray
$env:ANDROID_HOME = $sdkPath
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkPath, "User")

# 3. Setup Command-line Tools
Write-Host "[3/5] Ensuring SDK components..." -ForegroundColor Yellow
$cmdLineToolsPath = Join-Path $sdkPath "cmdline-tools\latest\bin"
$sdkManager = Join-Path $cmdLineToolsPath "sdkmanager.bat"
if (-not (Test-Path $sdkManager)) {
    Write-Host "Installing Android Command-line Tools..." -ForegroundColor Cyan
    $tempZip = Join-Path $env:TEMP "cmdline-tools.zip"
    $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    Invoke-WebRequest -Uri $url -OutFile $tempZip
    $extractPath = Join-Path $sdkPath "temp_cmdline"
    Expand-Archive -Path $tempZip -DestinationPath $extractPath -Force
    $finalToolsPath = Join-Path $sdkPath "cmdline-tools"
    if (-not (Test-Path $finalToolsPath)) { New-Item -ItemType Directory -Path $finalToolsPath | Out-Null }
    $latestPath = Join-Path $finalToolsPath "latest"
    if (Test-Path $latestPath) { Remove-Item -Path $latestPath -Recurse -Force }
    Move-Item -Path (Join-Path $extractPath "cmdline-tools") -Destination $latestPath -Force
    Remove-Item -Path $extractPath -Recurse -Force
    Remove-Item -Path $tempZip -Force
}
Write-Host "Accepting licenses..." -ForegroundColor Gray
$licenseInput = "y`ny`ny`ny`ny`ny`ny`n"
$licenseInput | & $sdkManager --licenses --sdk_root=$sdkPath
Write-Host "Installing Platform $ANDROID_API and Build-Tools $BUILD_TOOLS..." -ForegroundColor Gray
& $sdkManager --sdk_root=$sdkPath "platform-tools" "platforms;android-$ANDROID_API" "build-tools;$BUILD_TOOLS"

# 4. Configure local.properties
Write-Host "[4/5] Configuring local.properties..." -ForegroundColor Yellow
$projectRoot = Get-Location
$localProperties = Join-Path $projectRoot "local.properties"
$sdkPathEscaped = $sdkPath.Replace('\', '/')
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
    $newContent += "GOOGLE_AI_API_KEY="
}
$newContent | Set-Content -Path $localProperties -Encoding UTF8

# 5. Verify Build Environment
Write-Host "[5/5] Verifying build environment..." -ForegroundColor Yellow
$gradlew = Join-Path $projectRoot "gradlew.bat"
if (Test-Path $gradlew) {
    Write-Host "Running gradlew --version..." -ForegroundColor Gray
    try {
        & .\gradlew.bat --version
        Write-Host "Build environment verified!" -ForegroundColor Green
    } catch {
        Write-Host "Gradle check failed, but SDK is setup." -ForegroundColor Yellow
    }
}
Write-Host "`nSetup Complete!" -ForegroundColor Green
Write-Host "You can now build the project using: .\gradlew.bat assembleDebug" -ForegroundColor Cyan
