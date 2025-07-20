# GitHub Actions CI/CD Documentation

## Overview

This repository includes several GitHub Actions workflows to ensure code quality, automated testing, and release management for the Jellyfin Android client.

## Workflows

### 1. `android-ci.yml` - Basic CI
**Triggers:** Push/PR to `main` or `develop` branches

**What it does:**
- ✅ Builds debug APK using `gradlew assembleDebug`
- ✅ Runs unit tests
- ✅ Uploads build artifacts
- ✅ Caches Gradle dependencies for faster builds

### 2. `android-ci-extended.yml` - Comprehensive CI
**Triggers:** Push/PR to `main` or `develop` branches, manual trigger

**What it does:**
- 🔍 **Lint Check** - Analyzes code for potential issues
- 🧪 **Unit Tests** - Runs all unit tests with detailed reporting
- 🏗️ **Build Matrix** - Builds both debug and release APKs
- 🔒 **Security Scan** - Trivy vulnerability scanning (main branch only)

### 3. `dependency-check.yml` - Dependency Management
**Triggers:** Weekly schedule (Mondays 9 AM UTC), manual trigger

**What it does:**
- 📦 Checks for dependency updates
- ✅ Validates Gradle wrapper integrity
- 📊 Generates dependency reports

### 4. `release.yml` - Automated Releases
**Triggers:** Git tags matching `v*.*.*`, manual trigger

**What it does:**
- 🚀 Builds release APK
- 📝 Creates GitHub release with changelog
- 📱 Uploads APK as release asset

## Build Requirements

- **JDK:** 17 (Temurin distribution)
- **Android SDK:** Latest (auto-installed)
- **Gradle:** Uses wrapper (gradlew)
- **Min SDK:** 31 (Android 12)
- **Target SDK:** 36

## Artifacts

### Build Artifacts
- `debug-apk` - Debug APK (30 days retention)
- `release-apk` - Release APK (30 days retention)

### Reports
- `lint-reports` - Code analysis reports (7 days)
- `build-reports` - Build failure reports (7 days)
- `dependency-updates-report` - Dependency update info (7 days)

## Status Badges

Add these to your main README.md:

```markdown
[![Android CI](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/android-ci.yml/badge.svg)](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/android-ci.yml)
[![Dependency Check](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/dependency-check.yml/badge.svg)](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/dependency-check.yml)
```

## Local Development

To run the same checks locally:

```bash
# Basic build check
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug

# Check dependencies
./gradlew dependencyUpdates
```

## Release Process

1. **Create a tag:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **Automatic release:** The workflow will automatically:
   - Build release APK
   - Create GitHub release
   - Upload APK asset

## Troubleshooting

### Common Issues

1. **Build fails with SDK not found**
   - The workflow sets up Android SDK automatically
   - Creates `local.properties` with correct SDK path

2. **Gradle permission denied**
   - Workflow includes `chmod +x gradlew` step

3. **Cache misses**
   - Gradle caching is configured to speed up builds
   - Cache key includes Gradle files hash

### Debugging

- Check the **Actions** tab in GitHub for detailed logs
- Download build reports from failed runs
- Use `--stacktrace` flag for detailed error information

## Security

- All workflows use pinned action versions for security
- Trivy scanner checks for vulnerabilities
- Gradle wrapper validation prevents tampering

## Future Enhancements

- 🔐 APK signing with upload keys
- 🧪 Instrumented tests with emulator
- 📊 Code coverage reporting
- 🚀 Play Store deployment
- 🔄 Automatic dependency updates with Dependabot
