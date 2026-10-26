package com.example.on_device_ai.data

import java.io.File
import java.io.IOException

interface ModelDownloader {
    suspend fun download(
        url: String,
        destination: File,
        partialFile: File,
        expectedBytes: Long,
        onProgress: (bytesRead: Long) -> Unit,
    )
}

sealed class DownloadException : IOException() {
    class AccessDenied : DownloadException()
    class HttpFailure(val code: Int) : DownloadException()
    class Incomplete : DownloadException()
}
