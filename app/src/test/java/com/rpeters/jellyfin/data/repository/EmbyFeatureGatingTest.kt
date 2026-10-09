package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.session.JellyfinSessionManager
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.junit.Assert.*
import org.junit.Test

class EmbyFeatureGatingTest {
    private val emby = JellyfinServer("emby", "NAS", "http://nas:8096", serverType = ServerType.EMBY)

    @Test
    fun pluginInfo_onEmby_neverMakesAnHttpRequest() = runTest {
        val auth = mockk<IJellyfinAuthRepository>()
        every { auth.currentServer } returns MutableStateFlow(emby)
        val client = OkHttpClient.Builder().addInterceptor { error("Unsupported plugin request reached HTTP") }.build()
        val result = CinefinPluginRepository(client, auth, Json).getPluginInfo()
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.contains("only available on Jellyfin"))
    }

    @Test
    fun syncPlay_onEmby_rejectsBeforeCallingSdk() = runTest {
        val session = mockk<JellyfinSessionManager>()
        val auth = mockk<IJellyfinAuthRepository>()
        every { auth.currentServer } returns MutableStateFlow(emby)
        val client = mockk<ApiClient>()
        coEvery { session.executeWithAuth<List<GroupInfoDto>>(any(), any()) } coAnswers {
            secondArg<suspend (JellyfinServer, ApiClient) -> List<GroupInfoDto>>().invoke(emby, client)
        }
        val result = runCatching { SyncPlayRepository(session, auth).getGroups() }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { client.request(any(), any(), any(), any(), any()) }
    }
}
