package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.cache.JellyfinCache
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.common.BaseJellyfinRepository
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
    clientFactory: com.example.jellyfinandroid.di.JellyfinClientFactory,
    cache: JellyfinCache,
) : BaseJellyfinRepository(authRepository, clientFactory, cache) {

    suspend fun logout() {
        authRepository.logout()
    }

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> = execute("toggleFavorite") {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        if (isFavorite) {
            client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
        } else {
            client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
        }
        !isFavorite
    }

    suspend fun markAsWatched(itemId: String): ApiResult<Boolean> = execute("markAsWatched") {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.playStateApi.markPlayedItem(itemId = itemUuid, userId = userUuid)
        true
    }

    suspend fun markAsUnwatched(itemId: String): ApiResult<Boolean> = execute("markAsUnwatched") {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.playStateApi.markUnplayedItem(itemId = itemUuid, userId = userUuid)
        true
    }

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> = executeWithCache(
        operationName = "getFavorites",
        cacheKey = "user_favorites",
        cacheTtlMs = 10 * 60 * 1000L, // 10 minutes
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)

        val response = client.itemsApi.getItems(
            userId = userUuid,
            recursive = true,
            sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME),
            filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
        )
        response.content.items ?: emptyList()
    }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> = execute("deleteItem") {
        val server = validateServer()
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.libraryApi.deleteItem(itemId = itemUuid)
        true
    }

    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> = execute("deleteItemAsAdmin") {
        val server = validateServer()
        val itemUuid = parseUuid(itemId, "item")

        // Check admin permissions first
        val hasPermission = hasAdminDeletePermission(server)
        if (!hasPermission) {
            throw SecurityException("Administrator permissions required")
        }

        val client = getClient(server.url, server.accessToken)
        client.libraryApi.deleteItem(itemId = itemUuid)
        true
    }

    private suspend fun hasAdminDeletePermission(server: com.example.jellyfinandroid.data.JellyfinServer): Boolean {
        return try {
            val userUuid = parseUuid(server.userId ?: "", "user")
            val client = getClient(server.url, server.accessToken)
            val user = client.userApi.getCurrentUser().content
            user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true
        } catch (e: Exception) {
            false
        }
    }
}
