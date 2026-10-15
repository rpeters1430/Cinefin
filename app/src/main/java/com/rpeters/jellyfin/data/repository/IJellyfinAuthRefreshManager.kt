package com.rpeters.jellyfin.data.repository

interface IJellyfinAuthRefreshManager {
    fun currentAccessToken(): String?

    /** The signed-in server, so requests can carry the headers its product expects. */
    fun currentServer(): com.rpeters.jellyfin.data.JellyfinServer? = null
    fun scheduleProactiveRefreshIfNeeded()
    fun refreshAfterUnauthorized(attempt: Int): String?
}
