package com.rpeters.jellyfin.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OfflineProgressSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepository: JellyfinUserRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        SecureLogger.d("OfflineProgressWorker", "Starting offline progress sync")

        return try {
            val result = userRepository.syncOfflineProgress()
            if (result is ApiResult.Success) {
                SecureLogger.i("OfflineProgressWorker", "Successfully synced ${result.data} updates")
                Result.success()
            } else {
                SecureLogger.w("OfflineProgressWorker", "Sync failed, will retry later")
                Result.retry()
            }
        } catch (e: Exception) {
            SecureLogger.e("OfflineProgressWorker", "Error during sync work", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "OfflineProgressSyncWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineProgressSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )

            SecureLogger.d("OfflineProgressWorker", "Scheduled offline progress sync")
        }
    }
}
