# Noter - Voice Note Taking App Design Specification

**Date:** 2026-06-08  
**Platform:** Android 14+  
**Target Devices:** Pixel 8+ and devices with Gemini Nano support

## Overview

Noter is a lightweight Android app for voice-based note-taking with local AI-powered transcription and summarization. The app records audio, converts speech to text using Google ML Kit, and generates concise summaries using Gemini Nano (AICore). All processing happens on-device with no cloud dependency.

## Core Features

1. **Voice Recording:** Single-tap recording with timer display
2. **Speech-to-Text:** Automatic transcription using ML Kit Speech Recognition
3. **AI Summarization:** Auto-generated summaries via Gemini Nano when opening notes
4. **Note Management:** List view with timestamps, titles, and previews
5. **Export:** Copy text to clipboard and download as .txt files
6. **Audio Playback:** Replay original recordings

## Technical Architecture

### Architecture Pattern

**Clean Architecture with MVVM:**

- **UI Layer:** Jetpack Compose screens and composables
- **ViewModel Layer:** State management and coordination
- **Domain Layer:** Use cases (RecordNote, TranscribeAudio, SummarizeNote)
- **Data Layer:** Repositories managing Room DB and file system

### Key Components

**RecordingManager**
- Handles MediaRecorder lifecycle
- Saves audio files in M4A format
- Manages recording state and timer

**TranscriptionService**
- Integrates ML Kit SpeechRecognizer
- Processes audio files in background (WorkManager)
- Handles language detection and confidence scores

**SummarizationService**
- Integrates AICore Gemini Nano API
- Generates concise summaries with custom prompts
- Caches results to avoid redundant generation

**NoteRepository**
- Manages Room database operations
- Handles file I/O for audio and transcripts
- Ensures data-file consistency

**AudioPlayer**
- Plays back recorded audio files
- Manages audio focus and playback state

### Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture Components:** ViewModel, LiveData, Room Database
- **Async:** Kotlin Coroutines and Flow
- **Audio Recording:** MediaRecorder API
- **Speech Recognition:** Google ML Kit Speech-to-Text
- **AI Summarization:** AICore Gemini Nano
- **Background Work:** WorkManager for transcription
- **Navigation:** Compose Navigation
- **Dependency Injection:** Hilt (optional, can use manual DI for simplicity)

## Data Models

### Database Schema (Room)

```kotlin
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,              // Auto-generated from first line or timestamp
    val transcriptPath: String,     // Path to .txt file in Documents
    val audioPath: String,          // Path to .m4a file in Documents
    val summary: String?,           // Cached summary from Gemini Nano
    val createdAt: Long,            // Timestamp in milliseconds
    val duration: Int               // Recording duration in seconds
)
```

### File Storage Structure

```
/storage/emulated/0/Documents/Noter/
├── audio/
│   ├── note_[uuid].m4a
│   └── note_[uuid].m4a
└── transcripts/
    ├── note_[uuid].txt
    └── note_[uuid].txt
```

**Storage Strategy:**
- Audio files in M4A format (efficient compression, native support)
- Transcripts as UTF-8 plain text files
- Summaries stored in Room database (small, frequently accessed)
- File names use note UUID for linking
- Documents folder allows user access and backup

**Why M4A:** Provides approximately 10:1 compression compared to WAV while maintaining good quality for speech.

## User Flows

### Recording Flow

1. **Start Recording:**
   - User taps large red record button on main screen
   - App requests RECORD_AUDIO permission if not granted
   - Navigate to full-screen recording view
   - MediaRecorder initializes and starts capturing
   - Timer updates every second (MM:SS format)

2. **During Recording:**
   - Pulsing red button animation
   - Elapsed time display
   - Simple waveform visualization
   - Cancel button (discards recording)
   - Stop button (saves and transcribes)

3. **Stop Recording:**
   - Audio file saved to Documents/Noter/audio/
   - Return to main screen
   - Note appears in list with "Transcribing..." status
   - Background WorkManager job starts transcription
   - Show notification: "Transcribing your note..."

