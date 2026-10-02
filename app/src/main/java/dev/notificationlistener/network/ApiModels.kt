package dev.notificationlistener.network

import com.google.gson.annotations.SerializedName

data class AnalyzeRequest(
    @SerializedName("request_id") val requestId: String,
    val timezone: String,
    val conversation: ConversationInput,
    val messages: List<MessageInput>,
    @SerializedName("known_events") val knownEvents: List<KnownEventInput>
)

data class ConversationInput(
    val id: Long,
    val platform: String,
    val name: String,
    @SerializedName("course_code") val courseCode: String?
)

data class MessageInput(
    val id: Long,
    val sender: String,
    val text: String,
    val timestamp: Long,
    @SerializedName("local_category") val localCategory: String
)

data class KnownEventInput(
    @SerializedName("event_key") val eventKey: String,
    val category: String,
    @SerializedName("course_code") val courseCode: String?,
    val title: String,
    val summary: String,
    @SerializedName("starts_at") val startsAt: Long?,
    @SerializedName("due_at") val dueAt: Long?,
    val location: String?,
    val details: String?,
    val status: String,
    val confidence: Double,
    @SerializedName("updated_at") val updatedAt: Long
)

data class AnalyzeResponse(
    @SerializedName("request_id") val requestId: String,
    val signals: List<SignalOutput>
)

data class SignalOutput(
    val id: String,
    @SerializedName("event_key") val eventKey: String,
    val action: String,
    val category: String,
    @SerializedName("course_code") val courseCode: String?,
    val title: String,
    val summary: String,
    @SerializedName("starts_at") val startsAt: String?,
    @SerializedName("due_at") val dueAt: String?,
    val location: String?,
    val details: String?,
    val status: String,
    val confidence: Double,
    val urgency: String,
    @SerializedName("should_notify") val shouldNotify: Boolean,
    @SerializedName("change_summary") val changeSummary: String?,
    @SerializedName("evidence_message_ids") val evidenceMessageIds: List<Long>
)
