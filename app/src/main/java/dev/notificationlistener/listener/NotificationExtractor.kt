package dev.notificationlistener.listener

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import dev.notificationlistener.data.CaptureSettings
import dev.notificationlistener.data.NotificationSnapshotEntity

data class ExtractedMessage(
    val sender: String,
    val text: String,
    val timestamp: Long,
    val hasExactTimestamp: Boolean
)

data class ExtractedNotification(
    val snapshot: NotificationSnapshotEntity,
    val platform: String,
    val conversationKey: String,
    val conversationName: String,
    val messages: List<ExtractedMessage>
)

object NotificationExtractor {
    fun extract(sbn: StatusBarNotification): ExtractedNotification? {
        val notification = sbn.notification
        val extras = notification.extras ?: return null

        val title = extras.text(Notification.EXTRA_TITLE)
        val text = extras.text(Notification.EXTRA_TEXT)
        val bigText = extras.text(Notification.EXTRA_BIG_TEXT)
        val conversationTitle = extras.text(Notification.EXTRA_CONVERSATION_TITLE)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
            ?.filter(String::isNotBlank)
        val structured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            extractStructuredMessages(extras)
        } else {
            emptyList()
        }

        if (listOf(title, text, bigText, conversationTitle).all { it.isNullOrBlank() } &&
            lines.isNullOrEmpty() && structured.isEmpty()
        ) {
            return null
        }

        val platform = platformName(sbn.packageName)
        val conversationName = conversationTitle ?: title ?: platform
        val conversationKey = notification.shortcutId?.takeIf(String::isNotBlank)
            ?: conversationName.trim().lowercase()
        val messages = when {
            structured.isNotEmpty() -> structured
            !lines.isNullOrEmpty() -> lines.mapNotNull { line ->
                MessageNormalizer.fromDisplayText(line, title, sbn.postTime)
            }
            else -> MessageNormalizer.fromDisplayText(
                bigText ?: text.orEmpty(),
                title,
                sbn.postTime
            )?.let(::listOf).orEmpty()
        }

        return ExtractedNotification(
            snapshot = NotificationSnapshotEntity(
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                postedAt = sbn.postTime,
                title = title,
                text = text,
                bigText = bigText,
                textLines = lines?.joinToString("\n"),
                structuredMessages = structured.takeIf(List<ExtractedMessage>::isNotEmpty)
                    ?.joinToString("\n") { "${it.timestamp}\t${it.sender}\t${it.text}" },
                conversationTitle = conversationTitle,
                isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                capturedAt = System.currentTimeMillis()
            ),
            platform = platform,
            conversationKey = conversationKey,
            conversationName = conversationName,
            messages = messages
        )
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    private fun extractStructuredMessages(extras: android.os.Bundle): List<ExtractedMessage> =
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)
            ?.mapNotNull { message ->
                val body = message.text?.toString()?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                ExtractedMessage(
                    sender = message.senderPerson?.name?.toString()?.trim().orEmpty(),
                    text = body,
                    timestamp = message.timestamp,
                    hasExactTimestamp = true
                )
            }
            .orEmpty()

    private fun platformName(packageName: String): String = when (packageName) {
        CaptureSettings.MESSENGER_PACKAGE -> "Messenger"
        CaptureSettings.WHATSAPP_PACKAGE -> "WhatsApp"
        else -> packageName
    }
}

private fun android.os.Bundle.text(key: String): String? =
    getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank)
