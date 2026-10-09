package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.network.DeviceIdentityProvider
import com.rpeters.jellyfin.network.JellyfinAuthInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmbyConnectClientTest {
    @Test
    fun serverList_keepsLinkedKeyAndBothAddresses() {
        val servers = EmbyConnectClient.parseServers(Json.parseToJsonElement("""[
            {"Name":"NAS","SystemId":"server","AccessKey":"linked-key",
             "LocalAddress":"http://192.168.1.10:8096/emby","Url":"https://media.example.com/emby"},
            {"Name":"Incomplete","SystemId":"other","Url":"https://media.example.com"}
        ]""").jsonArray, "connect-user")
        assertEquals(1, servers.size)
        assertEquals(listOf("http://192.168.1.10:8096/emby", "https://media.example.com/emby"), servers.single().addresses)
        assertEquals("connect-user", servers.single().userId)
        assertEquals("linked-key", servers.single().accessKey)
    }

    @Test
    fun exchange_usesLinkedKeyThenLocalToken_withoutSessionInterceptor() = runTest {
        val identity = mockk<DeviceIdentityProvider>()
        every { identity.clientName() } returns "Cinefin"
        every { identity.clientVersion() } returns "test"
        every { identity.deviceName() } returns "Phone"
        every { identity.deviceId() } returns "device"
        val sessionInterceptor = mockk<JellyfinAuthInterceptor>()
        val shared = OkHttpClient.Builder().addInterceptor(sessionInterceptor).build()
        val client = EmbyConnectClient(dagger.Lazy { shared }, identity)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"LocalUserId":"e96573aacb144a45b3c4585e3e5071c7","AccessToken":"local-token"}"""))
            server.enqueue(MockResponse().setBody("""{"Id":"e96573aacb144a45b3c4585e3e5071c7","Name":"Ryan"}"""))
            server.start()
            val result = client.exchange(server.url("/emby").toString(), "connect-user", "linked-key")
            assertEquals("local-token", result.accessToken)
            assertEquals("Ryan", result.user?.name)
            val exchange = server.takeRequest()
            assertEquals("/emby/Connect/Exchange?format=json&ConnectUserId=connect-user", exchange.path)
            assertEquals("linked-key", exchange.getHeader("X-Emby-Token"))
            assertTrue(exchange.getHeader("X-Emby-Authorization")!!.contains("DeviceId=\"device\""))
            val user = server.takeRequest()
            assertEquals("local-token", user.getHeader("X-Emby-Token"))
            assertEquals("/emby/Users/e96573aacb144a45b3c4585e3e5071c7", user.path)
        }
        verify(exactly = 0) { sessionInterceptor.intercept(any()) }
    }
}
