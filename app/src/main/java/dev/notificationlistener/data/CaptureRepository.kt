package dev.notificationlistener.data

import androidx.room.withTransaction
import dev.notificationlistener.analysis.LocalAcademicClassifier
import dev.notificationlistener.listener.ExtractedNotification
import dev.notificationlistener.listener.MessageFingerprint

class CaptureRepository(private val database: AppDatabase) {
    private val dao = database.dao()

    suspend fun ingest(extracted: ExtractedNotification): Int = database.withTransaction {
        val snapshotId = dao.insertSnapshot(extracted.snapshot)
        val newConversationId = dao.insertConversation(
            ConversationEntity(
                platform = extracted.platform,
                externalKey = extracted.conversationKey,
                displayName = extracted.conversationName,
                lastSeen = extracted.snapshot.postedAt
            )
        )
        val conversationId = if (newConversationId != -1L) {
            newConversationId
        } else {
            requireNotNull(dao.findConversationId(extracted.platform, extracted.conversationKey))
        }
        dao.touchConversation(conversationId, extracted.conversationName, extracted.snapshot.postedAt)
        val courseHints = dao.courseHints(conversationId)?.split(',').orEmpty()
        var latestRelevant = dao.latestRelevantTimestamp(
            conversationId,
            LocalAcademicClassifier.UPLOAD_THRESHOLD
        )

        if (extracted.messages.isEmpty()) return@withTransaction 0
        dao.insertMessages(
            extracted.messages.map { message ->
                val classification = LocalAcademicClassifier.classify(message.text, courseHints)
                val followsRelevantMessage = classification.score < LocalAcademicClassifier.UPLOAD_THRESHOLD &&
                    latestRelevant?.let { message.timestamp in it..(it + CONTEXT_WINDOW_MILLIS) } == true
                if (classification.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD) {
                    latestRelevant = maxOf(latestRelevant ?: Long.MIN_VALUE, message.timestamp)
                }
                MessageEntity(
                    snapshotId = snapshotId,
                    conversationId = conversationId,
                    platform = extracted.platform,
                    sender = message.sender,
                    text = message.text,
                    messageTimestamp = message.timestamp,
                    capturedAt = extracted.snapshot.capturedAt,
                    fingerprint = MessageFingerprint.create(
                        packageName = extracted.snapshot.packageName,
                        conversationKey = extracted.conversationKey,
                        sender = message.sender,
                        text = message.text,
                        timestamp = message.timestamp,
                        hasExactTimestamp = message.hasExactTimestamp
                    ),
                    localCategory = if (followsRelevantMessage) "CONTEXT" else classification.category,
                    relevanceScore = if (followsRelevantMessage) {
                        LocalAcademicClassifier.UPLOAD_THRESHOLD
                    } else classification.score,
                    analysisState = if (
                        classification.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD || followsRelevantMessage
                    ) "PENDING" else "LOCAL_ONLY"
                )
            }
        ).count { it != -1L }
    }

    suspend fun setConversationEnabled(id: Long, enabled: Boolean) = database.withTransaction {
        dao.setConversationEnabled(id, enabled)
        if (enabled) dao.queueRelevantMessages(id, LocalAcademicClassifier.UPLOAD_THRESHOLD)
    }

    suspend fun setConversationCourse(id: Long, courseId: Long?) = database.withTransaction {
        dao.setConversationCourse(id, courseId)
        val hints = dao.courseHints(id)?.split(',').orEmpty()
        dao.messagesForConversation(id).forEach { message ->
            val result = LocalAcademicClassifier.classify(message.text, hints)
            dao.updateLocalAnalysis(
                message.id,
                result.category,
                result.score,
                if (result.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD) "PENDING" else "LOCAL_ONLY"
            )
        }
    }

    suspend fun saveAnalysis(
        messageIds: List<Long>,
        signals: List<AcademicSignalEntity>,
        events: List<AcademicEventEntity>,
        revisions: List<EventRevisionEntity>
    ) =
        database.withTransaction {
            if (signals.isNotEmpty()) dao.insertSignals(signals)
            if (events.isNotEmpty()) dao.upsertEvents(events)
            if (revisions.isNotEmpty()) dao.insertRevisions(revisions)
            if (messageIds.isNotEmpty()) dao.setAnalysisState(messageIds, "ANALYZED")
        }

    suspend fun markAnalysisRetry(messageIds: List<Long>) {
        if (messageIds.isNotEmpty()) dao.setAnalysisState(messageIds, "RETRY")
    }

    suspend fun removeExpired(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * DAY_MILLIS
        dao.deleteSnapshotsOlderThan(cutoff)
    }

    suspend fun deleteCapturedData() = database.withTransaction {
        dao.deleteAllRevisions()
        dao.deleteAllEvents()
        dao.deleteAllSignals()
        dao.deleteAllMessages()
        dao.deleteAllSnapshots()
        dao.deleteAllConversations()
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val CONTEXT_WINDOW_MILLIS = 15L * 60L * 1000L
    }
}
