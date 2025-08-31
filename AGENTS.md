# Repository Guidelines

## Project Structure & Module Organization
- Single Gradle module: `:app` (Kotlin + Jetpack Compose).
- Source code: `app/src/main/java/com/rpeters/jellyfin/...`
- UI/resources: `app/src/main/res`; manifest: `app/src/main/AndroidManifest.xml`.
- Unit tests (JVM): `app/src/test/java`; instrumentation tests: `app/src/androidTest/java`.
- Build config: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`; versions in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- Build debug APK: `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug`.
- Install on device: `./gradlew installDebug` — deploys the debug APK.
- JVM unit tests: `./gradlew testDebugUnitTest` — runs JUnit tests on the JVM.
- Instrumentation tests: `./gradlew connectedAndroidTest` — requires emulator/device; uses `HiltTestRunner`.
- Lint: `./gradlew lintDebug` — Android Lint report under `app/build/reports/lint`.
- Coverage: `./gradlew jacocoTestReport` — HTML/XML under `app/build/reports`.

## Coding Style & Naming Conventions
- Kotlin style, 4‑space indent, ~120‑char lines.
- Names: Classes `PascalCase`; functions/vars `camelCase`; constants `UPPER_SNAKE_CASE`.
- Packages under `com.rpeters.jellyfin`.
- UI: Compose + Material 3; unidirectional data flow, MVVM; DI via Hilt.

## Testing Guidelines
- Frameworks: JUnit4, MockK, Turbine, AndroidX Test; Hilt testing configured.
- Focus on ViewModel/Repository logic; mock network/I/O boundaries.
- Test names: prefer descriptive functions (e.g., `loadMovieDetails_updates_state_on_success`).
- Run: unit — `testDebugUnitTest`; instrumentation — `connectedAndroidTest`.
- Coverage: `jacocoTestReport`; generated/DI classes are already filtered.

## Commit & Pull Request Guidelines
- Commits: Conventional Commits (e.g., `feat:`, `fix:`, `docs:`); keep changes small and focused.
- Branches: `feature/...`, `bugfix/...`, `hotfix/...`, `docs/...`.
- PRs: clear description, linked issues, screenshots/GIFs for UI changes, and list affected areas/coverage. Ensure `testDebugUnitTest` and `lintDebug` pass.

## Security & Configuration Tips
- Do not commit secrets/keystores; prefer Android Keystore/Encrypted storage.
- Network config: `app/src/main/res/xml/network_security_config.xml`.
- Minimum SDK 26; Java 17 with desugaring enabled.
- Keep versions aligned in `gradle/libs.versions.toml`.

