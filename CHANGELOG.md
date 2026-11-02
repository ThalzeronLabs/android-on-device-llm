# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- **Answers are several times faster.** Tested on a POCO X6 Pro (Dimensity 8300) with Qwen2.5 0.5B: 8.7 tok/s in
  v0.2.3. Android ran the model's threads on the phone's slow cores; the updated llama-kotlin keeps them on a
  persistent thread pool pinned to the fast cores (23.1 tok/s from that alone), and the app now draws the answer
  once per frame instead of once per token, so the screen no longer competes with the model for the CPU.
- **No more crash on phones without dot-product instructions** (Cortex-A53/A73 phones, and x86_64 emulators running
  the arm64 build): the app closed on the first answer with SIGILL. The right CPU code is now picked at start-up.
- The speed badge showed the previous answer's speed while a new one was being written; it now shows the live speed.
- The battery banner said answers use "every CPU core"; they use the fastest cores.

### Changed

- The orb only moves while waiting for an answer to start, and never with "Remove animations" on. An idle screen
  draws nothing, which also saves battery and lets UI test tools see the screen settle.
- New `staging` build type: a release build that installs next to the published app, for measuring on a phone.

### Known issue

- Updating from v0.1.0 needs an uninstall first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): v0.1.0 was signed with a
  one-off key. From v0.2.1 on, every release is signed with the same demo key and updates in place.

## [0.2.3] - 2026-11-02

### Changed

- Updated [llama-kotlin](https://github.com/ThalzeronLabs/llama-kotlin) to v0.2.2, which builds as a git submodule
  without anything extra from the including project: the `VERSION_NAME` property and the Dokka plugin added for
  v0.2.2 are gone from this build. No change in the app itself.

## [0.2.2] - 2026-11-02

### Changed

- Updated [llama-kotlin](https://github.com/ThalzeronLabs/llama-kotlin) to v0.2.1. The temperature of each
  conversation is now passed with every answer instead of opening a new session when it changes, so the model's
  KV cache is kept and only the new part of a conversation is processed before answering.

## [0.2.1] - 2026-11-02

**From v0.2.1 on, updates install over the previous version; v0.2.0 users must uninstall once.**

### Added

- Screenshot tests with Roborazzi: nine screens (chat, model catalog, conversations, chat settings, battery saver)
  in light and dark, rendered on the JVM. CI compares them with `docs/screenshots/`, and the README shows them.

### Changed

- Releases are signed with a fixed Thalzeron Labs demo key kept in the repository's Actions secrets (showcase apps
  only, never Google Play), so new versions update over the previous one. The release workflow checks the
  certificate against the published SHA-256 and falls back to a throwaway key when the secrets are missing.

### Fixed

- Text in the conversation drawer and the bottom sheets was drawn in the secondary (grey) color in both themes,
  because one theme color matched the container color. A unit test now checks the text color on every container.

## [0.2.0] - 2026-11-02

### Added

- **Conversation history** saved on the phone with Room: a drawer lists every chat (most recent first), and each one
  can be continued, renamed or deleted. The first question becomes the title.
- **Model catalog** with four Apache-2.0 models, each with its size, license and official Hugging Face link:
  SmolLM2 360M Instruct (`Q8_0`, 386 MB), Qwen2.5 0.5B Instruct (`Q4_K_M`, 491 MB, still the default),
  SmolLM2 1.7B Instruct (`Q4_K_M`, 1.06 GB) and Qwen2.5 1.5B Instruct (`Q4_K_M`, 1.12 GB). Every file is pinned to
  its published SHA-256. Download several (one at a time, with pause and resume), **switch models without
  restarting the app**, delete the ones you no longer need.
- **Per-conversation settings**: system prompt, temperature (0–1.5, default 0.7) and maximum answer length
  (64–2048 tokens, default 512), saved with each chat, with a reset button.
- **Share a conversation as Markdown** or save it as a `.md` file; **long-press a message to copy it**.
- **Battery saver**: at 20% or less, or with Android's Battery Saver on, a banner warns, offers **Stop** while an
  answer is being written and caps answers at 256 tokens. It can be turned off until the battery recovers.
- Long conversations are trimmed to the newest turns that fit in the 4096-token context window instead of failing.
- Tests: Room repository (Robolectric), ViewModel flows (Turbine), model manager and catalog, Markdown export,
  settings, battery, Compose UI tests for copy, the battery banner, the model picker, settings and history, and a
  WCAG contrast check of both themes.

### Changed

- Light and dark themes: secondary text and error colors adjusted so every text pair meets WCAG AA (4.5:1).
- The model download and the engine now live app-wide, so a download keeps going when the screen is rotated.

### Migration

- A model downloaded (or imported) by v0.1.0 is moved to the new layout on first launch, not downloaded again;
  an unfinished v0.1.0 download resumes where it stopped.

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

[Unreleased]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.2.3...HEAD
[0.2.3]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/ThalzeronLabs/android-on-device-llm/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ThalzeronLabs/android-on-device-llm/releases/tag/v0.1.0
