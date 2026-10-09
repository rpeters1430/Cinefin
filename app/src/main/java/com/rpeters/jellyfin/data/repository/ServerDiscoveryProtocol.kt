package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.DiscoveredServer
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.utils.ServerUrlValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Each probe uses its own socket: discovery replies contain no product identifier. */
internal object ServerDiscoveryProtocol {
    val probes = mapOf(
        ServerType.JELLYFIN to "Who is JellyfinServer?",
        ServerType.EMBY to "who is EmbyServer?",
    )

    fun parse(message: String, type: ServerType): DiscoveredServer? = runCatching {
        val json = Json.parseToJsonElement(message).jsonObject
        val id = json.getValue("Id").jsonPrimitive.content.takeIf { it.isNotBlank() } ?: return null
        val name = json.getValue("Name").jsonPrimitive.content.takeIf { it.isNotBlank() } ?: return null
        val address = ServerUrlValidator.validateAndNormalizeUrl(json.getValue("Address").jsonPrimitive.content)
            ?: return null
        DiscoveredServer(name, address, id, json["Version"]?.jsonPrimitive?.content, type)
    }.getOrNull()
}
