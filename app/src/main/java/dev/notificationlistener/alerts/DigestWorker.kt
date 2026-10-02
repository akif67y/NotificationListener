package dev.notificationlistener.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.notificationlistener.data.AppDatabase
import dev.notificationlistener.data.CaptureSettings

class DigestWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!CaptureSettings.dailyDigestEnabled(applicationContext)) return Result.success()
        val now = System.currentTimeMillis()
        val events = AppDatabase.get(applicationContext).dao()
            .upcomingEvents(now, now + 7L * 24L * 60L * 60L * 1000L)
        EventNotifier.publishDigest(applicationContext, events)
        return Result.success()
    }
}
