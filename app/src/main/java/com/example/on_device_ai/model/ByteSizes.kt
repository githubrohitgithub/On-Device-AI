package com.example.on_device_ai.model

import java.util.Locale

internal object ByteSizes {
    private const val BYTES_PER_MB = 1024.0 * 1024.0

    fun formatMegabytes(bytes: Long): String {
        return String.format(Locale.getDefault(), "%.1f", bytes / BYTES_PER_MB)
    }
}
