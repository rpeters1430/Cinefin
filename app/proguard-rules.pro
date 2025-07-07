# ================================================================================
# Jellyfin Android Client - ProGuard/R8 Rules (Optimized)
# ================================================================================
# Modern R8-optimized rules for the Jellyfin Android client
# These rules balance code shrinking with functionality preservation
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
# KOTLIN SERIALIZATION (Modern Rules)
# ================================
# Critical for JSON parsing - prevents crashes during API responses
-keepattributes *Annotation*, InnerClasses

# Keep all serializable classes and their serializers
-keep,allowobfuscation @kotlinx.serialization.Serializable class * {
    static **$Companion Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializers
-keep,allowobfuscation class * implements kotlinx.serialization.KSerializer
-keep,allowobfuscation class **$$serializer { *; }

# Keep Kotlin serialization internals
-keep class kotlinx.serialization.internal.** { *; }
-keep class kotlinx.serialization.json.** { *; }

# R8 full mode strips generic signatures from return types if not kept
-if class **$$serializer
-keep class <1>

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
-keep,allowobfuscation,allowshrinking class retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# ================================
# JELLYFIN SDK
# ================================
# Preserve Jellyfin SDK classes and methods to maintain API functionality
-keep,allowobfuscation class org.jellyfin.sdk.model.** { *; }
-keep,allowobfuscation class org.jellyfin.sdk.api.** { *; }
-keep interface org.jellyfin.sdk.** { *; }

# Keep client and authentication classes
-keep class org.jellyfin.sdk.Jellyfin { *; }
-keep class org.jellyfin.sdk.discovery.** { *; }

# Don't warn about Jellyfin SDK dependencies
-dontwarn org.jellyfin.sdk.**

# ================================
# SLF4J LOGGING
# ================================
# Keep SLF4J classes for Jellyfin SDK logging
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**

# ================================
# HILT DEPENDENCY INJECTION
# ================================
# Keep Hilt generated classes
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class **_HiltComponents$* { *; }

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

# Keep Hilt ViewModels
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
}

# ================================
# ANDROIDX MEDIA3 EXOPLAYER
# ================================
# Essential for video/audio playback functionality
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }

# Keep ExoPlayer format support
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }

# Don't warn about Media3 dependencies
-dontwarn androidx.media3.**

# ================================
# COIL IMAGE LOADING
# ================================
# Prevent image loading failures
-keep class coil.** { *; }
-keep class coil.compose.** { *; }
-keep class coil.transform.** { *; }
-dontwarn coil.**

# ================================
# KOTLIN COROUTINES
# ================================
# Critical for asynchronous operations
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Keep coroutines intrinsics
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ================================
# JETPACK COMPOSE (Optimized)
# ================================
# Keep Compose compiler generated classes
-keep class androidx.compose.runtime.** { *; }

# Keep CompositionLocal providers
-keep class androidx.compose.runtime.CompositionLocalKt { *; }

# Keep @Composable functions from being inlined
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Material 3 theming
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material3.adaptive.** { *; }

# Keep Compose UI essentials
-keep class androidx.compose.ui.graphics.** { *; }
-keep class androidx.compose.ui.text.** { *; }

# ================================
# ANDROIDX ESSENTIALS (Selective)
# ================================
# Keep only essential androidx classes, not everything
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.datastore.** { *; }

# Keep ViewModels and LiveData
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# ================================
# ANDROID FRAMEWORK RULES
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
    public void set*(***);
    public *** get*();
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

# Keep R class and its inner classes
-keep class **.R$* { *; }

# ================================
# APPLICATION SPECIFIC
# ================================
# Keep Application class
-keep class com.example.jellyfinandroid.JellyfinApplication { *; }

# Keep main activity
-keep class com.example.jellyfinandroid.MainActivity { *; }

# Keep ViewModels
-keep class com.example.jellyfinandroid.**.viewmodel.** { *; }

# Keep data classes used for API communication
-keep,allowobfuscation class com.example.jellyfinandroid.data.** { *; }
-keep,allowobfuscation class com.example.jellyfinandroid.network.** { *; }

# Keep model classes
-keep,allowobfuscation class com.example.jellyfinandroid.model.** { *; }

# ================================
# MODERN ANDROID RULES
# ================================
# Keep annotation default values
-keepattributes AnnotationDefault

# Keep generic signatures (needed for reflection)
-keepattributes Signature

# Keep classes that use reflection
-keepclassmembers class * {
    @kotlin.jvm.JvmField *;
}

# Keep companion objects
-keepclassmembers class * {
    public static **$Companion Companion;
}

# ================================
# WARNINGS SUPPRESSION
# ================================
# Suppress warnings for missing classes that are optional
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Suppress warnings for newer Java APIs
-dontwarn java.lang.invoke.StringConcatFactory

# ================================
# OPTIMIZATION SETTINGS
# ================================
# Allow aggressive optimization
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Don't preverify (not needed for Android)
-dontpreverify

# ================================================================================
# TESTING INSTRUCTIONS
# ================================================================================
# To test these optimized rules:
# 1. Enable minification: isMinifyEnabled = true in build.gradle.kts
# 2. Build release APK: ./gradlew assembleRelease
# 3. Test critical functionality:
#    - Server connection and authentication
#    - API calls and data parsing
#    - Media playback
#    - Image loading
#    - Navigation
#    - Dependency injection
# 4. Monitor logs for ClassNotFoundException or NoSuchMethodException
# 5. Use APK analyzer to verify size reduction
#
# These rules are optimized for R8 and should provide better code shrinking
# while maintaining all required functionality.
# ================================================================================