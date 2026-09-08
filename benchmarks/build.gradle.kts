plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("me.champeau.jmh") version "0.7.3"
}

kotlin {
    jvmToolchain(21)
}

// The production code under benchmark lives in the Android `:app` module. Only the
// platform independent files are compiled here, which keeps the benchmarks runnable
// on a plain JVM while still measuring the real production implementations.
kotlin.sourceSets.named("main") {
    kotlin.srcDir("../app/src/main/java")
    kotlin.setIncludes(
        listOf(
            // Benchmark fixtures living in this module.
            "com/rpeters/jellyfin/benchmarks/**",
            // Production sources under benchmark (JVM only, no Android dependencies).
            "com/rpeters/jellyfin/utils/RatingUtils.kt",
            "com/rpeters/jellyfin/utils/ServerUrlNormalizer.kt",
            "com/rpeters/jellyfin/utils/UrlNormalizer.kt",
            "com/rpeters/jellyfin/data/model/SeerrModels.kt",
        ),
    )
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

jmh {
    // Version of the CodSpeed JMH fork wired through `settings.gradle.kts`.
    jmhVersion.set("1.37")

    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ns")
    warmupIterations.set(2)
    warmup.set("1s")
    iterations.set(3)
    timeOnIteration.set("1s")
    fork.set(2)
    resultFormat.set("JSON")

    // Force a System.gc() between iterations so a GC pause does not land inside a
    // measurement window and show up as a spurious regression.
    forceGC.set(true)
}
