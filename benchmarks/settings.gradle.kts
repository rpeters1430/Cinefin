// Standalone Gradle build hosting the JVM benchmarks.
//
// It is intentionally kept out of the main Android build so the benchmarks can be
// compiled and executed with a plain JDK, without requiring the Android SDK.
// Run it from the repository root with: ./gradlew -p benchmarks jmh

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "cinefin-benchmarks"

// Use the CodSpeed fork of JMH so benchmark results are reported to CodSpeed.
includeBuild("../third-party/codspeed-jvm/jmh-fork") {
    dependencySubstitution {
        substitute(module("org.openjdk.jmh:jmh-core"))
            .using(project(":jmh-core"))
        substitute(module("org.openjdk.jmh:jmh-generator-annprocess"))
            .using(project(":jmh-generator-annprocess"))
        substitute(module("org.openjdk.jmh:jmh-generator-bytecode"))
            .using(project(":jmh-generator-bytecode"))
        substitute(module("org.openjdk.jmh:jmh-generator-reflection"))
            .using(project(":jmh-generator-reflection"))
        substitute(module("org.openjdk.jmh:jmh-generator-asm"))
            .using(project(":jmh-generator-asm"))
    }
}
