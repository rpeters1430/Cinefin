plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rpeters.jellyfin"
    compileSdk = libs.versions.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.rpeters.jellyfin"
        minSdk = 26 // Android 8.0+ (was 31) - Broader device compatibility
        targetSdk = 35 // Use stable SDK 35 for runtime, keep compileSdk at 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.rpeters.jellyfin.testing.HiltTestRunner"

        // Test configuration
        testInstrumentationRunnerArguments += mapOf(
            "clearPackageData" to "true",
            "useTestStorageService" to "true",
        )
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose BOM - This manages all Compose library versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Material 3 - Latest Alpha Versions
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.window)

    // Material 3 Expressive Components (2024-2025)
    // Note: Some components not yet available in stable releases
    // Pull-to-refresh is included in the Compose BOM
    // implementation(libs.androidx.material3.carousel)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)

    // Security for encrypted storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Jellyfin SDK
    implementation(libs.jellyfin.sdk)

    // SLF4J Android Implementation for Jellyfin SDK logging
    implementation(libs.slf4j.android)
    // Silence SLF4J warnings
    implementation("org.slf4j:slf4j-nop:2.0.17")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)

    // Media3 for video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls) // HLS playback (master.m3u8)
    implementation(libs.androidx.media3.exoplayer.dash) // DASH playback (stream.mpd)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.session)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)
    implementation(libs.google.cast.framework)

    // Hilt for dependency injection
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Android TV Compose
    implementation(libs.androidx.tv.material)

    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)

    // Hilt testing
    testImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kspTest("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")

    // Turbine for testing StateFlow
    testImplementation("app.cash.turbine:turbine:1.2.1")

    // MockWebServer for network testing
    testImplementation("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")

    // Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Hilt instrumentation testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kspAndroidTest("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")

    // Debug Tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Test coverage configuration
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest", "createDebugCoverageReport")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/data/model/*.*",
        "**/di/*.*",
    )

    val debugTree = fileTree("${layout.buildDirectory.asFile.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory.asFile.get()) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
            include("outputs/code_coverage/debugAndroidTest/connected/*/coverage.ec")
        },
    )
}

// Ensure jacoco agent is applied for coverage
apply(plugin = "jacoco")
