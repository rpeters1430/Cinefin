package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.get
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface RemoteConfigRepository {
    suspend fun fetchAndActivate(): Boolean
    fun getString(key: String): String
    fun getBoolean(key: String): Boolean
    fun getLong(key: String): Long
    fun getDouble(key: String): Double
}

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : RemoteConfigRepository {

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Log.e("RemoteConfig", "Failed to fetch and activate remote config", e)
            false
        }
    }

    override fun getString(key: String): String {
        return remoteConfig.getString(key)
    }

    override fun getBoolean(key: String): Boolean {
        return remoteConfig.getBoolean(key)
    }

    override fun getLong(key: String): Long {
        return remoteConfig.getLong(key)
    }

    override fun getDouble(key: String): Double {
        return remoteConfig.getDouble(key)
    }
}
