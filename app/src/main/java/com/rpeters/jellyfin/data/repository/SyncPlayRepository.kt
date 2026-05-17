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
) {
    private val TAG = "SyncPlayRepository"

    suspend fun getGroups(): List<GroupInfoDto> {
        return sessionManager.executeWithAuth("syncPlayGetGroups") { _, client ->
            client.syncPlayApi.syncPlayGetGroups().content
        }
    }

    suspend fun getGroup(groupId: String): GroupInfoDto {
        val parsedGroupId = parseGroupId(groupId)
        return sessionManager.executeWithAuth("syncPlayGetGroup") { _, client ->
            client.syncPlayApi.syncPlayGetGroup(parsedGroupId).content
        }
    }

    suspend fun joinGroup(groupId: String): GroupInfoDto {
        val parsedGroupId = parseGroupId(groupId)
        sessionManager.executeWithAuth("syncPlayJoinGroup") { _, client ->
            client.syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(groupId = parsedGroupId))
        }

        val group = getGroup(parsedGroupId.toString())
        SecureLogger.i(TAG, "Joined SyncPlay group: ${group.groupName} (${group.groupId})")
        return group
    }

    suspend fun leaveGroup() {
        sessionManager.executeWithAuth("syncPlayLeaveGroup") { _, client ->
            client.syncPlayApi.syncPlayLeaveGroup()
        }
        SecureLogger.i(TAG, "Left SyncPlay group")
    }

    suspend fun createGroup(name: String): GroupInfoDto {
        val groupName = name.trim().ifBlank { "Cinefin SyncPlay" }
        val group = sessionManager.executeWithAuth("syncPlayCreateGroup") { _, client ->
            client.syncPlayApi.syncPlayCreateGroup(NewGroupRequestDto(groupName = groupName)).content
        }
        SecureLogger.i(TAG, "Created SyncPlay group: ${group.groupName} (${group.groupId})")
        return group
    }

    suspend fun sendCommand(command: String, positionTicks: Long? = null) {
        val normalized = command.trim().lowercase()
        sessionManager.executeWithAuth("syncPlayCommand:$normalized") { _, client ->
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
