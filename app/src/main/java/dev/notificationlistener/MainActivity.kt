package dev.notificationlistener

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.notificationlistener.listener.MessageNotificationListener
import dev.notificationlistener.alerts.EventNotifier
import dev.notificationlistener.ui.NotificationCollectorApp

class MainActivity : ComponentActivity() {
    private var notificationAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationAccessGranted = isNotificationAccessEnabled()
        EventNotifier.createChannels(this)
        setContent {
            NotificationCollectorApp(
                notificationAccessGranted = notificationAccessGranted,
                openNotificationSettings = {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccessGranted = isNotificationAccessEnabled()
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val component = ComponentName(this, MessageNotificationListener::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= 27) {
            getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.split(':')
                ?.mapNotNull(ComponentName::unflattenFromString)
                ?.contains(component) == true
        }
    }
}
