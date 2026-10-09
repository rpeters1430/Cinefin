package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.session.JellyfinSessionManager
import com.rpeters.jellyfin.utils.SecureLogger
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayRepository @Inject constructor(
    private val sessionManager: JellyfinSessionManager,
    private val authRepository: IJellyfinAuthRepository,
) {
    private val TAG = "SyncPlayRepository"
    val currentServer = authRepository.currentServer

    private fun requireSupported(server: com.rpeters.jellyfin.data.JellyfinServer) {
        check(server.serverType.supportsSyncPlay) { "SyncPlay is only available on Jellyfin servers" }
    }

    suspend fun getGroups(): List<GroupInfoDto> {
        return sessionManager.executeWithAuth("syncPlayGetGroups") { server, client ->
            requireSupported(server)
            client.syncPlayApi.syncPlayGetGroups().content
        }
    }

    suspend fun getGroup(groupId: String): GroupInfoDto {
        val parsedGroupId = parseGroupId(groupId)
        return sessionManager.executeWithAuth("syncPlayGetGroup") { server, client ->
            requireSupported(server)
            client.syncPlayApi.syncPlayGetGroup(parsedGroupId).content
        }
    }

    suspend fun joinGroup(groupId: String): GroupInfoDto {
        val parsedGroupId = parseGroupId(groupId)
        sessionManager.executeWithAuth("syncPlayJoinGroup") { server, client ->
            requireSupported(server)
            client.syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(groupId = parsedGroupId))
        }

        val group = getGroup(parsedGroupId.toString())
        SecureLogger.i(TAG, "Joined SyncPlay group: ${group.groupName} (${group.groupId})")
        return group
    }

    suspend fun leaveGroup() {
        sessionManager.executeWithAuth("syncPlayLeaveGroup") { server, client ->
            requireSupported(server)
            client.syncPlayApi.syncPlayLeaveGroup()
        }
        SecureLogger.i(TAG, "Left SyncPlay group")
    }

    suspend fun createGroup(name: String): GroupInfoDto {
        val groupName = name.trim().ifBlank { "Cinefin SyncPlay" }
        val group = sessionManager.executeWithAuth("syncPlayCreateGroup") { server, client ->
            requireSupported(server)
            client.syncPlayApi.syncPlayCreateGroup(NewGroupRequestDto(groupName = groupName)).content
        }
        SecureLogger.i(TAG, "Created SyncPlay group: ${group.groupName} (${group.groupId})")
        return group
    }

    suspend fun sendCommand(command: String, positionTicks: Long? = null) {
        val normalized = command.trim().lowercase()
        sessionManager.executeWithAuth("syncPlayCommand:$normalized") { server, client ->
            requireSupported(server)
            when (normalized) {
                "play", "unpause" -> client.syncPlayApi.syncPlayUnpause()
                "pause" -> client.syncPlayApi.syncPlayPause()
                "stop" -> client.syncPlayApi.syncPlayStop()
                "seek" -> client.syncPlayApi.syncPlaySeek(
                    SeekRequestDto(positionTicks = positionTicks ?: 0L),
                )
                "ping" -> client.syncPlayApi.syncPlayPing(
                    PingRequestDto(ping = System.currentTimeMillis()),
                )
                else -> throw IllegalArgumentException("Unsupported SyncPlay command: $command")
            }
        }
        SecureLogger.i(TAG, "Sent SyncPlay command: $normalized at $positionTicks")
    }

    private fun parseGroupId(groupId: String): UUID {
        return runCatching { UUID.fromString(groupId.trim()) }
            .getOrElse { throw IllegalArgumentException("SyncPlay group ID must be a valid UUID") }
    }
}
