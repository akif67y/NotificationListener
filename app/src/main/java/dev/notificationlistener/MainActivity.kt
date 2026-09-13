package dev.notificationlistener

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.notificationlistener.data.CaptureDatabase
import dev.notificationlistener.data.CaptureSettings
import dev.notificationlistener.listener.MessageNotificationListener
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var database: CaptureDatabase
    private lateinit var statusText: TextView
    private lateinit var pauseButton: Button
    private lateinit var captureLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = CaptureDatabase(applicationContext)
        database.removeExpired(7)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildContent(): ScrollView {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        content.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            setText(R.string.privacy_summary)
            textSize = 15f
            setPadding(0, padding / 2, 0, padding)
        })

        statusText = TextView(this).apply { textSize = 17f }
        content.addView(statusText)

        content.addView(Button(this).apply {
            setText(R.string.open_access_settings)
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        })

        content.addView(CheckBox(this).apply {
            setText(R.string.capture_messenger)
            isChecked = CaptureSettings.messengerEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                CaptureSettings.setMessengerEnabled(this@MainActivity, checked)
            }
        })

        content.addView(CheckBox(this).apply {
            setText(R.string.capture_whatsapp)
            isChecked = CaptureSettings.whatsappEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                CaptureSettings.setWhatsappEnabled(this@MainActivity, checked)
            }
        })

        pauseButton = Button(this).apply {
            setOnClickListener {
                CaptureSettings.setPaused(
                    this@MainActivity,
                    !CaptureSettings.isPaused(this@MainActivity)
                )
                refresh()
            }
        }
        content.addView(pauseButton)

        content.addView(Button(this).apply {
            setText(R.string.refresh_captures)
            setOnClickListener { refresh() }
        })

        content.addView(Button(this).apply {
            setText(R.string.delete_all)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.delete_title)
                    .setMessage(R.string.delete_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        database.deleteAll()
                        refresh()
                    }
                    .show()
            }
        })

        content.addView(TextView(this).apply {
            setText(R.string.recent_captures)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, padding, 0, padding / 2)
        })

        captureLog = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
        }
        content.addView(captureLog)

        return ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun refresh() {
        val enabled = isNotificationAccessEnabled()
        statusText.text = if (enabled) {
            getString(R.string.access_enabled)
        } else {
            getString(R.string.access_disabled)
        }

        val paused = CaptureSettings.isPaused(this)
        pauseButton.setText(if (paused) R.string.resume_capture else R.string.pause_capture)

        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        val captures = database.recent()
        captureLog.text = if (captures.isEmpty()) {
            getString(R.string.no_captures)
        } else {
            captures.joinToString("\n\n") { item ->
                buildString {
                    append(formatter.format(Date(item.postedAt)))
                    append("  ")
                    append(if (item.packageName == CaptureSettings.MESSENGER_PACKAGE) "Messenger" else "WhatsApp")
                    item.conversationTitle?.let { append("\nConversation: ").append(it) }
                    item.title?.let { append("\nTitle: ").append(it) }
                    item.text?.let { append("\nText: ").append(it) }
                    item.bigText?.takeIf { it != item.text }?.let { append("\nExpanded: ").append(it) }
                    item.textLines?.let { append("\nLines:\n").append(it) }
                    item.structuredMessages?.let { append("\nMessagingStyle records:\n").append(it) }
                    if (item.isGroupSummary) append("\n[Grouped summary]")
                }
            }
        }
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

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}
