package dev.notificationlistener.analysis

import dev.notificationlistener.data.PendingMessage

data class ContextWindow(val conversationId: Long, val messages: List<PendingMessage>)

object ContextWindowBuilder {
    fun build(messages: List<PendingMessage>, maxMessages: Int = 12): List<ContextWindow> =
        messages.groupBy(PendingMessage::conversationId)
            .map { (conversationId, grouped) ->
                ContextWindow(conversationId, grouped.sortedBy(PendingMessage::messageTimestamp).takeLast(maxMessages))
            }
            .sortedBy { it.messages.firstOrNull()?.messageTimestamp ?: Long.MAX_VALUE }
}
