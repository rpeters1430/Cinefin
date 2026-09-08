# GitHub Actions CI/CD Documentation

## Overview

This repository includes GitHub Actions workflows for Android build verification, dependency monitoring, and repository automation.

## Workflows

### 1. `android-ci.yml` - Android CI
**Triggers:** Push/PR to `main` or `develop` branches

**What it does:**
- ✅ Builds debug APK using `:app:assembleDebug`
- ✅ Runs unit tests with `:app:testDebugUnitTest`
- ✅ Runs lint with `:app:lintDebug`
- ✅ Uploads APK + test/lint reports as artifacts
- ✅ Caches Gradle dependencies for faster builds

### 3. `dependency-check.yml` - Dependency Management
**Triggers:** Push/PR to `main` or `develop`, weekly schedule

**What it does:**
- 📦 Generates dependency graph/report for the app runtime classpath
- 🔎 Runs GitHub dependency review on pull requests

### 4. `gemini.yml` - Gemini CLI automation
**Triggers:** New/reopened issues, non-draft PR updates, maintainer commands and manual dispatch.

Uses code-review/security extensions and Android/Firebase skills. Validates model
output before posting triage comments, PR reviews and allowed labels. See
[GEMINI_CLI_USAGE.md](GEMINI_CLI_USAGE.md) for setup and commands.

### 5. `workflow-validation.yml` - Automation validation
Checks workflow syntax with actionlint and tests routing/publication safeguards.

## Build Requirements

- **JDK:** 21 (Temurin distribution)
- **Android SDK:** Latest (auto-installed)
- **Gradle:** Uses wrapper (gradlew)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35
- **Compile SDK:** 36

## Artifacts

### Build Artifacts
- `debug-apk` - Debug APK

### Reports
- `lint-reports` - Code analysis reports
- `unit-test-reports` - JVM test outputs
- `dependency-report` - Dependency classpath report

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

2. **CI validates each push/PR:** Android CI builds, tests, and lints automatically.

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

- 🧪 Instrumented tests with emulator in CI
- 📊 Code coverage reporting upload
- 🚀 Release workflow for tags
- 🔄 Automatic dependency update PRs
