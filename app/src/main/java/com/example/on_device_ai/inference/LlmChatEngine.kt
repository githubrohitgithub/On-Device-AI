package com.example.on_device_ai.inference

interface LlmChatEngine : AutoCloseable {
    fun load()

    fun generate(
        prompt: String,
        onChunk: (chunk: String, done: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun cancelGeneration()

    fun resetConversation()
}

class ModelNotReadyException : IllegalStateException()
