package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.network.DeviceIdentityProvider
import okhttp3.Request

/** The `X-Plex-*` headers that identify this app to plex.tv and to Plex Media Servers. */
object PlexClientHeaders {
    const val TOKEN = "X-Plex-Token"
    const val CLIENT_IDENTIFIER = "X-Plex-Client-Identifier"
    const val PRODUCT = "X-Plex-Product"
    const val VERSION = "X-Plex-Version"
    const val PLATFORM = "X-Plex-Platform"
    const val DEVICE = "X-Plex-Device"

    const val PRODUCT_NAME = "Cinefin"
    const val PLATFORM_NAME = "Android"

    /** Sets the identity headers on [builder]; never the token. */
    fun applyTo(builder: Request.Builder, identity: DeviceIdentityProvider): Request.Builder = builder
        .header(CLIENT_IDENTIFIER, identity.plexClientIdentifier())
        .header(PRODUCT, PRODUCT_NAME)
        .header(VERSION, identity.clientVersion())
        .header(PLATFORM, PLATFORM_NAME)
        .header(DEVICE, identity.deviceName())
}
