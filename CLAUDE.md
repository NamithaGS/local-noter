# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Local Noter is an Android voice-notes app: it records audio, transcribes it on-device, and
summarises the transcript on-device. Recording, transcription, and summarisation never
touch a server — that's a design constraint, so prefer offline/on-device solutions over
cloud APIs for that part of the app. The one deliberate exception is the opt-in Google
Drive backup (`domain/backup/`): a user who explicitly connects an account (⋮ menu →
"Setup Google Drive" on the note list) gets their notes archived into their own Drive as
real Google Docs — still nothing sent anywhere without an explicit, revocable opt-in.
Backup writes two structures under a `LocalNoter` root folder the app creates itself
(required by the `drive.file` OAuth scope, which only grants visibility into files/folders
the app created): `AllNotes/<year>/<month>/<date>` (every note, full transcript + summary
if present, chronological) and `SummarizedNotes/<tag>` (just the AI summary, grouped by
the note's user-set tag — see `NoteFiler.archiveNotes`/`summarizeNotes`).

## Technology Stack

- Kotlin 2.3.0, Jetpack Compose (Material 3), Coroutines/Flow
- Room 2.8.4 (persistence, schema version 4, `fallbackToDestructiveMigration()` — no real
  `Migration`s written yet), WorkManager (background transcription + daily backup),
  Navigation Compose
- sherpa-onnx (Zipformer transducer model) — offline speech-to-text. No published
  Maven/JitPack artifact exists for its Android bindings, so the release `.aar` is
  vendored at `app/libs/sherpa-onnx-1.13.7.aar` (verified against the real repo source
  and decompiled classes before committing, not just docs). Replaced Vosk this session.
- Summarization is **pluggable** — see `domain/summarization/SummarizationConfig.ACTIVE_BACKEND`:
  - `GEMINI_NANO`: ML Kit GenAI Summarization `1.0.0-beta1` via AICore (Pixel 8+/Galaxy
    S24+ only). Currently broken — rejects every summarization attempt with a
    "policy check failure" even on supported hardware, an apparent beta-API/AICore
    version-skew issue, not something fixable from this codebase.
  - `LITERT_LM` (currently active): runs Gemma3-1B-IT directly via
    `com.google.ai.edge.litertlm:litertlm-android:0.17.0`, bypassing AICore entirely.
    Needs a one-time ~560MB model download from a gated Hugging Face repo — the user
    supplies their own HF access token via "Setup Hugging Face" in the ⋮ menu.
- Google Drive/Docs backup: `play-services-auth`, `google-api-services-drive`,
  `google-api-services-docs` — see Project Overview above.
- AGP (matches Kotlin plugin), KSP 2.3.0, Gradle 8.9, JDK 17, minSdk 34 / compileSdk 35 /
  targetSdk 35

## Build, Test, Run

```bash
scripts/fetch-sherpa-model.sh    # REQUIRED once per clone; downloads ~125 MB model
./gradlew assembleDebug
./gradlew testDebugUnitTest      # JVM unit tests (Robolectric where needed) - 63 tests, all passing
./gradlew connectedAndroidTest   # instrumented tests, needs a device/emulator
```

Requires JDK 17. If the machine's default `java` is newer (Robolectric's bundled ASM
can't parse newer bytecode — fails with `Unsupported class file major version ...`),
point Gradle at a JDK 17 install explicitly: `JAVA_HOME=/path/to/jdk17 ./gradlew test`.
Also requires `ANDROID_HOME` pointing at an SDK with platform 35.

CI (`.github/workflows/release.yml`) builds and attaches `app-debug.apk` to a GitHub
Release on every `v*` tag push (or manual `workflow_dispatch`). `app/debug.keystore` is
committed on purpose and wired into `signingConfigs.debug` — without it, CI's ephemeral
runners would generate a new random signing key (and SHA-1) on every build, breaking the
registered Google OAuth client each time.

## Architecture

MVVM over a repository. Recording and transcription are decoupled through WorkManager:

1. `RecordingViewModel.stopRecording()` inserts a `Note` with a "Transcribing..."
   placeholder title and enqueues `TranscriptionWorker` (pass keys via
   `TranscriptionWorker.KEY_*`, never string literals).
2. `TranscriptionWorker` → `SherpaOnnxTranscriber` → `PcmAudioDecoder` → writes the
   transcript file → `NoteSummarizer` (facade over whichever `SummarizationEngine` is
   active) → updates the Room row.

Backup is a separate two-pass daily job (`DriveBackupWorker`, self-rescheduling via
`DriveBackupScheduler`), or triggered manually from the note list:
1. `NoteFiler.archiveNotes()` — every not-yet-uploaded note into `AllNotes/...`.
2. `NoteFiler.summarizeNotes()` — notes with both a tag and an existing summary into
   `SummarizedNotes/<tag>`, each entry's date/time as a Docs `HEADING_4`
   (`DriveService.appendToDocWithHeading` — needs an explicit index fetched from the doc
   first, since a follow-up `updateParagraphStyle` request needs a concrete range;
   `EndOfSegmentLocation` alone can't be targeted that way).

Key constraints when touching this path:

- **sherpa-onnx only accepts 16 kHz mono float32 PCM.** `RecordingManager` records AAC at
  16 kHz mono exactly so `PcmAudioDecoder` can decode 1:1, but the decoder still
  downmixes and resamples so older recordings keep working. If you change the recording
  format, check both sides. `AudioSource` is `VOICE_RECOGNITION` (not `MIC` — too quiet
  without AGC on many devices; not `VOICE_COMMUNICATION` — its call-oriented
  noise-suppression/echo-cancellation made things worse without a real call in progress).
- **sherpa-onnx's *offline* recognizer wants the whole utterance in one `acceptWaveform()`
  call**, unlike Vosk's incremental streaming API — `SherpaOnnxTranscriber` accumulates
  `PcmAudioDecoder`'s chunked output into one buffer before feeding it through, rather
  than streaming chunk-by-chunk. It also has no built-in silence/VAD detection, so it can
  occasionally produce a short hallucinated phrase on pure silence or noise (observed on
  the emulator's synthetic mic) — a real device with real speech shouldn't hit this, but
  if it becomes a nuisance the fix is adding sherpa-onnx's own VAD pass before
  recognition, not swapping models again.
- **The sherpa-onnx model lives in `assets/`, not git.** `SherpaOnnxTranscriber` copies it
  to app-private filesystem storage on first use (ONNX Runtime needs real file paths, not
  asset streams) — `scripts/fetch-sherpa-model.sh` downloads it into assets.
- **Summarisation is best-effort regardless of backend.** `NoteSummarizer.summarize()`
  returns a `SummarizationResult` (`Success` / `Skipped` / `Failed` / `NeedsSetup`) and
  never throws; a note must remain valid with `summary == null`. `NeedsSetup` (LiteRT-LM
  model not downloaded) is surfaced via a snackbar pointing at the ⋮ menu, not an
  automatic prompt — downloading ~560MB should never be a side effect of tapping
  Summarize on an arbitrary note.
- **Release builds are minified.** JNA resolves Vosk bindings reflectively, so new
  reflective dependencies need rules in `app/proguard-rules.pro`.
- **Mockito + Kotlin non-null parameters**: plain `org.mockito.Mockito.any()` returns
  `null`, which trips Kotlin's runtime null-check on a non-null parameter before the stub
  is reached. Use `anyString()`/`anyInt()`/etc. for primitives, or import
  `org.mockito.kotlin.any()` explicitly (it shadows the wildcard-imported `Mockito.any()`)
  for reference types. Getting this wrong doesn't just break the one test — the resulting
  exception can corrupt Mockito's global matcher state and cascade into unrelated-looking
  failures in whichever test happens to run next in the same JVM.

Dependencies are wired by hand in `MainActivity.NoterApp()` and re-resolved inside
`TranscriptionWorker`/`DriveBackupWorker` (WorkManager constructs workers itself). A DI
framework would remove that duplication.

## Known Issues

- No real Room `Migration`s exist yet — every schema bump (several so far) has just
  relied on `fallbackToDestructiveMigration()`, wiping local data. Fine pre-release;
  needs fixing before this could ship to anyone else.
