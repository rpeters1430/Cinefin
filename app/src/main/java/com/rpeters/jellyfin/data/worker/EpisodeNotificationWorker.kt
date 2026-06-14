package com.rpeters.jellyfin.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rpeters.jellyfin.MainActivity
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.EpisodeNotificationPreferencesRepository
import com.rpeters.jellyfin.data.preferences.FollowedSeriesNotification
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class EpisodeNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesRepository: EpisodeNotificationPreferencesRepository,
    private val mediaRepository: JellyfinMediaRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = preferencesRepository.getPreferences()
        if (!preferences.enabled || preferences.followedSeries.isEmpty()) {
            return Result.success()
        }

        ensureNotificationChannel()
        var shouldRetry = false
        preferences.followedSeries.forEach { followed ->
            when (val snapshot = loadSnapshot(followed.seriesId)) {
                null -> shouldRetry = true
                else -> {
                    if (snapshot.episodeCount > followed.episodeCount || snapshot.seasonCount > followed.seasonCount) {
                        showNewEpisodeNotification(
                            followed = followed,
                            newSeasons = (snapshot.seasonCount - followed.seasonCount).coerceAtLeast(0),
                            newEpisodes = (snapshot.episodeCount - followed.episodeCount).coerceAtLeast(0),
                        )
                    }
                    preferencesRepository.updateSeriesBaseline(
                        seriesId = followed.seriesId,
                        seasonCount = snapshot.seasonCount,
                        episodeCount = snapshot.episodeCount,
                    )
                }
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun loadSnapshot(seriesId: String): SeriesSnapshot? {
        val seasons = when (val result = mediaRepository.getSeasonsForSeries(seriesId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> {
                SecureLogger.w(TAG, "Failed to load seasons for notification check: $seriesId ${result.message}")
                return null
            }
            is ApiResult.Loading -> return null
        }

        var episodeCount = 0
        seasons.forEach { season ->
            val seasonId = season.id.toString()
            val episodes = when (val result = mediaRepository.getEpisodesForSeason(seasonId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> {
                    SecureLogger.w(TAG, "Failed to load episodes for notification check: $seasonId ${result.message}")
                    emptyList()
                }
                is ApiResult.Loading -> emptyList()
            }
            episodeCount += episodes.size
        }

        return SeriesSnapshot(seasonCount = seasons.size, episodeCount = episodeCount)
    }

    private fun showNewEpisodeNotification(
        followed: FollowedSeriesNotification,
        newSeasons: Int,
        newEpisodes: Int,
    ) {
        if (!canPostNotifications()) return

        val title = when {
            newSeasons > 0 && newEpisodes > 0 -> "New season available"
            newSeasons > 0 -> "New season added"
            else -> "New episode available"
        }
        val message = buildString {
            append(followed.seriesName)
            append(": ")
            if (newEpisodes > 0) {
                append(newEpisodes)
                append(if (newEpisodes == 1) " new episode" else " new episodes")
            }
            if (newSeasons > 0) {
                if (newEpisodes > 0) append(", ")
                append(newSeasons)
                append(if (newSeasons == 1) " new season" else " new seasons")
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            followed.seriesId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID_BASE + (followed.seriesId.hashCode() and 0x7FFFFFFF) % 10000, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New episodes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts when followed shows get new episodes or seasons"
        }
        manager.createNotificationChannel(channel)
    }

    private data class SeriesSnapshot(
        val seasonCount: Int,
        val episodeCount: Int,
    )

    @Singleton
    class Scheduler @Inject constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule() {
            val request = PeriodicWorkRequestBuilder<EpisodeNotificationWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runOnce() {
            val request = OneTimeWorkRequestBuilder<EpisodeNotificationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        private const val TAG = "EpisodeNotificationWorker"
        private const val WORK_NAME = "EpisodeNotificationWorker"
        private const val WORK_NAME_ONCE = "EpisodeNotificationWorkerOnce"
        private const val CHANNEL_ID = "new_episode_notifications"
        private const val NOTIFICATION_ID_BASE = 7200
    }
}
