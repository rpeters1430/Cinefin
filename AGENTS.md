# Repository Guidelines

## Project Structure & Module Organization

- Single Gradle module: `:app` (Kotlin + Jetpack Compose).
- Source code: `app/src/main/java/com/rpeters/jellyfin/...`.
- UI/resources: `app/src/main/res`; manifest: `app/src/main/AndroidManifest.xml`.
- Unit tests (JVM): `app/src/test/java`; instrumentation: `app/src/androidTest/java`.
- Build config: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`; versions in
  `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

- Build debug APK: `./gradlew assembleDebug` → outputs `app/build/outputs/apk/debug`.
- Install on device/emulator: `./gradlew installDebug`.
- JVM unit tests: `./gradlew testDebugUnitTest`.
- Instrumentation tests (device/emulator; Hilt runner): `./gradlew connectedAndroidTest`.
- Lint (HTML report under `app/build/reports/lint`): `./gradlew lintDebug`.
- Coverage (HTML/XML under `app/build/reports`): `./gradlew jacocoTestReport`.

## Coding Style & Naming Conventions

- Kotlin style, 4‑space indent, ~120‑char lines.
- Names: classes `PascalCase`; functions/vars `camelCase`; constants `UPPER_SNAKE_CASE`.
- Packages under `com.rpeters.jellyfin`.
- UI: Compose + Material 3; unidirectional data flow (MVVM). DI via Hilt.
- Keep changes small, focused, and consistent with existing patterns.

## Testing Guidelines

- Frameworks: JUnit4, MockK, Turbine, AndroidX Test; Hilt testing configured.
- Focus on ViewModel/Repository logic; mock network/I/O boundaries.
- Name tests descriptively (e.g., `loadMovieDetails_updates_state_on_success`).
- Run: unit `testDebugUnitTest`; instrumentation `connectedAndroidTest`.
- Coverage: `jacocoTestReport` (generated/DI classes already filtered).

## Commit & Pull Request Guidelines

- Use Conventional Commits (e.g., `feat:`, `fix:`, `docs:`).
- Branches: `feature/...`, `bugfix/...`, `hotfix/...`, `docs/...`.
- PRs: clear description, linked issues, and screenshots/GIFs for UI changes. Note affected areas
  and test/coverage impact.
- Ensure `./gradlew testDebugUnitTest` and `./gradlew lintDebug` pass before requesting review.

## Security & Configuration Tips

- Do not commit secrets/keystores; prefer Android Keystore/Encrypted storage.
- Network config: `app/src/main/res/xml/network_security_config.xml`.
- Minimum SDK 26; Java 17 with desugaring enabled.
- Keep versions aligned in `gradle/libs.versions.toml`.
