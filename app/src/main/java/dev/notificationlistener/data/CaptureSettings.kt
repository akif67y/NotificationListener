package dev.notificationlistener.data

import android.content.Context

object CaptureSettings {
    const val MESSENGER_PACKAGE = "com.facebook.orca"
    const val WHATSAPP_PACKAGE = "com.whatsapp"

    private const val PREFS = "capture_settings"
    private const val KEY_PAUSED = "paused"
    private const val KEY_MESSENGER = "messenger"
    private const val KEY_WHATSAPP = "whatsapp"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPaused(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, value).apply()
    }

    fun messengerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MESSENGER, true)

    fun setMessengerEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MESSENGER, value).apply()
    }

    fun whatsappEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WHATSAPP, false)

    fun setWhatsappEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WHATSAPP, value).apply()
    }

    fun accepts(context: Context, packageName: String): Boolean {
        if (isPaused(context)) return false
        return when (packageName) {
            MESSENGER_PACKAGE -> messengerEnabled(context)
            WHATSAPP_PACKAGE -> whatsappEnabled(context)
            else -> false
        }
    }
}
