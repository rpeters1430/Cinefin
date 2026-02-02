pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }

// Disable toolchain auto-detection to avoid downloading JDK
gradle.beforeProject {
    extensions.getByType<JavaPluginExtension>().toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Jellyfin Android"
include(":app")
