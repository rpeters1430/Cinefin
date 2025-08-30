package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.BaseJellyfinRepository
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that contains operations specific to the user or session.
 * Functions here were previously scattered across [JellyfinRepository].
 */
@Singleton
class JellyfinUserRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    clientFactory: com.rpeters.jellyfin.di.JellyfinClientFactory,
    cache: JellyfinCache,
) : BaseJellyfinRepository(authRepository, clientFactory, cache) {

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
            response.content.items ?: emptyList()
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

    private suspend fun hasAdminDeletePermission(
        server: com.rpeters.jellyfin.data.JellyfinServer,
    ): Boolean {
        return try {
            // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
            val result = withServerClient("hasAdminDeletePermission") { server, client ->
                val userUuid = parseUuid(server.userId ?: "", "user")
                val user = client.userApi.getCurrentUser().content
                user?.policy?.isAdministrator == true || user?.policy?.enableContentDeletion == true
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
