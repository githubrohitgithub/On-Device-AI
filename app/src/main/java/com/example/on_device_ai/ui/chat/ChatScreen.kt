package com.example.on_device_ai.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.on_device_ai.R
import com.example.on_device_ai.ui.Spacing
import com.example.on_device_ai.ui.theme.OnDeviceAITheme

private val BubbleMaxWidth = 320.dp
private const val MAX_INPUT_LINES = 4

@Composable
fun ChatRoute(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(application))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        messages = state.messages,
        draft = state.draft,
        phase = state.phase,
        errorMessage = state.errorMessage,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onReset = viewModel::resetConversation,
        onRetry = viewModel::retryLoad,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    draft: String,
    phase: ChatPhase,
    errorMessage: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onReset,
                        enabled = phase == ChatPhase.Ready && messages.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.chat_new))
                    }
                },
            )
        },
        bottomBar = {
            if (phase == ChatPhase.Ready || phase == ChatPhase.Generating) {
                ChatInputBar(
                    draft = draft,
                    phase = phase,
                    errorMessage = errorMessage,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (phase) {
                ChatPhase.Loading -> StatusMessage(
                    message = stringResource(R.string.chat_loading),
                    showProgress = true,
                )
                is ChatPhase.Failed -> FailedMessage(message = phase.message, onRetry = onRetry)
                ChatPhase.Ready, ChatPhase.Generating -> MessageList(
                    messages = messages,
                    isGenerating = phase == ChatPhase.Generating,
                )
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>, isGenerating: Boolean) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        val lastIndex = messages.lastIndex
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }
    if (messages.isEmpty()) {
        StatusMessage(message = stringResource(R.string.chat_empty), showProgress = false)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        items(messages, key = { it.id }) { message ->
            val showPending = isGenerating &&
                message.id == messages.lastOrNull()?.id &&
                message.author == MessageAuthor.Assistant &&
                message.text.isEmpty()
            if (message.text.isNotEmpty() || showPending) {
                ChatBubble(message = message, showPending = showPending)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, showPending: Boolean) {
    val isUser = message.author == MessageAuthor.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(Spacing.card),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                text = if (showPending) stringResource(R.string.chat_writing) else message.text,
                modifier = Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .padding(Spacing.card),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    phase: ChatPhase,
    errorMessage: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = Spacing.tight) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    maxLines = MAX_INPUT_LINES,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                if (phase == ChatPhase.Generating) {
                    OutlinedButton(onClick = onStop) {
                        Text(stringResource(R.string.chat_stop))
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() && phase == ChatPhase.Ready,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, showProgress: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.item, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) CircularProgressIndicator()
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FailedMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.item, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    OnDeviceAITheme {
        ChatScreen(
            messages = listOf(
                ChatMessage(1, MessageAuthor.User, "Hello"),
                ChatMessage(2, MessageAuthor.Assistant, "Hi. I run on this phone."),
            ),
            draft = "",
            phase = ChatPhase.Ready,
            errorMessage = null,
            onDraftChange = {},
            onSend = {},
            onStop = {},
            onReset = {},
            onRetry = {},
            onBack = {},
        )
    }
}
