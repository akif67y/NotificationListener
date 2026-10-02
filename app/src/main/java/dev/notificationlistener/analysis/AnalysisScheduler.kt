package dev.notificationlistener.analysis

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.UUID

object AnalysisScheduler {
    private const val ONE_TIME_NAME = "academic-analysis-now"
    private const val PERIODIC_NAME = "academic-analysis-periodic"
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueue(context: Context): UUID = enqueue(context, ExistingWorkPolicy.KEEP)

    fun enqueueNow(context: Context): UUID = enqueue(context, ExistingWorkPolicy.REPLACE)

    private fun enqueue(context: Context, policy: ExistingWorkPolicy): UUID {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_NAME,
            policy,
            request
        )
        return request.id
    }

    fun setPeriodic(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(ONE_TIME_NAME)
            manager.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AnalysisWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        enqueue(context)
    }
}
