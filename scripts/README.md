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

### `release-android.sh` (Linux/macOS)

Automates the full Android release flow for this repository.

**Features:**
- Increments `versionCode` by 1
- Increments the trailing numeric segment of `versionName` by 1
- Commits the version bump
- Builds a signed release bundle with `./gradlew bundleRelease`
- Creates an annotated git tag like `v14.57`
- Pushes the branch and tag
- Publishes a GitHub release with generated release notes and uploads the AAB

**Usage:**

```bash
./scripts/release-android.sh
```

Non-interactive:

```bash
./scripts/release-android.sh --yes
```

Useful options:

```bash
./scripts/release-android.sh --skip-push --skip-release
./scripts/release-android.sh --dry-run
./scripts/release-android.sh --no-changelog
./scripts/release-android.sh --tag-prefix release-
```

**Requirements:**

- `gh` authenticated to the target GitHub repo
- A working git remote named `origin`
- Signing credentials configured through `local.properties`, `gradle.properties`, or environment variables:
  - `JELLYFIN_KEYSTORE_FILE`
  - `JELLYFIN_KEYSTORE_PASSWORD`
  - `JELLYFIN_KEY_ALIAS`
  - `JELLYFIN_KEY_PASSWORD`

`--dry-run` shows the exact version bump, tag, and generated GitHub changelog preview without changing files or creating a release.
If the tree is dirty during a real run, the script shows the changed files, asks whether they should be included, then bumps the versions, creates the release commit, and only after that runs the signed bundle build.

## Other Scripts

### `gen-local-properties.ps1` / `gen-local-properties.sh`

Generates or updates `local.properties` with Android SDK path from environment variables. Used primarily in CI/CD environments.
Preserves existing entries (such as `GOOGLE_AI_API_KEY` and signing keys) and adds a `GOOGLE_AI_API_KEY=` placeholder if missing.

**Windows:**
```powershell
.\scripts\gen-local-properties.ps1
```

**Linux/macOS:**
```bash
./scripts/gen-local-properties.sh
```
