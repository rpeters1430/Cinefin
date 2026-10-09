package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID

/**
 * Lets the Jellyfin SDK's typed APIs (`client.libraryApi.getItems(...)` and so on) talk to an
 * Emby server, so the repositories do not need an Emby branch per call.
 *
 * Every SDK call funnels through [request]. For an Emby server this:
 *  1. turns [ServerIdCodec] UUIDs in the path and query back into Emby's numeric item IDs;
 *  2. rewrites the few routes Emby names differently (see [EmbyRoute]);
 *  3. sends the request through the ordinary SDK client, whose auth header Emby accepts;
 *  4. rewrites the JSON response with [EmbyJsonNormalizer] so the SDK can decode it.
 *
 * Emby answers most Jellyfin-shaped routes as they are, including `/Items?userId=…` with
 * camelCase query names (checked against Emby 4.11), which is why the route table is short.
 *
 * @param delegate a plain SDK client for the same server and token.
 * @param userId the signed-in user, used for routes where Emby wants it in the path.
 */
class EmbyApiClient(
    private val delegate: ApiClient,
    private val userId: String?,
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
        val route = EmbyRoute.find(pathTemplate)
        route?.cannedResponse?.let { return RawResponse(it.toByteArray(), HTTP_OK, emptyMap()) }

        val embyPathParameters = pathParameters.mapValues { toEmbyParameter(it.value) }.toMutableMap()
        val embyQueryParameters = queryParameters.mapValues { toEmbyParameter(it.value) }.toMutableMap()
        if (route?.listsItems == true) {
            embyQueryParameters[FIELDS_PARAMETER] = withListFields(embyQueryParameters[FIELDS_PARAMETER])
        }
        val embyPath = route?.embyPath ?: pathTemplate
        if (USER_ID_PLACEHOLDER in embyPath) {
            embyPathParameters[USER_ID_PARAMETER] = embyQueryParameters[USER_ID_PARAMETER]?.toString() ?: userId
                ?: throw IllegalStateException("No signed-in user for Emby request $pathTemplate")
        }

        val response = delegate.request(method, embyPath, embyPathParameters, embyQueryParameters, requestBody)
        val serializer = route?.response
        if (serializer == null || response.body.isEmpty()) {
            if (route == null) SecureLogger.w(TAG, "No Emby route for $pathTemplate; response passed through unchanged")
            return response
        }

        val itemId = embyPathParameters[ITEM_ID_PARAMETER]?.toString()
        val parsed = json.parseToJsonElement(response.body.decodeToString())
        val shaped = route.reshape(parsed, itemId)
        val normalized = EmbyJsonNormalizer.normalize(serializer.descriptor, shaped)
        return RawResponse(normalized.toString().toByteArray(), response.status, response.headers)
    }

    /** IDs go out as Emby knows them; everything else is left for the SDK to format. */
    private fun toEmbyParameter(value: Any?): Any? = when (value) {
        is UUID -> ServerIdCodec.decode(value)
        is String -> ServerIdCodec.decode(value)
        is Iterable<*> -> value.map(::toEmbyParameter)
        else -> value
    }

    /**
     * Jellyfin puts the year, ratings and dates on every item in a list. Emby only returns them
     * when they are named in `fields`, so ask for them on every item query.
     */
    private fun withListFields(requested: Any?): List<Any> {
        val current = (requested as? Iterable<*>)?.filterNotNull().orEmpty()
        val alreadyRequested = current.mapTo(HashSet()) { it.toString() }
        return current + LIST_FIELDS.filterNot { it in alreadyRequested }
    }

    private companion object {
        const val TAG = "EmbyApiClient"
        const val FIELDS_PARAMETER = "fields"
        val LIST_FIELDS = listOf(
            "ProductionYear",
            "PremiereDate",
            "EndDate",
            "CommunityRating",
            "CriticRating",
            "OfficialRating",
            "Status",
            "ChildCount",
            "RecursiveItemCount",
            "Container",
        )
        const val HTTP_OK = 200
        const val USER_ID_PARAMETER = "userId"
        const val USER_ID_PLACEHOLDER = "{userId}"
        const val ITEM_ID_PARAMETER = "itemId"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * How one SDK route is served by Emby.
 *
 * @param embyPath the Emby path template when it differs from the SDK's; `{userId}` is filled in.
 * @param response the SDK type the caller will decode, which decides how the JSON is normalized.
 *   Null for routes with no response body.
 * @param cannedResponse returned without contacting the server, for features Emby does not have.
 * @param reshape adjusts the parsed response before normalizing; receives the request's item ID.
 * @param listsItems true for item queries, which need the list fields Emby omits by default.
 */
internal class EmbyRoute(
    val embyPath: String? = null,
    val response: KSerializer<*>? = null,
    val cannedResponse: String? = null,
    val listsItems: Boolean = false,
    val reshape: (JsonElement, String?) -> JsonElement = { element, _ -> element },
) {
    companion object {
        private val items = EmbyRoute(response = BaseItemDtoQueryResult.serializer(), listsItems = true)

        /** Emby leaves `ItemId` out of user data; the SDK requires it. */
        private val userData = { element: JsonElement, itemId: String? ->
            if (element is JsonObject && itemId != null && "ItemId" !in element) {
                JsonObject(element + ("ItemId" to JsonPrimitive(itemId)))
            } else {
                element
            }
        }

        // Keyed by the SDK's path template.
        private val routes: Map<String, EmbyRoute> = mapOf(
            // Served by Emby under the same path.
            "/Items" to items,
            "/Items/{itemId}/Similar" to items,
            "/Shows/NextUp" to items,
            "/Items/{itemId}/PlaybackInfo" to EmbyRoute(response = PlaybackInfoResponse.serializer()),
            "/System/Info" to EmbyRoute(response = SystemInfo.serializer()),
            "/System/Info/Public" to EmbyRoute(response = PublicSystemInfo.serializer()),
            "/Sessions" to EmbyRoute(response = ListSerializer(SessionInfoDto.serializer())),
            "/Sessions/Playing" to EmbyRoute(),
            "/Sessions/Playing/Progress" to EmbyRoute(),
            "/Sessions/Playing/Stopped" to EmbyRoute(),
            "/Items/{itemId}" to EmbyRoute(),
            "/Library/Refresh" to EmbyRoute(),

            // Named differently on Emby: the user is part of the path.
            "/UserViews" to EmbyRoute(embyPath = "/Users/{userId}/Views", response = BaseItemDtoQueryResult.serializer()),
            "/Users/Me" to EmbyRoute(embyPath = "/Users/{userId}", response = UserDto.serializer()),
            "/UserFavoriteItems/{itemId}" to EmbyRoute(
                embyPath = "/Users/{userId}/FavoriteItems/{itemId}",
                response = UserItemDataDto.serializer(),
                reshape = userData,
            ),
            "/UserPlayedItems/{itemId}" to EmbyRoute(
                embyPath = "/Users/{userId}/PlayedItems/{itemId}",
                response = UserItemDataDto.serializer(),
                reshape = userData,
            ),
            // Emby has no user-data route; the item itself carries it.
            "/UserItems/{itemId}/UserData" to EmbyRoute(
                embyPath = "/Users/{userId}/Items/{itemId}",
                response = UserItemDataDto.serializer(),
                reshape = { element, itemId ->
                    userData((element as? JsonObject)?.get("UserData") ?: JsonNull, itemId)
                },
            ),

            // Jellyfin-only: intro and credit segments. Emby marks these on chapters instead.
            "/MediaSegments/{itemId}" to EmbyRoute(cannedResponse = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""),
        )

        fun find(pathTemplate: String): EmbyRoute? = routes[pathTemplate]
    }
}
