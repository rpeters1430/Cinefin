package com.rpeters.jellyfin.benchmarks;

import com.rpeters.jellyfin.data.model.SeerrSearchResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Benchmarks the kotlinx.serialization decoding of Jellyseerr responses.
 *
 * <p>Search and discover screens decode a page of results on every request, so decoding cost
 * directly affects perceived latency when browsing.
 */
@State(Scope.Benchmark)
public class SeerrPayloadBenchmark {

  private String searchPageJson;
  private String largeSearchPageJson;
  private SeerrSearchResult searchPage;

  @Setup
  public void setup() {
    searchPageJson = SeerrPayloads.INSTANCE.getSearchPageJson();
    largeSearchPageJson = SeerrPayloads.INSTANCE.getLargeSearchPageJson();
    searchPage = SeerrPayloads.INSTANCE.parseSearchPage(searchPageJson);
  }

  @Benchmark
  public SeerrSearchResult decodeSearchPage() {
    return SeerrPayloads.INSTANCE.parseSearchPage(searchPageJson);
  }

  @Benchmark
  public SeerrSearchResult decodeLargeSearchPage() {
    return SeerrPayloads.INSTANCE.parseSearchPage(largeSearchPageJson);
  }

  @Benchmark
  public String encodeSearchPage() {
    return SeerrPayloads.INSTANCE.encodeSearchPage(searchPage);
  }
}
