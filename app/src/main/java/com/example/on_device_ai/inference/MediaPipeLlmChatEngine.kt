@file:Suppress("DEPRECATION")

package com.example.on_device_ai.inference

import android.content.Context
import com.example.on_device_ai.model.QwenModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class MediaPipeLlmChatEngine(
    private val context: Context,
    private val modelFile: File,
) : LlmChatEngine {

    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private val closed = AtomicBoolean(false)
    private val generationCancelled = AtomicBoolean(false)

    override fun load() {
        if (llm != null) return
        if (!modelFile.isFile || modelFile.length() != QwenModel.SIZE_BYTES) {
            throw ModelNotReadyException()
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(QwenModel.MAX_TOKENS)
            .setMaxTopK(QwenModel.TOP_K)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        try {
            val createdSession = openSession(created)
            llm = created
            session = createdSession
        } catch (error: Throwable) {
            created.close()
            throw error
        }
    }

    override fun generate(
        prompt: String,
        onChunk: (chunk: String, done: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val activeSession = session ?: throw ModelNotReadyException()
        generationCancelled.set(false)
        activeSession.addQueryChunk(prompt)
        val future = activeSession.generateResponseAsync { chunk, done ->
            if (!closed.get() && !generationCancelled.get()) {
                onChunk(chunk, done)
            }
        }
        future.addListener(
            {
                try {
                    future.get()
                } catch (error: Throwable) {
                    if (!closed.get() && !generationCancelled.get()) {
                        onError(unwrap(error))
                    }
                }
            },
            Runnable::run,
        )
    }

    override fun cancelGeneration() {
        generationCancelled.set(true)
        try {
            session?.cancelGenerateResponseAsync()
        } catch (_: Throwable) {
            // The session can already be idle when the user taps stop.
        }
    }

    override fun resetConversation() {
        val current = llm ?: throw ModelNotReadyException()
        cancelGeneration()
        try {
            session?.close()
        } catch (_: Throwable) {
            // A session that never started generating can still be replaced.
        }
        session = openSession(current)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generationCancelled.set(true)
        val activeSession = session
        val activeEngine = llm
        session = null
        llm = null
        try {
            activeSession?.cancelGenerateResponseAsync()
        } catch (_: Throwable) {
            // Closing must continue even if cancellation is rejected.
        }
        try {
            activeSession?.close()
        } catch (_: Throwable) {
            // The native session may already have been released.
        }
        try {
            activeEngine?.close()
        } catch (_: Throwable) {
            // The engine is going away with the screen.
        }
    }

    private fun openSession(inference: LlmInference): LlmInferenceSession {
        val options = LlmInferenceSessionOptions.builder()
            .setTopK(QwenModel.TOP_K)
            .setTopP(QwenModel.TOP_P)
            .setTemperature(QwenModel.TEMPERATURE)
            .setPromptTemplates(promptTemplates())
            .build()
        return LlmInferenceSession.createFromOptions(inference, options)
    }

    private fun promptTemplates(): PromptTemplates {
        return PromptTemplates.builder()
            .setUserPrefix(QwenModel.USER_PREFIX)
            .setUserSuffix(QwenModel.USER_SUFFIX)
            .setModelPrefix(QwenModel.MODEL_PREFIX)
            .setModelSuffix(QwenModel.MODEL_SUFFIX)
            .setSystemPrefix(QwenModel.SYSTEM_PREFIX)
            .setSystemSuffix(QwenModel.SYSTEM_SUFFIX)
            .build()
    }

    private fun unwrap(error: Throwable): Throwable {
        val cause = (error as? ExecutionException)?.cause
        return cause ?: error
    }
}
