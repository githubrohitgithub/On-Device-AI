package com.example.on_device_ai.ui.setup

import com.example.on_device_ai.model.QwenModel

data class ModelSetupUiState(
    val sizeBytes: Long = QwenModel.SIZE_BYTES,
    val downloadedBytes: Long = 0L,
    val status: ModelSetupStatus = ModelSetupStatus.NotDownloaded,
)

sealed interface ModelSetupStatus {
    data object NotDownloaded : ModelSetupStatus
    data object Downloading : ModelSetupStatus
    data object Ready : ModelSetupStatus
    data class Failed(val message: String) : ModelSetupStatus
}
