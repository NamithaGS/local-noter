# Noter Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Android voice note-taking app with on-device transcription and AI summarization.

**Architecture:** Clean Architecture with MVVM. UI in Jetpack Compose, ViewModels for state, Use Cases for business logic, Repository for data persistence (Room + Files).

**Tech Stack:** Kotlin, Jetpack Compose, Room, ML Kit Speech Recognition, AICore Gemini Nano, WorkManager

---

## File Structure

**Data Layer:**
- `app/src/main/java/com/noter/data/db/NoteEntity.kt` - Room entity
- `app/src/main/java/com/noter/data/db/NoteDao.kt` - Database operations
- `app/src/main/java/com/noter/data/db/AppDatabase.kt` - Room database
- `app/src/main/java/com/noter/data/repository/NoteRepository.kt` - Data access
- `app/src/main/java/com/noter/data/model/Note.kt` - Domain model

**Domain Layer:**
- `app/src/main/java/com/noter/domain/RecordingManager.kt` - Audio recording
- `app/src/main/java/com/noter/domain/TranscriptionService.kt` - ML Kit integration
- `app/src/main/java/com/noter/domain/SummarizationService.kt` - Gemini Nano
- `app/src/main/java/com/noter/domain/AudioPlayer.kt` - Audio playback
- `app/src/main/java/com/noter/domain/TranscriptionWorker.kt` - Background work

**UI Layer:**
- `app/src/main/java/com/noter/ui/theme/Theme.kt` - Material 3 theme
- `app/src/main/java/com/noter/ui/theme/Color.kt` - Color definitions
- `app/src/main/java/com/noter/ui/screens/NoteListScreen.kt` - Main screen
- `app/src/main/java/com/noter/ui/screens/RecordingScreen.kt` - Recording UI
- `app/src/main/java/com/noter/ui/screens/NoteDetailScreen.kt` - Detail view
- `app/src/main/java/com/noter/ui/viewmodels/NoteListViewModel.kt` - List state
- `app/src/main/java/com/noter/ui/viewmodels/RecordingViewModel.kt` - Recording state
- `app/src/main/java/com/noter/ui/viewmodels/NoteDetailViewModel.kt` - Detail state
- `app/src/main/java/com/noter/ui/navigation/NavGraph.kt` - Navigation setup
- `app/src/main/java/com/noter/MainActivity.kt` - Entry point

**Utilities:**
- `app/src/main/java/com/noter/util/TimeFormatter.kt` - Time utilities
- `app/src/main/java/com/noter/util/PermissionHelper.kt` - Permission handling
- `app/src/main/java/com/noter/util/FileHelper.kt` - File operations

---

## Task 1: Project Setup and Gradle Configuration

**Files:**
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create Android project structure**

```bash
mkdir -p app/src/main/{java/com/noter,res,AndroidManifest.xml}
mkdir -p app/src/test/java/com/noter
mkdir -p app/src/androidTest/java/com/noter
```

- [ ] **Step 2: Write build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.noter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noter"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.mlkit:speech-recognition:16.0.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 3: Write AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Noter"
        android:theme="@style/Theme.Noter">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Noter">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>
    
</manifest>
```

- [ ] **Step 4: Verify build configuration**

```bash
cd /Users/nganiga/Projects/mine/noter
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit project setup**

```bash
git init
git add .
git commit -m "chore: initialize Android project with Gradle and dependencies"
```

---

## Task 2: Data Layer - Room Database Setup

**Files:**
- Create: `app/src/main/java/com/noter/data/db/NoteEntity.kt`
- Create: `app/src/main/java/com/noter/data/db/NoteDao.kt`
- Create: `app/src/main/java/com/noter/data/db/AppDatabase.kt`
- Test: `app/src/test/java/com/noter/data/db/NoteDaoTest.kt`

- [ ] **Step 1: Write failing database test**

