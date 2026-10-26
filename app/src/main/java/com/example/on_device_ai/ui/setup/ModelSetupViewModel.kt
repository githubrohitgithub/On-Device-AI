package com.example.on_device_ai.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.on_device_ai.R
import com.example.on_device_ai.data.DownloadException
import com.example.on_device_ai.data.ModelDownloader
import com.example.on_device_ai.data.ModelFiles
import com.example.on_device_ai.data.OkHttpModelDownloader
import com.example.on_device_ai.model.QwenModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelSetupViewModel(
    application: Application,
    private val files: ModelFiles,
    private val downloader: ModelDownloader,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ModelSetupUiState> = _uiState.asStateFlow()
    private var downloadJob: Job? = null

    fun download() {
        if (_uiState.value.status == ModelSetupStatus.Downloading) return
        if (files.isReady()) {
            _uiState.update { it.copy(status = ModelSetupStatus.Ready, downloadedBytes = QwenModel.SIZE_BYTES) }
            return
        }
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(status = ModelSetupStatus.Downloading, downloadedBytes = 0L)
            }
            try {
                downloader.download(
                    url = QwenModel.DOWNLOAD_URL,
                    destination = files.modelFile(),
                    partialFile = files.partialFile(),
                    expectedBytes = QwenModel.SIZE_BYTES,
                    onProgress = { bytesRead ->
                        _uiState.update { state -> state.copy(downloadedBytes = bytesRead) }
                    },
                )
                _uiState.update {
                    it.copy(status = ModelSetupStatus.Ready, downloadedBytes = QwenModel.SIZE_BYTES)
                }
            } catch (error: CancellationException) {
                files.deletePartial()
                _uiState.update {
                    it.copy(status = ModelSetupStatus.NotDownloaded, downloadedBytes = 0L)
                }
                throw error
            } catch (error: DownloadException) {
                _uiState.update {
                    it.copy(status = ModelSetupStatus.Failed(messageFor(error)), downloadedBytes = 0L)
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(
                        status = ModelSetupStatus.Failed(text(R.string.download_error_network)),
                        downloadedBytes = 0L,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deleteModel() {
        if (_uiState.value.status == ModelSetupStatus.Downloading) return
        files.deleteAll()
        _uiState.update {
            it.copy(status = ModelSetupStatus.NotDownloaded, downloadedBytes = 0L)
        }
    }

    private fun initialState(): ModelSetupUiState {
        val ready = files.isReady()
        return ModelSetupUiState(
            sizeBytes = QwenModel.SIZE_BYTES,
            downloadedBytes = if (ready) QwenModel.SIZE_BYTES else 0L,
            status = if (ready) ModelSetupStatus.Ready else ModelSetupStatus.NotDownloaded,
        )
    }

    private fun messageFor(error: DownloadException): String {
        return when (error) {
            is DownloadException.AccessDenied -> text(R.string.download_error_access)
            is DownloadException.HttpFailure -> text(R.string.download_error_http, error.code)
            is DownloadException.Incomplete -> text(R.string.download_error_incomplete)
        }
    }

    private fun text(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ModelSetupViewModel(
                        application = application,
                        files = ModelFiles.create(application),
                        downloader = OkHttpModelDownloader(),
                    )
                }
            }
        }
    }
}
