package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.emby.EmbyConnectServer

class EmbyConnectState(
    val isBusy: Boolean = false,
    val servers: List<EmbyConnectServer> = emptyList(),
    val error: String? = null,
) {
    fun copy(isBusy: Boolean = this.isBusy, error: String? = this.error) = EmbyConnectState(isBusy, servers, error)
}
