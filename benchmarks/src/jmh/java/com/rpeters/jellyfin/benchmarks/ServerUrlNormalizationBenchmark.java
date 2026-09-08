package com.rpeters.jellyfin.benchmarks;

import com.rpeters.jellyfin.utils.ServerUrlNormalizerKt;
import com.rpeters.jellyfin.utils.UrlNormalizerKt;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks the server URL normalization helpers.
 *
 * <p>These run on every credential lookup, connection attempt and stored-session migration, and
 * they are on the critical path of app startup.
 */
@State(Scope.Benchmark)
public class ServerUrlNormalizationBenchmark {

  private static final List<String> RAW_URLS =
      List.of(
          "https://jellyfin.example.com",
          "  HTTP://Media.Local:8096/  ",
          "media.example.org:8920/jellyfin/",
          "https://JELLYFIN.example.com/media/",
          "192.168.1.42:8096",
          "http://[2001:db8::1]:8096/jellyfin");

  @Benchmark
  public String normalizeServerUrl() {
    return ServerUrlNormalizerKt.normalizeServerUrl("  HTTP://Media.Local:8096/  ");
  }

  @Benchmark
  public void normalizeServerUrls(Blackhole blackhole) {
    for (int i = 0; i < RAW_URLS.size(); i++) {
      blackhole.consume(ServerUrlNormalizerKt.normalizeServerUrl(RAW_URLS.get(i)));
    }
  }

  @Benchmark
  public void normalizeServerUrlsLegacy(Blackhole blackhole) {
    for (int i = 0; i < RAW_URLS.size(); i++) {
      blackhole.consume(ServerUrlNormalizerKt.normalizeServerUrlLegacy(RAW_URLS.get(i)));
    }
  }

  @Benchmark
  public String normalizeJellyfinBase() {
    return UrlNormalizerKt.normalizeJellyfinBase("media.example.org:8920/");
  }
}
