# On-device AI — technical document

On-device chat for Android. The app downloads one language model, then runs it locally with MediaPipe. Prompts and replies stay on the phone after the download.

Package: `com.example.on_device_ai`  
Min SDK 24, target and compile SDK 37, Java 11.

## Model

Only **Qwen2.5 0.5B Instruct**, 8-bit, MediaPipe `.task` bundle.

| | |
|---|---|
| File | `Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` |
| Size | 546,660,344 bytes (521.5 MB) |
| Source | [litert-community/Qwen2.5-0.5B-Instruct](https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct) |
| Context | 1280 tokens (`ekv1280` baked into the file) |
| Backend | CPU |
| Decode | temperature 0.7, top-K 40, top-P 0.8 |

`maxTokens` must stay 1280. That value is the kv-cache size inside the task file. Settings live in `QwenModel`.

Chat tokens are the Qwen Instruct template:

```text
<|im_start|>user
…<|im_end|>
<|im_start|>assistant
…<|im_end|>
```

The session applies those prefixes and suffixes. The UI sends the raw user text.

No Hugging Face token. The file is public. The download URL is the Hugging Face `resolve/main` link for that filename.

## User flow

1. **Setup** (`setup`) is the start destination. It shows the model name and size.
2. **Download** writes the file into app storage and shows progress. The user can cancel or retry.
3. **Start chat** is available only when the file length equals `QwenModel.SIZE_BYTES`.
4. **Chat** (`chat`) loads the model into memory, then streams a reply.
5. **Back** pops the chat screen and releases the native engine.
6. **Delete model** removes the file after a confirm dialog. The next chat requires a new download.

A finished download survives process death. Opening the app again shows **Downloaded on this phone**.

## Runtime requirements

- A **64-bit ARM phone**. MediaPipe ships `libllm_inference_engine_jni`, and this model does not run on an emulator. An `UnsatisfiedLinkError` is shown as an unsupported-device message.
- Network only for the download (`INTERNET`). Chat itself does not call a server.
- `android:largeHeap="true"` because loading the model needs a large native heap.
- About 521.5 MB of free storage, plus RAM for the kv-cache while the chat screen is open.

## Architecture

MVVM. Screens receive state and callbacks. They do not take a `ViewModel`.

```text
MainActivity
  OnDeviceApp (NavHost)
    ModelSetupRoute  -> ModelSetupViewModel -> ModelFiles + ModelDownloader
    ChatRoute         -> ChatViewModel       -> LlmChatEngine
```

| Layer | Types | Role |
|---|---|---|
| UI | `ModelSetupScreen`, `ChatScreen` | Render state, send callbacks |
| Presentation | `ModelSetupViewModel`, `ChatViewModel` | Download and chat state (`StateFlow`) |
| Storage | `ModelFiles` | Path, size check, delete |
| Download | `ModelDownloader`, `OkHttpModelDownloader` | HTTP download |
| Inference | `LlmChatEngine`, `MediaPipeLlmChatEngine` | Load, generate, cancel, reset, close |
| Config | `QwenModel` | The only model: URL, size, decode, prompt tokens |

ViewModel factories are the composition root. `ModelSetupViewModel` is constructed with `OkHttpModelDownloader`. `ChatViewModel` is constructed with `MediaPipeLlmChatEngine`. The UI depends on the interfaces, not on OkHttp or MediaPipe.

Navigation Compose owns the back stack:

- `setup` keeps the setup `ViewModel` for the activity, so a download continues across rotation.
- `chat` scopes `ChatViewModel` to that destination. Leaving the screen calls `onCleared()`, which closes the native engine on a single background thread named `llm-inference`.

## Download

`OkHttpModelDownloader` saves to a `.part` file beside the destination, then renames it only when `partialFile.length() == expectedBytes`.

- Progress is reported about every 256 KB.
- `Accept-Encoding: identity` so the byte count matches the file on disk.
- Connect timeout 30 seconds, read timeout 120 seconds, no overall call timeout.
- Cancel deletes the partial file and returns the card to **Not downloaded**.
- HTTP 401 and 403 become `DownloadException.AccessDenied`. Any other non-success status becomes `HttpFailure`. A short or corrupt body becomes `Incomplete`.

Ready means `filesDir/models/<file name>` exists and its length is exactly 546,660,344 bytes. A smaller file is treated as missing.

## Inference

`MediaPipeLlmChatEngine` uses `com.google.mediapipe:tasks-genai:0.10.27`.

1. `load()` rejects a missing or wrong-sized file with `ModelNotReadyException`.
2. It builds `LlmInference` with the model path, `maxTokens = 1280`, `maxTopK = 40`, and `Backend.CPU`.
3. It opens an `LlmInferenceSession` with the Qwen decode settings and `PromptTemplates`.
4. `generate()` calls `addQueryChunk(prompt)`, then `generateResponseAsync`.
5. MediaPipe invokes the listener with **new text only**, not the full reply. `ChatViewModel` appends each chunk onto the assistant message. `done == true` returns the phase to ready.
6. **Stop** sets a cancel flag and calls `cancelGenerateResponseAsync()`.
7. **New chat** closes the session and opens a new one, which clears the 1280-token history.
8. `close()` cancels, closes the session, then closes `LlmInference`. It is safe to call twice.

Load and generate run on the `llm-inference` thread so prefill does not block the main thread. Listener updates go into `StateFlow`, which is safe to write from that callback thread.

The MediaPipe LLM API is deprecated upstream in favor of LiteRT-LM. This app stays on `tasks-genai:0.10.27` on purpose. `MediaPipeLlmChatEngine` suppresses that deprecation.

## Screens and state

**Setup** (`ModelSetupUiState`)

- `NotDownloaded` — Download
- `Downloading` — bytes read, progress bar, Cancel
- `Ready` — Start chat, Delete model
- `Failed` — message and Try again

**Chat** (`ChatUiState`)

- `Loading` — model is being mapped into memory
- `Ready` — send is enabled when the draft is not blank
- `Generating` — Stop replaces Send; an empty assistant bubble shows “Writing…”
- `Failed` — load error and Try again

Send errors keep the transcript and show `errorMessage` above the input. They do not replace a successful load with the failed phase.

## Source map

```text
app/src/main/java/com/example/on_device_ai/
  MainActivity.kt
  model/QwenModel.kt
  model/ByteSizes.kt
  data/ModelFiles.kt
  data/ModelDownloader.kt
  data/OkHttpModelDownloader.kt
  inference/LlmChatEngine.kt
  inference/MediaPipeLlmChatEngine.kt
  ui/OnDeviceApp.kt
  ui/setup/ModelSetupScreen.kt
  ui/setup/ModelSetupViewModel.kt
  ui/setup/ModelSetupUiState.kt
  ui/chat/ChatScreen.kt
  ui/chat/ChatViewModel.kt
  ui/chat/ChatUiState.kt
```

User-visible copy is in `app/src/main/res/values/strings.xml`.

Dependency versions are written in `app/build.gradle.kts` and the root `build.gradle.kts`. This project does not use `gradle/libs.versions.toml`.

## Build and run

```bash
./gradlew :app:assembleDebug
```

Install the debug APK on a physical arm64 device, open the app, download the model, then start chat. The first load after download can take several seconds. Later visits still load the model when the chat screen opens, and unload it when the user leaves chat.

## Limits

- One model. There is no model picker.
- Context is 1280 tokens for the whole session, including the prompt template. **New chat** is how the user clears that history.
- CPU only. There is no GPU switch.
- The model file is stored in app-private storage, not shared storage.
- A download that dies mid-write is discarded. The next attempt starts over. There is no byte-range resume.
