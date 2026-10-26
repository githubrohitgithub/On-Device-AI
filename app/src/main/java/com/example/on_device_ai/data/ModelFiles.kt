package com.example.on_device_ai.data

import android.content.Context
import com.example.on_device_ai.model.QwenModel
import java.io.File

class ModelFiles(
    private val directory: File,
    private val fileName: String,
    private val expectedBytes: Long,
) {
    init {
        directory.mkdirs()
    }

    fun modelFile(): File = File(directory, fileName)

    fun partialFile(): File = File(directory, fileName + PARTIAL_SUFFIX)

    fun isReady(): Boolean {
        val file = modelFile()
        return file.isFile && file.length() == expectedBytes
    }

    fun deleteAll() {
        modelFile().delete()
        partialFile().delete()
    }

    fun deletePartial() {
        partialFile().delete()
    }

    companion object {
        private const val DIRECTORY_NAME = "models"
        const val PARTIAL_SUFFIX = ".part"

        fun create(context: Context): ModelFiles {
            return ModelFiles(
                directory = File(context.filesDir, DIRECTORY_NAME),
                fileName = QwenModel.FILE_NAME,
                expectedBytes = QwenModel.SIZE_BYTES,
            )
        }
    }
}
