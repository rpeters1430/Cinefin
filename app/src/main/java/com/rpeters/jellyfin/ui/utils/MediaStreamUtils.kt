package com.rpeters.jellyfin.ui.utils

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

fun List<MediaStream>?.findDefaultVideoStream(): MediaStream? {
    return this?.firstOrNull { stream ->
        stream.type == MediaStreamType.VIDEO && stream.isDefault == true
    } ?: this?.firstOrNull { stream -> stream.type == MediaStreamType.VIDEO }
}

fun List<MediaStream>?.findDefaultAudioStream(): MediaStream? {
    return this?.firstOrNull { stream ->
        stream.type == MediaStreamType.AUDIO && stream.isDefault == true
    } ?: this?.firstOrNull { stream -> stream.type == MediaStreamType.AUDIO }
}
