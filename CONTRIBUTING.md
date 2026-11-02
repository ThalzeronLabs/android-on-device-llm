# Contributing

Thanks for taking the time! This is a small, readable codebase on purpose: one screen, one ViewModel, a few
interfaces. Contributions of any size are welcome, from a typo to a new feature.

## Where to start

- Issues labelled [`good first issue`](https://github.com/ThalzeronLabs/android-on-device-llm/labels/good%20first%20issue)
  are scoped to a few files and say where to look.
- Found a bug? Open an issue with the **Bug report** template: phone model, Android version and the model you used
  help a lot.
- Bigger idea? Open a **Feature request** first so we can agree on the approach before you write code.

## Set up

```bash
git clone --recursive https://github.com/ThalzeronLabs/android-on-device-llm
cd android-on-device-llm
./gradlew :app:testDebugUnitTest      # fast, no device needed
./gradlew :app:installDebug           # on a phone or an x86_64 emulator
```

You need JDK 17 and the Android SDK with NDK `28.2.13676358` and CMake `4.1.2` (Android Studio installs them on
first sync). Forgot `--recursive`? Run `git submodule update --init --recursive`.

## How the code is organised

| Path | What it does |
|---|---|
| `app/src/main/kotlin/.../ChatViewModel.kt` | Chat, history, settings and battery state (`ChatUiState`) and logic. |
| `app/src/main/kotlin/.../chat/` | Plain Kotlin: settings, titles, context-window trimming, Markdown export. |
| `app/src/main/kotlin/.../ui/` | Compose UI and the Thalzeron theme (`#FFD600` on `#121212`). |
| `app/src/main/kotlin/.../data/ChatEngine.kt` | The model engine, behind an interface; the real one uses llama-kotlin. |
| `app/src/main/kotlin/.../data/ModelCatalog.kt` | The downloadable models, pinned by size and SHA-256. |
| `app/src/main/kotlin/.../data/ModelManager.kt` | Downloads, deletes, imports and switches models (app-wide). |
| `app/src/main/kotlin/.../data/ModelStore.kt` | Where the model files live. |
| `app/src/main/kotlin/.../data/ModelDownloader.kt` | Resumable, verified download of a pinned model file. |
| `app/src/main/kotlin/.../data/ConversationRepository.kt`, `data/db/` | Saved conversations (Room). Schema in `app/schemas/`. |
| `app/src/main/kotlin/.../battery/` | Battery level and Battery Saver as a `Flow`. |
| `third_party/llama-kotlin` | The inference library (git submodule). Changes to it go to [its own repo](https://github.com/ThalzeronLabs/llama-kotlin). |

Adding a model to the catalog? Use the size and SHA-256 that Hugging Face publishes for the file (the LFS object id,
also sent as `X-Linked-ETag` by its `resolve` URL), and only models whose license allows redistribution-free download
without a login.

Updating llama-kotlin? Check out the new tag in `third_party/llama-kotlin`, set `VERSION_NAME` in
`gradle.properties` to the same version (the module reads it from this build), and add any plugin its build uses to
`gradle/libs.versions.toml`.

Changing the database? Bump the version in `ChatDatabase`, add a migration and commit the new schema JSON.

## Pull requests

1. Branch from `main`, keep the change focused on one thing.
2. Add or update unit tests for logic changes. Everything is tested on the JVM (fakes, Turbine, and Robolectric for
   Room and Compose UI), so new logic should be too.
3. Run `./gradlew :app:testDebugUnitTest` before pushing. CI runs the tests and builds the release APK.
4. For UI changes, run `./gradlew :app:recordRoborazziDebug`, commit the updated `docs/screenshots/` and mention
   them in the PR (CI fails when the screens no longer match).
5. Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).

Code style: the official Kotlin style (`kotlin.code.style=official`), English everywhere (code, comments, commits).

## Privacy rule

The app's promise is that **nothing the user types leaves the phone**. Please don't add analytics, crash reporters,
remote config or any network call besides downloading a model file the user asked for.

## Releases (maintainers)

Bump `versionName`/`versionCode` in `app/build.gradle.kts`, move the `Unreleased` notes into a new version in
`CHANGELOG.md`, merge, then push a tag `vX.Y.Z` (or run the **Release** workflow by hand on `main` with that tag).
The [release workflow](.github/workflows/release.yml) builds the APK, signs it with the demo key from the Actions
secrets (`DEMO_KEYSTORE_BASE64`, `DEMO_KEYSTORE_PASSWORD`, `DEMO_KEY_ALIAS`, `DEMO_KEY_PASSWORD`), checks its
certificate and publishes the GitHub release.

By contributing you agree that your contribution is licensed under the [Apache-2.0 license](LICENSE).