4. **Transcription Complete:**
   - Transcript saved to Documents/Noter/transcripts/
   - Note metadata saved to Room database
   - Title auto-generated from first 30 chars or timestamp
   - Update notification: "Note ready"
   - Note status updates in list

### Note Viewing Flow

1. **Open Note:**
   - User taps note in list
   - Navigate to detail screen
   - Display action buttons (Copy, Download, AI)
   - Check if summary exists in cache

2. **Generate Summary:**
   - If no cached summary: Show loading indicator in summary card
   - Call AICore Gemini Nano with prompt
   - Display summary in yellow card at top
   - Cache summary in database

3. **Interact with Note:**
   - Read summary for quick overview
   - Scroll down to full transcript
   - Tap "Copy" to copy transcript to clipboard
   - Tap "Download" to export as .txt file
   - Tap "AI" button to regenerate summary
   - Tap play FAB to listen to original audio

### Export Flow

**Copy to Clipboard:**
- Copies full transcript text
- Shows brief "Copied" toast confirmation

**Download:**
- Creates .txt file in Downloads folder
- Filename: `noter_[title]_[date].txt`
- Shows system notification with "Open" action
- File contains transcript only (not summary)

## Screen Specifications

### 1. Main Screen (NoteListScreen)

**Layout:**
- Top app bar with "Noter" title
- Record button card at top:
  - Horizontal layout
  - 80dp red circular button on left
  - "Start Recording" title and subtitle on right
  - Background: light gray, rounded corners
- "NOTES LIST" section header
- Scrollable list of note items
- Empty state: "No notes yet. Tap above to record."

**Note Item:**
- Title (bold, 16sp)
- Preview text from transcript (first 60 chars, gray, 14sp)
- Timestamp (relative: "2 hours ago", "Yesterday")
- Divider between items

**States:**
- Loading: Skeleton placeholders
- Empty: Empty state message
- Transcribing: Show spinner next to note title

### 2. Recording Screen (RecordingScreen)

**Layout:**
- Full screen modal
- Large pulsing red circle (120dp) in center
- Timer above button (24sp, bold)
- Animated waveform bars below button
- Bottom bar with Cancel and Stop buttons

**Animations:**
- Record button: Scale pulse 1.0 → 1.05 → 1.0 (1 second cycle)
- Waveform: Random height bars updating at 60fps

**Behavior:**
- Back button shows confirmation dialog: "Discard recording?"
- Cancel button: Immediate discard, return to main screen
- Stop button: Save and navigate back

### 3. Note Detail Screen (NoteDetailScreen)

**Layout:**
- Top app bar: Back arrow, note title, timestamp
- Action buttons row (horizontal, equal width):
  - 📋 Copy
  - 💾 Download  
  - ✨ AI (regenerate summary)
