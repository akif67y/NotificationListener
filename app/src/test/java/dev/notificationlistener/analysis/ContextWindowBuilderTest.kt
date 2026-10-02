package dev.notificationlistener.analysis

import dev.notificationlistener.data.PendingMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextWindowBuilderTest {
    @Test
    fun `groups by conversation and keeps newest bounded context`() {
        val input = (1L..15L).map { pending(it, 7, it) } + pending(30, 8, 30)
        val windows = ContextWindowBuilder.build(input, maxMessages = 12)

        assertEquals(2, windows.size)
        assertEquals((4L..15L).toList(), windows.first { it.conversationId == 7L }.messages.map { it.id })
        assertEquals(listOf(30L), windows.first { it.conversationId == 8L }.messages.map { it.id })
    }

    private fun pending(id: Long, conversationId: Long, timestamp: Long) = PendingMessage(
        id = id,
        conversationId = conversationId,
        platform = "Messenger",
        conversationName = "CSE",
        courseCode = "CSE 221",
        sender = "Student",
        text = "message $id",
        messageTimestamp = timestamp,
        localCategory = "COURSE_QUESTION"
    )
}
