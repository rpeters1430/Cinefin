# ================================================================================
# Jellyfin Android Client - ProGuard Rules
# ================================================================================
# These rules ensure the app functions correctly when minification is enabled
# for release builds. Without these rules, critical crashes would occur.
# ================================================================================

# ================================
# DEBUG INFORMATION & CRASH REPORTS
# ================================
# Keep line numbers for better crash reports in production
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep debugging attributes for better stack traces
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod

# ================================
# KOTLIN SERIALIZATION
# ================================
# Critical for JSON parsing - prevents crashes during API responses
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep all serializable classes and their serializers
-keep @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}

# Keep serializers for all serializable classes
-keep class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }

# Don't warn about kotlinx.serialization
-dontwarn kotlinx.serialization.**

# ================================
# RETROFIT & NETWORKING
# ================================
# Prevent API interface methods from being obfuscated
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# Keep generic signatures for Retrofit
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.ConscryptHostnameVerifier

# ================================
# JELLYFIN SDK
# ================================
# Preserve Jellyfin SDK classes and methods to maintain API functionality
-keep class org.jellyfin.sdk.** { *; }
-keep interface org.jellyfin.sdk.** { *; }

# Keep all model classes that might be used for API responses
-keep class org.jellyfin.sdk.model.** { *; }
-keep class org.jellyfin.sdk.api.** { *; }

# Don't warn about Jellyfin SDK dependencies
-dontwarn org.jellyfin.sdk.**

# ================================
# SLF4J LOGGING
# ================================
# Keep SLF4J classes for Jellyfin SDK logging
-keep class org.slf4j.** { *; }
-keep interface org.slf4j.** { *; }
-dontwarn org.slf4j.**

# ================================
# HILT DEPENDENCY INJECTION
# ================================
# Keep Hilt generated classes and annotations
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.hilt.** { *; }

# Keep Hilt entry points and components
-keep @dagger.hilt.InstallIn class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.Module class *

# Keep classes with @Inject constructors
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# Keep classes with @Inject fields
-keepclasseswithmembers class * {
    @javax.inject.Inject <fields>;
}

# Keep classes with @Inject methods
-keepclasseswithmembers class * {
    @javax.inject.Inject <methods>;
}

# ================================
# MEDIA3 EXOPLAYER
# ================================
# Essential for video/audio playback functionality
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Keep ExoPlayer implementation classes
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }

# Don't warn about Media3 dependencies
-dontwarn androidx.media3.**

# ================================
# COIL IMAGE LOADING
# ================================
# Prevent image loading failures
-keep class coil.** { *; }
-keep interface coil.** { *; }

# Keep Coil Compose integration
-keep class coil.compose.** { *; }

# Don't warn about Coil dependencies
-dontwarn coil.**

# ================================
# KOTLIN COROUTINES
# ================================
# Critical for asynchronous operations
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep coroutines intrinsics
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ================================
# JETPACK COMPOSE
# ================================
# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Keep Compose compiler generated classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep @Composable functions
-keep @androidx.compose.runtime.Composable class *
-keep class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ================================
# ANDROIDX & ANDROID CORE
# ================================
# Keep essential Android framework classes
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep DataStore preferences
-keep class androidx.datastore.** { *; }

# Keep Navigation component classes
-keep class androidx.navigation.** { *; }

# ================================
# GENERAL ANDROID RULES
# ================================
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================
# SERIALIZATION SUPPORT
# ================================
# Keep classes that might be serialized
-keep class com.example.jellyfinandroid.data.** { *; }
-keep class com.example.jellyfinandroid.network.** { *; }

# Keep model classes used for API communication
-keep class * implements java.io.Serializable { *; }

# ================================
# APPLICATION SPECIFIC
# ================================
# Keep Application class
-keep class com.example.jellyfinandroid.JellyfinApplication { *; }

# Keep main activity
-keep class com.example.jellyfinandroid.MainActivity { *; }

# Keep ViewModels
-keep class com.example.jellyfinandroid.ui.viewmodel.** { *; }

# ================================
# WARNINGS SUPPRESSION
# ================================
# Suppress warnings for optional dependencies
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ================================================================================
# TESTING INSTRUCTIONS
# ================================================================================
# To enable minification and test these rules:
# 1. In app/build.gradle.kts, change: isMinifyEnabled = false to isMinifyEnabled = true
# 2. Build release APK: ./gradlew assembleRelease  
# 3. Test critical functionality:
#    - Jellyfin server connection and authentication
#    - API calls for library content (movies, TV shows, music)
#    - Media playback (video and audio)
#    - Image loading and caching
#    - Navigation between screens
#    - Dependency injection (Hilt)
# 4. Check logs for any ClassNotFoundException or NoSuchMethodException
# 5. If crashes occur, add specific -keep rules for affected classes
#
# These rules cover all major dependencies but may need refinement based on
# actual usage patterns and any custom model classes added to the project.
# ================================================================================
# END OF PROGUARD RULES  
# ================================================================================