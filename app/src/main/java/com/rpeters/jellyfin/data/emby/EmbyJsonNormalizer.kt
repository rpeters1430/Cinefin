package com.rpeters.jellyfin.data.emby

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Turns an Emby JSON response into something the Jellyfin SDK's serializers can decode.
 *
 * Emby and Jellyfin share most field names, but a raw Emby response never decodes as an SDK
 * model, for three reasons (measured against Emby 4.11): numeric IDs sit in fields the SDK types
 * as UUID, Emby omits fields the SDK marks required, and Emby has enum values the SDK lacks.
 *
 * Rather than keep a list of fields to patch, this walks the target type's serializer descriptor
 * next to the JSON and fixes each element by its declared type. It therefore follows the SDK
 * automatically when the SDK models change.
 */
@OptIn(ExperimentalSerializationApi::class)
object EmbyJsonNormalizer {
    private val json = Json {
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    private val hex32 = Regex("[0-9a-fA-F]{32}")
    private val zeroUuid = UUID(0L, 0L).toString()

    fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonElement): T =
        json.decodeFromJsonElement(deserializer, normalize(deserializer.descriptor, element))

    fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        decode(deserializer, json.parseToJsonElement(body))

    fun normalize(descriptor: SerialDescriptor, element: JsonElement): JsonElement =
        normalizeOrNull(descriptor, element, parentId = null) ?: defaultFor(descriptor, parentId = null)

    /**
     * @param parentId the already-normalized `Id` of the enclosing object, used to fill a missing
     *   `ItemId` (Emby leaves it out of `UserData`).
     * @return null when the element has no valid representation and should be dropped.
     */
    private fun normalizeOrNull(descriptor: SerialDescriptor, element: JsonElement, parentId: JsonElement?): JsonElement? {
        if (element is JsonNull) {
            return if (descriptor.isNullable) element else null
        }
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> normalizeObject(descriptor, element, parentId)

            StructureKind.LIST -> {
                val array = element as? JsonArray ?: return null
                val itemDescriptor = descriptor.getElementDescriptor(0)
                JsonArray(array.mapNotNull { normalizeOrNull(itemDescriptor, it, parentId) })
            }

            StructureKind.MAP -> {
                val obj = element as? JsonObject ?: return null
                val keyDescriptor = descriptor.getElementDescriptor(0)
                val valueDescriptor = descriptor.getElementDescriptor(1)
                val allowedKeys = if (keyDescriptor.kind == SerialKind.ENUM) enumNames(keyDescriptor) else null
                JsonObject(
                    obj.filterKeys { allowedKeys == null || it in allowedKeys }
                        .mapNotNull { (key, value) -> normalizeOrNull(valueDescriptor, value, parentId)?.let { key to it } }
                        .toMap(),
                )
            }

            SerialKind.ENUM -> element.takeIf { (it as? JsonPrimitive)?.content in enumNames(descriptor) }

            is PrimitiveKind -> {
                val primitive = element as? JsonPrimitive ?: return null
                if (isUuid(descriptor)) normalizeId(primitive) else primitive
            }

            else -> element
        }
    }

    private fun normalizeObject(descriptor: SerialDescriptor, element: JsonElement, parentId: JsonElement?): JsonElement? {
        val obj = element as? JsonObject ?: return null
        val idIndex = descriptor.getElementIndexOrNull("Id")
        val ownId = idIndex?.let { index ->
            obj["Id"]?.let { normalizeOrNull(descriptor.getElementDescriptor(index), it, parentId) }
        }
        val idForChildren = ownId ?: parentId

        val result = LinkedHashMap<String, JsonElement>(descriptor.elementsCount)
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val childDescriptor = descriptor.getElementDescriptor(index)
            val normalized = when {
                index == idIndex -> ownId
                else -> obj[name]?.let { normalizeOrNull(childDescriptor, it, idForChildren) }
            }
            when {
                normalized != null -> result[name] = normalized
                !descriptor.isElementOptional(index) ->
                    result[name] = defaultFor(childDescriptor, parentId = idForChildren.takeIf { name == "ItemId" })
            }
        }
        return JsonObject(result)
    }

    /** Emby GUIDs pass through; numeric item IDs become [ServerIdCodec] UUIDs. */
    private fun normalizeId(primitive: JsonPrimitive): JsonElement? {
        val value = primitive.content
        if (hex32.matches(value) || runCatching { UUID.fromString(value) }.isSuccess) return primitive
        return ServerIdCodec.encode(value)?.let { JsonPrimitive(it.toString()) }
    }

    /** A stand-in for a field the SDK requires and Emby did not send. */
    private fun defaultFor(descriptor: SerialDescriptor, parentId: JsonElement?): JsonElement = when {
        descriptor.isNullable -> JsonNull
        isUuid(descriptor) -> parentId ?: JsonPrimitive(zeroUuid)
        descriptor.kind == PrimitiveKind.STRING -> JsonPrimitive("")
        descriptor.kind == PrimitiveKind.BOOLEAN -> JsonPrimitive(false)
        descriptor.kind is PrimitiveKind -> JsonPrimitive(0)
        descriptor.kind == StructureKind.LIST -> JsonArray(emptyList())
        descriptor.kind == StructureKind.MAP -> JsonObject(emptyMap())
        descriptor.kind == SerialKind.ENUM -> JsonPrimitive(descriptor.getElementName(0))
        descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT ->
            normalizeObject(descriptor, JsonObject(emptyMap()), parentId) ?: JsonObject(emptyMap())
        else -> JsonNull
    }

    private fun SerialDescriptor.getElementIndexOrNull(name: String): Int? =
        (0 until elementsCount).firstOrNull { getElementName(it) == name }

    private fun enumNames(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it) }

    private fun isUuid(descriptor: SerialDescriptor): Boolean =
        descriptor.kind == PrimitiveKind.STRING && descriptor.serialName.removeSuffix("?").endsWith("UUID")
}
