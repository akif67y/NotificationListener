package dev.notificationlistener.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MessageNormalizerTest {
    @Test
    fun `group-style display text separates sender and body`() {
        val result = MessageNormalizer.fromDisplayText("Rafi: CT tomorrow", "CSE 221", 42L)!!

        assertEquals("Rafi", result.sender)
        assertEquals("CT tomorrow", result.text)
        assertFalse(result.hasExactTimestamp)
    }

    @Test
    fun `direct message uses title as sender`() {
        val result = MessageNormalizer.fromDisplayText("Can you explain BFS?", "Nadia", 42L)!!

        assertEquals("Nadia", result.sender)
        assertEquals("Can you explain BFS?", result.text)
    }

    @Test
    fun `blank display text is ignored`() {
        assertNull(MessageNormalizer.fromDisplayText("   ", "Nadia", 42L))
    }
}
