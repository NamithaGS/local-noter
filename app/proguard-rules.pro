# Vosk talks to libvosk.so through JNA, which resolves classes and members
# reflectively. Without these rules the release build strips the bindings and
# transcription fails at runtime with UnsatisfiedLinkError.
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }

# JNA ships desktop-only code paths that reference AWT, which does not exist on Android.
-dontwarn java.awt.**
-dontwarn javax.swing.**

# Guava (pulled in by the ML Kit GenAI APIs) references compile-time-only annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
