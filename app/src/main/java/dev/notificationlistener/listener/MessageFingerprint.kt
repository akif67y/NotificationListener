package dev.notificationlistener.listener

import java.security.MessageDigest
import java.util.Locale

object MessageFingerprint {
    private const val FALLBACK_BUCKET_MILLIS = 10L * 60L * 1000L

    fun create(
        packageName: String,
        conversationKey: String,
        sender: String,
        text: String,
        timestamp: Long,
        hasExactTimestamp: Boolean
    ): String {
        val timeIdentity = if (hasExactTimestamp) timestamp else timestamp / FALLBACK_BUCKET_MILLIS
        val canonical = listOf(
            packageName.trim().lowercase(Locale.ROOT),
            conversationKey.trim().lowercase(Locale.ROOT),
            sender.trim().lowercase(Locale.ROOT),
            text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT),
            timeIdentity.toString()
        ).joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
