# Android App Upgrade and Maintenance Plan

## Current Status (December 2025)

### Version Management Analysis

#### Files Where Versions Are Declared
1. **`gradle/libs.versions.toml`** - Primary version catalog (MAIN SOURCE OF TRUTH)
2. **`settings.gradle.kts`** - Plugin versions (DUPLICATE - should be removed)
3. **`app/build.gradle.kts`** - Hardcoded dependencies (should use catalog)
4. **`gradle/wrapper/gradle-wrapper.properties`** - Gradle wrapper version
5. **`CLAUDE.md`** - Documentation (reference only, not authoritative)

#### Identified Issues

##### 🔴 Critical Issues
1. **Duplicate Version Declarations**: Plugin versions declared in BOTH `settings.gradle.kts` AND `gradle/libs.versions.toml`
   - Kotlin: 2.2.21
   - KSP: 2.3.3
   - Hilt: 2.57.2
   - Android Gradle Plugin (AGP): 8.13.2

2. **Hardcoded Dependencies** in `app/build.gradle.kts`:
   - Line 113: `implementation("org.slf4j:slf4j-nop:2.0.17")` - not in catalog
   - Line 165: `testImplementation("app.cash.turbine:turbine:1.2.1")` - not in catalog
   - Lines 161-162, 168, 179-180: Hilt testing using `${libs.versions.hilt.get()}`

##### 🟡 Code TODOs Found
1. **SecureCredentialManager.kt:123-124**
   - TODO: Consider adding a user setting to enable/disable authentication requirement
   - TODO: Add support for biometric-only keys (API 30+) using BiometricPrompt

2. **BiometricAuthManager.kt:117-118**
   - TODO: Consider adding a user setting to choose security level vs. compatibility
   - TODO: Add warning UI for devices using BIOMETRIC_WEAK

---

## Dependency Upgrade Opportunities

### Core Dependencies (All Current)
| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| Kotlin | 2.2.21 | 2.2.21 | ✅ Up-to-date (2.3.0-RC3 available) |
| AGP | 8.13.2 | 8.13.2 | ✅ Up-to-date |
| Gradle Wrapper | 8.14.3 | 8.14.3 | ✅ Up-to-date |
| Compose BOM | 2025.12.00 | 2025.12.00 | ✅ Up-to-date |
| Core KTX | 1.17.0 | 1.17.0 | ✅ Up-to-date |
| Lifecycle | 2.10.0 | 2.10.0 | ✅ Up-to-date |
| Activity Compose | 1.12.1 | 1.12.1 | ✅ Up-to-date |

### Material 3 (Intentionally Alpha/Beta)
| Dependency | Current | Status |
|-----------|---------|--------|
| Material3 | 1.5.0-alpha10 | ✅ Intentional (alpha/beta by design) |
| Material3 Adaptive | 1.3.0-alpha05 | ✅ Intentional (alpha/beta by design) |
| Material3 Expressive | 1.5.0-alpha02 | ✅ Intentional (alpha/beta by design) |

### Other Libraries
| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| OkHttp | 5.3.2 | 5.3.2 | ✅ Up-to-date |
| Coil | 3.3.0 | 3.3.0 | ✅ Up-to-date |
| Hilt | 2.57.2 | 2.57.2 | ✅ Up-to-date |
| Media3 | 1.9.0-rc01 | 1.9.0-rc01 | ✅ Up-to-date (stable 1.9.0 not yet released) |
| Navigation | 2.9.6 | 2.9.6 | ✅ Up-to-date |
| Retrofit | 3.0.0 | 3.0.0 | ✅ Up-to-date |
| Coroutines | 1.10.2 | 1.10.2 | ✅ Up-to-date |
| Jellyfin SDK | 1.8.4 | 1.8.4 | ✅ Up-to-date |

**NOTE**: slf4j-nop (2.0.17) and Turbine (1.2.1) are hardcoded and should be added to version catalog.

---

## Cleanup and Consolidation Plan

