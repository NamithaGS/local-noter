# sherpa-onnx's Kotlin wrapper classes declare `external fun`s that its native library
# resolves by exact JVM method signature. The vendored AAR's own proguard.txt is empty
# (verified - not just unused), so without these rules a minified build would let R8
# rename/strip them and transcription would fail at runtime with UnsatisfiedLinkError.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Guava (pulled in by the ML Kit GenAI APIs) references compile-time-only annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
