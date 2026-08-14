package com.example.data.model

import java.util.UUID

enum class ChatSender {
    USER,
    ASSISTANT
}

data class FinancialChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val suggestions: List<String> = emptyList()
)
