package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.SonarrPreferences
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrRepositoryTest {

    private val server = MockWebServer()
    private val prefs = MutableStateFlow(SonarrPreferences.DEFAULT)
    private val routes = mutableMapOf<String, MockResponse>()
    private val repository = SonarrRepository(
        OkHttpClient(),
        mockk<ArrPreferencesRepository> { every { sonarrPreferencesFlow } returns prefs },
        Json { ignoreUnknownKeys = true; explicitNulls = false },
    )

    private fun route(method: String, path: String, code: Int = 200, body: String = "[]") {
        routes["$method $path"] = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    }

    private fun requests(): List<RecordedRequest> = (0 until server.requestCount).map { server.takeRequest() }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return routes["${request.method} $path"] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        prefs.value = SonarrPreferences(baseUrl = server.url("/").toString(), apiKey = "secret", isEnabled = true)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun notConfigured_returnsBadRequestWithoutCallingServer() = runTest {
        prefs.value = SonarrPreferences.DEFAULT
        val result = repository.testConnection()
        assertTrue(result is ApiResult.Error)
        assertEquals(0, server.requestCount)
        assertNull(repository.findTvdbId("Anything"))
    }

    @Test
    fun testConnection_successSendsApiKey() = runTest {
        route("GET", "/api/v3/system/status", body = """{"version":"4.0"}""")
        assertTrue(repository.testConnection() is ApiResult.Success)
        assertEquals("secret", server.takeRequest().getHeader("X-Api-Key"))
    }

    @Test
    fun testConnection_mapsHttpErrors() = runTest {
        route("GET", "/api/v3/system/status", code = 401, body = "{}")
        assertEquals(ErrorType.UNAUTHORIZED, (repository.testConnection() as ApiResult.Error).errorType)
        route("GET", "/api/v3/system/status", code = 503, body = "{}")
        assertEquals(ErrorType.SERVER_ERROR, (repository.testConnection() as ApiResult.Error).errorType)
        route("GET", "/api/v3/system/status", code = 418, body = "{}")
        assertEquals(ErrorType.UNKNOWN, (repository.testConnection() as ApiResult.Error).errorType)
    }

    @Test
    fun getQualityProfiles_parsesList() = runTest {
        route("GET", "/api/v3/qualityProfile", body = """[{"id":1,"name":"HD-1080p"},{"id":4,"name":"Any"}]""")
        val result = repository.getQualityProfiles() as ApiResult.Success
        assertEquals(listOf(1, 4), result.data.map { it.id })
    }

    @Test
    fun getQualityProfiles_error() = runTest {
        route("GET", "/api/v3/qualityProfile", code = 404, body = "{}")
        assertEquals(ErrorType.NOT_FOUND, (repository.getQualityProfiles() as ApiResult.Error).errorType)
    }

    @Test
    fun addSeries_newSeries_addsWithRootFolderAndSearchesSeasons() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route(
            "GET", "/api/v3/series/lookup",
            body = """[{"title":"Show","titleSlug":"show","tvdbId":42,"seasons":[{"seasonNumber":0},{"seasonNumber":1},{"seasonNumber":2}]}]""",
        )
        route("GET", "/api/v3/rootFolder", body = """[{"path":"/tv"}]""")
        route("GET", "/api/v3/qualityProfile", body = """[{"id":7,"name":"HD"}]""")
        route("POST", "/api/v3/series", body = """{"id":99}""")
        route("POST", "/api/v3/command", body = "{}")

        val result = repository.addSeries(tvdbId = 42, requestedSeasons = listOf(2))

        assertTrue(result is ApiResult.Success)
        val sent = requests()
        val add = sent.first { it.method == "POST" && it.path!!.startsWith("/api/v3/series") }.body.readUtf8()
        assertTrue(add.contains("\"rootFolderPath\":\"/tv\""))
        assertTrue(add.contains("\"qualityProfileId\":7"))
        val command = sent.last { it.path!!.startsWith("/api/v3/command") }.body.readUtf8()
        assertTrue(command.contains("SeasonSearch"))
        assertTrue(command.contains("\"seasonNumber\":2"))
    }

    @Test
    fun addSeries_wholeSeries_usesSeriesSearchAndRefetchesIdWhenMissing() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route(
            "GET", "/api/v3/series/lookup",
            body = """[{"title":"Show","titleSlug":"show","tvdbId":42,"rootFolderPath":"/media","qualityProfileId":3,"seasons":[{"seasonNumber":1}]}]""",
        )
        route("POST", "/api/v3/series", body = "{}")
        route("POST", "/api/v3/command", body = "{}")

        assertTrue(repository.addSeries(tvdbId = 42, requestedSeasons = null, qualityProfileId = 5) is ApiResult.Success)
    }

    @Test
    fun addSeries_existingSeries_updatesMonitoringAndSearches() = runTest {
        route(
            "GET", "/api/v3/series",
            body = """[{"id":12,"title":"Show","monitored":false,"seasons":[{"seasonNumber":0,"monitored":false},{"seasonNumber":1,"monitored":false},{"seasonNumber":2,"monitored":false}]}]""",
        )
        route("PUT", "/api/v3/series/12", body = """{"id":12}""")
        route("POST", "/api/v3/command", body = "{}")

        assertTrue(repository.addSeries(tvdbId = 42, requestedSeasons = listOf(1)) is ApiResult.Success)
        val update = requests().first { it.method == "PUT" }.body.readUtf8()
        assertTrue(update.contains("\"monitored\":true"))

        assertTrue(repository.addSeries(tvdbId = 42, requestedSeasons = null) is ApiResult.Success)
    }

    @Test
    fun addSeries_existingSeriesUpdateFails() = runTest {
        route("GET", "/api/v3/series", body = """[{"id":12,"seasons":[]}]""")
        route("PUT", "/api/v3/series/12", code = 500, body = "{}")
        assertEquals(ErrorType.SERVER_ERROR, (repository.addSeries(42, listOf(1)) as ApiResult.Error).errorType)
    }

    @Test
    fun addSeries_lookupMissing_isNotFound() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route("GET", "/api/v3/series/lookup", body = "[]")
        assertEquals(ErrorType.NOT_FOUND, (repository.addSeries(42, null) as ApiResult.Error).errorType)
    }

    @Test
    fun addSeries_noRootFolderOrProfile_isBadRequest() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route("GET", "/api/v3/series/lookup", body = """[{"title":"Show","tvdbId":42}]""")
        route("GET", "/api/v3/rootFolder", body = "[]")
        assertEquals(ErrorType.BAD_REQUEST, (repository.addSeries(42, null) as ApiResult.Error).errorType)

        route("GET", "/api/v3/rootFolder", body = """[{"path":"/tv"}]""")
        route("GET", "/api/v3/qualityProfile", body = "[]")
        assertEquals(ErrorType.BAD_REQUEST, (repository.addSeries(42, null) as ApiResult.Error).errorType)
    }

    @Test
    fun addSeries_rejected() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route("GET", "/api/v3/series/lookup", body = """[{"title":"Show","tvdbId":42,"rootFolderPath":"/tv","qualityProfileId":1}]""")
        route("POST", "/api/v3/series", code = 400, body = "{}")
        assertTrue(repository.addSeries(42, null) is ApiResult.Error)
    }

    @Test
    fun requestEpisode_existingSeries_searchesEpisode() = runTest {
        route("GET", "/api/v3/series", body = """[{"id":12,"title":"Show","tvdbId":42}]""")
        route("GET", "/api/v3/episode", body = """[{"id":501,"seasonNumber":1,"episodeNumber":1},{"id":502,"seasonNumber":1,"episodeNumber":2}]""")
        route("POST", "/api/v3/command", body = "{}")

        assertTrue(repository.requestEpisode(42, 1, 2) is ApiResult.Success)
        val command = requests().last { it.path!!.startsWith("/api/v3/command") }.body.readUtf8()
        assertTrue(command.contains("EpisodeSearch"))
        assertTrue(command.contains("502"))
    }

    @Test
    fun requestEpisode_missingEpisodeAndCommandFailure() = runTest {
        route("GET", "/api/v3/series", body = """[{"id":12,"tvdbId":42}]""")
        route("GET", "/api/v3/episode", body = "[]")
        assertEquals(ErrorType.NOT_FOUND, (repository.requestEpisode(42, 1, 9) as ApiResult.Error).errorType)

        route("GET", "/api/v3/episode", body = """[{"id":1,"seasonNumber":1,"episodeNumber":9}]""")
        route("POST", "/api/v3/command", code = 403, body = "{}")
        assertEquals(ErrorType.UNAUTHORIZED, (repository.requestEpisode(42, 1, 9) as ApiResult.Error).errorType)
    }

    @Test
    fun requestEpisode_seriesNotInSonarr_addFailsPropagates() = runTest {
        route("GET", "/api/v3/series", body = "[]")
        route("GET", "/api/v3/series/lookup", body = "[]")
        assertTrue(repository.requestEpisode(42, 1, 1) is ApiResult.Error)
    }

    @Test
    fun findTvdbId_prefersTmdbThenExactTitle() = runTest {
        route("GET", "/api/v3/series/lookup", body = """[{"title":"Other","tvdbId":5},{"title":"The Show","tvdbId":77}]""")
        assertEquals(5, repository.findTvdbId("The Show", tmdbId = 10))
        assertEquals(77, repository.findTvdbId("the show"))
    }

    @Test
    fun findTvdbId_ambiguousOrSingle() = runTest {
        route("GET", "/api/v3/series/lookup", body = """[{"title":"A","tvdbId":1},{"title":"B","tvdbId":2}]""")
        assertNull(repository.findTvdbId("C"))
        route("GET", "/api/v3/series/lookup", body = """[{"title":"Only","tvdbId":3}]""")
        assertEquals(3, repository.findTvdbId("Different"))
        route("GET", "/api/v3/series/lookup", code = 500, body = "{}")
        assertNull(repository.findTvdbId("x"))
    }
}
