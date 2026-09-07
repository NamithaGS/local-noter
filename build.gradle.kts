// Top-level build file. Plugin versions are declared here once and applied
// (without version) in module build files.
plugins {
    id("com.android.application") version "8.7.3" apply false
    // Bumped from 2.0.20: com.google.mlkit:genai-prompt (any version, even the oldest
    // beta) ships Kotlin metadata that this project's old Kotlin compiler can't read.
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
}
