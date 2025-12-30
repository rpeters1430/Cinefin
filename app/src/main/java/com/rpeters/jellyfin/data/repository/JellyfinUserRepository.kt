package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.BaseJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ErrorType
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.UserItemDataDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that contains operations specific to the user or session.
 * Functions here were previously scattered across [JellyfinRepository].
 */
@Singleton
class JellyfinUserRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: com.rpeters.jellyfin.data.session.JellyfinSessionManager,
    cache: JellyfinCache,
) : BaseJellyfinRepository(authRepository, sessionManager, cache) {

    suspend fun logout() {
        authRepository.logout()
    }

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> =
        withServerClient("toggleFavorite") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            if (isFavorite) {
                client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
            } else {
                client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
            }
            !isFavorite
        }

    suspend fun markAsWatched(itemId: String): ApiResult<Boolean> =
        withServerClient("markAsWatched") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            client.playStateApi.markPlayedItem(itemId = itemUuid, userId = userUuid)
            true
        }

    suspend fun markAsUnwatched(itemId: String): ApiResult<Boolean> =
        withServerClient("markAsUnwatched") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            client.playStateApi.markUnplayedItem(itemId = itemUuid, userId = userUuid)
            true
        }

    suspend fun getItemUserData(itemId: String): ApiResult<UserItemDataDto> =
        withServerClient("getItemUserData") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            val response = client.itemsApi.getItemUserData(itemId = itemUuid, userId = userUuid)
            response.content
        }

    suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        canSeek: Boolean = true,
    ): ApiResult<Unit> =
        withServerClient("reportPlaybackStart") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackStartInfo(
                canSeek = canSeek,
                itemId = itemUuid,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                isPaused = isPaused,
                isMuted = isMuted,
                positionTicks = positionTicks,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
                playSessionId = sessionId,
            )
            client.playStateApi.reportPlaybackStart(info)
            Unit
        }

    suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        canSeek: Boolean = true,
    ): ApiResult<Unit> =
        withServerClient("reportPlaybackProgress") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackProgressInfo(
                canSeek = canSeek,
                itemId = itemUuid,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = isMuted,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
                playSessionId = sessionId,
            )
            client.playStateApi.reportPlaybackProgress(info)
            Unit
        }

    suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        failed: Boolean = false,
    ): ApiResult<Unit> =
        withServerClient("reportPlaybackStopped") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackStopInfo(
                itemId = itemUuid,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                playSessionId = sessionId,
                failed = failed,
            )
            client.playStateApi.reportPlaybackStopped(info)
            Unit
        }

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> =
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        withServerClient("getFavorites") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME),
                filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
            )
            response.content.items
        }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> =
        withServerClient("deleteItem") { server, client ->
            val itemUuid = parseUuid(itemId, "item")
            client.libraryApi.deleteItem(itemId = itemUuid)
            true
        }

    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> =
        withServerClient("deleteItemAsAdmin") { server, client ->
            val itemUuid = parseUuid(itemId, "item")

            // Check admin permissions first
            val hasPermission = hasAdminDeletePermission(server)
            if (!hasPermission) {
                throw SecurityException("Administrator permissions required")
            }

            client.libraryApi.deleteItem(itemId = itemUuid)
            true
        }

    suspend fun refreshItemMetadata(itemId: String): ApiResult<Boolean> =
        ApiResult.Error(
            "Metadata refresh not yet implemented - requires Jellyfin SDK update",
            errorType = ErrorType.BAD_REQUEST,
        )

    private suspend fun hasAdminDeletePermission(
        server: com.rpeters.jellyfin.data.JellyfinServer,
    ): Boolean {
        return try {
            // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
            val result = withServerClient("hasAdminDeletePermission") { server, client ->
                val userUuid = parseUuid(server.userId ?: "", "user")
                val user = client.userApi.getCurrentUser().content
                user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true
            }
            when (result) {
                is ApiResult.Success -> result.data
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}
