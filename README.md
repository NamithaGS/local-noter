# Local Noter

A lightweight, privacy-focused Android app that records voice notes and turns them into text and summaries using on-device AI.

**The premise: everything needed to do this runs on your phone.** Transcription and summarization both happen locally - there are no calls to an external LLM provider (no OpenAI, no Anthropic, no cloud inference of any kind) for either step. The only network activity in the app is two things you explicitly turn on yourself: backing your notes up to your own Google Drive, and a one-time download of the on-device summarization model's weights.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2014%2B-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg" alt="UI">
</p>

---

## 📥 Installation

1. Go to the [Releases page](https://github.com/NamithaGS/local-noter/releases)
2. Download the latest `app-debug.apk`
3. Install on your Android phone (Android 14+ required)

**Requirements:**
- Android 14 or higher
- ~250MB free storage space if you set up on-device summarization (the model download is ~560MB one-time; see below)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎙️ **Voice Recording** | One-tap recording, M4A audio |
| 📝 **Speech-to-Text** | Automatic transcription via sherpa-onnx (on-device, offline) |
| 🤖 **AI Summarization** | Three-bullet summaries, fully on-device - no cloud LLM call |
| 🏷️ **Topic Tags** | Tag a note's topic yourself to route its summary into a per-topic doc |
| ☁️ **Optional Drive Backup** | Back up notes as real Google Docs to your own Drive - opt-in, your account, your data |
| 🔒 **Privacy First** | Transcription and summarization never leave your phone |

---

## 🧠 How your data is handled

- **Transcription** (sherpa-onnx) and **summarization** run entirely on-device. Nothing about what you say or record is sent to any AI provider.
- **Google Drive backup is optional and off by default.** If you turn it on (⋮ menu → Setup Google Drive), the app writes to a `LocalNoter` folder it creates in *your* Drive, using access scoped to only the files it creates itself - it can't see or touch anything else in your Drive.
- **On-device summarization needs a one-time model download** (⋮ menu → Setup Hugging Face). You provide your own free Hugging Face account and access token to fetch the model weights once; after that, summarization runs fully offline.

---

## 🔑 Permissions

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Recording voice notes |
| `POST_NOTIFICATIONS` | Transcription status updates |
| `INTERNET` | Only used for the two opt-in features above (Drive backup, model download) |

Permissions are requested at runtime only when needed.

---

## 💾 Where your notes live

```
/storage/emulated/0/Documents/Noter/
├── audio/          # M4A recordings
└── transcripts/    # Plain text transcripts
```

Stored in your Documents folder specifically so it's easy to find, back up with any file manager, and survives an app uninstall if you choose to keep it.

---

## 🐛 Troubleshooting

**"AI summarization isn't producing anything"**
→ Open the ⋮ menu → Setup Hugging Face and complete the one-time model download. See in-app instructions for the Hugging Face token.

**"Couldn't connect to Google Drive" / backup fails**
→ Open the ⋮ menu → Setup Google Drive - it walks through what's needed. This feature requires a Google Cloud project be configured for the app; see [DEVELOPER.md](DEVELOPER.md) if you're building your own fork.

**Transcripts come back empty ("No speech detected")**
→ Speak closer to the mic; check the in-app level graph while recording to confirm your voice is registering.

---

## 🏗️ Building from source

See [DEVELOPER.md](DEVELOPER.md) for the tech stack, project structure, build setup, and test suite.

---

## 📄 License

[Add your license here - MIT, Apache 2.0, etc.]

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/NamithaGS/local-noter/issues)
- **Discussions:** [GitHub Discussions](https://github.com/NamithaGS/local-noter/discussions)

---

**Made with ❤️ for privacy-conscious note-takers**