```kotlin
package com.noter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        noteDao = database.noteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveNote() = runBlocking {
        val note = NoteEntity(
            id = "test-id",
            title = "Test Note",
            transcriptPath = "/path/to/transcript.txt",
            audioPath = "/path/to/audio.m4a",
            summary = null,
            createdAt = System.currentTimeMillis(),
            duration = 60
        )
        
        noteDao.insert(note)
        val retrieved = noteDao.getAllNotes().first()
        
        assertEquals(1, retrieved.size)
        assertEquals("Test Note", retrieved[0].title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: FAIL with "Unresolved reference: NoteEntity"

- [ ] **Step 3: Implement NoteEntity**

```kotlin
package com.noter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val transcriptPath: String,
    val audioPath: String,
    val summary: String?,
    val createdAt: Long,
    val duration: Int
)
```

- [ ] **Step 4: Implement NoteDao**

```kotlin
package com.noter.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)
    
    @Update
    suspend fun update(note: NoteEntity)
    
    @Delete
    suspend fun delete(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

- [ ] **Step 5: Implement AppDatabase**

```kotlin
package com.noter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "noter_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
./gradlew test
```

Expected: PASS

- [ ] **Step 7: Commit database layer**

```bash
git add app/src/main/java/com/noter/data/db/
git add app/src/test/java/com/noter/data/db/
git commit -m "feat: add Room database with Note entity and DAO"
```

---

## Task 3: Domain Model and Repository

**Files:**
- Create: `app/src/main/java/com/noter/data/model/Note.kt`
- Create: `app/src/main/java/com/noter/data/repository/NoteRepository.kt`
- Test: `app/src/test/java/com/noter/data/repository/NoteRepositoryTest.kt`

- [ ] **Step 1: Write failing repository test**

```kotlin
package com.noter.data.repository

import com.noter.data.db.NoteDao
import com.noter.data.db.NoteEntity
import com.noter.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class NoteRepositoryTest {
    @Mock
    private lateinit var noteDao: NoteDao
    
    private lateinit var repository: NoteRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = NoteRepository(noteDao)
    }

    @Test
    fun getAllNotesConvertsEntitiesToDomainModels() = runBlocking {
        val entity = NoteEntity(
            id = "1",
            title = "Test",
            transcriptPath = "/transcript.txt",
            audioPath = "/audio.m4a",
            summary = "Summary",
            createdAt = 1000L,
            duration = 60
        )
        `when`(noteDao.getAllNotes()).thenReturn(flowOf(listOf(entity)))
        
        val notes = repository.getAllNotes().first()
        
        assertEquals(1, notes.size)
        assertEquals("Test", notes[0].title)
        assertEquals("Summary", notes[0].summary)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: FAIL with "Unresolved reference: Note"

- [ ] **Step 3: Implement Note domain model**

```kotlin
package com.noter.data.model

data class Note(
    val id: String,
    val title: String,
    val transcriptPath: String,
    val audioPath: String,
    val summary: String?,
    val createdAt: Long,
    val duration: Int
)
```

- [ ] **Step 4: Implement NoteRepository**

```kotlin
package com.noter.data.repository

import com.noter.data.db.NoteDao
import com.noter.data.db.NoteEntity
import com.noter.data.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val noteDao: NoteDao) {
    
    fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun getNoteById(id: String): Note? {
        return noteDao.getNoteById(id)?.toDomainModel()
    }
    
    suspend fun insertNote(note: Note) {
        noteDao.insert(note.toEntity())
    }
    
    suspend fun updateNote(note: Note) {
        noteDao.update(note.toEntity())
    }
    
    suspend fun deleteNote(note: Note) {
        noteDao.deleteById(note.id)
    }
    
    private fun NoteEntity.toDomainModel() = Note(
        id = id,
        title = title,
        transcriptPath = transcriptPath,
        audioPath = audioPath,
        summary = summary,
        createdAt = createdAt,
        duration = duration
    )
    
    private fun Note.toEntity() = NoteEntity(
        id = id,
        title = title,
        transcriptPath = transcriptPath,
        audioPath = audioPath,
        summary = summary,
        createdAt = createdAt,
        duration = duration
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew test
```

Expected: PASS

- [ ] **Step 6: Commit repository layer**

```bash
git add app/src/main/java/com/noter/data/model/
git add app/src/main/java/com/noter/data/repository/
git add app/src/test/java/com/noter/data/repository/
git commit -m "feat: add Note domain model and repository with entity conversion"
```

---

## Task 4: File and Utility Helpers

**Files:**
- Create: `app/src/main/java/com/noter/util/FileHelper.kt`
- Create: `app/src/main/java/com/noter/util/TimeFormatter.kt`
- Create: `app/src/main/java/com/noter/util/PermissionHelper.kt`
- Test: `app/src/test/java/com/noter/util/TimeFormatterTest.kt`

- [ ] **Step 1: Write failing time formatter test**

```kotlin
package com.noter.util

import org.junit.Assert.*
import org.junit.Test

class TimeFormatterTest {
    
    @Test
    fun formatDurationZeroSeconds() {
        assertEquals("00:00", TimeFormatter.formatDuration(0))
    }
    
    @Test
    fun formatDurationUnderMinute() {
        assertEquals("00:45", TimeFormatter.formatDuration(45))
    }
    
    @Test
    fun formatDurationMultipleMinutes() {
        assertEquals("05:30", TimeFormatter.formatDuration(330))
    }
    
    @Test
    fun formatDurationOverHour() {
        assertEquals("65:15", TimeFormatter.formatDuration(3915))
    }
    
    @Test
    fun formatRelativeTimeHoursAgo() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2 * 60 * 60 * 1000)
        assertEquals("2 hours ago", TimeFormatter.formatRelativeTime(twoHoursAgo, now))
    }
    
    @Test
    fun formatRelativeTimeYesterday() {
        val now = System.currentTimeMillis()
        val yesterday = now - (25 * 60 * 60 * 1000)
        assertEquals("Yesterday", TimeFormatter.formatRelativeTime(yesterday, now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: FAIL with "Unresolved reference: TimeFormatter"

- [ ] **Step 3: Implement TimeFormatter**

```kotlin
package com.noter.util

object TimeFormatter {
    
    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
    
    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            days < 2 -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> "${days / 7} week${if (days < 14) "" else "s"} ago"
        }
    }
}
```

- [ ] **Step 4: Implement FileHelper**

```kotlin
package com.noter.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

object FileHelper {
    
    private const val AUDIO_DIR = "Noter/audio"
    private const val TRANSCRIPT_DIR = "Noter/transcripts"
    
    fun getAudioFile(context: Context, noteId: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            AUDIO_DIR
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "note_$noteId.m4a")
    }
    
    fun getTranscriptFile(context: Context, noteId: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            TRANSCRIPT_DIR
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "note_$noteId.txt")
    }
    
    fun writeTranscript(file: File, text: String) {
        try {
            file.writeText(text)
        } catch (e: IOException) {
            throw IOException("Failed to write transcript: ${e.message}")
        }
    }
    
    fun readTranscript(file: File): String {
        return if (file.exists()) {
            file.readText()
        } else {
            throw IOException("Transcript file not found")
        }
    }
    
    fun checkStorageSpace(): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val usableSpace = dir.usableSpace
        val minRequired = 100 * 1024 * 1024L
        return usableSpace >= minRequired
    }
}
```

- [ ] **Step 5: Implement PermissionHelper**

```kotlin
package com.noter.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
    }
    
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew test
```

Expected: PASS

- [ ] **Step 7: Commit utility helpers**

```bash
git add app/src/main/java/com/noter/util/
git add app/src/test/java/com/noter/util/
git commit -m "feat: add file, time, and permission utility helpers"
```

---

## Task 5: Theme and UI Foundation

**Files:**
- Create: `app/src/main/java/com/noter/ui/theme/Color.kt`
- Create: `app/src/main/java/com/noter/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Define color palette**

```kotlin
package com.noter.ui.theme

import androidx.compose.ui.graphics.Color

val RecordRed = Color(0xFFE74C3C)
val AIBlue = Color(0xFF3498DB)
val BackgroundLight = Color(0xFFF5F5F5)
val CardBackground = Color(0xFFECF0F1)
val SummaryBackground = Color(0xFFFFF3CD)
val SummaryBorder = Color(0xFFFFC107)
val TextPrimary = Color(0xFF2C3E50)
val TextSecondary = Color(0xFF7F8C8D)
```

- [ ] **Step 2: Define Material 3 theme**

```kotlin
package com.noter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = RecordRed,
    secondary = AIBlue,
    background = BackgroundLight,
    surface = CardBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun NoterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
```

- [ ] **Step 3: Create themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Noter" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 4: Commit theme foundation**

```bash
git add app/src/main/java/com/noter/ui/theme/
git add app/src/main/res/values/themes.xml
git commit -m "feat: add Material 3 theme with color palette and typography"
```

---

## Task 6: RecordingManager - Audio Recording

**Files:**
- Create: `app/src/main/java/com/noter/domain/RecordingManager.kt`
- Test: `app/src/test/java/com/noter/domain/RecordingManagerTest.kt`

- [ ] **Step 1: Write RecordingManager**

```kotlin
package com.noter.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.noter.util.FileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingManager(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTime: Long = 0
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _elapsedTime = MutableStateFlow(0)
    val elapsedTime: StateFlow<Int> = _elapsedTime
    
    fun startRecording(noteId: String): Result<File> {
        return try {
            val file = FileHelper.getAudioFile(context, noteId)
            currentFile = file
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.RECORDING
            
            Result.success(file)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }
    
    fun stopRecording(): Result<Int> {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            _recordingState.value = RecordingState.IDLE
            _elapsedTime.value = 0
            
            Result.success(duration)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }
    
    fun updateElapsedTime() {
        if (_recordingState.value == RecordingState.RECORDING) {
            _elapsedTime.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        }
    }
    
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null
            
            _recordingState.value = RecordingState.IDLE
            _elapsedTime.value = 0
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
        }
    }
    
    enum class RecordingState {
        IDLE, RECORDING, ERROR
    }
}
```

- [ ] **Step 2: Commit RecordingManager**

```bash
git add app/src/main/java/com/noter/domain/RecordingManager.kt
git commit -m "feat: add RecordingManager for audio recording with MediaRecorder"
```

---

## Task 7: TranscriptionWorker - Background Transcription

**Files:**
- Create: `app/src/main/java/com/noter/domain/TranscriptionWorker.kt`

- [ ] **Step 1: Write TranscriptionWorker**

```kotlin
package com.noter.domain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noter.data.db.AppDatabase
import com.noter.util.FileHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val noteId = inputData.getString("noteId") ?: return Result.failure()
        val audioPath = inputData.getString("audioPath") ?: return Result.failure()
        
        return try {
            val transcript = transcribeAudio(audioPath)
            
            val transcriptFile = FileHelper.getTranscriptFile(applicationContext, noteId)
            FileHelper.writeTranscript(transcriptFile, transcript)
            
            val database = AppDatabase.getDatabase(applicationContext)
            val note = database.noteDao().getNoteById(noteId)
            
            note?.let {
                val title = generateTitle(transcript)
                database.noteDao().update(it.copy(title = title))
            }
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun transcribeAudio(audioPath: String): String {
        return suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    recognizer.destroy()
                    continuation.resume(transcript)
                }
                
                override fun onError(error: Int) {
                    recognizer.destroy()
                    continuation.resume("")
                }
                
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            recognizer.startListening(intent)
        }
    }
    
    private fun generateTitle(transcript: String): String {
        return if (transcript.length > 30) {
            transcript.substring(0, 30).trim() + "..."
        } else if (transcript.isNotEmpty()) {
            transcript
        } else {
            "Untitled Note ${System.currentTimeMillis()}"
        }
    }
}
```

- [ ] **Step 2: Commit TranscriptionWorker**

```bash
git add app/src/main/java/com/noter/domain/TranscriptionWorker.kt
git commit -m "feat: add TranscriptionWorker for background speech-to-text"
```

---

## Task 8: Complete the remaining 3 tasks in separate plan file

Due to Android complexity, split remaining work into second plan file.

- [ ] **Step 1: Create continuation plan**

Create: `docs/superpowers/plans/2026-06-08-noter-ui-implementation.md` with:
- Task 9: ViewModels (NoteListViewModel, RecordingViewModel, NoteDetailViewModel)
- Task 10: UI Screens (NoteListScreen, RecordingScreen, NoteDetailScreen)
- Task 11: Navigation and MainActivity

- [ ] **Step 2: Note split point**

Current plan completes foundation. Next plan builds UI layer.
