package dev.notificationlistener.analysis

import dev.notificationlistener.network.SignalOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMapperTest {
    @Test
    fun `maps RFC3339 timestamps and creates revision`() {
        val mapped = EventMapper.map(signal(), 7, listOf(10, 11), null, 1000)
        assertEquals(1789362900000L, mapped.event?.dueAt)
        assertEquals("10,11", mapped.event?.evidenceMessageIds)
        assertEquals("CREATE", mapped.revision?.action)
    }

    @Test
    fun `no change does not rewrite event`() {
        val mapped = EventMapper.map(signal().copy(action = "NO_CHANGE"), 7, listOf(10), null, 1000)
        assertNull(mapped.event)
        assertNull(mapped.revision)
    }

    @Test
    fun `invalid timestamp is safely omitted`() {
        assertNull(EventMapper.parseTimestamp("tomorrow"))
        assertTrue(EventMapper.parseTimestamp("2026-09-14T11:15:00+06:00") != null)
    }

    private fun signal() = SignalOutput(
        id = "signal-1",
        eventKey = "event-1",
        action = "CREATE",
        category = "DEADLINE",
        courseCode = "CSE 221",
        title = "Assignment deadline",
        summary = "Submit by Sunday",
        startsAt = null,
        dueAt = "2026-09-14T11:15:00+06:00",
        location = null,
        details = "Assignment 2",
        status = "CONFIRMED",
        confidence = 0.92,
        urgency = "HIGH",
        shouldNotify = true,
        changeSummary = null,
        evidenceMessageIds = listOf(10, 11)
    )
}
