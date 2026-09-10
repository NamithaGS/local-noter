# Developer Guide

Building, testing, and understanding the internals of Local Noter. For what the app does as a user, see [README.md](README.md).

---

## 🚀 Quick Start

```bash
git clone https://github.com/NamithaGS/local-noter.git
cd local-noter
```

Open in Android Studio (**File → Open**), let Gradle sync, then:

```bash
scripts/fetch-vosk-model.sh    # ~41MB, not committed to git - required before transcription works
```

Connect a device (USB debugging on) or start an emulator, then **Run** in Android Studio, or:

```bash
./gradlew installDebug
```

---

## 🔧 Tech Stack

**Architecture:** MVVM, Repository pattern, Room for persistence

**Core:**
- Kotlin 2.3.0, Jetpack Compose (Material 3), Coroutines & Flow
- Room 2.8.4 (database currently at schema version 4 - `fallbackToDestructiveMigration()`, no real `Migration`s written yet; a schema bump wipes local data, which is an accepted pre-release trade-off)
- WorkManager (background transcription, daily Drive backup job)
- compileSdk/targetSdk 35, minSdk 34, JDK 17

**On-device AI (no external LLM calls anywhere in this stack):**
- [Vosk](https://alphacephei.com/vosk/) `0.3.75` - offline speech-to-text
- Summarization is **pluggable** between two backends (`SummarizationConfig.ACTIVE_BACKEND` is the single switch point):
  - **`GEMINI_NANO`** - ML Kit GenAI Summarization (`genai-summarization:1.0.0-beta1`) via AICore. Requires Pixel 8+/Galaxy S24+-class hardware. As of writing this backend reliably rejects every summarization attempt with a "policy check failure" even on supported hardware - looks like a beta-API/AICore version-skew issue, not something fixable from the app. Not currently active.
  - **`LITERT_LM`** (currently active) - runs Gemma3-1B-IT directly via Google's [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) API (`com.google.ai.edge.litertlm:litertlm-android:0.17.0`), bypassing AICore's safety-classifier layer entirely. The model (~560MB, `.litertlm` format) is downloaded at runtime from Hugging Face's gated `litert-community/Gemma3-1B-IT` repo - the end user supplies their own HF access token via the in-app "Setup Hugging Face" flow, since accepting Gemma's license isn't something the app can do on anyone's behalf.
  - Topic classification for filing summaries (formerly a separate on-device AI classification step, `WorkClassifier`) has been removed - a note's topic is now purely its user-set tag (`Note.manualTag`), routed straight into `SummarizedNotes/<tag>`.

**Optional Google Drive backup:**
- `play-services-auth` (OAuth), `google-api-services-drive` + `google-api-services-docs` (REST clients)
- Scoped to `drive.file` + Docs - the app can only see/write files and folders it created itself, never anything else in the user's Drive
- Structure: `LocalNoter/AllNotes/<year>/<month>/<date>` (every note, full transcript + summary if present) and `LocalNoter/SummarizedNotes/<tag>` (just the AI summary, grouped by the user's tag, one dated entry per note with the timestamp as a Docs `HEADING_4`)

**Testing:**
- JUnit 4, Mockito + mockito-kotlin, Robolectric, Compose UI Test, AndroidX Test
- 63 unit tests (`app/src/test`), all passing; UI/integration tests under `app/src/androidTest` need a device/emulator

---

## 📁 Project Structure

```
local-noter/
├── app/src/main/java/com/noter/
│   ├── data/
│   │   ├── db/                    # Room: AppDatabase, NoteEntity, NoteDao
│   │   ├── model/                 # Note (domain model)
│   │   └── repository/            # NoteRepository
│   ├── domain/
│   │   ├── RecordingManager.kt
│   │   ├── TranscriptionWorker.kt
│   │   ├── transcription/         # Vosk + PCM audio decoding
│   │   ├── summarization/         # Pluggable summarization (see Tech Stack above)
│   │   │   ├── NoteSummarizer.kt              # facade every caller uses
│   │   │   ├── SummarizationConfig.kt         # the GEMINI_NANO / LITERT_LM switch
│   │   │   ├── SummarizationEngine.kt         # interface both backends implement
│   │   │   ├── SummarizationResult.kt         # Success / Skipped / Failed / NeedsSetup
│   │   │   ├── GeminiNanoSummarizationEngine.kt
│   │   │   └── litertlm/
│   │   │       ├── LiteRtLmSummarizationEngine.kt
│   │   │       ├── GemmaModelDownloader.kt    # downloads the gated HF model file
│   │   │       └── HuggingFaceTokenStore.kt
│   │   └── backup/                # Google Drive/Docs backup pipeline
│   │       ├── DriveAuth.kt               # Google Sign-In, OAuth scopes
│   │       ├── DriveService.kt            # Drive/Docs REST calls
│   │       ├── NoteFiler.kt                # archiveNotes() (backup) + summarizeNotes()
│   │       ├── NoteSectionFormatter.kt     # doc-entry text formatting
│   │       ├── DriveBackupScheduler.kt    # WorkManager one-time-request self-rescheduling
│   │       ├── DriveBackupWorker.kt       # the daily two-pass job
│   │       └── BackupStatusStore.kt       # "last backed up" persistence
│   ├── ui/
│   │   ├── screens/                # NoteListScreen, NoteDetailScreen, setup dialogs
│   │   ├── viewmodels/
│   │   ├── theme/
│   │   └── navigation/
│   ├── util/
│   └── MainActivity.kt
├── app/src/test/                  # Unit tests (JVM, Robolectric where needed)
├── app/src/androidTest/           # Compose UI tests, Room integration test
├── app/debug.keystore             # Committed on purpose - see "Debug signing" below
└── .github/workflows/release.yml  # CI: build + attach APK on tag push
```

---

## ☁️ Setting up Google Drive backup (for your own build/account)

Backup writes real Google Docs, which needs a Google Cloud project configured for this exact app build. The in-app "Setup Google Drive" dialog (⋮ menu on the note list) walks through this with the exact values needed, but in short, at [console.cloud.google.com](https://console.cloud.google.com):

1. Select or create a project.
2. **APIs & Services → Library**: enable "Google Drive API" and "Google Docs API".
3. **APIs & Services → Credentials**: create an OAuth Client ID, type **Android**, package name `com.noter`, and the SHA-1 of the signing key you're building with (see "Debug signing" below for why this doesn't change between builds/machines here).
4. **APIs & Services → OAuth consent screen**: add your Google account as a test user if the app is in Testing mode.

## 🤗 Setting up on-device summarization (LiteRT-LM)

1. Accept the Gemma license at [huggingface.co/litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) (requires a free Hugging Face account).
2. Generate an access token at [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens).
3. In the app: ⋮ menu → Setup Hugging Face → paste the token. This downloads the ~560MB model once; it's cached under the app's internal storage after that.

---

## 🔏 Debug signing

`app/debug.keystore` is committed intentionally, with `signingConfigs.debug` in `app/build.gradle.kts` pointed at it explicitly (password `android`, alias `androiddebugkey` - the same defaults AGP's own implicit debug keystore uses). Without this, AGP falls back to an auto-generated keystore at `~/.android/debug.keystore` the first time it's needed on any given machine - fine locally, but on GitHub Actions' ephemeral runners that meant a **new random signing key on every CI run**, which broke Google Sign-In every time (the OAuth client above is registered against one fixed SHA-1). Pinning a real keystore file keeps the SHA-1 identical across every machine and every build. It's a debug-only key with a public, well-known password - not a secret.

---

## 🚀 Automated Builds (GitHub Actions)

Tag-triggered release build - `.github/workflows/release.yml`:

```bash
git tag v5
git push origin v5
```

This fetches the Vosk model, runs `./gradlew assembleDebug`, and attaches the resulting `app-debug.apk` to a GitHub Release for that tag via `softprops/action-gh-release`. Can also be triggered manually from the **Actions** tab (`workflow_dispatch`).

There's no `assembleRelease` signing config - only debug builds are produced by CI.

---

## 🧪 Testing

```bash
./gradlew testDebugUnitTest              # Unit tests (JVM) - 63 tests, all passing
./gradlew connectedAndroidTest           # UI/integration tests - needs a device/emulator
./gradlew compileDebugAndroidTestKotlin  # Just compile-check the androidTest sources
```

Notes for anyone extending the test suite:
- Use `org.mockito.kotlin.any()` (not plain `org.mockito.Mockito.any()`) whenever you're matching a Kotlin non-null reference-type parameter - plain Mockito's `any()` returns `null`, which trips Kotlin's runtime null-check on the parameter before the stub is even reached. `anyString()`/`anyInt()`/etc. are fine as-is for primitives.
- A `StateFlow` built with `.stateIn(..., SharingStarted.WhileSubscribed(...), initialValue = X)` always has *some* current value the moment anything subscribes - a plain `.first()` in a test can return the seeded `initialValue` before the upstream flow has a chance to emit. Use `.first { predicate }` to wait for the value you actually want.
- To assert an intermediate state a `ViewModel` sets partway through a coroutine (e.g. `isLoading = true` before an awaited call resolves), you need both `UnconfinedTestDispatcher` (so the coroutine runs eagerly instead of sitting queued) *and* a mock that genuinely suspends (e.g. `doSuspendableAnswer { delay(1); ... }` from mockito-kotlin) - a fully synchronous mock gives the coroutine no real point to pause at, so the whole body runs as one indivisible step regardless of dispatcher.

---

## 🛠️ Development Setup

### Prerequisites
- Android Studio (recent stable) or a standalone Android SDK + JDK 17
- Android SDK Platform 35

### First-time setup
```bash
scripts/fetch-vosk-model.sh   # required - transcription throws ModelNotInstalledException without it
```

### Building
```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # build + install to a connected device/emulator
```

---

## 🐛 Troubleshooting (build/dev)

**"SDK location not found"**
→ Set `ANDROID_HOME`, or configure via Android Studio's Project Structure → SDK Location.

**Robolectric tests fail with `Unsupported class file major version ...`**
→ Your default `java` is newer than Robolectric's bundled ASM supports. Run Gradle with `JAVA_HOME` pointed at a JDK 17 install explicitly, e.g. `JAVA_HOME=/path/to/jdk17 ./gradlew test`.

**Transcripts come back empty / `ModelNotInstalledException` in logcat**
→ Run `scripts/fetch-vosk-model.sh` and rebuild - the model isn't in version control.

**Google Sign-In fails with "could not connect" after a fresh CI build**
→ Confirm `app/debug.keystore` is actually being used (`signingConfigs.debug.storeFile` in `app/build.gradle.kts`) and that its SHA-1 matches what's registered in Google Cloud Console. Verify with:
```bash
keytool -list -v -keystore app/debug.keystore -storepass android -alias androiddebugkey
```

**A Drive/Docs API call fails with a bare "403 Forbidden"**
→ The raw exception message from `GoogleJsonResponseException` is the whole HTTP body; `NoteListViewModel.describeError()` unpacks `.details.message` for the actual reason (commonly: Drive API or Docs API not enabled on the Cloud project - see the setup steps above).

---

## 🎯 Roadmap

- [ ] Real Room `Migration`s (currently destructive on every schema bump)
- [ ] Note search and filtering
- [ ] Dark theme
- [ ] Flip `SummarizationConfig.ACTIVE_BACKEND` back to `GEMINI_NANO` once Google fixes the policy-check-failure bug

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run `./gradlew testDebugUnitTest` (and `connectedAndroidTest` if you touched UI)
4. Commit and push
5. Open a Pull Request

**Code standards:**
- Follow Kotlin coding conventions
- Write tests for new logic where it's feasible to (see the Testing section for the gotchas that make some of this codebase hard to test naively)
- Keep functions small and focused; avoid introducing abstractions the current code doesn't need yet
