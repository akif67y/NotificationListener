package dev.notificationlistener.listener

object MessageNormalizer {
    fun fromDisplayText(
        displayText: String,
        fallbackSender: String?,
        timestamp: Long
    ): ExtractedMessage? {
        val cleaned = displayText.trim().takeIf(String::isNotBlank) ?: return null
        val separator = cleaned.indexOf(':')
        val hasPlausibleSender = separator in 1..80 && separator < cleaned.lastIndex
        val sender = if (hasPlausibleSender) {
            cleaned.substring(0, separator).trim()
        } else {
            fallbackSender?.trim().orEmpty()
        }
        val body = if (hasPlausibleSender) {
            cleaned.substring(separator + 1).trim()
        } else {
            cleaned
        }
        if (body.isBlank()) return null
        return ExtractedMessage(sender, body, timestamp, hasExactTimestamp = false)
    }
}
