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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        private const val TIMEOUT_MS = 2000
        private const val MAX_RETRIES = 2
    }

    override fun discoverServers(): Flow<List<DiscoveredServer>> = flow {
        val discoveredServers = mutableMapOf<String, DiscoveredServer>()
        
        // Initial empty list
        emit(emptyList())

        repeat(MAX_RETRIES) {
            for ((type, probe) in ServerDiscoveryProtocol.probes) {
                currentCoroutineContext().ensureActive()
                try {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        socket.soTimeout = TIMEOUT_MS
                        val sendData = probe.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(sendData, sendData.size, getBroadcastAddress(), DISCOVERY_PORT))
                        val buffer = ByteArray(4096)
                        // A busy LAN cannot extend discovery indefinitely.
                        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L
                        while (System.nanoTime() < deadline) {
                            currentCoroutineContext().ensureActive()
                            socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000L).toInt().coerceAtLeast(1)
                            try {
                                val packet = DatagramPacket(buffer, buffer.size)
                                socket.receive(packet)
                                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                                val server = ServerDiscoveryProtocol.parse(message, type) ?: continue
                                val key = "${server.serverType}:${server.id}"
                                if (discoveredServers.putIfAbsent(key, server) == null) {
                                    emit(discoveredServers.values.toList())
                                }
                            } catch (_: SocketTimeoutException) {
                                break
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    SecureLogger.w(TAG, "Local server discovery unavailable", e)
                }
            }
            delay(500)
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

}