- Summary card (if available):
  - Yellow background (#FFF3CD)
  - Orange left border (#FFC107)
  - "📝 SUMMARY" header
  - Summary text (14sp, line height 1.5)
  - Loading state: Spinner with "Generating summary..."
  - Error state: Error message with retry button
- "FULL TRANSCRIPT" section header (gray, uppercase, 12sp)
- Transcript text (scrollable, 16sp, line height 1.6, gray background)
- Floating Action Button (bottom right): Play audio icon

**States:**
- Loading summary: Spinner in summary card
- Summary error: Error message with retry
- Playing audio: FAB changes to pause icon

## AI Integration

### ML Kit Speech Recognition

**Configuration:**
- Language: Auto-detect (default to English)
- Recognition mode: On-device
- Partial results: Disabled (wait for final result)

**Process:**
1. Check if speech model is downloaded
2. If not: Prompt user to download via ML Kit download manager
3. Create RecognizerIntent with audio file URI
4. Process in background WorkManager job
5. Save transcript to file and update database
6. Handle errors with retry logic

**Error Handling:**
- Model not available → Download prompt
- Recognition fails → Retry once automatically
- No speech detected → Show warning, keep audio
- Very long audio (>30 min) → Process in chunks

### AICore Gemini Nano

**Setup:**
- Check availability: `AiCoreClient.isAvailable()` on app launch
- Show warning if unavailable (device not supported)
- Initialize model on first use

**Summarization:**

**Prompt Template:**
```
Summarize the following note in 1-2 concise sentences. Focus on key points and action items: [transcript]
```

**Parameters:**
- Temperature: 0.3 (focused, deterministic)
- Max tokens: 100 (keep summaries brief)
- Input limit: 2000 words (truncate longer transcripts)

**Caching Strategy:**
- Cache summaries in Room database
- Regenerate only when user taps "AI" button
- Cache never expires (user-controlled regeneration)

**Error Handling:**
- AICore unavailable → "AI summarization requires Gemini Nano. Please ensure your device supports it."
- Model loading fails → "Unable to load AI model" with retry button
- Timeout (>30s) → Cancel and show retry
- Rate limiting → Exponential backoff retry

## Error Handling & Edge Cases

### Permissions

**Required Permissions:**
- `RECORD_AUDIO` (runtime) - Required before recording
- `POST_NOTIFICATIONS` (runtime, Android 13+) - For transcription notifications
- `WRITE_EXTERNAL_STORAGE` (Android <13) - For Documents folder

**Permission Flow:**
- Request on first recording attempt
- If denied: Show rationale dialog with "Open Settings" button
- Gracefully degrade if notifications denied (no background updates)

### Storage Management

- Check available space before recording (minimum 100MB)
- Warn user if storage low
- Provide manual cleanup option in settings (delete old notes)
- Handle external storage unmounted: Show error, disable recording

### Recording Interruptions

**Phone Call During Recording:**
- Auto-save recording up to interruption point
- Show notification: "Recording saved due to phone call"
- Return to main screen

**App Backgrounded:**
- Continue recording in foreground service
- Show persistent notification with stop action
- Resume UI when returning to app

**Battery Saver Mode:**
- Detect and warn: "Battery saver may affect recording quality"
- Continue recording normally

**Audio Focus Loss:**
- Pause recording automatically
- Show resume button or auto-resume when focus regained

### Data Integrity

**Orphaned Files:**
- On app launch: Scan Documents/Noter folders
- Delete audio/transcript files with no database entry
- Delete database entries with missing files

**Database-File Sync:**
- Verify file existence before displaying note
- Show "File missing" error if audio/transcript not found
- Offer to remove broken note from list

**Crash Recovery:**
- Incomplete transcriptions: Resume on next app launch
- Temporary recording files: Clean up on restart
- Use WorkManager's built-in retry for transcription jobs

### Network & Model Availability

**ML Kit Model:**
- Check model availability on first launch
- Download prompt with progress indicator
- Offline-first: All processing on-device
- Handle model download failures gracefully

**AICore Gemini Nano:**
- Verify device support on app launch
- Show clear error if unsupported: "Your device doesn't support AI summarization"
- Degrade gracefully: Allow note viewing without summary
- Don't block app functionality if unavailable

### Transcription Edge Cases

**Silent Audio:**
- Detect no speech in recording
- Show warning: "No speech detected in recording"
- Keep audio file for manual playback

**Very Long Recordings (>30 min):**
- Split into 5-minute chunks
- Process each chunk separately
- Concatenate results

**Multiple Languages:**
- Use dominant language detected by ML Kit
- Accept transcription in any supported language

**Poor Audio Quality:**
- Show confidence scores if available
- Warning: "Transcription may be inaccurate (low audio quality)"
- Keep original audio for verification

## Testing Strategy

### Unit Tests

- ViewModels: State transitions and business logic
- Use cases: Recording, transcription, summarization flows
- Repository: Database and file operations
- Utilities: Time formatting, file naming, permission checks

### Integration Tests

- Room database queries and migrations
- File system operations (read/write/delete)
- ML Kit and AICore API interactions (mocked)

### UI Tests (Compose)

- Navigation flows between screens
- Recording button states and transitions
- Note list display and interactions
- Detail screen summary generation

### Manual Testing

- Recording quality on different devices
- ML Kit transcription accuracy
- Gemini Nano summary quality
- Permission flows and edge cases
- Storage management scenarios

## Build Configuration

### Minimum Requirements

- **minSdk:** 34 (Android 14)
- **targetSdk:** 35 (Android 15)
- **compileSdk:** 35

### Dependencies

**Core:**
```gradle
androidx.core:core-ktx
androidx.lifecycle:lifecycle-runtime-ktx
androidx.activity:activity-compose
```

**Compose:**
```gradle
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.compose.ui:ui-tooling-preview
androidx.navigation:navigation-compose
```

**Room Database:**
```gradle
androidx.room:room-runtime
androidx.room:room-ktx
kapt androidx.room:room-compiler
```

**ML Kit:**
```gradle
com.google.mlkit:speech-recognition
```

**AICore (Gemini Nano):**
```gradle
com.google.android.aicore:aicore-client
```

**WorkManager:**
```gradle
androidx.work:work-runtime-ktx
```

**Testing:**
```gradle
junit:junit
androidx.test.ext:junit
androidx.test.espresso:espresso-core
androidx.compose.ui:ui-test-junit4
```

### Gradle Configuration

**ProGuard Rules:**
- Keep AICore and ML Kit classes
- Keep Room entities and DAOs
- Standard Android optimization

**Build Types:**
- Debug: Logging enabled, no obfuscation
- Release: ProGuard enabled, logging disabled

### App Signing

- Use standard Android keystore
- Enable APK/AAB signing in release builds

## Performance Considerations

### App Size

**Target:** <15MB APK
- Jetpack Compose adds ~3-5MB
- ML Kit downloads models separately (~50MB)
- Minimal dependencies
- Enable R8 shrinking and obfuscation

### Memory

- Streaming audio recording (no full buffer)
- Lazy loading in note list (paginate if needed)
- Release MediaRecorder after use
- Limit cached summaries in memory

### Battery

- Recording in foreground service only
- WorkManager for background transcription (respects Doze)
- No wake locks or continuous polling
- AI inference only on-demand

### Storage

- M4A compression reduces audio size
- No redundant data storage
- User-controllable cleanup
- Typical note: ~1MB audio + 5KB text + 500B summary

## Security & Privacy

### Data Privacy

- All processing on-device (no cloud)
- No analytics or tracking
- No network permissions required
- User owns all data (Documents folder)

### Permissions Justification

- RECORD_AUDIO: Core functionality
- POST_NOTIFICATIONS: User experience (transcription status)
- WRITE_EXTERNAL_STORAGE: User data access and backup

### Secure Storage

- No encryption needed (user-accessible Documents folder)
- Standard Android file permissions
- No sensitive data collected

## Future Enhancements (Out of Scope)

- Note editing and manual transcription corrections
- Search and filtering
- Tags and categories
- Cloud sync and backup
- Dark theme support
- Note sharing
- Multiple language support in UI
- Voice commands during recording
- Speaker identification
- Export to other formats (PDF, Markdown)

## Success Criteria

1. **Recording Quality:** Clear audio capture on supported devices
2. **Transcription Accuracy:** >85% word accuracy for clear speech
3. **Summary Quality:** Concise, relevant summaries that capture key points
4. **Performance:** Recording starts <1 second, transcription completes <30 seconds for 5-minute note
5. **Reliability:** No crashes, graceful error handling
6. **Usability:** Intuitive UI requiring no tutorial
7. **Storage Efficiency:** <2MB per 5-minute note (audio + text + summary)

## Why This Design

**On-Device AI:** Privacy-focused, works offline, no API costs.

**List-First UI:** Users need quick access to existing notes, not just recording.

**Auto-Summarization:** Saves time reviewing notes, shows value of AI immediately.

**Shared Storage:** Users can backup/access files easily, transparency builds trust.

**Material 3 + Compose:** Modern Android development, clean code, future-proof.

**M4A Audio:** Best balance of quality and file size for voice recordings.

**Minimal Features:** Each feature serves core use case, no bloat, lightweight app.