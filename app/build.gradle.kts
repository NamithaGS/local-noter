plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Required from Kotlin 2.0 onward; replaces composeOptions.kotlinCompilerExtensionVersion.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.noter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noter"
        minSdk = 34
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"

        ndk {
            // libvosk.so is ~10 MB per ABI. Shipping all four would add ~40 MB to the
            // APK, so limit to the two phone ABIs plus x86_64 for emulator testing.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            // google-auth-library-{oauth2-http,credentials} (pulled in transitively by
            // the Drive API client) both ship this file; its content isn't needed at
            // runtime so excluding it is the standard fix rather than picking a side.
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
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

    // Speech-to-text: on-device, offline. Pulls in libvosk.so + net.java.dev.jna.
    // Requires a model in app/src/main/assets/ - see scripts/fetch-vosk-model.sh.
    implementation("com.alphacephei:vosk-android:0.3.75")

    // Summarization via Gemini Nano through AICore. Only functional on devices that
    // ship AICore (Pixel 8+, Galaxy S24+); degrades gracefully elsewhere.
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")

    // Google Drive daily backup: Sign-In for OAuth, API client for the Drive v3 REST calls.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20260823-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    // NoteDaoTest and FileHelperTest live under src/test (Robolectric, JVM) but use
    // AndroidJUnit4/ApplicationProvider, so these need to be on the test classpath too,
    // not just androidTestImplementation (which only covers src/androidTest).
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    // Compose test artifacts are also version-managed by the BOM, but the BOM
    // `platform(...)` above is only applied to `implementation`, not `androidTestImplementation`.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // mockito-android (not mockito-core) so the inline mock maker works on-device;
    // Compose screen tests here mock RecordingManager/NoteRepository the same way the
    // JVM tests do, but that needs its own copy since androidTestImplementation doesn't
    // inherit from testImplementation.
    androidTestImplementation("org.mockito:mockito-android:5.2.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
