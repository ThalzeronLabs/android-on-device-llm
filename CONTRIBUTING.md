# Contributing

Thanks for taking the time! This is a small, readable codebase on purpose: one screen, one ViewModel, two
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
| `app/src/main/kotlin/.../ChatViewModel.kt` | All screen state (`ChatUiState`) and logic. |
| `app/src/main/kotlin/.../ui/` | Compose UI and the Thalzeron theme (`#FFD600` on `#121212`). |
| `app/src/main/kotlin/.../data/ChatEngine.kt` | The model engine, behind an interface; the real one uses llama-kotlin. |
| `app/src/main/kotlin/.../data/ModelDownloader.kt` | Resumable, verified download of a pinned model file. |
| `app/src/main/kotlin/.../data/ModelSource.kt` | Where the model lives; download or import. |
| `third_party/llama-kotlin` | The inference library (git submodule). Changes to it go to [its own repo](https://github.com/ThalzeronLabs/llama-kotlin). |

## Pull requests

1. Branch from `main`, keep the change focused on one thing.
2. Add or update unit tests for logic changes. The ViewModel and the downloader are tested with fakes on the JVM, so
   new logic should be too.
3. Run `./gradlew :app:testDebugUnitTest` before pushing. CI runs the tests and builds the release APK.
4. For UI changes, add a screenshot or a short recording to the PR.
5. Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).

Code style: the official Kotlin style (`kotlin.code.style=official`), English everywhere (code, comments, commits).

## Privacy rule

The app's promise is that **nothing the user types leaves the phone**. Please don't add analytics, crash reporters,
remote config or any network call besides downloading a model file the user asked for.

## Releases (maintainers)

Bump `versionName`/`versionCode` in `app/build.gradle.kts`, move the `Unreleased` notes into a new version in
`CHANGELOG.md`, merge, then push a tag `vX.Y.Z` (or run the **Release** workflow by hand on `main` with that tag).
The [release workflow](.github/workflows/release.yml) builds the APK and publishes the GitHub release.

By contributing you agree that your contribution is licensed under the [Apache-2.0 license](LICENSE).
