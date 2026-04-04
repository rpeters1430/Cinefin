package com.rpeters.jellyfin.ui.player.dlna

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

@UnstableApi
@Singleton
class DlnaPlaybackController @Inject constructor() {
    data class DlnaPlaybackState(
        val currentPositionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
    )

    companion object {
        private const val TAG = "DlnaPlayback"
        private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun loadAndPlay(device: DlnaDevice, streamUrl: String, title: String): Boolean {
        val metadata = buildDidlLite(title)
        val setUriBody = """
            <u:SetAVTransportURI xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
                <CurrentURI>${xmlEscape(streamUrl)}</CurrentURI>
                <CurrentURIMetaData>${xmlEscape(metadata)}</CurrentURIMetaData>
            </u:SetAVTransportURI>
        """.trimIndent()
        val setOk = sendSoapAction(device, "SetAVTransportURI", setUriBody)
        if (!setOk) return false
        return play(device)
    }

    suspend fun play(device: DlnaDevice): Boolean {
        val body = """
            <u:Play xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
                <Speed>1</Speed>
            </u:Play>
        """.trimIndent()
        return sendSoapAction(device, "Play", body)
    }

    suspend fun pause(device: DlnaDevice): Boolean {
        val body = """
            <u:Pause xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
            </u:Pause>
        """.trimIndent()
        return sendSoapAction(device, "Pause", body)
    }

    suspend fun stop(device: DlnaDevice): Boolean {
        val body = """
            <u:Stop xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
            </u:Stop>
        """.trimIndent()
        return sendSoapAction(device, "Stop", body)
    }

    suspend fun seek(device: DlnaDevice, positionMs: Long): Boolean {
        val target = toDlnaTime(positionMs)
        val body = """
            <u:Seek xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
                <Unit>REL_TIME</Unit>
                <Target>$target</Target>
            </u:Seek>
        """.trimIndent()
        return sendSoapAction(device, "Seek", body)
    }

    suspend fun getPlaybackState(device: DlnaDevice): DlnaPlaybackState? {
        val positionInfoBody = """
            <u:GetPositionInfo xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
            </u:GetPositionInfo>
        """.trimIndent()
        val transportInfoBody = """
            <u:GetTransportInfo xmlns:u="$SERVICE">
                <InstanceID>0</InstanceID>
            </u:GetTransportInfo>
        """.trimIndent()

        val positionResponse = sendSoapRequest(device, "GetPositionInfo", positionInfoBody) ?: return null
        val transportResponse = sendSoapRequest(device, "GetTransportInfo", transportInfoBody)

        val relTime = parseTagText(positionResponse, "RelTime")
        val trackDuration = parseTagText(positionResponse, "TrackDuration")
        val currentTransportState = transportResponse?.let { parseTagText(it, "CurrentTransportState") }

        return DlnaPlaybackState(
            currentPositionMs = parseDlnaTime(relTime),
            durationMs = parseDlnaTime(trackDuration),
            isPlaying = currentTransportState.equals("PLAYING", ignoreCase = true),
        )
    }

    private suspend fun sendSoapAction(device: DlnaDevice, action: String, innerBody: String): Boolean = withContext(Dispatchers.IO) {
        sendSoapRequest(device, action, innerBody) != null
    }

    private suspend fun sendSoapRequest(device: DlnaDevice, action: String, innerBody: String): String? = withContext(Dispatchers.IO) {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    $innerBody
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val request = Request.Builder()
            .url(device.avTransportControlUrl)
            .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            .addHeader("SOAPACTION", "\"$SERVICE#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    SecureLogger.w(TAG, "DLNA action $action failed for ${device.friendlyName}: HTTP ${response.code}")
                    return@use null
                }
                response.body?.string()
            }
        }.onFailure { e ->
            SecureLogger.w(TAG, "DLNA action $action threw for ${device.friendlyName}", e)
        }.getOrNull()
    }

    private fun buildDidlLite(title: String): String {
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="0" parentID="0" restricted="1">
                    <dc:title>${xmlEscape(title)}</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun toDlnaTime(positionMs: Long): String {
        val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun parseDlnaTime(value: String?): Long {
        if (value.isNullOrBlank() || value == "NOT_IMPLEMENTED") return 0L
        val parts = value.split(":")
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: return 0L
        val minutes = parts[1].toLongOrNull() ?: return 0L
        val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return 0L
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000L
    }

    private fun parseTagText(xml: String, tagName: String): String? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val nodes = document.getElementsByTagName(tagName)
            val node = nodes.item(0) as? Element ?: return@runCatching null
            node.textContent?.trim()
        }.onFailure { e ->
            SecureLogger.w(TAG, "Failed parsing DLNA SOAP response tag $tagName", e)
        }.getOrNull()
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
