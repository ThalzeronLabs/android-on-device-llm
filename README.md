# On-device LLM — a private AI chat that runs entirely on your Android phone

**No server. No API key. No internet after the first download.** The model runs on the phone's CPU through
[llama.cpp](https://github.com/ggml-org/llama.cpp), wrapped by my Kotlin library
[llama-kotlin](https://github.com/ThalzeronLabs/llama-kotlin).

<!-- Screenshot / GIF goes here -->

## What it shows

- **On-device inference:** a 0.5B-parameter chat model (Qwen2.5, 4-bit quantized, ~490 MB) answering in real time.
- **Streaming UI in Jetpack Compose:** tokens appear as they are generated; **Stop** cancels mid-answer and keeps
  what was written; a live **tokens/s** chip shows the speed.
- **Clean architecture:** a `ViewModel` exposing one immutable `StateFlow<ChatUiState>`, with the model engine and the
  download source behind interfaces — so the whole screen logic is unit-tested with fakes, no device needed.
- **Robust model handling:** download with progress to a `.part` file (a cancelled download never looks like a valid
  model), or import your own `.gguf` (checked by its magic bytes).

## Architecture

```
ChatScreen (Compose)  ◀── StateFlow<ChatUiState> ──  ChatViewModel
                                                        │
                         ┌──────────────────────────────┴───────────────┐
                         ▼                                              ▼
                ChatEngine (interface)                         ModelSource (interface)
                LlamaChatEngine ── llama-kotlin ── llama.cpp   HttpModelSource (download / import)
```

## Build

```bash
git clone --recursive https://github.com/ThalzeronLabs/android-on-device-llm
cd android-on-device-llm
./gradlew :app:assembleRelease
```

Requirements: Android Studio with NDK 28 and CMake. The app runs on Android 8.0+; for usable speed, a phone with
6 GB+ of RAM.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

## License

Apache-2.0 © Esdras Souza (Thalzeron Labs). The model is downloaded from the official
[Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) repository under its own license.
