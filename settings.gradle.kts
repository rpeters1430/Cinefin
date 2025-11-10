pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("com.google.devtools.ksp") version "2.3.2" // ← Fixed version
        id("com.google.dagger.hilt.android") version "2.57.2"
        id("com.android.application") version "8.13.1"
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
