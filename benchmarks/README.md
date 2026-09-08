# Cinefin benchmarks

JMH benchmarks for the performance sensitive, platform-independent code of the app. Results are
tracked continuously with [CodSpeed](https://app.codspeed.io/rpeters1430/Cinefin) through the
`CodSpeed Benchmarks` GitHub Actions workflow.

## Layout

This directory is a **standalone Gradle build** (it has its own `settings.gradle.kts`) and is
therefore not part of the Android build. That keeps the benchmarks runnable with a plain JDK 21,
without the Android SDK, and keeps benchmark dependencies out of the app.

- `src/jmh/java` — the JMH benchmark classes.
- `src/main/kotlin` — benchmark fixtures (payload builders).
- Production code is not duplicated: `build.gradle.kts` compiles a small, explicitly listed set of
  JVM-only sources straight from `../app/src/main/java`, so the benchmarks always measure the real
  implementations.

Only Android-free sources can be added to that list. Today it covers:

| Source                                     | Benchmarked behaviour                                    |
| ------------------------------------------ | -------------------------------------------------------- |
| `utils/RatingUtils.kt`                     | Official rating normalization, run for every library item |
| `utils/ServerUrlNormalizer.kt`             | Server URL normalization (current and legacy variants)    |
| `utils/UrlNormalizer.kt`                   | Jellyfin base URL normalization                           |
| `data/model/SeerrModels.kt`                | kotlinx.serialization decoding/encoding of Seerr payloads |

## Requirements

- JDK 21 or later.
- The `third-party/codspeed-jvm` submodule, which provides the CodSpeed fork of JMH:

```bash
git submodule update --init --recursive
```

## Running

From the repository root:

```bash
./gradlew -p benchmarks jmh
```

To run a subset, build the benchmark jar and pass a JMH pattern to it:

```bash
./gradlew -p benchmarks jmhJar
java -jar benchmarks/build/libs/cinefin-benchmarks-jmh.jar ".*SeerrPayloadBenchmark.*"
```

To run them through the CodSpeed runner locally (see the
[CodSpeed docs](https://codspeed.io/docs/benchmarks/java)):

```bash
codspeed run --mode walltime -- ./gradlew -p benchmarks jmh
```

## Adding a benchmark

1. If the code under benchmark is not compiled yet, add its file to the include list in
   `build.gradle.kts`. It must not depend on the Android framework.
2. Add a JMH class in `src/jmh/java/com/rpeters/jellyfin/benchmarks`.
3. Always return the computed value or feed it to a `Blackhole` so the JIT cannot eliminate the
   work being measured.
