package com.rpeters.jellyfin.data.emby

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

/**
 * The JSON here follows the shape of responses captured from Emby 4.11.0.6, with names, paths
 * and IDs replaced.
 */
class EmbyJsonNormalizerTest {

    private val movieJson = """
        {
          "Name": "Sample Movie",
          "ServerId": "1cd2cfae841648e68c2384b35d829543",
          "Id": "1035",
          "ParentId": "5",
          "Type": "Movie",
          "MediaType": "Video",
          "IsFolder": false,
          "RunTimeTicks": 54000000000,
          "ProductionYear": 2024,
          "DateCreated": "2025-03-01T10:15:30.0000000Z",
          "Genres": ["Action"],
          "GenreItems": [{"Name": "Action", "Id": 10602}],
          "People": [{"Name": "Some Actor", "Id": "11782", "Role": "Lead", "Type": "Actor"}],
          "Studios": [{"Name": "Some Studio", "Id": 11781}],
          "ImageTags": {"Primary": "aaa", "Logo": "bbb", "SomeEmbyOnlyImage": "ccc"},
          "BackdropImageTags": ["ddd"],
          "LockedFields": ["Name", "SortName"],
          "UserData": {"PlaybackPositionTicks": 1200, "PlayCount": 2, "IsFavorite": true, "Played": false},
          "Chapters": [{"StartPositionTicks": 0, "Name": "Chapter 1", "MarkerType": "Chapter", "ChapterIndex": 0}],
          "MediaSources": [{
            "Protocol": "File",
            "Id": "mediasource_1035",
            "Type": "Default",
            "Container": "mkv",
            "IsRemote": false,
            "SupportsTranscoding": true,
            "SupportsDirectStream": true,
            "SupportsDirectPlay": true,
            "IsInfiniteStream": false,
            "RequiresOpening": false,
            "RequiresClosing": false,
            "RequiresLooping": false,
            "SupportsProbing": true,
            "ReadAtNativeFramerate": false,
            "MediaStreams": [{
              "Codec": "hevc", "Type": "Video", "Index": 0, "Width": 1920, "Height": 804,
              "IsInterlaced": false, "IsDefault": true, "IsForced": false, "IsHearingImpaired": false,
              "IsExternal": false, "IsTextSubtitleStream": false, "SupportsExternalStream": false
            }]
          }],
          "EmbyOnlyField": {"anything": 1}
        }
    """.trimIndent()

    private val authJson = """
        {
          "User": {
            "Name": "tester",
            "ServerId": "1cd2cfae841648e68c2384b35d829543",
            "Id": "e96573aacb144a45b3c4585e3e5071c7",
            "HasPassword": true,
            "HasConfiguredPassword": true,
            "Configuration": {"PlayDefaultAudioTrack": true, "DisplayMissingEpisodes": false, "SubtitleMode": "Smart"},
            "Policy": {"IsAdministrator": true, "IsHidden": false, "IsDisabled": false}
          },
          "SessionInfo": {
            "PlayState": {"CanSeek": false, "IsPaused": false, "IsMuted": false, "RepeatMode": "RepeatNone"},
            "Id": "0123456789abcdef0123456789abcdef",
            "UserId": "e96573aacb144a45b3c4585e3e5071c7",
            "Client": "Cinefin",
            "LastActivityDate": "2026-10-08T23:00:00.0000000Z",
            "DeviceName": "Pixel",
            "DeviceId": "device-1",
            "ApplicationVersion": "1.0",
            "SupportsRemoteControl": false
          },
          "AccessToken": "token-value",
          "ServerId": "1cd2cfae841648e68c2384b35d829543"
        }
    """.trimIndent()

    @Test
    fun rawEmbyItem_doesNotDecodeWithSdkSerializer() {
        val sdkJson = Json { ignoreUnknownKeys = true }
        try {
            sdkJson.decodeFromString(BaseItemDto.serializer(), movieJson)
            fail("Expected the raw Emby item to be rejected; the normalizer would be unnecessary otherwise")
        } catch (expected: SerializationException) {
            // The reason this class exists.
        } catch (expected: IllegalArgumentException) {
            // UUID parsing failures surface as IllegalArgumentException.
        }
    }

