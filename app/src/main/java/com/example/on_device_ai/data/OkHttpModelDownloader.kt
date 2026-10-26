package com.example.on_device_ai.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

class OkHttpModelDownloader(
    private val client: OkHttpClient = defaultClient(),
) : ModelDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        partialFile: File,
        expectedBytes: Long,
        onProgress: (bytesRead: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            partialFile.delete()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header(HEADER_USER_AGENT, USER_AGENT)
                    .header(HEADER_ACCEPT_ENCODING, ACCEPT_IDENTITY)
                    .build()
                client.newCall(request).execute().use { response ->
                    ensureSuccess(response)
                    val body = response.body ?: throw DownloadException.Incomplete()
                    writeBody(body, partialFile, onProgress)
                }
                if (partialFile.length() != expectedBytes) throw DownloadException.Incomplete()
                if (destination.exists() && !destination.delete()) {
                    throw DownloadException.Incomplete()
                }
                if (!partialFile.renameTo(destination)) throw DownloadException.Incomplete()
            } catch (error: Throwable) {
                partialFile.delete()
                throw error
            }
        }
    }

    private suspend fun writeBody(
        body: ResponseBody,
        partialFile: File,
        onProgress: (bytesRead: Long) -> Unit,
    ) {
        val job = coroutineContext.job
        body.byteStream().use { input ->
            partialFile.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var readTotal = 0L
                var lastReported = 0L
                while (true) {
                    job.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    readTotal += count
                    if (readTotal - lastReported >= PROGRESS_STEP_BYTES) {
                        lastReported = readTotal
                        onProgress(readTotal)
                    }
                }
                output.flush()
                onProgress(readTotal)
            }
        }
    }

    private fun ensureSuccess(response: Response) {
        when (response.code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> throw DownloadException.AccessDenied()
            in HTTP_SUCCESS_START..HTTP_SUCCESS_END -> Unit
            else -> throw DownloadException.HttpFailure(response.code)
        }
    }

    companion object {
        private const val USER_AGENT = "OnDeviceAI"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        private const val ACCEPT_IDENTITY = "identity"
        private const val BUFFER_SIZE = 8 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_SUCCESS_START = 200
        private const val HTTP_SUCCESS_END = 299

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .build()
        }
    }
}
