package dev.notificationlistener.alerts

import android.Manifest
import android.annotation.SuppressLint
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
import dev.notificationlistener.MainActivity
import dev.notificationlistener.R
import dev.notificationlistener.data.AcademicEventEntity
import dev.notificationlistener.data.CaptureSettings

object EventNotifier {
    private const val EVENTS_CHANNEL = "academic_event_updates"
    private const val DIGEST_CHANNEL = "academic_daily_digest"

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    EVENTS_CHANNEL,
                    "Important academic updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Confirmed deadlines, tests, cancellations, and corrections" },
                NotificationChannel(
                    DIGEST_CHANNEL,
                    "Academic daily digest",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "One summary of upcoming confirmed academic events" }
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun publishEvent(context: Context, event: AcademicEventEntity, changeSummary: String?) {
        if (!CaptureSettings.eventAlertsEnabled(context) || !canNotify(context)) return
        createChannels(context)
        val course = event.courseCode?.let { "${it} · " }.orEmpty()
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(course + event.title)
            .setContentText(changeSummary ?: event.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(changeSummary ?: event.summary))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(event.eventKey.hashCode(), notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun publishDigest(context: Context, events: List<AcademicEventEntity>) {
        if (!CaptureSettings.dailyDigestEnabled(context) || events.isEmpty() || !canNotify(context)) return
        createChannels(context)
        val lines = events.take(5).map { event ->
            "${event.courseCode?.let { "${it}: " }.orEmpty()}${event.title}"
        }
        val notification = NotificationCompat.Builder(context, DIGEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Academic digest · ${events.size} upcoming")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(0xACADE, notification)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
