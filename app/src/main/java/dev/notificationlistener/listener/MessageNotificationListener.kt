package dev.notificationlistener.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.notificationlistener.data.CaptureDatabase
import dev.notificationlistener.data.CaptureSettings

class MessageNotificationListener : NotificationListenerService() {
    private val database by lazy { CaptureDatabase(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        database.removeExpired(RETENTION_DAYS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!CaptureSettings.accepts(applicationContext, sbn.packageName)) return

        val capture = NotificationExtractor.extract(sbn) ?: return
        runCatching {
            database.insert(capture)
            database.removeExpired(RETENTION_DAYS)
        }
        // Message content is deliberately never written to Logcat.
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private companion object {
        const val RETENTION_DAYS = 7
    }
}
