package com.example.on_device_ai.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.on_device_ai.R
import com.example.on_device_ai.data.ModelFiles
import com.example.on_device_ai.inference.LlmChatEngine
import com.example.on_device_ai.inference.MediaPipeLlmChatEngine
import com.example.on_device_ai.inference.ModelNotReadyException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val engine: LlmChatEngine,
    private val dispatcher: ExecutorCoroutineDispatcher,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val stopRequested = AtomicBoolean(false)

    init {
        load()
    }

    fun onDraftChange(value: String) {
        publish { it.copy(draft = value, errorMessage = null) }
    }

    fun send() {
        val snapshot = _uiState.value
        val prompt = snapshot.draft.trim()
        if (prompt.isEmpty() || snapshot.phase != ChatPhase.Ready) return
        stopRequested.set(false)
        val userId = nextId(snapshot.messages)
        publish {
            it.copy(
                draft = "",
                phase = ChatPhase.Generating,
                errorMessage = null,
                messages = it.messages +
                    ChatMessage(userId, MessageAuthor.User, prompt) +
                    ChatMessage(userId + 1, MessageAuthor.Assistant, ""),
            )
        }
        viewModelScope.launch(dispatcher) {
            try {
                engine.generate(prompt, ::onChunk, ::onGenerateError)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onGenerateError(error)
            }
        }
    }

    fun stop() {
        if (_uiState.value.phase != ChatPhase.Generating) return
        stopRequested.set(true)
        viewModelScope.launch(dispatcher) {
            engine.cancelGeneration()
        }
        publish { it.copy(phase = ChatPhase.Ready) }
    }

    fun resetConversation() {
        if (_uiState.value.phase != ChatPhase.Ready) return
        viewModelScope.launch(dispatcher) {
            try {
                engine.resetConversation()
                publish { it.copy(messages = emptyList(), errorMessage = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onGenerateError(error)
            }
        }
    }

    fun retryLoad() {
        if (_uiState.value.phase !is ChatPhase.Failed) return
        load()
    }

    override fun onCleared() {
        dispatcher.executor.execute {
            try {
                engine.close()
            } finally {
                dispatcher.close()
            }
        }
    }

    private fun load() {
        publish { it.copy(phase = ChatPhase.Loading, errorMessage = null) }
        viewModelScope.launch(dispatcher) {
            try {
                engine.load()
                publish { it.copy(phase = ChatPhase.Ready) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: ModelNotReadyException) {
                publish { it.copy(phase = ChatPhase.Failed(text(R.string.chat_model_missing))) }
            } catch (error: Throwable) {
                val message = if (isUnsupportedDevice(error)) {
                    text(R.string.chat_unsupported_device)
                } else {
                    text(R.string.chat_model_load_failed, error.message.orEmpty())
                }
                publish { it.copy(phase = ChatPhase.Failed(message)) }
            }
        }
    }

    private fun onChunk(chunk: String, done: Boolean) {
        if (stopRequested.get()) return
        publish { state ->
            val messages = state.messages.mapIndexed { index, message ->
                if (index == state.messages.lastIndex && message.author == MessageAuthor.Assistant) {
                    message.copy(text = message.text + chunk)
                } else {
                    message
                }
            }
            state.copy(
                messages = messages,
                phase = if (done) ChatPhase.Ready else ChatPhase.Generating,
            )
        }
    }

    private fun onGenerateError(error: Throwable) {
        if (stopRequested.get()) return
        publish {
            it.copy(
                phase = ChatPhase.Ready,
                errorMessage = text(R.string.chat_reply_failed, error.message.orEmpty()),
            )
        }
    }

    private fun publish(transform: (ChatUiState) -> ChatUiState) {
        if (!viewModelScope.isActive) return
        _uiState.update(transform)
    }

    private fun text(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun nextId(messages: List<ChatMessage>): Long {
        return (messages.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    private fun isUnsupportedDevice(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is UnsatisfiedLinkError) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val INFERENCE_THREAD = "llm-inference"

        fun factory(application: Application): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    val dispatcher = Executors.newSingleThreadExecutor { runnable ->
                        Thread(runnable, INFERENCE_THREAD).apply { isDaemon = true }
                    }.asCoroutineDispatcher()
                    ChatViewModel(
                        application = application,
                        engine = MediaPipeLlmChatEngine(
                            context = application,
                            modelFile = ModelFiles.create(application).modelFile(),
                        ),
                        dispatcher = dispatcher,
                    )
                }
            }
        }
    }
}
