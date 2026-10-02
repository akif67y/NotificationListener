package dev.notificationlistener.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.notificationlistener.data.AcademicSignalEntity
import dev.notificationlistener.data.AppDatabase
import dev.notificationlistener.data.CaptureRepository
import dev.notificationlistener.data.CaptureSettings
import dev.notificationlistener.data.SecretStore
import dev.notificationlistener.alerts.EventNotifier
import dev.notificationlistener.network.AnalysisApi
import dev.notificationlistener.network.AnalyzeRequest
import dev.notificationlistener.network.ConversationInput
import dev.notificationlistener.network.MessageInput
import dev.notificationlistener.network.KnownEventInput
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.TimeZone
import java.util.UUID

class AnalysisWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!CaptureSettings.cloudEnabled(applicationContext)) {
            return Result.failure(statusData("Cloud analysis is disabled."))
        }
        val url = CaptureSettings.backendUrl(applicationContext)
        val token = SecretStore.backendToken(applicationContext)
        if (!url.startsWith("https://") || token.length < 32) {
            return Result.failure(statusData("Analysis failed: save a valid HTTPS URL and device token."))
        }

        val database = AppDatabase.get(applicationContext)
        val repository = CaptureRepository(database)
        val windows = ContextWindowBuilder.build(database.dao().pendingMessages())
        if (windows.isEmpty()) {
            return Result.success(statusData(
                "No pending messages. Enable the chat in Chats, then receive a relevant message."
            ))
        }
        val api = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnalysisApi::class.java)

        var analyzedMessages = 0
        var savedSignals = 0
        windows.forEach { window ->
            try {
                val throughTimestamp = window.messages.maxOf { it.messageTimestamp }
                val contextMessages = database.dao().contextMessages(
                    window.conversationId,
                    throughTimestamp
                ).sortedBy { it.messageTimestamp }
                val first = contextMessages.firstOrNull() ?: window.messages.first()
                val knownEvents = database.dao().knownEvents(window.conversationId)
                val request = AnalyzeRequest(
                    requestId = UUID.randomUUID().toString(),
                    timezone = TimeZone.getDefault().id,
                    conversation = ConversationInput(
                        id = window.conversationId,
                        platform = first.platform,
                        name = first.conversationName,
                        courseCode = first.courseCode
                    ),
                    messages = contextMessages.map {
                        MessageInput(it.id, it.sender, it.text, it.messageTimestamp, it.localCategory)
                    },
                    knownEvents = knownEvents.map {
                        KnownEventInput(
                            eventKey = it.eventKey,
                            category = it.category,
                            courseCode = it.courseCode,
                            title = it.title,
                            summary = it.summary,
                            startsAt = it.startsAt,
                            dueAt = it.dueAt,
                            location = it.location,
                            details = it.details,
                            status = it.status,
                            confidence = it.confidence,
                            updatedAt = it.updatedAt
                        )
                    }
                )
                val response = api.analyze("Bearer $token", request)
                val allowedIds = contextMessages.mapTo(mutableSetOf()) { it.id }
                val signals = mutableListOf<AcademicSignalEntity>()
                val events = mutableListOf<dev.notificationlistener.data.AcademicEventEntity>()
                val revisions = mutableListOf<dev.notificationlistener.data.EventRevisionEntity>()
                val notifications = mutableListOf<Pair<dev.notificationlistener.data.AcademicEventEntity, String?>>()
                response.signals.filter { it.category != "IRRELEVANT" }.forEach { signal ->
                    val evidence = signal.evidenceMessageIds.filter(allowedIds::contains)
                    if (evidence.isEmpty()) return@forEach
                    signals += AcademicSignalEntity(
                        id = signal.id,
                        conversationId = window.conversationId,
                        category = signal.category,
                        courseCode = signal.courseCode,
                        title = signal.title,
                        summary = signal.summary,
                        confidence = signal.confidence.coerceIn(0.0, 1.0),
                        urgency = signal.urgency,
                        shouldNotify = signal.shouldNotify,
                        evidenceMessageIds = evidence.joinToString(","),
                        createdAt = System.currentTimeMillis()
                    )
                    val mapped = EventMapper.map(
                        signal = signal,
                        conversationId = window.conversationId,
                        evidence = evidence,
                        existing = knownEvents.firstOrNull { it.eventKey == signal.eventKey },
                        now = System.currentTimeMillis()
                    )
                    mapped.event?.let {
                        events += it
                        if (
                            it.shouldNotify && it.confidence >= NOTIFICATION_CONFIDENCE &&
                            it.status in setOf("CONFIRMED", "CORRECTION")
                        ) {
                            notifications += it to signal.changeSummary
                        }
                    }
                    mapped.revision?.let { revisions += it }
                }
                repository.saveAnalysis(window.messages.map { it.id }, signals, events, revisions)
                analyzedMessages += window.messages.size
                savedSignals += signals.size
                notifications.forEach { (event, change) ->
                    EventNotifier.publishEvent(applicationContext, event, change)
                }
            } catch (error: Exception) {
                repository.markAnalysisRetry(window.messages.map { it.id })
                val message = when {
                    error is HttpException && error.code() == 401 ->
                        "Analysis failed: the device token does not match the backend."
                    error is HttpException && error.code() == 404 ->
                        "Analysis failed: the backend URL is incorrect or the API route is missing."
                    error is HttpException && error.code() == 422 ->
                        "Analysis failed: the backend rejected the message format."
                    error is HttpException && error.code() == 429 ->
                        "Analysis paused: the backend rate limit was reached."
                    error is HttpException && error.code() in 400..499 ->
                        "Analysis failed: backend returned HTTP ${error.code()}."
                    else -> "Backend unavailable. Android will retry automatically."
                }
                return if (error is HttpException && error.code() in 400..499) {
                    Result.failure(statusData(message))
                } else {
                    setProgress(statusData(message))
                    Result.retry()
                }
            }
        }
        val message = if (savedSignals == 0) {
            "Analyzed $analyzedMessages pending message(s); no academic event was found."
        } else {
            "Analysis complete: $savedSignals event signal(s) from $analyzedMessages message(s)."
        }
        return Result.success(statusData(message))
        // No message body, response, or exception is logged.
    }

    private fun statusData(message: String) = workDataOf(OUTPUT_STATUS to message)

    companion object {
        const val OUTPUT_STATUS = "analysis_status"
        const val NOTIFICATION_CONFIDENCE = 0.80
    }
}
