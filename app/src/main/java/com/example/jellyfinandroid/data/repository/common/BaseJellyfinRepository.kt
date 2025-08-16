package com.example.jellyfinandroid.data.repository.common

import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinAuthRepository
import com.example.jellyfinandroid.data.utils.RepositoryUtils
import com.example.jellyfinandroid.di.JellyfinClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject

/**
 * Lightweight base class shared by repository implementations.
 * It centralizes common helpers like client creation and safe
 * execution wrappers so individual repositories can stay focused
 * on their domain logic.
 */
open class BaseJellyfinRepository @Inject constructor(
    protected val authRepository: JellyfinAuthRepository,
    private val clientFactory: JellyfinClientFactory,
) {
    protected fun getClient(serverUrl: String, accessToken: String?): ApiClient =
        clientFactory.getClient(serverUrl, accessToken)

    protected fun validateServer(): JellyfinServer =
        RepositoryUtils.validateServer(authRepository.getCurrentServer())

    protected fun parseUuid(id: String, type: String) =
        RepositoryUtils.parseUuid(id, type)

    /**
     * Wraps a suspend block returning [ApiResult]. Any thrown exception
     * is converted to an [ApiResult.Error] with a best-effort error type.
     */
    protected suspend fun <T> execute(block: suspend () -> T): ApiResult<T> =
        try {
            ApiResult.Success(block())
        } catch (e: Exception) {
            val error = RepositoryUtils.getErrorType(e)
            ApiResult.Error(e.message ?: "Unknown error", e, error)
        }
}
