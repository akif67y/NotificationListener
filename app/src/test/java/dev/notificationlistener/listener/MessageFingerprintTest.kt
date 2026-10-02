package dev.notificationlistener.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageFingerprintTest {
    @Test
    fun `same structured message has stable fingerprint`() {
        val first = fingerprint("Rafi", "  CT   tomorrow ", 1_000L, true)
        val repeatedNotificationUpdate = fingerprint("rafi", "ct tomorrow", 1_000L, true)

        assertEquals(first, repeatedNotificationUpdate)
    }

    @Test
    fun `different structured timestamps remain distinct`() {
        assertNotEquals(
            fingerprint("Rafi", "yes", 1_000L, true),
            fingerprint("Rafi", "yes", 2_000L, true)
        )
    }

    @Test
    fun `fallback messages in same ten minute window deduplicate`() {
        assertEquals(
            fingerprint("Rafi", "CT tomorrow", 600_001L, false),
            fingerprint("Rafi", "CT tomorrow", 1_199_999L, false)
        )
    }

    private fun fingerprint(sender: String, text: String, timestamp: Long, exact: Boolean) =
        MessageFingerprint.create(
            packageName = "com.facebook.orca",
            conversationKey = "cse221",
            sender = sender,
            text = text,
            timestamp = timestamp,
            hasExactTimestamp = exact
        )
}