    @Test
    fun decode_embyMovie_mapsNumericIdsToCodecUuids() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), movieJson)

        assertEquals("1035", ServerIdCodec.decode(item.id))
        assertEquals("5", item.parentId?.let(ServerIdCodec::decode))
        assertEquals("11782", item.people?.single()?.id?.let(ServerIdCodec::decode))
        assertEquals("10602", item.genreItems?.single()?.id?.let(ServerIdCodec::decode))
        assertEquals("11781", item.studios?.single()?.id?.let(ServerIdCodec::decode))
    }

    @Test
    fun decode_embyMovie_keepsOrdinaryFields() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), movieJson)

        assertEquals("Sample Movie", item.name)
        assertEquals(BaseItemKind.MOVIE, item.type)
        assertEquals(54_000_000_000L, item.runTimeTicks)
        assertEquals(2024, item.productionYear)
        assertNotNull(item.dateCreated)
        assertEquals(listOf("Action"), item.genres)
        assertEquals(listOf("ddd"), item.backdropImageTags)
    }

    @Test
    fun decode_embyMovie_fillsUserDataItemIdFromTheItem() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), movieJson)

        val userData = item.userData
        assertNotNull(userData)
        assertEquals(item.id, userData?.itemId)
        assertEquals(1200L, userData?.playbackPositionTicks)
        assertEquals(2, userData?.playCount)
        assertTrue(userData?.isFavorite == true)
        assertFalse(userData?.played == true)
    }

    @Test
    fun decode_embyMovie_dropsEnumValuesAndImageTypesTheSdkDoesNotKnow() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), movieJson)

        assertEquals(setOf(ImageType.PRIMARY, ImageType.LOGO), item.imageTags?.keys)
        assertEquals(1, item.lockedFields?.size)
    }

    @Test
    fun decode_embyMovie_defaultsRequiredMediaSourceFieldsEmbyOmits() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), movieJson)

        val source = item.mediaSources?.single()
        assertEquals("mediasource_1035", source?.id)
        assertEquals("mkv", source?.container)
        assertTrue(source?.supportsDirectPlay == true)
        val video = source?.mediaStreams?.single()
        assertEquals(MediaStreamType.VIDEO, video?.type)
        assertEquals("hevc", video?.codec)
        assertEquals(1920, video?.width)
    }

    @Test
    fun decode_itemWithUnknownType_getsPlaceholderTypeNotMovie() {
        val unknown = movieJson.replace("\"Type\": \"Movie\"", "\"Type\": \"SomeFutureEmbyType\"")

        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), unknown)

        // The SDK requires a type; the first enum constant is used so one odd item cannot fail
        // a whole page. It must not be reported as a movie.
        assertFalse(item.type == BaseItemKind.MOVIE)
    }

    @Test
    fun decode_embySignIn_producesSdkAuthenticationResult() {
        val result = EmbyJsonNormalizer.decode(AuthenticationResult.serializer(), authJson)

        assertEquals("token-value", result.accessToken)
        assertEquals("1cd2cfae841648e68c2384b35d829543", result.serverId)
        assertEquals("tester", result.user?.name)
        assertEquals(UUID.fromString("e96573aa-cb14-4a45-b3c4-585e3e5071c7"), result.user?.id)
        assertTrue(result.user?.policy?.isAdministrator == true)
        assertFalse(ServerIdCodec.isEncoded(result.user!!.id))
    }

    @Test
    fun decode_minimalItem_onlyIdAndType_succeeds() {
        val item = EmbyJsonNormalizer.decode(BaseItemDto.serializer(), """{"Id":"42","Type":"Series"}""")

        assertEquals("42", ServerIdCodec.decode(item.id))
        assertEquals(BaseItemKind.SERIES, item.type)
        assertNull(item.userData)
    }
}
