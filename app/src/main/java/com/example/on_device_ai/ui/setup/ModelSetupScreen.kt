package com.example.on_device_ai.ui.setup

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.on_device_ai.R
import com.example.on_device_ai.model.ByteSizes
import com.example.on_device_ai.model.QwenModel
import com.example.on_device_ai.ui.Spacing
import com.example.on_device_ai.ui.theme.OnDeviceAITheme

@Composable
fun ModelSetupRoute(onStartChat: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ModelSetupViewModel = viewModel(
        factory = ModelSetupViewModel.factory(application),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModelSetupScreen(
        state = state,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onDelete = viewModel::deleteModel,
        onStartChat = onStartChat,
    )
}

@Composable
fun ModelSetupScreen(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onStartChat: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.item),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModelCard(
                state = state,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onStartChat = onStartChat,
                onAskDelete = { confirmDelete = true },
            )
        }
    }
    if (confirmDelete) {
        DeleteModelDialog(
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ModelCard(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onStartChat: () -> Unit,
    onAskDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text(
                text = stringResource(R.string.model_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.model_variant),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.size_mb, ByteSizes.formatMegabytes(state.sizeBytes)),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.model_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.model_requirement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModelActions(
                state = state,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onStartChat = onStartChat,
                onAskDelete = onAskDelete,
            )
        }
    }
}

@Composable
private fun ModelActions(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onStartChat: () -> Unit,
    onAskDelete: () -> Unit,
) {
    when (val status = state.status) {
        ModelSetupStatus.NotDownloaded -> {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_download))
            }
        }
        ModelSetupStatus.Downloading -> DownloadingActions(state, onCancelDownload)
        ModelSetupStatus.Ready -> ReadyActions(onStartChat, onAskDelete)
        is ModelSetupStatus.Failed -> {
            Text(text = status.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun DownloadingActions(state: ModelSetupUiState, onCancelDownload: () -> Unit) {
    val fraction = (state.downloadedBytes.toFloat() / state.sizeBytes.toFloat()).coerceIn(0f, 1f)
    Text(
        text = stringResource(
            R.string.download_progress,
            ByteSizes.formatMegabytes(state.downloadedBytes),
            ByteSizes.formatMegabytes(state.sizeBytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_cancel))
    }
}

@Composable
private fun ReadyActions(onStartChat: () -> Unit, onAskDelete: () -> Unit) {
    Text(
        text = stringResource(R.string.model_ready),
        color = MaterialTheme.colorScheme.primary,
    )
    Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_start_chat))
    }
    TextButton(onClick = onAskDelete, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_delete))
    }
}

@Composable
private fun DeleteModelDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_body,
                    ByteSizes.formatMegabytes(QwenModel.SIZE_BYTES),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_keep))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ModelSetupPreview() {
    OnDeviceAITheme {
        ModelSetupScreen(
            state = ModelSetupUiState(sizeBytes = QwenModel.SIZE_BYTES),
            onDownload = {},
            onCancelDownload = {},
            onDelete = {},
            onStartChat = {},
        )
    }
}
