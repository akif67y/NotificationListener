package dev.notificationlistener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert
    suspend fun insertSnapshot(snapshot: NotificationSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("SELECT id FROM conversations WHERE platform = :platform AND external_key = :externalKey LIMIT 1")
    suspend fun findConversationId(platform: String, externalKey: String): Long?

    @Query("UPDATE conversations SET display_name = :displayName, last_seen = :lastSeen WHERE id = :id")
    suspend fun touchConversation(id: Long, displayName: String, lastSeen: Long)

    @Query("SELECT courses.code || ',' || courses.title || ',' || courses.aliases FROM conversations JOIN courses ON courses.id = conversations.default_course_id WHERE conversations.id = :conversationId")
    suspend fun courseHints(conversationId: Long): String?

    @Query("SELECT MAX(message_timestamp) FROM messages WHERE conversation_id = :conversationId AND relevance_score >= :threshold")
    suspend fun latestRelevantTimestamp(conversationId: Long, threshold: Double): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>): List<Long>

    @Query(
        """
        SELECT c.id, c.platform, c.display_name AS displayName, c.enabled,
               c.default_course_id AS defaultCourseId, courses.code AS courseCode,
               c.last_seen AS lastSeen, COUNT(m.id) AS messageCount
        FROM conversations c
        LEFT JOIN courses ON courses.id = c.default_course_id
        LEFT JOIN messages m ON m.conversation_id = c.id
        GROUP BY c.id
        ORDER BY c.last_seen DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationWithCourse>>

    @Query("UPDATE conversations SET enabled = :enabled WHERE id = :conversationId")
    suspend fun setConversationEnabled(conversationId: Long, enabled: Boolean)

    @Query("UPDATE messages SET analysis_state = 'PENDING' WHERE conversation_id = :conversationId AND relevance_score >= :threshold AND analysis_state = 'LOCAL_ONLY'")
    suspend fun queueRelevantMessages(conversationId: Long, threshold: Double)

    @Query("UPDATE conversations SET default_course_id = :courseId WHERE id = :conversationId")
    suspend fun setConversationCourse(conversationId: Long, courseId: Long?)

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId")
    suspend fun messagesForConversation(conversationId: Long): List<MessageEntity>

    @Query("UPDATE messages SET local_category = :category, relevance_score = :score, analysis_state = CASE WHEN analysis_state = 'ANALYZED' THEN analysis_state ELSE :state END WHERE id = :messageId")
    suspend fun updateLocalAnalysis(messageId: Long, category: String, score: Double, state: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Query("SELECT * FROM courses ORDER BY code COLLATE NOCASE")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)

    @Query(
        """
        SELECT m.id, m.platform, m.sender, m.text,
               m.message_timestamp AS messageTimestamp,
               c.display_name AS conversationName,
               c.enabled AS analysisEnabled,
               courses.code AS courseCode,
               m.local_category AS localCategory,
               m.relevance_score AS relevanceScore,
               m.analysis_state AS analysisState
        FROM messages m
        JOIN conversations c ON c.id = m.conversation_id
        LEFT JOIN courses ON courses.id = c.default_course_id
        ORDER BY m.message_timestamp DESC, m.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentMessages(limit: Int = 200): Flow<List<MessageListItem>>

    @Query("SELECT * FROM captures ORDER BY posted_at DESC, id DESC LIMIT :limit")
    fun observeSnapshots(limit: Int = 100): Flow<List<NotificationSnapshotEntity>>

    @Query("SELECT * FROM academic_signals ORDER BY created_at DESC LIMIT :limit")
    fun observeSignals(limit: Int = 100): Flow<List<AcademicSignalEntity>>

    @Query(
        """
        SELECT e.event_key AS eventKey, e.conversation_id AS conversationId,
               c.display_name AS conversationName, e.category,
               e.course_code AS courseCode, e.title, e.summary,
               e.starts_at AS startsAt, e.due_at AS dueAt, e.location, e.details,
               e.status, e.confidence, e.urgency, e.should_notify AS shouldNotify,
               e.archived, e.updated_at AS updatedAt
        FROM academic_events e
        JOIN conversations c ON c.id = e.conversation_id
        WHERE e.archived = 0
        ORDER BY
            CASE WHEN e.due_at IS NOT NULL THEN e.due_at
                 WHEN e.starts_at IS NOT NULL THEN e.starts_at
                 ELSE 9223372036854775807 END ASC,
            e.updated_at DESC
        """
    )
    fun observeEvents(): Flow<List<EventWithConversation>>

    @Query("SELECT * FROM academic_events WHERE conversation_id = :conversationId AND archived = 0 ORDER BY updated_at DESC LIMIT :limit")
    suspend fun knownEvents(conversationId: Long, limit: Int = 30): List<AcademicEventEntity>

    @Query("SELECT * FROM academic_events WHERE event_key = :eventKey LIMIT 1")
    suspend fun event(eventKey: String): AcademicEventEntity?

    @Upsert
    suspend fun upsertEvents(events: List<AcademicEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevisions(revisions: List<EventRevisionEntity>)

    @Query(
        """
        SELECT r.id, r.event_key AS eventKey, r.action,
               r.change_summary AS changeSummary, r.created_at AS createdAt,
               e.title, e.course_code AS courseCode
        FROM event_revisions r
        JOIN academic_events e ON e.event_key = r.event_key
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT :limit
        """
    )
    fun observeRevisions(limit: Int = 100): Flow<List<RevisionListItem>>

    @Query("UPDATE academic_events SET archived = :archived WHERE event_key = :eventKey")
    suspend fun setEventArchived(eventKey: String, archived: Boolean)

    @Query(
        """
        SELECT * FROM academic_events
        WHERE archived = 0
          AND status IN ('CONFIRMED', 'CORRECTION')
          AND COALESCE(due_at, starts_at) BETWEEN :now AND :until
        ORDER BY COALESCE(due_at, starts_at) ASC
        LIMIT :limit
        """
    )
    suspend fun upcomingEvents(now: Long, until: Long, limit: Int = 20): List<AcademicEventEntity>

    @Query(
        """
        SELECT m.id, m.conversation_id AS conversationId, m.platform,
               c.display_name AS conversationName, courses.code AS courseCode,
               m.sender, m.text, m.message_timestamp AS messageTimestamp,
               m.local_category AS localCategory
        FROM messages m
        JOIN conversations c ON c.id = m.conversation_id
        LEFT JOIN courses ON courses.id = c.default_course_id
        WHERE c.enabled = 1 AND m.analysis_state IN ('PENDING', 'RETRY')
        ORDER BY m.message_timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun pendingMessages(limit: Int = 60): List<PendingMessage>

    @Query(
        """
        SELECT m.id, m.conversation_id AS conversationId, m.platform,
               c.display_name AS conversationName, courses.code AS courseCode,
               m.sender, m.text, m.message_timestamp AS messageTimestamp,
               m.local_category AS localCategory
        FROM messages m
        JOIN conversations c ON c.id = m.conversation_id
        LEFT JOIN courses ON courses.id = c.default_course_id
        WHERE m.conversation_id = :conversationId AND m.message_timestamp <= :throughTimestamp
        ORDER BY m.message_timestamp DESC, m.id DESC
        LIMIT :limit
        """
    )
    suspend fun contextMessages(
        conversationId: Long,
        throughTimestamp: Long,
        limit: Int = 12
    ): List<PendingMessage>

    @Query("UPDATE messages SET analysis_state = :state WHERE id IN (:messageIds)")
    suspend fun setAnalysisState(messageIds: List<Long>, state: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<AcademicSignalEntity>)

    @Query("DELETE FROM captures WHERE captured_at < :cutoff")
    suspend fun deleteSnapshotsOlderThan(cutoff: Long)

    @Query("DELETE FROM captures")
    suspend fun deleteAllSnapshots()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM academic_signals")
    suspend fun deleteAllSignals()

    @Query("DELETE FROM event_revisions")
    suspend fun deleteAllRevisions()

    @Query("DELETE FROM academic_events")
    suspend fun deleteAllEvents()
}
