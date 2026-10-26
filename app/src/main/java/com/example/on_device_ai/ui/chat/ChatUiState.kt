package com.example.on_device_ai.ui.chat

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val phase: ChatPhase = ChatPhase.Loading,
    val errorMessage: String? = null,
)

data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String,
)

enum class MessageAuthor {
    User,
    Assistant,
}

sealed interface ChatPhase {
    data object Loading : ChatPhase
    data object Ready : ChatPhase
    data object Generating : ChatPhase
    data class Failed(val message: String) : ChatPhase
}
