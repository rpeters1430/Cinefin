pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.android") version "2.2.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
        id("com.google.devtools.ksp") version "2.2.0-2.0.2"  // ← Fixed version
        id("com.google.dagger.hilt.android") version "2.57"
        id("com.android.application") version "8.12.0"
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
 