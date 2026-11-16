package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID

/**
 * Lets the Jellyfin SDK's typed APIs talk to a Plex Media Server (KD3).
 *
 * Implements a route table of handlers keyed on SDK pathTemplate. Handlers make native Plex
 * requests through [delegate] and return Jellyfin-shaped models mapped by [PlexMapper].
 */
class PlexApiClient(
    private val delegate: ApiClient,
    private val currentUserProvider: () -> UserDto? = { null },
    private val serverProvider: () -> JellyfinServer? = { null },
) : ApiClient() {

    override val baseUrl: String? get() = delegate.baseUrl
    override val accessToken: String? get() = delegate.accessToken
    override val clientInfo: ClientInfo get() = delegate.clientInfo
    override val deviceInfo: DeviceInfo get() = delegate.deviceInfo
    override val httpClientOptions: HttpClientOptions get() = delegate.httpClientOptions
    override val webSocket: SocketApi get() = delegate.webSocket

    override fun update(baseUrl: String?, accessToken: String?, clientInfo: ClientInfo, deviceInfo: DeviceInfo) =
        delegate.update(baseUrl, accessToken, clientInfo, deviceInfo)

    override suspend fun request(
        method: HttpMethod,
        pathTemplate: String,
        pathParameters: Map<String, Any?>,
        queryParameters: Map<String, Any?>,
        requestBody: Any?,
    ): RawResponse {
        val handler = routes[pathTemplate]
        if (handler != null) {
            return handler(method, pathParameters, queryParameters, requestBody)
        }

        SecureLogger.w(TAG, "Unhandled Plex route: $pathTemplate")
        if (BuildConfig.DEBUG) {
            throw UnsupportedOperationException("Unhandled Plex route: $pathTemplate")
        }
        return RawResponse(EMPTY_BODY, HTTP_OK, emptyMap())
    }

    internal suspend fun executePlexRequest(
        method: HttpMethod = HttpMethod.GET,
        path: String,
        pathParams: Map<String, Any?> = emptyMap(),
        queryParams: Map<String, Any?> = emptyMap(),
        body: Any? = null,
    ): RawResponse {
        val query = queryParams.toMutableMap()
        val token = delegate.accessToken
        if (!token.isNullOrBlank() && !query.containsKey(PlexClientHeaders.TOKEN)) {
            query[PlexClientHeaders.TOKEN] = token
        }
        return delegate.request(method, path, pathParams, query, body)
    }

    private fun <T> jsonResponse(
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
        headers: Map<String, List<String>> = emptyMap(),
    ): RawResponse {
        val body = json.encodeToString(serializer, value).toByteArray()
        return RawResponse(body, HTTP_OK, headers)
    }

    private val handlePublicSystemInfo: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val response = executePlexRequest(path = "/identity")
            val identity = PlexIdentityProbe.parse(response.body.decodeToString())
            val server = serverProvider()
            val info = PublicSystemInfo(
                id = identity?.machineIdentifier ?: server?.id ?: UUID.randomUUID().toString(),
                serverName = server?.name ?: "Plex Media Server",
                version = identity?.version ?: "1.0",
                productName = "Plex Media Server",
                operatingSystem = "Plex",
                startupWizardCompleted = true,
            )
            jsonResponse(PublicSystemInfo.serializer(), info, response.headers)
        }

    private val handleSystemInfo: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val response = executePlexRequest(path = "/identity")
            val identity = PlexIdentityProbe.parse(response.body.decodeToString())
            val server = serverProvider()
            val id = identity?.machineIdentifier ?: server?.id ?: UUID.randomUUID().toString()
            val serverName = server?.name ?: "Plex Media Server"
            val version = identity?.version ?: "1.0"
            val jsonPayload = """{"Id":"$id","ServerName":"$serverName","Version":"$version","ProductName":"Plex Media Server","OperatingSystem":"Plex","StartupWizardCompleted":true,"HasPendingRestart":false,"IsShuttingDown":false,"SupportsLibraryMonitor":false,"WebSocketPortNumber":0}"""
            val info = json.decodeFromString(SystemInfo.serializer(), jsonPayload)
            jsonResponse(SystemInfo.serializer(), info, response.headers)
        }

    private val handleUsersMe: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val server = serverProvider()
            val user = currentUserProvider() ?: UserDto(
                name = server?.username ?: "Plex User",
                id = server?.userId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID(),
                serverId = server?.id,
                hasPassword = false,
            )
            jsonResponse(UserDto.serializer(), user)
        }

    private val handleUserViews: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val response = executePlexRequest(path = "/library/sections")
            val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
            val queryResult = PlexMapper.toQueryResult(container)
            jsonResponse(BaseItemDtoQueryResult.serializer(), queryResult, response.headers)
        }

    private val handleItems: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, queryParameters, _ ->
            val params = PlexQueryPlanner.fromQueryMap(queryParameters)
            val plan = PlexQueryPlanner.plan(params)
            if (plan.isStaticEmpty) {
                jsonResponse(
                    BaseItemDtoQueryResult.serializer(),
                    BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
                )
            } else {
                val response = executePlexRequest(path = plan.path, queryParams = plan.queryParameters)
                val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
                val queryResult = PlexMapper.toQueryResult(container)
                jsonResponse(BaseItemDtoQueryResult.serializer(), queryResult, response.headers)
            }
        }

    private val handleItemById: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { method, pathParameters, _, _ ->
            val rawId = pathParameters["itemId"]?.toString() ?: ""
            val rk = ServerIdCodec.decode(rawId)
            if (method == HttpMethod.DELETE) {
                val response = executePlexRequest(method = HttpMethod.DELETE, path = "/library/metadata/$rk")
                RawResponse(EMPTY_BODY, response.status, response.headers)
            } else {
                val response = executePlexRequest(
                    path = "/library/metadata/$rk",
                    queryParams = mapOf(
                        "includeChapters" to 1,
                        "includeMarkers" to 1,
                        "includeExtras" to 1,
                    ),
                )
                val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
                val metadata = container.metadata.firstOrNull()
                    ?: throw NoSuchElementException("Plex item not found: $rk")
                val item = PlexMapper.toBaseItemDto(metadata)
                jsonResponse(BaseItemDto.serializer(), item, response.headers)
            }
        }

    private val handleSimilar: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val response = executePlexRequest(path = "/library/metadata/$rk/similar")
            val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
            val queryResult = PlexMapper.toQueryResult(container)
            jsonResponse(BaseItemDtoQueryResult.serializer(), queryResult, response.headers)
        }

    private val handleNextUp: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val response = executePlexRequest(path = "/library/onDeck")
            val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
            val episodes = container.metadata.filter { it.type.equals("episode", ignoreCase = true) }
            val queryResult = PlexMapper.toQueryResult(container.copy(metadata = episodes))
            jsonResponse(BaseItemDtoQueryResult.serializer(), queryResult, response.headers)
        }

    private val handleLocalTrailers: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val response = executePlexRequest(
                path = "/library/metadata/$rk",
                queryParams = mapOf("includeExtras" to 1),
            )
            jsonResponse(ListSerializer(BaseItemDto.serializer()), emptyList(), response.headers)
        }

    private val handleUserPlayed: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { method, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val plexPath = if (method == HttpMethod.DELETE) "/:/unscrobble" else "/:/scrobble"
            val response = executePlexRequest(
                path = plexPath,
                queryParams = mapOf(
                    "identifier" to "com.plexapp.plugins.library",
                    "key" to rk,
                ),
            )
            val played = method != HttpMethod.DELETE
            val userData = UserItemDataDto(
                playbackPositionTicks = 0L,
                playCount = if (played) 1 else 0,
                played = played,
                isFavorite = false,
                key = rk,
                itemId = ServerIdCodec.PLEX.encode(rk) ?: UUID.randomUUID(),
            )
            jsonResponse(UserItemDataDto.serializer(), userData, response.headers)
        }

    private val handleItemUserData: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val response = executePlexRequest(path = "/library/metadata/$rk")
            val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
            val metadata = container.metadata.firstOrNull()
            val userData = metadata?.let { PlexMapper.toUserDataDto(it) } ?: UserItemDataDto(
                playbackPositionTicks = 0L,
                playCount = 0,
                played = false,
                isFavorite = false,
                key = rk,
                itemId = ServerIdCodec.PLEX.encode(rk) ?: UUID.randomUUID(),
            )
            jsonResponse(UserItemDataDto.serializer(), userData, response.headers)
        }

    private val handleFavoriteItem: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val userData = UserItemDataDto(
                playbackPositionTicks = 0L,
                playCount = 0,
                played = false,
                isFavorite = false,
                key = rk,
                itemId = ServerIdCodec.PLEX.encode(rk) ?: UUID.randomUUID(),
            )
            jsonResponse(UserItemDataDto.serializer(), userData)
        }

    private val handleRefresh: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            val response = executePlexRequest(path = "/library/sections/all/refresh")
            RawResponse(EMPTY_BODY, response.status, response.headers)
        }

    private val handleSessions: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, _, _, _ ->
            jsonResponse(ListSerializer(SessionInfoDto.serializer()), emptyList())
        }

    private val handlePlaybackInfo: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, queryParameters, requestBody ->
            val rawId = pathParameters["itemId"]?.toString() ?: ""
            val rk = ServerIdCodec.decode(rawId)

            val audioIndex = when (requestBody) {
                is PlaybackInfoDto -> requestBody.audioStreamIndex
                else -> (queryParameters["audioStreamIndex"] as? Number)?.toInt()
            }
            val subtitleIndex = when (requestBody) {
                is PlaybackInfoDto -> requestBody.subtitleStreamIndex
                else -> (queryParameters["subtitleStreamIndex"] as? Number)?.toInt()
            }
            val maxBitrate = when (requestBody) {
                is PlaybackInfoDto -> requestBody.maxStreamingBitrate
                else -> (queryParameters["maxStreamingBitrate"] as? Number)?.toInt()
            }

            // 1. Fetch metadata first to know partId and stream IDs
            val metaResponse = executePlexRequest(
                path = "/library/metadata/$rk",
                queryParams = mapOf(
                    "includeChapters" to 1,
                    "includeMarkers" to 1,
                ),
            )
            checkRemotePaywall(metaResponse)

            val metaContainer = json.decodeFromString<PlexMediaContainerResponse>(metaResponse.body.decodeToString()).mediaContainer
            val metadata = metaContainer.metadata.firstOrNull()
                ?: throw NoSuchElementException("Plex item not found for playback: $rk")
            val part = metadata.media.firstOrNull()?.parts?.firstOrNull()

            // 2. Audio/subtitle stream selection via PUT /library/parts/{partId}
            if (part?.id != null && (audioIndex != null || subtitleIndex != null)) {
                val putParams = mutableMapOf<String, Any>()
                if (audioIndex != null) {
                    val audioStream = part.streams.firstOrNull { it.streamType == 2 && it.index == audioIndex }
                    if (audioStream?.id != null) {
                        putParams["audioStreamID"] = audioStream.id
                    }
                }
                if (subtitleIndex != null) {
                    if (subtitleIndex < 0) {
                        putParams["subtitleStreamID"] = 0
                    } else {
                        val subStream = part.streams.firstOrNull { it.streamType == 3 && it.index == subtitleIndex }
                        putParams["subtitleStreamID"] = subStream?.id ?: 0
                    }
                }
                if (putParams.isNotEmpty()) {
                    runCatching {
                        executePlexRequest(
                            method = HttpMethod.POST,
                            path = "/library/parts/${part.id}",
                            queryParams = putParams,
                        )
                    }
                }
            }

            // 3. Call universal decision endpoint
            val session = UUID.randomUUID().toString()
            val decisionParams = mutableMapOf<String, Any>(
                "path" to "/library/metadata/$rk",
                "mediaIndex" to 0,
                "partIndex" to 0,
                "protocol" to "hls",
                "fastSeek" to 1,
                "directPlay" to 1,
                "directStream" to 1,
                "subtitleSize" to 100,
                "audioBoost" to 100,
                "autoAdjustQuality" to 0,
                "copyts" to 1,
                "session" to session,
            )
            if (maxBitrate != null && maxBitrate > 0) {
                decisionParams["maxVideoBitrate"] = maxBitrate / 1000
            }

            val decisionResponse = executePlexRequest(
                path = "/video/:/transcode/universal/decision",
                queryParams = decisionParams,
            )
            checkRemotePaywall(decisionResponse)

            val decisionContainer = runCatching {
                json.decodeFromString<PlexMediaContainerResponse>(decisionResponse.body.decodeToString()).mediaContainer
            }.getOrNull()

            val decisionMeta = decisionContainer?.metadata?.firstOrNull() ?: metadata
            val decisionPart = decisionMeta.media.firstOrNull()?.parts?.firstOrNull() ?: part

            val decision = decisionPart?.decision?.lowercase()
            val supportsDirectPlay = decision == null || decision == "directplay"
            val supportsDirectStream = decision == null || decision != "transcode"
            val supportsTranscoding = true

            val server = serverProvider() ?: JellyfinServer(id = "plex", name = "Plex", url = delegate.baseUrl ?: "")
            val token = delegate.accessToken ?: server.accessToken ?: ""
            val directPlayUrl = PlexUrlBuilder.buildDirectPlayStreamUrl(server, rk, decisionPart?.key)
            val transcodeUrl = PlexUrlBuilder.buildTranscodeStreamUrl(server, rk, session = session, maxBitrate = maxBitrate)

            // Map streams
            val mediaStreams = decisionPart?.streams.orEmpty().map { stream ->
                val streamType = when (stream.streamType) {
                    1 -> MediaStreamType.VIDEO
                    2 -> MediaStreamType.AUDIO
                    3 -> MediaStreamType.SUBTITLE
                    else -> MediaStreamType.EMBEDDED_IMAGE
                }
                val streamKey = stream.key
                val isExternal = streamKey != null
                val deliveryUrl = if (streamKey != null) {
                    val subPath = if (streamKey.startsWith("/")) streamKey else "/$streamKey"
                    "${server.url.trimEnd('/')}$subPath?${PlexTokenUrls.TOKEN_PARAMETER}=$token"
                } else null

                MediaStream(
                    codec = stream.codec,
                    index = stream.index ?: 0,
                    type = streamType,
                    language = stream.language,
                    displayTitle = stream.displayTitle ?: stream.extendedDisplayTitle ?: stream.title,
                    title = stream.title,
                    isDefault = stream.default == true,
                    isForced = false,
                    isHearingImpaired = false,
                    isOriginal = false,
                    isInterlaced = false,
                    isTextSubtitleStream = stream.codec != "pgs" && stream.codec != "vobsub" && stream.codec != "dvdsub",
                    supportsExternalStream = true,
                    channels = stream.channels,
                    deliveryMethod = if (stream.streamType == 3) {
                        if (isExternal) SubtitleDeliveryMethod.EXTERNAL else SubtitleDeliveryMethod.EMBED
                    } else null,
                    deliveryUrl = deliveryUrl,
                    isExternal = isExternal,
                )
            }

            val defaultAudioIndex = audioIndex ?: decisionPart?.streams?.firstOrNull { it.streamType == 2 && (it.selected == true || it.default == true) }?.index
                ?: decisionPart?.streams?.firstOrNull { it.streamType == 2 }?.index
            val defaultSubIndex = subtitleIndex ?: decisionPart?.streams?.firstOrNull { it.streamType == 3 && (it.selected == true || it.default == true) }?.index

            val mediaSource = MediaSourceInfo(
                protocol = MediaProtocol.HTTP,
                id = decisionPart?.id?.toString() ?: rk,
                path = directPlayUrl,
                type = MediaSourceType.DEFAULT,
                container = decisionPart?.container ?: "mp4",
                size = decisionPart?.size,
                name = decisionMeta.title,
                isRemote = false,
                runTimeTicks = (decisionPart?.duration ?: decisionMeta.duration)?.let { it * 10_000L },
                readAtNativeFramerate = false,
                ignoreDts = false,
                ignoreIndex = false,
                genPtsInput = false,
                supportsTranscoding = supportsTranscoding,
                supportsDirectStream = supportsDirectStream,
                supportsDirectPlay = supportsDirectPlay,
                isInfiniteStream = false,
                requiresOpening = false,
                requiresClosing = false,
                requiresLooping = false,
                supportsProbing = true,
                bitrate = decisionMeta.media.firstOrNull()?.bitrate?.let { it * 1000 },
                transcodingUrl = transcodeUrl,
                transcodingSubProtocol = MediaStreamProtocol.HLS,
                transcodingContainer = "ts",
                hasSegments = true,
                mediaStreams = mediaStreams,
                defaultAudioStreamIndex = defaultAudioIndex,
                defaultSubtitleStreamIndex = defaultSubIndex,
            )

            val playbackInfo = PlaybackInfoResponse(
                mediaSources = listOf(mediaSource),
                playSessionId = session,
                errorCode = null,
            )
            jsonResponse(PlaybackInfoResponse.serializer(), playbackInfo, decisionResponse.headers)
        }

    private val handleMediaSegments: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, _ ->
            val rk = ServerIdCodec.decode(pathParameters["itemId"]?.toString() ?: "")
            val response = executePlexRequest(
                path = "/library/metadata/$rk",
                queryParams = mapOf("includeMarkers" to 1),
            )
            val container = json.decodeFromString<PlexMediaContainerResponse>(response.body.decodeToString()).mediaContainer
            val metadata = container.metadata.firstOrNull()
            val itemUuid = ServerIdCodec.PLEX.encode(rk) ?: UUID.randomUUID()
            val segments = metadata?.markers.orEmpty().mapNotNull { marker ->
                val segmentType = when (marker.type.lowercase()) {
                    "intro" -> MediaSegmentType.INTRO
                    "credits" -> MediaSegmentType.OUTRO
                    else -> null
                } ?: return@mapNotNull null
                MediaSegmentDto(
                    id = UUID.nameUUIDFromBytes("$itemUuid/${marker.id ?: marker.type}".toByteArray()),
                    itemId = itemUuid,
                    type = segmentType,
                    startTicks = marker.startTimeOffset * 10_000L,
                    endTicks = marker.endTimeOffset * 10_000L,
                )
            }
            val queryResult = MediaSegmentDtoQueryResult(
                items = segments,
                totalRecordCount = segments.size,
                startIndex = 0,
            )
            jsonResponse(MediaSegmentDtoQueryResult.serializer(), queryResult, response.headers)
        }

    private val handleTimeline: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, requestBody ->
            val info = extractTimelineInfo(requestBody)
            val rk = ServerIdCodec.decode(info.itemId ?: pathParameters["itemId"]?.toString() ?: "")
            val timeMs = (info.positionTicks ?: 0L) / 10_000L

            if (rk.isNotBlank()) {
                val state = if (info.isPaused) "paused" else "playing"
                runCatching {
                    executePlexRequest(
                        path = "/:/timeline",
                        queryParams = mapOf(
                            "ratingKey" to rk,
                            "key" to "/library/metadata/$rk",
                            "state" to state,
                            "time" to timeMs,
                        ),
                    )
                }
            }
            RawResponse(EMPTY_BODY, HTTP_OK, emptyMap())
        }

    private val handleTimelineStopped: suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse =
        { _, pathParameters, _, requestBody ->
            val info = extractTimelineInfo(requestBody)
            val rk = ServerIdCodec.decode(info.itemId ?: pathParameters["itemId"]?.toString() ?: "")
            val timeMs = (info.positionTicks ?: 0L) / 10_000L

            if (rk.isNotBlank()) {
                runCatching {
                    executePlexRequest(
                        path = "/:/timeline",
                        queryParams = mapOf(
                            "ratingKey" to rk,
                            "key" to "/library/metadata/$rk",
                            "state" to "stopped",
                            "time" to timeMs,
                        ),
                    )
                }
            }
            if (!info.sessionId.isNullOrBlank()) {
                runCatching {
                    executePlexRequest(
                        path = "/video/:/transcode/universal/stop",
                        queryParams = mapOf("session" to info.sessionId),
                    )
                }
            }
            RawResponse(EMPTY_BODY, HTTP_OK, emptyMap())
        }

    private fun checkRemotePaywall(response: RawResponse) {
        val body = response.body.decodeToString()
        if (response.status == 403 || response.status == 402 ||
            body.contains("Plex Pass", ignoreCase = true) ||
            body.contains("Remote Watch Pass", ignoreCase = true)
        ) {
            throw IllegalStateException("This server needs Plex Pass or Remote Watch Pass for streaming outside its home network")
        }
    }

    private fun extractTimelineInfo(body: Any?): TimelineInfo {
        return when (body) {
            is PlaybackStartInfo -> TimelineInfo(
                itemId = body.itemId.toString(),
                positionTicks = body.positionTicks,
                isPaused = body.isPaused,
                sessionId = body.playSessionId ?: body.sessionId,
            )
            is PlaybackProgressInfo -> TimelineInfo(
                itemId = body.itemId.toString(),
                positionTicks = body.positionTicks,
                isPaused = body.isPaused,
                sessionId = body.playSessionId ?: body.sessionId,
            )
            is PlaybackStopInfo -> TimelineInfo(
                itemId = body.itemId.toString(),
                positionTicks = body.positionTicks,
                isPaused = false,
                sessionId = body.playSessionId ?: body.sessionId,
            )
            is JsonElement -> {
                val obj = body as? JsonObject
                val itemId = (obj?.get("ItemId") as? JsonPrimitive)?.content
                val pos = (obj?.get("PositionTicks") as? JsonPrimitive)?.longOrNull
                val paused = (obj?.get("IsPaused") as? JsonPrimitive)?.booleanOrNull ?: false
                val session = (obj?.get("PlaySessionId") as? JsonPrimitive)?.content
                    ?: (obj?.get("SessionId") as? JsonPrimitive)?.content
                TimelineInfo(itemId, pos, paused, session)
            }
            is String -> {
                val elem = runCatching { json.parseToJsonElement(body) }.getOrNull()
                extractTimelineInfo(elem)
            }
            else -> TimelineInfo(null, null, false, null)
        }
    }

    private data class TimelineInfo(
        val itemId: String?,
        val positionTicks: Long?,
        val isPaused: Boolean,
        val sessionId: String?,
    )

    private val routes: Map<String, suspend (HttpMethod, Map<String, Any?>, Map<String, Any?>, Any?) -> RawResponse> = mapOf(
        "/System/Info/Public" to handlePublicSystemInfo,
        "/System/Info" to handleSystemInfo,
        "/Users/Me" to handleUsersMe,
        "/UserViews" to handleUserViews,
        "/Items" to handleItems,
        "/Items/{itemId}" to handleItemById,
        "/Items/{itemId}/Similar" to handleSimilar,
        "/Shows/NextUp" to handleNextUp,
        "/Items/{itemId}/LocalTrailers" to handleLocalTrailers,
        "/UserPlayedItems/{itemId}" to handleUserPlayed,
        "/UserItems/{itemId}/UserData" to handleItemUserData,
        "/UserFavoriteItems/{itemId}" to handleFavoriteItem,
        "/Library/Refresh" to handleRefresh,
        "/Sessions" to handleSessions,
        "/Items/{itemId}/PlaybackInfo" to handlePlaybackInfo,
        "/MediaSegments/{itemId}" to handleMediaSegments,
        "/Sessions/Playing" to handleTimeline,
        "/Sessions/Playing/Progress" to handleTimeline,
        "/Sessions/Playing/Stopped" to handleTimelineStopped,
    )

    private companion object {
        const val TAG = "PlexApiClient"
        const val HTTP_OK = 200
        val EMPTY_BODY = byteArrayOf()
        val json = Json { ignoreUnknownKeys = true }
    }
}
