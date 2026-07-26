# Noter - AI-Powered Voice Notes

A lightweight, privacy-focused Android app that records voice notes and converts them to text using on-device AI. No cloud required - your notes never leave your phone.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2014%2B-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg" alt="UI">
</p>

---

## 📥 Installation (For Users)

**Want to just use the app?**

1. Go to the [Releases page](https://github.com/NamithaGS/noter/releases)
2. Download the latest `noter-debug.apk`
3. Install on your Android phone (Android 14+ required)
4. Done! No development tools needed.

**Requirements:**
- Android 14 or higher
- Pixel 8+ (recommended for AI summarization) or compatible device
- ~100MB storage space

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎙️ **Voice Recording** | One-tap recording with M4A audio compression |
| 📝 **Speech-to-Text** | Automatic transcription using Vosk (on-device, offline) |
| 🤖 **AI Summarization** | Concise summaries via Gemini Nano (requires compatible device) |
| 💾 **Local Storage** | All data stored in your Documents folder - easy to backup |
| 📋 **Export** | Copy to clipboard or download as .txt files |
| 🔊 **Playback** | Replay your original recordings anytime |
| 🔒 **Privacy First** | 100% on-device processing, no cloud, no tracking |

---

## 🏗️ For Developers

### Quick Start

**1. Clone the repository:**
```bash
git clone https://github.com/NamithaGS/noter.git
cd noter
```

**2. Open in Android Studio:**
- Launch Android Studio
- **File → Open** → Select the `noter` folder
- Wait for Gradle sync to complete

**3. Run on your phone:**
- Enable USB Debugging on your Android device
- Connect via USB
- Click **Run** (▶️) in Android Studio
- Select your device

Done! The app installs and launches on your phone.

---

## 🔧 Tech Stack

**Architecture:** Clean Architecture + MVVM

**Languages & Frameworks:**
- Kotlin
- Jetpack Compose (Material 3)
- Kotlin Coroutines & Flow

**Android Jetpack:**
- Room Database
- Navigation Compose
- WorkManager
- ViewModel & LiveData

**AI & ML:**
- [Vosk](https://alphacephei.com/vosk/) `0.3.75` — offline speech recognition
- ML Kit GenAI Summarization `1.0.0-beta1` — Gemini Nano via AICore

**Testing:**
- JUnit 4
- Mockito
- Compose UI Test
- AndroidX Test

---

## 📁 Project Structure

```
noter/
├── app/src/main/java/com/noter/
│   ├── data/               # Data layer
│   │   ├── db/            # Room database (entities, DAOs)
│   │   ├── model/         # Domain models
│   │   └── repository/    # Repository pattern
│   ├── domain/            # Business logic
│   │   ├── RecordingManager.kt
│   │   ├── TranscriptionWorker.kt
│   │   ├── transcription/  # Vosk + audio decoding
│   │   └── summarization/  # Gemini Nano summarizer
│   ├── ui/                # UI layer
│   │   ├── screens/       # Compose screens
│   │   ├── viewmodels/    # State management
│   │   ├── theme/         # Material 3 theme
│   │   └── navigation/    # Navigation graph
│   ├── util/              # Utilities
│   └── MainActivity.kt    # App entry point
├── app/src/test/          # Unit tests
├── app/src/androidTest/   # Integration & UI tests
└── .github/workflows/     # CI/CD (GitHub Actions)
```

---

## 🚀 Automated Builds (GitHub Actions)

This project uses **GitHub Actions** to automatically build APKs - no Android Studio required!

### How it works:

**Every push to `main`:**
- GitHub automatically builds the APK
- Download from **Actions** tab → Latest run → Artifacts

**Version releases:**
```bash
git tag v1.0.0
git push origin v1.0.0
```
- GitHub automatically creates a Release
- APK is attached to the release
- Users can download from Releases page

**Manual trigger:**
- Go to **Actions** tab → **Build APK** → **Run workflow**

### Benefits:
✅ No Android Studio needed for releases  
✅ Consistent builds on GitHub's servers  
✅ Automatic APK generation for every version  
✅ Users get production-ready APKs instantly

---

## 🧪 Testing

**Run all tests:**
```bash
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Integration & UI tests
```

**Test Coverage:**
- 59 comprehensive tests
- Unit tests: ViewModels, RecordingManager, Utilities
- Integration tests: Repository with Room database
- UI tests: Compose screens and interactions

---

## 🔑 Permissions

| Permission | Purpose | Required? |
|------------|---------|-----------|
| `RECORD_AUDIO` | Voice recording | ✅ Yes |
| `POST_NOTIFICATIONS` | Transcription status updates | ⚠️ Optional |

Permissions are requested at runtime only when needed.

---

## 💾 Storage Location

Notes are stored in:
```
/storage/emulated/0/Documents/Noter/
├── audio/          # M4A recordings (~1MB per 5-minute note)
└── transcripts/    # Plain text files (~5KB each)
```

**Why Documents folder?**
- Easy backup via file managers
- Accessible by other apps
- Survives app uninstall (user choice)

---

## 🛠️ Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK API 34+

### First-time setup:
1. Clone and open in Android Studio
2. **Download the speech model** (required — transcription fails without it):
   ```bash
   scripts/fetch-vosk-model.sh
   ```
   This fetches ~41 MB into `app/src/main/assets/vosk-model-en-us/`. It is not committed
   to git, so every fresh clone needs this once.
3. Sync Gradle (happens automatically)
4. Configure Android SDK if needed:
   - **File → Project Structure → SDK Location**
   - Install SDK Platform 34 if missing

### Building:
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires signing)
./gradlew installDebug           # Install to connected device
```

**APK location:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 🐛 Troubleshooting

**"SDK location not found"**  
→ Set `ANDROID_HOME` environment variable or configure in Android Studio

**"Installed Build Tools revision X.X.X is corrupted"**  
→ Tools → SDK Manager → SDK Tools → Reinstall Build Tools

**"App requires Android 14+ but device is older"**  
→ Use a device with Android 14+ or create an AVD with API 34+

**"AI summarization requires Gemini Nano"**  
→ This feature requires Pixel 8+ or compatible device. App works without it.

**Transcripts come back empty / "Vosk model not installed" in logcat**  
→ Run `scripts/fetch-vosk-model.sh` and rebuild. The model is not in version control.

**Gradle sync fails**  
→ File → Invalidate Caches → Restart

---

## 🎯 Roadmap

- [ ] Note editing and manual corrections
- [ ] Search and filtering
- [ ] Tags and categories  
- [ ] Dark theme
- [ ] Multiple language UI support
- [ ] Cloud sync (optional, opt-in)
- [ ] Widget support

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run tests: `./gradlew test connectedAndroidTest`
4. Commit changes (`git commit -m 'feat: add amazing feature'`)
5. Push to branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

**Code standards:**
- Follow Kotlin coding conventions
- Write tests for new features
- Keep functions small and focused
- Update documentation for significant changes

---

## 📄 License

[Add your license here - MIT, Apache 2.0, etc.]

---

## 🙏 Acknowledgments

Built with:
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit
- [Vosk](https://alphacephei.com/vosk/) - Offline speech recognition
- [ML Kit GenAI](https://developers.google.com/ml-kit/genai/summarization/android) - On-device summarization
- [AICore Gemini Nano](https://ai.google.dev/) - On-device AI summarization
- [Room](https://developer.android.com/training/data-storage/room) - Local database

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/NamithaGS/noter/issues)
- **Discussions:** [GitHub Discussions](https://github.com/NamithaGS/noter/discussions)

---

**Made with ❤️ for privacy-conscious note-takers**
