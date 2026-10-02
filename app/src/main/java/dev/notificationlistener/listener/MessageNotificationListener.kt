package dev.notificationlistener.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.notificationlistener.analysis.AnalysisScheduler
import dev.notificationlistener.data.AppDatabase
import dev.notificationlistener.data.CaptureRepository
import dev.notificationlistener.data.CaptureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MessageNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { CaptureRepository(AppDatabase.get(applicationContext)) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            repository.removeExpired(CaptureSettings.retentionDays(applicationContext))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!CaptureSettings.accepts(applicationContext, sbn.packageName)) return
        val extracted = NotificationExtractor.extract(sbn) ?: return
        serviceScope.launch {
            runCatching {
                val inserted = repository.ingest(extracted)
                repository.removeExpired(CaptureSettings.retentionDays(applicationContext))
                if (inserted > 0 && CaptureSettings.cloudEnabled(applicationContext)) {
                    AnalysisScheduler.enqueue(applicationContext)
                }
            }
            // Message content and failures are deliberately never written to Logcat.
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
