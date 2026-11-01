# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-11-01

First public release.

### Added

- Offline AI chat in Jetpack Compose on top of [llama-kotlin](https://github.com/ThalzeronLabs/llama-kotlin)
  (llama.cpp): streamed answers, **Stop** that keeps the partial answer, a live tokens/s chip and a model info sheet.
- In-app model download of Qwen2.5-0.5B-Instruct `Q4_K_M` (491 MB) from Hugging Face:
  - starts by itself on Wi-Fi, asks first on mobile data;
  - progress, **pause and resume** with HTTP `Range` (survives a dropped connection or a killed app);
  - automatic retries with exponential backoff on network errors and 5xx;
  - free-space check before starting;
  - **SHA-256 verification** (`74a4da8c…`) before the file is installed; a corrupted file is deleted.
- Import of any `.gguf` model from the phone's storage, shown under its own file name.
- Release workflow: pushing a `v*` tag builds the `arm64-v8a` APK, signs it with a throwaway demo key generated in
  the workflow and attaches it, with its SHA-256, to a GitHub release.
- Unit tests for the downloader (fake server and a real local HTTP socket), the model source and the ViewModel.

[Unreleased]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ThalzeronLabs/android-on-device-llm/releases/tag/v0.1.0
