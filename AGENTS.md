# Repository Guidelines

## Project Structure & Module Organization
- Single Gradle module: `:app` (Kotlin + Compose).
- Source: `app/src/main/java/com/rpeters/jellyfin/...`
- UI/resources: `app/src/main/res`, manifest: `app/src/main/AndroidManifest.xml`.
- Tests: JVM unit tests in `app/src/test/java`, instrumentation tests in `app/src/androidTest/java`.
- Build config: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, versions in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- Build debug APK: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).
- Install on device: `./gradlew installDebug`.
- Unit tests (JVM): `./gradlew testDebugUnitTest`.
- Instrumentation tests: `./gradlew connectedAndroidTest` (emulator/device required; uses `HiltTestRunner`).
- Lint (Android Lint): `./gradlew lintDebug`.
- Coverage report (JaCoCo): `./gradlew jacocoTestReport` → HTML/XML under `app/build/reports`.

## Coding Style & Naming Conventions
- Kotlin conventions, 4‑space indent, max line ~120.
- Names: Classes `PascalCase`, functions/vars `camelCase`, constants `UPPER_SNAKE_CASE`.
- Packages under `com.rpeters.jellyfin`.
- UI with Jetpack Compose + Material 3; follow unidirectional data flow and MVVM; DI via Hilt.

## Testing Guidelines
- Frameworks: JUnit4, MockK, Turbine, AndroidX Test; Hilt testing set up.
- Focus on ViewModel/Repository logic; mock network and I/O.
- Naming: prefer descriptive test names (e.g., `fun loadMovieDetails_updates_state_on_success()`).
- Coverage: generate with `jacocoTestReport`; avoid counting generated/DI classes (already filtered).

## Commit & Pull Request Guidelines
- Commits: Conventional Commits (e.g., `feat:`, `fix:`, `docs:`); small, focused changes.
- Branches: `feature/...`, `bugfix/...`, `hotfix/...`, `docs/...`.
- PRs: clear description, link issues, include screenshots/GIFs for UI changes, and list test coverage/affected areas. Ensure `testDebugUnitTest` and `lintDebug` pass.

## Security & Configuration Tips
- Do not commit secrets/keystores; use Android Keystore/Encrypted storage (`data/` managers).
- Network config: see `app/src/main/res/xml/network_security_config.xml`.
- Minimum SDK 26, Java 17 with desugaring enabled; keep versions in `libs.versions.toml` aligned.
