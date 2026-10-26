package com.example.on_device_ai.model

/**
 * Qwen2.5 0.5B Instruct, 8-bit, packaged for MediaPipe LLM Inference.
 * [MAX_TOKENS] matches the kv-cache size baked into the task file (ekv1280).
 */
object QwenModel {
    const val FILE_NAME = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/$FILE_NAME"
    const val SIZE_BYTES = 546_660_344L
    const val MAX_TOKENS = 1280
    const val TOP_K = 40
    const val TOP_P = 0.8f
    const val TEMPERATURE = 0.7f
    const val USER_PREFIX = "<|im_start|>user\n"
    const val USER_SUFFIX = "<|im_end|>\n"
    const val MODEL_PREFIX = "<|im_start|>assistant\n"
    const val MODEL_SUFFIX = "<|im_end|>\n"
    const val SYSTEM_PREFIX = "<|im_start|>system\n"
    const val SYSTEM_SUFFIX = "<|im_end|>\n"
}
