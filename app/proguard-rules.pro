# ProGuard rules for ScreenMirror

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.example.screenmirror.data.** { *; }

# Splash Screen Components
-keep class com.example.screenmirror.SplashActivity { *; }
-keep class com.example.screenmirror.splash.ParticleView { *; }
-keepclassmembers class com.example.screenmirror.splash.ParticleView {
    public <init>(...);
}

# MotionLayout
-keep class androidx.constraintlayout.motion.widget.** { *; }
-keepclassmembers class * extends androidx.constraintlayout.motion.widget.MotionLayout {
    *;
}
