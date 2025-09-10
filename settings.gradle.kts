pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.android") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
        id("com.google.devtools.ksp") version "2.2.10-2.0.2" // ← Fixed version
        id("com.google.dagger.hilt.android") version "2.57.1"
        id("com.android.application") version "8.13.0"
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
