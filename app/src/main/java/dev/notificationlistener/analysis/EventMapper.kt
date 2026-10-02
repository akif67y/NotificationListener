package dev.notificationlistener.analysis

import dev.notificationlistener.data.AcademicEventEntity
import dev.notificationlistener.data.EventRevisionEntity
import dev.notificationlistener.network.SignalOutput
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class MappedEvent(
    val event: AcademicEventEntity?,
    val revision: EventRevisionEntity?
)

object EventMapper {
    fun map(
        signal: SignalOutput,
        conversationId: Long,
        evidence: List<Long>,
        existing: AcademicEventEntity?,
        now: Long
    ): MappedEvent {
        if (signal.action == "NO_CHANGE") return MappedEvent(null, null)
        val createdAt = existing?.createdAt ?: now
        val event = AcademicEventEntity(
            eventKey = signal.eventKey,
            conversationId = conversationId,
            category = signal.category,
            courseCode = signal.courseCode,
            title = signal.title,
            summary = signal.summary,
            startsAt = parseTimestamp(signal.startsAt),
            dueAt = parseTimestamp(signal.dueAt),
            location = signal.location,
            details = signal.details,
            status = signal.status,
            confidence = signal.confidence.coerceIn(0.0, 1.0),
            urgency = signal.urgency,
            shouldNotify = signal.shouldNotify,
            evidenceMessageIds = evidence.joinToString(","),
            lastSignalId = signal.id,
            archived = false,
            createdAt = createdAt,
            updatedAt = now
        )
        return MappedEvent(
            event,
            EventRevisionEntity(
                eventKey = signal.eventKey,
                signalId = signal.id,
                action = signal.action,
                changeSummary = signal.changeSummary,
                createdAt = now
            )
        )
    }

    fun parseTimestamp(value: String?): Long? {
        if (value == null) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
