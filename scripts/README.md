# Scripts Directory

This directory contains utility scripts for the Jellyfin Android project.

## Version Management & Build Script

### `bump-version.ps1` (Windows PowerShell)

Automates version bumping and building for releases.

**Features:**
- Automatically increments `versionCode` by 1
- Prompts for new `versionName` (with current value displayed)
- Updates `app/build.gradle.kts` with new version numbers
- Offers multiple build options:
  - Build debug APK
  - Clean build
  - Build signed release bundle (AAB) for Play Console
  - Build signed release APK
  - Skip build (just update version numbers)
- Shows output file location and size after successful build

**Usage:**

From the project root directory, run:

```powershell
.\scripts\bump-version.ps1
```

Or from PowerShell with execution policy restrictions:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bump-version.ps1
```

**Requirements:**

- PowerShell (included with Windows)
- For release builds: Signing credentials configured in `gradle.properties` or environment variables:
  - `JELLYFIN_KEYSTORE_FILE`
  - `JELLYFIN_KEYSTORE_PASSWORD`
  - `JELLYFIN_KEY_ALIAS`
  - `JELLYFIN_KEY_PASSWORD`

**Example Flow:**

```
========================================
  Jellyfin Android Version Manager
========================================

Reading current version information...
Current versionCode: 26
Current versionName: 13.94

New versionCode will be: 27

Enter new versionName (or press Enter to keep '13.94'): 13.95

----------------------------------------
Version Changes Summary:
  versionCode: 26 -> 27
  versionName: 13.94 -> 13.95
----------------------------------------

Do you want to proceed with these changes? (Y/N): y

Updating app\build.gradle.kts...
Version numbers updated successfully!

========================================
  Build Options
========================================

1) Build debug APK (assembleDebug)
2) Clean build (clean)
3) Build signed release bundle for Play Console (bundleRelease)
4) Build signed release APK (assembleRelease)
5) Skip build

Enter your choice (1-5): 3

Building signed release bundle (AAB) for Play Console...
...
```

## Other Scripts

### `gen-local-properties.ps1` / `gen-local-properties.sh`

Generates `local.properties` file with Android SDK path from environment variables. Used primarily in CI/CD environments.

**Windows:**
```powershell
.\scripts\gen-local-properties.ps1
```

**Linux/macOS:**
```bash
./scripts/gen-local-properties.sh
```
