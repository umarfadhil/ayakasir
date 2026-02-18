package com.ayakasir.app.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "SyncWorker started (attempt $runAttemptCount)")
            val result = syncManager.syncAll()
            Log.d(TAG, "SyncWorker completed: ${result.pushed} pushed, ${result.failed} failed")
            if (result.hasFailures && runAttemptCount < 3) {
                Log.w(TAG, "Retrying sync (failures detected)")
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker error: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
    
    companion object {
        private const val TAG = "SyncWorker"
    }
}
