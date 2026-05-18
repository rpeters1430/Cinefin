package com.rpeters.jellyfin.data.repository

import android.content.Context
import com.rpeters.jellyfin.data.model.DiscoveredServer
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

interface IJellyfinDiscoveryRepository {
    fun discoverServers(): Flow<List<DiscoveredServer>>
}

@Singleton
class JellyfinDiscoveryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : IJellyfinDiscoveryRepository {

    companion object {
        private const val TAG = "JellyfinDiscovery"
        private const val DISCOVERY_PORT = 7359
        private const val DISCOVERY_MESSAGE = "Who is JellyfinServer?"
        private const val TIMEOUT_MS = 2000
        private const val MAX_RETRIES = 2
    }

    override fun discoverServers(): Flow<List<DiscoveredServer>> = flow {
        val discoveredServers = mutableMapOf<String, DiscoveredServer>()
        
        // Initial empty list
        emit(emptyList())

        withContext(Dispatchers.IO) {
            repeat(MAX_RETRIES) {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    socket.soTimeout = TIMEOUT_MS

                    val sendData = DISCOVERY_MESSAGE.toByteArray()
                    val sendPacket = DatagramPacket(
                        sendData,
                        sendData.size,
                        getBroadcastAddress(),
                        DISCOVERY_PORT
                    )

                    socket.send(sendPacket)

                    val buffer = ByteArray(4096)
                    while (true) {
                        try {
                            val receivePacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(receivePacket)
                            
                            val message = String(receivePacket.data, 0, receivePacket.length)
                            val server = parseDiscoveryMessage(message)
                            if (server != null && !discoveredServers.containsKey(server.id)) {
                                discoveredServers[server.id] = server
                                // Emit current list
                                emit(discoveredServers.values.toList())
                            }
                        } catch (e: SocketTimeoutException) {
                            break // Done receiving for this attempt
                        }
                    }
                    socket.close()
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Discovery error: ${e.message}")
                }
                delay(500) // Small delay between retries
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getBroadcastAddress(): InetAddress {
        return try {
            var broadcastAddress: InetAddress? = null
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        broadcastAddress = broadcast
                        break
                    }
                }
                if (broadcastAddress != null) break
            }
            broadcastAddress ?: InetAddress.getByName("255.255.255.255")
        } catch (e: Exception) {
            InetAddress.getByName("255.255.255.255")
        }
    }

    private fun parseDiscoveryMessage(message: String): DiscoveredServer? {
        return try {
            val json = JSONObject(message)
            DiscoveredServer(
                name = json.getString("Name"),
                address = json.getString("Address"),
                id = json.getString("Id"),
                version = json.optString("Version")
            )
        } catch (e: Exception) {
            null
        }
    }
}
