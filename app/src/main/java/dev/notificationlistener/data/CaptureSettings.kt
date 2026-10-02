package dev.notificationlistener.data

import android.content.Context

object CaptureSettings {
    const val MESSENGER_PACKAGE = "com.facebook.orca"
    const val WHATSAPP_PACKAGE = "com.whatsapp"

    private const val PREFS = "capture_settings"
    private const val KEY_PAUSED = "paused"
    private const val KEY_MESSENGER = "messenger"
    private const val KEY_WHATSAPP = "whatsapp"
    private const val KEY_RETENTION_DAYS = "retention_days"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_CLOUD_ENABLED = "cloud_enabled"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_EVENT_ALERTS = "event_alerts"
    private const val KEY_DAILY_DIGEST = "daily_digest"

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

    fun retentionDays(context: Context): Int =
        prefs(context).getInt(KEY_RETENTION_DAYS, 7).takeIf { it in RETENTION_OPTIONS } ?: 7

    fun setRetentionDays(context: Context, value: Int) {
        require(value in RETENTION_OPTIONS)
        prefs(context).edit().putInt(KEY_RETENTION_DAYS, value).apply()
    }

    fun onboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    fun cloudEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_CLOUD_ENABLED, false)

    fun setCloudEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOUD_ENABLED, value).apply()
    }

    fun backendUrl(context: Context): String = prefs(context).getString(KEY_BACKEND_URL, "").orEmpty()

    fun setBackendUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_BACKEND_URL, value.trim().trimEnd('/') + "/").apply()
    }

    fun eventAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EVENT_ALERTS, false)

    fun setEventAlertsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_EVENT_ALERTS, value).apply()
    }

    fun dailyDigestEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAILY_DIGEST, false)

    fun setDailyDigestEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAILY_DIGEST, value).apply()
    }

    fun accepts(context: Context, packageName: String): Boolean {
        if (isPaused(context)) return false
        return when (packageName) {
            MESSENGER_PACKAGE -> messengerEnabled(context)
            WHATSAPP_PACKAGE -> whatsappEnabled(context)
            else -> false
        }
    }


    val RETENTION_OPTIONS = listOf(1, 3, 7, 14, 30)
}
