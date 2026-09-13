package dev.notificationlistener.listener

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import dev.notificationlistener.data.CapturedNotification

object NotificationExtractor {
    fun extract(sbn: StatusBarNotification): CapturedNotification? {
        val notification = sbn.notification
        val extras = notification.extras ?: return null

        val title = extras.text(Notification.EXTRA_TITLE)
        val text = extras.text(Notification.EXTRA_TEXT)
        val bigText = extras.text(Notification.EXTRA_BIG_TEXT)
        val conversationTitle = extras.text(Notification.EXTRA_CONVERSATION_TITLE)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
        val structuredMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            extractStructuredMessages(extras)
        } else {
            null
        }

        // Do not retain empty bookkeeping notifications.
        if (listOf(title, text, bigText, conversationTitle, lines, structuredMessages)
                .all { it.isNullOrBlank() }
        ) {
            return null
        }

        return CapturedNotification(
            id = 0,
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            postedAt = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            textLines = lines,
            structuredMessages = structuredMessages,
            conversationTitle = conversationTitle,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            capturedAt = System.currentTimeMillis()
        )
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    private fun extractStructuredMessages(extras: android.os.Bundle): String? =
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)
            ?.mapNotNull { message ->
                val body = message.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val sender = message.senderPerson?.name?.toString() ?: ""
                "${message.timestamp}\t$sender\t$body"
            }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
}

private fun android.os.Bundle.text(key: String): String? =
    getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
