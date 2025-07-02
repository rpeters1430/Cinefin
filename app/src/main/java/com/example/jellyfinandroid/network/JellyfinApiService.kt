package com.example.jellyfinandroid.network

import com.example.jellyfinandroid.data.AuthenticationResult
import com.example.jellyfinandroid.data.ServerInfo
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface JellyfinApiService {
    
    @GET("System/Ping")
    suspend fun pingServer(): Response<String>
    
    @GET("System/Info")
    suspend fun getServerInfo(): Response<ServerInfo>
    
    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body request: AuthenticationRequest
    ): Response<AuthenticationResult>
    
    @GET("Users/{userId}/Items")
    suspend fun getUserItems(
        @Path("userId") userId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 100
    ): Response<ItemsResult>
    
    @GET("Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String
    ): Response<ItemsResult>
    
    @GET("Items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String
    ): Response<BaseItem>
}

@Serializable
data class AuthenticationRequest(
    val Username: String,
    val Pw: String
)

@Serializable
data class ItemsResult(
    val Items: List<BaseItem>,
    val TotalRecordCount: Int,
    val StartIndex: Int
)

@Serializable
data class BaseItem(
    val Id: String,
    val Name: String,
    val Type: String,
    val ServerId: String? = null,
    val ChannelId: String? = null,
    val Overview: String? = null,
    val Taglines: List<String> = emptyList(),
    val Genres: List<String> = emptyList(),
    val CommunityRating: Float? = null,
    val OfficialRating: String? = null,
    val ProductionYear: Int? = null,
    val PremiereDate: String? = null,
    val ExternalUrls: List<ExternalUrl> = emptyList(),
    val Path: String? = null,
    val EnableMediaSourceDisplay: Boolean = false,
    val DateCreated: String? = null,
    val DateLastMediaAdded: String? = null,
    val SpecialFeatureCount: Int? = null,
    val Movie3DFormat: String? = null,
    val PrimaryImageAspectRatio: Float? = null,
    val VideoType: String? = null,
    val ImageTags: ImageTags? = null,
    val BackdropImageTags: List<String> = emptyList(),
    val ScreenshotImageTags: List<String> = emptyList(),
    val ParentId: String? = null,
    val Chapters: List<Chapter> = emptyList(),
    val LocationType: String? = null,
    val IsoType: String? = null,
    val MediaType: String? = null,
    val EndDate: String? = null,
    val HomepageUrl: String? = null,
    val Budget: Double? = null,
    val Revenue: Double? = null,
    val ProviderIds: Map<String, String> = emptyMap(),
    val IsFolder: Boolean = false,
    val ParentIndexNumber: Int? = null,
    val IndexNumber: Int? = null,
    val ParentThumbItemId: String? = null,
    val ParentThumbImageTag: String? = null,
    val ParentPrimaryImageItemId: String? = null,
    val ParentPrimaryImageTag: String? = null,
    val People: List<Person> = emptyList(),
    val Studios: List<NameIdPair> = emptyList(),
    val GenreItems: List<NameIdPair> = emptyList(),
    val UserData: UserItemData? = null,
    val ChildCount: Int? = null,
    val SpecialEpisodeNumbers: List<Int> = emptyList(),
    val DisplayPreferencesId: String? = null,
    val Tags: List<String> = emptyList(),
    val PrimaryImageItemId: String? = null,
    val ThumbImageItemId: String? = null,
    val ThumbImageTag: String? = null,
    val PrimaryImageTag: String? = null,
    val ArtistItems: List<NameIdPair> = emptyList(),
    val Album: String? = null,
    val CollectionType: String? = null,
    val DisplayOrder: String? = null,
    val AlbumId: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val SeriesId: String? = null,
    val SeasonId: String? = null,
    val SpecialFeatureCount2: Int? = null,
    val SeriesName: String? = null,
    val SeriesPrimaryImageTag: String? = null,
    val SeasonName: String? = null,
    val VideoStreams: List<MediaStream> = emptyList(),
    val AudioStreams: List<MediaStream> = emptyList(),
    val SubtitleStreams: List<MediaStream> = emptyList(),
    val MediaStreams: List<MediaStream> = emptyList()
)

@Serializable
data class ExternalUrl(
    val Name: String,
    val Url: String
)

@Serializable
data class ImageTags(
    val Primary: String? = null,
    val Art: String? = null,
    val Backdrop: String? = null,
    val Banner: String? = null,
    val Logo: String? = null,
    val Thumb: String? = null,
    val Disc: String? = null,
    val Box: String? = null,
    val Screenshot: String? = null,
    val Menu: String? = null,
    val Chapter: String? = null,
    val BoxRear: String? = null,
    val Profile: String? = null
)

@Serializable
data class Chapter(
    val StartPositionTicks: Long,
    val Name: String? = null,
    val ImagePath: String? = null,
    val ImageDateModified: String? = null,
    val ImageTag: String? = null
)

@Serializable
data class Person(
    val Id: String? = null,
    val Name: String,
    val Role: String? = null,
    val Type: String,
    val PrimaryImageTag: String? = null
)

@Serializable
data class NameIdPair(
    val Id: String,
    val Name: String
)

@Serializable
data class UserItemData(
    val Rating: Float? = null,
    val PlayedPercentage: Float? = null,
    val UnplayedItemCount: Int? = null,
    val PlaybackPositionTicks: Long = 0,
    val PlayCount: Int = 0,
    val IsFavorite: Boolean = false,
    val Likes: Boolean? = null,
    val LastPlayedDate: String? = null,
    val Played: Boolean = false,
    val Key: String? = null,
    val ItemId: String? = null
)

@Serializable
data class MediaStream(
    val Codec: String? = null,
    val TimeBase: String? = null,
    val CodecTimeBase: String? = null,
    val VideoRange: String? = null,
    val DisplayTitle: String? = null,
    val IsInterlaced: Boolean = false,
    val BitRate: Int? = null,
    val BitDepth: Int? = null,
    val RefFrames: Int? = null,
    val IsDefault: Boolean = false,
    val IsForced: Boolean = false,
    val Height: Int? = null,
    val Width: Int? = null,
    val AverageFrameRate: Float? = null,
    val RealFrameRate: Float? = null,
    val Profile: String? = null,
    val Type: String,
    val AspectRatio: String? = null,
    val Index: Int,
    val IsExternal: Boolean = false,
    val IsTextSubtitleStream: Boolean = false,
    val SupportsExternalStream: Boolean = false,
    val Protocol: String? = null,
    val PixelFormat: String? = null,
    val Level: Int? = null,
    val IsAnamorphic: Boolean = false,
    val ExtendedVideoType: String? = null,
    val ExtendedVideoSubType: String? = null,
    val AttachmentSize: Int? = null,
    val MimeType: String? = null,
    val Language: String? = null,
    val Channels: Int? = null,
    val SampleRate: Int? = null,
    val IsAVC: Boolean? = null,
    val ChannelLayout: String? = null,
    val NalLengthSize: String? = null,
    val EncoderTag: String? = null,
    val ColorPrimaries: String? = null,
    val ColorSpace: String? = null,
    val ColorTransfer: String? = null,
    val DvVersionMajor: Int? = null,
    val DvVersionMinor: Int? = null,
    val DvProfile: Int? = null,
    val DvLevel: Int? = null,
    val RpuPresentFlag: Int? = null,
    val ElPresentFlag: Int? = null,
    val BlPresentFlag: Int? = null,
    val DvBlSignalCompatibilityId: Int? = null
)
