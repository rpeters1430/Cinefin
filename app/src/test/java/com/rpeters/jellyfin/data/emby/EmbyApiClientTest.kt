package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class EmbyApiClientTest {

    private val delegate = RecordingApiClient()
    private val client = EmbyApiClient(delegate, userId = USER_ID)
    private val sdkJson = Json { ignoreUnknownKeys = true }

    private val userUuid: UUID = UUID.fromString(USER_ID)
    private val movieId: UUID = ServerIdCodec.encode("1035")!!

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
    fun getItems_keepsPathDecodesIdsAndNormalizesResponse() = runTest {
        delegate.respondWith(
            """{"Items":[{"Name":"Sample","Id":"1035","Type":"Movie","UserData":{"Played":true}}],"TotalRecordCount":182}""",
        )

        val response = client.request(
            HttpMethod.GET,
            "/Items",
            emptyMap(),
            mapOf("userId" to userUuid, "parentId" to ServerIdCodec.encode("5"), "ids" to listOf(movieId), "limit" to 20),
            null,
        )

        assertEquals("/Items", delegate.path)
        assertEquals(USER_ID, delegate.query["userId"])
        assertEquals("5", delegate.query["parentId"])
        assertEquals(listOf("1035"), delegate.query["ids"])
        assertEquals(20, delegate.query["limit"])
        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals(182, result.totalRecordCount)
        val item = result.items.single()
        assertEquals(movieId, item.id)
        assertEquals(BaseItemKind.MOVIE, item.type)
        assertEquals(movieId, item.userData?.itemId)
        assertTrue(item.userData?.played == true)
    }

    @Test
    fun itemQueries_askEmbyForTheListFieldsJellyfinSendsByDefault() = runTest {
        delegate.respondWith("""{"Items":[],"TotalRecordCount":0}""")

        client.request(HttpMethod.GET, "/Items", emptyMap(), mapOf("userId" to userUuid), null)

        val fields = (delegate.query["fields"] as List<*>).map { it.toString() }
        assertTrue(fields.containsAll(listOf("ProductionYear", "CommunityRating", "OfficialRating", "PremiereDate")))
    }

    @Test
    fun itemQueries_keepRequestedFieldsAndDoNotDuplicateThem() = runTest {
        delegate.respondWith("""{"Items":[],"TotalRecordCount":0}""")

        client.request(
            HttpMethod.GET,
            "/Shows/NextUp",
            emptyMap(),
            mapOf("fields" to listOf(ItemFields.OVERVIEW, "ProductionYear")),
            null,
        )

        val fields = delegate.query["fields"] as List<*>
        assertTrue(ItemFields.OVERVIEW in fields)
        assertEquals(1, fields.count { it.toString() == "ProductionYear" })
        assertTrue("CommunityRating" in fields)
    }

    @Test
    fun nonItemRoutes_doNotGetListFields() = runTest {
        delegate.respondWith("""{"Items":[],"TotalRecordCount":0}""")

        client.request(HttpMethod.GET, "/UserViews", emptyMap(), mapOf("userId" to userUuid), null)

        assertNull(delegate.query["fields"])
    }

    @Test
    fun getUserViews_usesEmbyUserScopedPath() = runTest {
        delegate.respondWith("""{"Items":[{"Name":"Movies","Id":"5","Type":"CollectionFolder","CollectionType":"movies"}],"TotalRecordCount":1}""")

        val response = client.request(HttpMethod.GET, "/UserViews", emptyMap(), mapOf("userId" to userUuid), null)

        assertEquals("/Users/{userId}/Views", delegate.path)
        assertEquals(USER_ID, delegate.pathParameters["userId"])
        val views = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        assertEquals("5", ServerIdCodec.decode(views.items.single().id))
    }

    @Test
    fun getCurrentUser_usesSignedInUserWhenRequestCarriesNone() = runTest {
        delegate.respondWith("""{"Name":"tester","Id":"e96573aacb144a45b3c4585e3e5071c7","HasPassword":true}""")

        val response = client.request(HttpMethod.GET, "/Users/Me", emptyMap(), emptyMap(), null)

        assertEquals("/Users/{userId}", delegate.path)
        assertEquals(USER_ID, delegate.pathParameters["userId"])
        val user = sdkJson.decodeFromString(UserDto.serializer(), response.body.decodeToString())
        assertEquals("tester", user.name)
        assertEquals(userUuid, user.id)
    }

    @Test
    fun markFavorite_usesEmbyPathWithNumericItemIdAndFillsItemId() = runTest {
        delegate.respondWith("""{"IsFavorite":true,"PlaybackPositionTicks":0,"PlayCount":0,"Played":false}""")

        val response = client.request(
            HttpMethod.POST,
            "/UserFavoriteItems/{itemId}",
            mapOf("itemId" to movieId),
            mapOf("userId" to userUuid),
            null,
        )

        assertEquals(HttpMethod.POST, delegate.method)
        assertEquals("/Users/{userId}/FavoriteItems/{itemId}", delegate.path)
        assertEquals("1035", delegate.pathParameters["itemId"])
        val userData = sdkJson.decodeFromString(UserItemDataDto.serializer(), response.body.decodeToString())
        assertTrue(userData.isFavorite)
        assertEquals(movieId, userData.itemId)
    }

    @Test
    fun getItemUserData_readsItFromTheItemBecauseEmbyHasNoUserDataRoute() = runTest {
        delegate.respondWith("""{"Name":"Sample","Id":"1035","Type":"Movie","UserData":{"PlaybackPositionTicks":900,"Played":false}}""")

        val response = client.request(
            HttpMethod.GET,
            "/UserItems/{itemId}/UserData",
            mapOf("itemId" to movieId),
            mapOf("userId" to userUuid),
            null,
        )

        assertEquals("/Users/{userId}/Items/{itemId}", delegate.path)
        val userData = sdkJson.decodeFromString(UserItemDataDto.serializer(), response.body.decodeToString())
        assertEquals(900L, userData.playbackPositionTicks)
        assertEquals(movieId, userData.itemId)
    }

    @Test
    fun getMediaSegments_answersEmptyWithoutCallingTheServer() = runTest {
        val response = client.request(HttpMethod.GET, "/MediaSegments/{itemId}", mapOf("itemId" to movieId), emptyMap(), null)

        assertNull(delegate.path)
        val segments = sdkJson.decodeFromString(MediaSegmentDtoQueryResult.serializer(), response.body.decodeToString())
        assertTrue(segments.items.isEmpty())
    }

    @Test
    fun playbackReport_passesBodyThroughAndReturnsEmptyResponseUntouched() = runTest {
        val body = Any()
        delegate.respondWith("")

        val response = client.request(HttpMethod.POST, "/Sessions/Playing", emptyMap(), emptyMap(), body)

        assertEquals("/Sessions/Playing", delegate.path)
        assertSame(body, delegate.body)
        assertEquals(0, response.body.size)
    }

    @Test
    fun playbackReport_sendsEmbyItemIdInTheBody() = runTest {
        delegate.respondWith("")
        val report = PlaybackStartInfo(
            itemId = movieId,
            mediaSourceId = "mediasource_1035",
            positionTicks = 1_200L,
            canSeek = true,
            isPaused = false,
            isMuted = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT,
        )

        client.request(HttpMethod.POST, "/Sessions/Playing", emptyMap(), emptyMap(), report)

        val sent = delegate.body as JsonObject
        assertEquals("1035", sent["ItemId"]?.jsonPrimitive?.content)
        assertEquals("mediasource_1035", sent["MediaSourceId"]?.jsonPrimitive?.content)
        assertEquals(1_200L, sent["PositionTicks"]?.jsonPrimitive?.long)
        // What the SDK client will actually put on the wire for this body.
        assertTrue(ApiSerializer.encodeRequestBody(sent)!!.contains("\"ItemId\":\"1035\""))
    }

    @Test
    fun requestBodyWithoutEncodedIds_isSentAsTheOriginalObject() = runTest {
        delegate.respondWith("")
        val report = PlaybackStartInfo(
            itemId = userUuid,
            canSeek = true,
            isPaused = false,
            isMuted = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT,
        )

        client.request(HttpMethod.POST, "/Sessions/Playing", emptyMap(), emptyMap(), report)

        assertSame(report, delegate.body)
    }

    @Test
    fun getItems_normalizesChapterMarkersForIntroAndCredits() = runTest {
        delegate.respondWith(
            """{
                "Items": [{
                    "Name": "Episode 1",
                    "Id": "1035",
                    "Type": "Episode",
                    "Chapters": [
                        {"StartPositionTicks": 0, "Name": "Scene 1", "MarkerType": "IntroStart"},
                        {"StartPositionTicks": 900000000, "Name": "Scene 2", "MarkerType": "IntroEnd"},
                        {"StartPositionTicks": 24000000000, "Name": "End Scene", "MarkerType": "CreditsStart"},
                        {"StartPositionTicks": 25000000000, "Name": "Credits Scene", "MarkerType": "CreditsStart"}
                    ]
                }],
                "TotalRecordCount": 1
            }""",
        )

        val response = client.request(
            HttpMethod.GET,
            "/Items",
            emptyMap(),
            mapOf("userId" to userUuid, "ids" to listOf(movieId)),
            null,
        )

        val result = sdkJson.decodeFromString(BaseItemDtoQueryResult.serializer(), response.body.decodeToString())
        val item = result.items.single()
        val chapters = item.chapters!!
        assertEquals(4, chapters.size)
        assertEquals("Intro - Scene 1", chapters[0].name)
        assertEquals("Scene 2", chapters[1].name)
        assertEquals("Credits - End Scene", chapters[2].name)
        assertEquals("Credits Scene", chapters[3].name)
    }

    @Test
    fun getItemById_decodesBaseItemDtoAndNormalizesChapters() = runTest {
        delegate.respondWith(
            """{
                "Name": "Episode 1",
                "Id": "1035",
                "Type": "Episode",
                "Chapters": [
                    {"StartPositionTicks": 0, "Name": "", "MarkerType": "IntroStart"},
                    {"StartPositionTicks": 900000000, "Name": "Intro End", "MarkerType": "IntroEnd"},
                    {"StartPositionTicks": 24000000000, "Name": "Outro", "MarkerType": "CreditsStart"}
                ]
            }""",
        )

        val response = client.request(
            HttpMethod.GET,
            "/Items/{itemId}",
            mapOf("itemId" to movieId),
            emptyMap(),
            null,
        )

        assertEquals("/Items/{itemId}", delegate.path)
        assertEquals("1035", delegate.pathParameters["itemId"])
        val item = sdkJson.decodeFromString(BaseItemDto.serializer(), response.body.decodeToString())
        assertEquals(movieId, item.id)
        val chapters = item.chapters!!
        assertEquals("Intro", chapters[0].name)
        assertEquals("Intro End", chapters[1].name)
        assertEquals("Outro", chapters[2].name)
    }

    @Test
    fun unknownRoute_isForwardedUnchanged() = runTest {
        delegate.respondWith("""{"anything":true}""")

        val response = client.request(HttpMethod.GET, "/Some/Future/Route", emptyMap(), emptyMap(), null)

        assertEquals("/Some/Future/Route", delegate.path)
        assertEquals("""{"anything":true}""", response.body.decodeToString())
    }

    private class RecordingApiClient : ApiClient() {
        var method: HttpMethod? = null
        var path: String? = null
        var pathParameters: Map<String, Any?> = emptyMap()
        var query: Map<String, Any?> = emptyMap()
        var body: Any? = null
        private var responseBody = ""

        fun respondWith(json: String) {
            responseBody = json
        }

        override val baseUrl: String? = "https://emby.example.com"
        override val accessToken: String? = "token"
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
            return RawResponse(responseBody.toByteArray(), 200, emptyMap())
        }
    }

    private companion object {
        const val USER_ID = "e96573aa-cb14-4a45-b3c4-585e3e5071c7"
    }
}
