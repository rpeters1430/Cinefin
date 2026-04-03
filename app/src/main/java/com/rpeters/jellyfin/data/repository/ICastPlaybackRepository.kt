package com.rpeters.jellyfin.data.repository

import org.jellyfin.sdk.model.api.PlaybackInfoResponse

interface ICastPlaybackRepository {
    suspend fun getCastPlaybackInfo(
        itemId: String,
        castReceiverProfile: CastReceiverProfile = CastReceiverProfile.CHROMECAST_STRICT,
        forceTranscode: Boolean = false,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse
}