### Phase 1: Consolidate Version Catalog (HIGH PRIORITY)
**Goal**: Single source of truth for all dependency versions

#### Actions:
1. ✅ Keep `gradle/libs.versions.toml` as the ONLY source for versions
2. ✅ Remove duplicate plugin versions from `settings.gradle.kts`
3. ✅ Add missing dependencies to version catalog:
   - slf4j-nop (2.0.17)
   - Turbine (1.2.1)
4. ✅ Update `app/build.gradle.kts` to use catalog references
5. ✅ Update CLAUDE.md to reflect single source of truth

### Phase 2: Simplify Build Configuration (MEDIUM PRIORITY)
**Goal**: Cleaner, more maintainable build files

#### Actions:
1. Convert all hardcoded versions in `app/build.gradle.kts` to catalog references
2. Ensure consistent dependency reference pattern
3. Remove unnecessary comments about versions
4. Document upgrade process in this file

### Phase 3: Address Code TODOs (LOW PRIORITY)
**Goal**: Complete pending features

#### Actions:
1. Implement user setting for biometric authentication requirements
2. Add biometric security level warnings in UI
3. Consider API 30+ biometric-only key support

---

## Upgrade Process Going Forward

### Where to Update Versions
**SINGLE SOURCE OF TRUTH**: `gradle/libs.versions.toml`

#### For Plugin Versions:
```toml
[versions]
agp = "8.13.2"          # Update here
kotlin = "2.2.21"       # Update here
ksp = "2.3.3"           # Update here
hilt = "2.57.2"         # Update here
```

#### For Library Versions:
```toml
[versions]
coil = "3.3.0"          # Update here
okhttp = "5.3.2"        # Update here
# etc...
```

#### For Gradle Wrapper:
Update in `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
```

### How to Check for Updates

#### 1. Manual Check (Recommended)
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Or manually check:
# - https://developer.android.com/jetpack/androidx/versions
# - https://github.com/JetBrains/kotlin/releases
# - https://square.github.io/okhttp/changelogs/changelog/
# - https://coil-kt.github.io/coil/changelog/
```

#### 2. Update Process
1. Check for new versions at official sources
2. Update version number in `gradle/libs.versions.toml`
3. Sync Gradle
4. Run tests: `./gradlew test`
5. Build app: `./gradlew assembleDebug`
6. Commit with message: `chore: update [dependency] to [version]`

### Important Notes
- **Material 3 Expressive**: Keep on alpha/beta intentionally (as noted by user)
- **Stable vs Preview**: Prefer stable unless specifically needed
- **Breaking Changes**: Always check release notes before major version bumps
- **Testing**: Run full test suite after any dependency update

---

## Quick Reference: Dependency Update Sources

### AndroidX Libraries
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/compose

### Kotlin & Gradle
- https://kotlinlang.org/docs/releases.html
- https://gradle.org/releases/
- https://developer.android.com/build/releases/gradle-plugin

### Third-Party Libraries
- **OkHttp**: https://square.github.io/okhttp/changelogs/changelog/
- **Coil**: https://coil-kt.github.io/coil/changelog/
- **Hilt**: https://github.com/google/dagger/releases
- **Retrofit**: https://github.com/square/retrofit/blob/master/CHANGELOG.md
- **Jellyfin SDK**: https://github.com/jellyfin/jellyfin-sdk-kotlin/releases

---

## Summary

### ✅ Good News
- All core dependencies are up-to-date
- No critical security vulnerabilities found
- Build configuration is mostly clean

### 🔧 Needs Cleanup
1. Remove duplicate plugin versions from `settings.gradle.kts`
2. Add missing dependencies to version catalog
3. Consolidate all version references

### 📋 Future Enhancements
1. Implement biometric authentication settings
2. Add security level warnings
3. Consider automated dependency update checks in CI/CD

---

**Last Updated**: December 2025
**Maintained By**: Automated analysis + manual review
**Next Review**: When adding new dependencies or quarterly
