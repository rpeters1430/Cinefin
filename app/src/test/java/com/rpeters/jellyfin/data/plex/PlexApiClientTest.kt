package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PlexApiClientTest {

    private val delegate = RecordingApiClient()
    private val fakeServer = JellyfinServer(
        id = "machine-identifier-123",
        name = "My Plex Server",
        url = "https://192-168-1-50.plex.direct:32400",
        serverType = ServerType.PLEX,
        userId = ServerIdCodec.PLEX.encode("42")?.toString(),
        username = "plexuser",
        accessToken = "plex-server-token",
    )
    private val client = PlexApiClient(
        delegate = delegate,
        serverProvider = { fakeServer },
    )
    private val sdkJson = Json { ignoreUnknownKeys = true }

    private fun loadResource(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)) { "Resource not found: $path" }.readText()

    @Before
    fun setUp() {
        mockkObject(SecureLogger)
        every { SecureLogger.w(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getPublicSystemInfo_callsIdentityAndReturnsPublicSystemInfo() = runTest {
        delegate.respondWith(
            """{"MediaContainer":{"machineIdentifier":"machine-123","version":"1.40.2"}}""",
        )

        val response = client.request(HttpMethod.GET, "/System/Info/Public", emptyMap(), emptyMap(), null)

        assertEquals("/identity", delegate.path)
        val info = sdkJson.decodeFromString(PublicSystemInfo.serializer(), response.body.decodeToString())
        assertEquals("machine-123", info.id)
        assertEquals("My Plex Server", info.serverName)
        assertEquals("1.40.2", info.version)
        assertEquals("Plex Media Server", info.productName)
        assertEquals("Plex", info.operatingSystem)
        assertTrue(info.startupWizardCompleted == true)
    }

    @Test
    fun getSystemInfo_callsIdentityAndReturnsSystemInfo() = runTest {
        delegate.respondWith(
            """{"MediaContainer":{"machineIdentifier":"machine-123","version":"1.40.2"}}""",
        )

        val response = client.request(HttpMethod.GET, "/System/Info", emptyMap(), emptyMap(), null)

        assertEquals("/identity", delegate.path)
        val info = sdkJson.decodeFromString(SystemInfo.serializer(), response.body.decodeToString())
        assertEquals("machine-123", info.id)
        assertEquals("My Plex Server", info.serverName)
        assertEquals("1.40.2", info.version)
        assertEquals("Plex Media Server", info.productName)
    }

    @Test
    fun getUsersMe_returnsCachedUser() = runTest {
        val response = client.request(HttpMethod.GET, "/Users/Me", emptyMap(), emptyMap(), null)

        val user = sdkJson.decodeFromString(UserDto.serializer(), response.body.decodeToString())
        assertEquals("plexuser", user.name)
        assertEquals(fakeServer.id, user.serverId)
        assertEquals(UUID.fromString(fakeServer.userId), user.id)
    }

    @Test
    fun getUserViews_callsSectionsAndReturnsCollectionFolders() = runTest {
        delegate.respondWith(loadResource("plex/pms/sections.json"))

        val response = client.request(HttpMethod.GET, "/UserViews", emptyMap(), emptyMap(), null)

        assertEquals("/library/sections", delegate.path)
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(2, result.items.size)
        assertEquals("Movies", result.items[0].name)
        assertEquals(CollectionType.MOVIES, result.items[0].collectionType)
        assertEquals(BaseItemKind.COLLECTION_FOLDER, result.items[0].type)
        assertEquals("TV Shows", result.items[1].name)
        assertEquals(CollectionType.TVSHOWS, result.items[1].collectionType)
    }

    @Test
    fun getItems_withFavoritesFilter_returnsStaticEmptyWithoutNetworkCall() = runTest {
        delegate.respondWith("should-not-be-called")

        val response = client.request(
            HttpMethod.GET,
            "/Items",
            emptyMap(),
            mapOf("filters" to listOf("IsFavorite")),
            null,
        )

        assertNull(delegate.path) // No network request made
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(0, result.items.size)
        assertEquals(0, result.totalRecordCount)
    }

    @Test
    fun getItems_browseMovies_callsSectionAll() = runTest {
        delegate.respondWith(loadResource("plex/pms/movie_metadata.json"))

        val sectionId = ServerIdCodec.PLEX.encode("1").toString()
        val response = client.request(
            HttpMethod.GET,
            "/Items",
            emptyMap(),
            mapOf(
                "parentId" to sectionId,
                "includeItemTypes" to listOf("Movie"),
                "recursive" to true,
                "sortBy" to listOf("SortName"),
                "sortOrder" to "Ascending",
                "limit" to 20,
            ),
            null,
        )

        assertEquals("/library/sections/1/all", delegate.path)
        assertEquals(PlexQueryPlanner.TYPE_MOVIE, delegate.query["type"])
        assertEquals("titleSort:asc", delegate.query["sort"])
        assertEquals(20, delegate.query[PlexQueryPlanner.HEADER_SIZE])
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(1, result.items.size)
        assertEquals("Inception", result.items[0].name)
    }

    @Test
    fun getItemById_callsMetadataEndpoint() = runTest {
        delegate.respondWith(loadResource("plex/pms/movie_metadata.json"))

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val response = client.request(
            HttpMethod.GET,
            "/Items/{itemId}",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        assertEquals("/library/metadata/1001", delegate.path)
        assertEquals(1, delegate.query["includeChapters"])
        assertEquals(1, delegate.query["includeMarkers"])
        val item = sdkJson.decodeFromString(BaseItemDto.serializer(), response.body.decodeToString())
        assertEquals("Inception", item.name)
        assertEquals(BaseItemKind.MOVIE, item.type)
    }

    @Test
    fun deleteItem_callsDeleteMetadata() = runTest {
        delegate.respondWith("")

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        client.request(
            HttpMethod.DELETE,
            "/Items/{itemId}",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        assertEquals(HttpMethod.DELETE, delegate.method)
        assertEquals("/library/metadata/1001", delegate.path)
    }

    @Test
    fun getSimilar_callsMetadataSimilar() = runTest {
        delegate.respondWith(loadResource("plex/pms/movie_metadata.json"))

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val response = client.request(
            HttpMethod.GET,
            "/Items/{itemId}/Similar",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        assertEquals("/library/metadata/1001/similar", delegate.path)
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(1, result.items.size)
    }

    @Test
    fun getNextUp_callsOnDeckAndFiltersEpisodes() = runTest {
        delegate.respondWith(loadResource("plex/pms/episode_metadata.json"))

        val response = client.request(
            HttpMethod.GET,
            "/Shows/NextUp",
            emptyMap(),
            emptyMap(),
            null,
        )

        assertEquals("/library/onDeck", delegate.path)
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(1, result.items.size)
        assertEquals("Celebration", result.items[0].name)
        assertEquals(BaseItemKind.EPISODE, result.items[0].type)
    }

    @Test
    fun scrobbleAndUnscrobble_callsPmsScrobbleRoutes() = runTest {
        delegate.respondWith("")

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val scrobbleResponse = client.request(
            HttpMethod.POST,
            "/UserPlayedItems/{itemId}",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )
        assertEquals("/:/scrobble", delegate.path)
        assertEquals("1001", delegate.query["key"])
        val scrobbleData = sdkJson.decodeFromString(UserItemDataDto.serializer(), scrobbleResponse.body.decodeToString())
        assertTrue(scrobbleData.played)

        val unscrobbleResponse = client.request(
            HttpMethod.DELETE,
            "/UserPlayedItems/{itemId}",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )
        assertEquals("/:/unscrobble", delegate.path)
        assertEquals("1001", delegate.query["key"])
        val unscrobbleData = sdkJson.decodeFromString(UserItemDataDto.serializer(), unscrobbleResponse.body.decodeToString())
        assertFalse(unscrobbleData.played)
    }

    @Test
    fun getItemUserData_callsMetadataEndpoint() = runTest {
        delegate.respondWith(loadResource("plex/pms/movie_metadata.json"))

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val response = client.request(
            HttpMethod.GET,
            "/UserItems/{itemId}/UserData",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        assertEquals("/library/metadata/1001", delegate.path)
        val data = sdkJson.decodeFromString(UserItemDataDto.serializer(), response.body.decodeToString())
        assertTrue(data.played)
        assertEquals(PlexMapper.msToTicks(2_400_000L), data.playbackPositionTicks)
    }

    @Test
    fun playbackInfo_directPlayDecision_returnsDirectPlayMediaSource() = runTest {
        delegate.respondForPath("/library/metadata/1001", loadResource("plex/pms/movie_metadata.json"))
        delegate.respondForPath("/video/:/transcode/universal/decision", loadResource("plex/pms/decision_direct_play.json"))

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val response = client.request(
            HttpMethod.POST,
            "/Items/{itemId}/PlaybackInfo",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        val info = sdkJson.decodeFromString(PlaybackInfoResponse.serializer(), response.body.decodeToString())
        assertNotNull(info.playSessionId)
        val source = info.mediaSources.firstOrNull()
        assertNotNull(source)
        assertTrue(source!!.supportsDirectPlay)
        assertTrue(source.supportsDirectStream)
        assertTrue(source.supportsTranscoding)
        assertTrue(source.path!!.contains("X-Plex-Token=plex-server-token"))
        assertTrue(source.path!!.contains("download=0"))
        assertTrue(source.transcodingUrl!!.contains("/video/:/transcode/universal/start.m3u8"))
        assertEquals(2, source.mediaStreams?.size)
        assertEquals(MediaStreamType.VIDEO, source.mediaStreams?.get(0)?.type)
        assertEquals(MediaStreamType.AUDIO, source.mediaStreams?.get(1)?.type)
    }

    @Test
    fun playbackInfo_transcodeDecision_returnsTranscodingMediaSource() = runTest {
        delegate.respondForPath("/library/metadata/1002", loadResource("plex/pms/movie_metadata.json"))
        delegate.respondForPath("/video/:/transcode/universal/decision", loadResource("plex/pms/decision_transcode.json"))

        val movieId = ServerIdCodec.PLEX.encode("1002").toString()
        val response = client.request(
            HttpMethod.POST,
            "/Items/{itemId}/PlaybackInfo",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        val info = sdkJson.decodeFromString(PlaybackInfoResponse.serializer(), response.body.decodeToString())
        val source = info.mediaSources.firstOrNull()
        assertNotNull(source)
        assertFalse(source!!.supportsDirectPlay)
        assertFalse(source.supportsDirectStream)
        assertTrue(source.supportsTranscoding)
    }

    @Test
    fun playbackInfo_burnSubtitlesDecision_returnsTranscodingWithBurn() = runTest {
        delegate.respondForPath("/library/metadata/1003", loadResource("plex/pms/movie_metadata.json"))
        delegate.respondForPath("/video/:/transcode/universal/decision", loadResource("plex/pms/decision_burn_subtitles.json"))

        val movieId = ServerIdCodec.PLEX.encode("1003").toString()
        val response = client.request(
            HttpMethod.POST,
            "/Items/{itemId}/PlaybackInfo",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        val info = sdkJson.decodeFromString(PlaybackInfoResponse.serializer(), response.body.decodeToString())
        val source = info.mediaSources.firstOrNull()
        assertNotNull(source)
        assertFalse(source!!.supportsDirectPlay)
        assertFalse(source.supportsDirectStream)
        assertTrue(source.supportsTranscoding)
        val subtitleStream = source.mediaStreams?.firstOrNull { it.type == MediaStreamType.SUBTITLE }
        assertNotNull(subtitleStream)
    }

    @Test
    fun playbackInfo_audioSubtitleSwitch_callsPutParts() = runTest {
        delegate.respondForPath("/library/metadata/1001", loadResource("plex/pms/movie_metadata.json"))
        delegate.respondForPath("/video/:/transcode/universal/decision", loadResource("plex/pms/decision_direct_play.json"))
        delegate.respondForPath("/library/parts/8001", "")

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        val playbackDto = PlaybackInfoDto(
            audioStreamIndex = 1,
            subtitleStreamIndex = 2,
        )
        client.request(
            HttpMethod.POST,
            "/Items/{itemId}/PlaybackInfo",
            mapOf("itemId" to movieId),
            emptyMap(),
            playbackDto,
        )

        val putCall = delegate.history.firstOrNull { it.path == "/library/parts/8001" }
        assertNotNull("Expected /library/parts/8001 call", putCall)
        assertEquals("/library/parts/8001", putCall!!.path)
        assertEquals(7002L, putCall.query["audioStreamID"])
        assertEquals(7003L, putCall.query["subtitleStreamID"])
    }

    @Test(expected = IllegalStateException::class)
    fun playbackInfo_remotePaywall403_throwsDescriptiveError() = runTest {
        delegate.respondForPath(
            "/library/metadata/1001",
            """{"error":"This server needs Plex Pass or Remote Watch Pass for streaming outside its home network"}""",
            status = 403,
        )

        val movieId = ServerIdCodec.PLEX.encode("1001").toString()
        client.request(
            HttpMethod.POST,
            "/Items/{itemId}/PlaybackInfo",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )
    }

    @Test
    fun timeline_playingAndProgress_callsTimelineWithMs() = runTest {
        delegate.respondWith("")

        val movieId = ServerIdCodec.PLEX.encode("1001")!!
        val startInfo = PlaybackStartInfo(
            itemId = movieId,
            sessionId = "session-1",
            positionTicks = 120_000_000L, // 12,000 ms
            canSeek = true,
            isPaused = false,
            isMuted = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT,
        )

        client.request(
            HttpMethod.POST,
            "/Sessions/Playing",
            emptyMap(),
            emptyMap(),
            startInfo,
        )

        assertEquals("/:/timeline", delegate.path)
        assertEquals("1001", delegate.query["ratingKey"])
        assertEquals("playing", delegate.query["state"])
        assertEquals(12000L, delegate.query["time"])

        val progressInfo = PlaybackProgressInfo(
            itemId = movieId,
            sessionId = "session-1",
            positionTicks = 150_000_000L, // 15,000 ms
            canSeek = true,
            isPaused = true,
            isMuted = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT,
        )

        client.request(
            HttpMethod.POST,
            "/Sessions/Playing/Progress",
            emptyMap(),
            emptyMap(),
            progressInfo,
        )

        assertEquals("/:/timeline", delegate.path)
        assertEquals("1001", delegate.query["ratingKey"])
        assertEquals("paused", delegate.query["state"])
        assertEquals(15000L, delegate.query["time"])
    }

    @Test
    fun timeline_stopped_callsTimelineStoppedAndTranscodeStop() = runTest {
        delegate.respondWith("")

        val movieId = ServerIdCodec.PLEX.encode("1001")!!
        val stopInfo = PlaybackStopInfo(
            itemId = movieId,
            sessionId = "session-1",
            playSessionId = "transcode-session-xyz",
            positionTicks = 180_000_000L,
            failed = false,
        )

        client.request(
            HttpMethod.POST,
            "/Sessions/Playing/Stopped",
            emptyMap(),
            emptyMap(),
            stopInfo,
        )

        val timelineCall = delegate.history.firstOrNull { it.path == "/:/timeline" }
        assertNotNull(timelineCall)
        assertEquals("stopped", timelineCall!!.query["state"])
        assertEquals(18000L, timelineCall.query["time"])

        val stopCall = delegate.history.firstOrNull { it.path == "/video/:/transcode/universal/stop" }
        assertNotNull("Expected /video/:/transcode/universal/stop call", stopCall)
        assertEquals("transcode-session-xyz", stopCall!!.query["session"])
    }

    @Test
    fun mediaSegments_episodeWithMarkers_returnsMediaSegmentQueryResult() = runTest {
        delegate.respondForPath("/library/metadata/2003", loadResource("plex/pms/episode_metadata.json"))

        val episodeId = ServerIdCodec.PLEX.encode("2003").toString()
        val response = client.request(
            HttpMethod.GET,
            "/MediaSegments/{itemId}",
            mapOf("itemId" to episodeId),
            emptyMap(),
            null,
        )

        val queryResult = sdkJson.decodeFromString(MediaSegmentDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(2, queryResult.totalRecordCount)
        assertEquals(2, queryResult.items.size)

        val intro = queryResult.items.first { it.type == MediaSegmentType.INTRO }
        assertEquals(150000L * 10_000L, intro.startTicks)
        assertEquals(230000L * 10_000L, intro.endTicks)

        val outro = queryResult.items.first { it.type == MediaSegmentType.OUTRO }
        assertEquals(3500000L * 10_000L, outro.startTicks)
        assertEquals(3600000L * 10_000L, outro.endTicks)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun unhandledRoute_throwsInDebug() = runTest {
        client.request(HttpMethod.GET, "/Unhandled/Route", emptyMap(), emptyMap(), null)
    }

    data class RecordedRequest(
        val method: HttpMethod,
        val path: String,
        val pathParameters: Map<String, Any?>,
        val query: Map<String, Any?>,
        val body: Any?,
    )

    private class RecordingApiClient : ApiClient() {
        var method: HttpMethod? = null
        var path: String? = null
        var pathParameters: Map<String, Any?> = emptyMap()
        var query: Map<String, Any?> = emptyMap()
        var body: Any? = null
        val history = mutableListOf<RecordedRequest>()
        private var defaultResponseBody = ""
        private val responsesByPath = mutableMapOf<String, RawResponse>()

        fun respondWith(json: String) {
            defaultResponseBody = json
        }

        fun respondForPath(path: String, json: String, status: Int = 200) {
            responsesByPath[path] = RawResponse(json.toByteArray(), status, emptyMap())
        }

        override val baseUrl: String? = "https://192-168-1-50.plex.direct:32400"
        override val accessToken: String? = "plex-server-token"
        override val clientInfo: ClientInfo = ClientInfo("Cinefin", "1.0")
        override val deviceInfo: DeviceInfo = DeviceInfo("device-id", "device")
        override val httpClientOptions: HttpClientOptions = HttpClientOptions()
        override val webSocket: SocketApi = mockk(relaxed = true)

        override fun update(baseUrl: String?, accessToken: String?, clientInfo: ClientInfo, deviceInfo: DeviceInfo) = Unit

        override suspend fun request(
            method: HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ): RawResponse {
            this.method = method
            this.path = pathTemplate
            this.pathParameters = pathParameters
            this.query = queryParameters
            this.body = requestBody
            history.add(RecordedRequest(method, pathTemplate, pathParameters, queryParameters, requestBody))

            val matched = responsesByPath[pathTemplate]
            return matched ?: RawResponse(defaultResponseBody.toByteArray(), 200, emptyMap())
        }
    }
}
