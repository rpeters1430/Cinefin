package com.rpeters.jellyfin.benchmarks;

import com.rpeters.jellyfin.utils.RatingUtilsKt;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks the official rating normalization used when rendering media metadata.
 *
 * <p>The function runs for every item of every library grid, so its cost is paid many times per
 * frame of scrolling.
 */
@State(Scope.Benchmark)
public class RatingNormalizationBenchmark {

  /** Ratings covering the fast paths (known values, aliases) and the regex based fallbacks. */
  private static final List<String> RAW_RATINGS =
      List.of(
          "PG-13",
          "tv-ma",
          "PG13",
          "Not Rated",
          "US-R",
          "DE-16",
          "FSK 12",
          "PEGI-18",
          "GB-15",
          "  nc17  ",
          "TV-Y7",
          "18+");

  @Benchmark
  public String normalizeKnownRating() {
    return RatingUtilsKt.normalizeOfficialRating("PG-13");
  }

  @Benchmark
  public String normalizePrefixedRating() {
    return RatingUtilsKt.normalizeOfficialRating("DE-16");
  }

  @Benchmark
  public void normalizeMixedRatings(Blackhole blackhole) {
    for (int i = 0; i < RAW_RATINGS.size(); i++) {
      blackhole.consume(RatingUtilsKt.normalizeOfficialRating(RAW_RATINGS.get(i)));
    }
  }
}
