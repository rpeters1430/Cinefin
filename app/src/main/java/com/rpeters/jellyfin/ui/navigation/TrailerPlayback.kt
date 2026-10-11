package com.rpeters.jellyfin.ui.navigation

import android.content.Context
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.TrailerUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plays the trailer for [itemId]: a trailer file on the server (Trailarr, or any local trailer
 * Jellyfin/Emby indexed) in the in-app player when there is one, otherwise [remoteUrl].
 */
internal fun CoroutineScope.launchTrailer(
    context: Context,
    mainViewModel: MainAppViewModel,
    itemId: String,
    remoteUrl: String,
) = launch {
    val localTrailer = mainViewModel.getLocalTrailer(itemId)
    val streamUrl = localTrailer?.let(mainViewModel::getStreamUrl)
    if (localTrailer != null && streamUrl != null) {
        MediaPlayerUtils.playMedia(
            context = context,
            streamUrl = streamUrl,
            item = localTrailer,
            startPosition = 0L,
        )
    } else {
        TrailerUtils.playTrailer(context, remoteUrl)
    }
}
