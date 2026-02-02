plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.google.firebase.perf)
}

android {
    namespace = "com.rpeters.jellyfin"
    compileSdk = libs.versions.sdk.get().toInt()

    defaultConfig {
        testInstrumentationRunnerArguments += mapOf(
            "clearPackageData" to "true",
            "useTestStorageService" to "true",
        )
        applicationId = "com.rpeters.jellyfin"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "13.98"

        testInstrumentationRunner = "com.rpeters.jellyfin.testing.HiltTestRunner"

        // Google AI API key for Gemini cloud fallback
        // Reads from (in order): gradle.properties, local.properties, or environment variable
        buildConfigField(
            "String",
            "GOOGLE_AI_API_KEY",
            "\"${project.findProperty("GOOGLE_AI_API_KEY") ?: System.getenv("GOOGLE_AI_API_KEY") ?: ""}\"",
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = (findProperty("JELLYFIN_KEYSTORE_FILE") as String?)
                ?: System.getenv("JELLYFIN_KEYSTORE_FILE")
                ?: "jellyfin-release.keystore"
            storeFile = file(keystorePath)
            storePassword = (findProperty("JELLYFIN_KEYSTORE_PASSWORD") as String?)
                ?: System.getenv("JELLYFIN_KEYSTORE_PASSWORD")
            keyAlias = (findProperty("JELLYFIN_KEY_ALIAS") as String?)
                ?: System.getenv("JELLYFIN_KEY_ALIAS")
                ?: "jellyfin-release"
            keyPassword = (findProperty("JELLYFIN_KEY_PASSWORD") as String?)
                ?: System.getenv("JELLYFIN_KEY_PASSWORD")
        }
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
            signingConfig = signingConfigs.getByName("release")

            ndk.debugSymbolLevel = "FULL"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        // jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                    "-opt-in=androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                    "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
                    "-Xannotation-default-target=param-property",
                ),
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            // Tell Play Console we don't have unstripped libraries
            // This suppresses the warning for third-party .so files
            useLegacyPackaging = false
        }
    }
    ndkVersion = "29.0.14206865"
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Material 3
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.window)
    implementation(libs.google.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Jellyfin SDK
    implementation(libs.jellyfin.sdk)
    implementation(libs.slf4j.android)

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
    implementation(libs.coil.network.okhttp)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.session)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)
    implementation(libs.google.cast.framework)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.appcheck)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.ai) // Unified SDK with Gemini Nano + Google AI support
    implementation(libs.google.mlkit.genai.prompt) // ML Kit GenAI Prompt API (on-device Gemini Nano)

    // Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.kotlinx.metadata.jvm)
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
    testImplementation(libs.robolectric)

    // Hilt testing
    testImplementation(libs.dagger.hilt.android.testing)
    kspTest("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")
    kspTest(libs.kotlinx.metadata.jvm)

    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)

    // Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.test.services)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.robolectric)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.dagger.hilt.android.testing)
    kspAndroidTest("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")
    kspAndroidTest(libs.kotlinx.metadata.jvm)

    // Debug Tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Force all core Compose artifacts to 1.7.6 to prevent "Franken-Compose" crashes on API 30
// This ensures ui, foundation, and animation are all in sync.
configurations.all {
    resolutionStrategy.eachDependency {
        val group = requested.group
        if (group == "androidx.compose.ui" ||
            group == "androidx.compose.foundation" ||
            group == "androidx.compose.animation"
        ) {
            useVersion("1.7.6")
            because("Force consistent core Compose version to fix NoSuchFieldError on API 30")
        }
    }
}

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

apply(plugin = "jacoco")
