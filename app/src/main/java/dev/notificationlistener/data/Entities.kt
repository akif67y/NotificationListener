package dev.notificationlistener.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captures",
    indices = [Index(value = ["posted_at"], name = "captures_posted_at")]
)
data class NotificationSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    val title: String?,
    val text: String?,
    @ColumnInfo(name = "big_text") val bigText: String?,
    @ColumnInfo(name = "text_lines") val textLines: String?,
    @ColumnInfo(name = "structured_messages") val structuredMessages: String?,
    @ColumnInfo(name = "conversation_title") val conversationTitle: String?,
    @ColumnInfo(name = "is_group_summary") val isGroupSummary: Boolean,
    @ColumnInfo(name = "captured_at") val capturedAt: Long
)

@Entity(
    tableName = "courses",
    indices = [Index(value = ["code"], unique = true)]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val title: String,
    val section: String = "",
    val aliases: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_course_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["platform", "external_key"], unique = true),
        Index(value = ["default_course_id"])
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    @ColumnInfo(name = "external_key") val externalKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val enabled: Boolean = false,
    @ColumnInfo(name = "default_course_id") val defaultCourseId: Long? = null,
    @ColumnInfo(name = "last_seen") val lastSeen: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = NotificationSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshot_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["snapshot_id"]),
        Index(value = ["conversation_id"]),
        Index(value = ["message_timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "snapshot_id") val snapshotId: Long,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val platform: String,
    val sender: String,
    val text: String,
    @ColumnInfo(name = "message_timestamp") val messageTimestamp: Long,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    val fingerprint: String,
    @ColumnInfo(name = "local_category", defaultValue = "'IRRELEVANT'") val localCategory: String = "IRRELEVANT",
    @ColumnInfo(name = "relevance_score", defaultValue = "0.0") val relevanceScore: Double = 0.0,
    @ColumnInfo(name = "analysis_state", defaultValue = "'LOCAL_ONLY'") val analysisState: String = "LOCAL_ONLY"
)

@Entity(
    tableName = "academic_signals",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["conversation_id"]), Index(value = ["created_at"])]
)
data class AcademicSignalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val category: String,
    @ColumnInfo(name = "course_code") val courseCode: String?,
    val title: String,
    val summary: String,
    val confidence: Double,
    val urgency: String,
    @ColumnInfo(name = "should_notify") val shouldNotify: Boolean,
    @ColumnInfo(name = "evidence_message_ids") val evidenceMessageIds: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "academic_events",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["course_code"]),
        Index(value = ["starts_at"]),
        Index(value = ["due_at"]),
        Index(value = ["updated_at"])
    ]
)
data class AcademicEventEntity(
    @PrimaryKey @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val category: String,
    @ColumnInfo(name = "course_code") val courseCode: String?,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "starts_at") val startsAt: Long?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    val location: String?,
    val details: String?,
    val status: String,
    val confidence: Double,
    val urgency: String,
    @ColumnInfo(name = "should_notify") val shouldNotify: Boolean,
    @ColumnInfo(name = "evidence_message_ids") val evidenceMessageIds: String,
    @ColumnInfo(name = "last_signal_id") val lastSignalId: String,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "event_revisions",
    foreignKeys = [ForeignKey(
        entity = AcademicEventEntity::class,
        parentColumns = ["event_key"],
        childColumns = ["event_key"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["event_key"]),
        Index(value = ["signal_id"], unique = true),
        Index(value = ["created_at"])
    ]
)
data class EventRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "signal_id") val signalId: String,
    val action: String,
    @ColumnInfo(name = "change_summary") val changeSummary: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

data class EventWithConversation(
    val eventKey: String,
    val conversationId: Long,
    val conversationName: String,
    val category: String,
    val courseCode: String?,
    val title: String,
    val summary: String,
    val startsAt: Long?,
    val dueAt: Long?,
    val location: String?,
    val details: String?,
    val status: String,
    val confidence: Double,
    val urgency: String,
    val shouldNotify: Boolean,
    val archived: Boolean,
    val updatedAt: Long
)

data class RevisionListItem(
    val id: Long,
    val eventKey: String,
    val action: String,
    val changeSummary: String?,
    val createdAt: Long,
    val title: String,
    val courseCode: String?
)

data class ConversationWithCourse(
    val id: Long,
    val platform: String,
    val displayName: String,
    val enabled: Boolean,
    val defaultCourseId: Long?,
    val courseCode: String?,
    val lastSeen: Long,
    val messageCount: Int
)

data class MessageListItem(
    val id: Long,
    val platform: String,
    val sender: String,
    val text: String,
    val messageTimestamp: Long,
    val conversationName: String,
    val analysisEnabled: Boolean,
    val courseCode: String?,
    val localCategory: String,
    val relevanceScore: Double,
    val analysisState: String
)

data class PendingMessage(
    val id: Long,
    val conversationId: Long,
    val platform: String,
    val conversationName: String,
    val courseCode: String?,
    val sender: String,
    val text: String,
    val messageTimestamp: Long,
    val localCategory: String
)
