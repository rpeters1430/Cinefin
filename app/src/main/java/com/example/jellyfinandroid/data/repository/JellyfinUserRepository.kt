package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.common.BaseJellyfinRepository
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
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
) : BaseJellyfinRepository(authRepository, clientFactory) {

    suspend fun logout() {
        authRepository.logout()
    }

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> = execute {
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

    suspend fun markAsWatched(itemId: String): ApiResult<Boolean> = execute {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.playStateApi.markPlayedItem(itemId = itemUuid, userId = userUuid)
        true
    }

    suspend fun markAsUnwatched(itemId: String): ApiResult<Boolean> = execute {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.playStateApi.markUnplayedItem(itemId = itemUuid, userId = userUuid)
        true
    }

    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> = execute {
        val server = validateServer()
        val itemUuid = parseUuid(itemId, "item")
        val client = getClient(server.url, server.accessToken)
        client.libraryApi.deleteItem(itemId = itemUuid)
        true
    }
}
