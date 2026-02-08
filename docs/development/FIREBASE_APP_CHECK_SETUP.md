# Firebase App Check Debug Token Setup

Firebase App Check helps protect your backend resources from abuse. During development and testing without Google Play Store, you need to register debug tokens.

## ✅ Already Done

Your app now automatically uses debug tokens in debug builds! The code has been added to `JellyfinApplication.kt`.

## 🔍 How to Get Your Debug Token

### Step 1: Build and Run the App (Debug Build)

```bash
gradlew installDebug
```

### Step 2: Find the Debug Token in Logcat

Run the app and filter logcat for "DebugAppCheckProvider":

```bash
adb logcat | findstr "DebugAppCheckProvider"
```

**OR** in Android Studio:
- Open **Logcat** tab
- Filter by: `DebugAppCheckProvider`

You'll see output like this:

```
D/DebugAppCheckProvider: Enter this debug token into the Firebase console:
D/DebugAppCheckProvider: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
```

**Copy that token** (the UUID format string)

### Step 3: Register the Token in Firebase Console

1. Go to your Firebase Console: https://console.firebase.google.com
2. Select your project: **JellyfinAndroid** (or whatever you named it)
3. Click **App Check** in the left sidebar
4. Click **Apps** tab at the top
5. Find your Android app (`com.rpeters.jellyfin`)
6. Click the **3 dots menu** → **Manage debug tokens**
7. Click **Add debug token**
8. Paste your token from logcat
9. Add a description: "James Dev Device" (or your device name)
10. Click **Save**

**Done!** Your debug build can now pass App Check verification.

## 📱 Multiple Devices

Each device/emulator has a **unique debug token**. If you test on multiple devices:

1. Install app on each device
2. Get the debug token from logcat for each device
3. Register each token in Firebase Console with different names:
   - "James Pixel 8"
   - "James Emulator"
   - "James Tablet"
   - etc.

## 🔐 How It Works

### Debug Builds (BuildConfig.DEBUG = true)
```kotlin
// Uses DebugAppCheckProviderFactory
// Prints debug token to logcat
// Token must be registered in Firebase Console
```

### Release Builds (BuildConfig.DEBUG = false)
```kotlin
// Uses PlayIntegrityAppCheckProviderFactory
// Automatically verified via Play Store
// No debug tokens needed
```

## ⚙️ Alternative: Environment Variable Method

If you want to hardcode a debug token for easier testing, you can use this approach:

### Option 1: Add to gradle.properties

```properties
# Add to gradle.properties (alongside your AI API key)
FIREBASE_APPCHECK_DEBUG_TOKEN=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
```

### Option 2: Update build.gradle.kts

```kotlin
defaultConfig {
    // ... existing config ...

    // App Check debug token (optional)
    buildConfigField(
        "String",
        "APPCHECK_DEBUG_TOKEN",
        "\"${project.findProperty("FIREBASE_APPCHECK_DEBUG_TOKEN") ?: ""}\""
    )
}
```

### Option 3: Update JellyfinApplication.kt

```kotlin
private fun initializeAppCheck() {
    val firebaseAppCheck = FirebaseAppCheck.getInstance()

    if (BuildConfig.DEBUG) {
        // If you set a hardcoded debug token
        if (BuildConfig.APPCHECK_DEBUG_TOKEN.isNotEmpty()) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            // Note: You still need to register this token in Firebase Console
        } else {
            // Auto-generate debug token (current behavior)
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
    } else {
        // Production: Use Play Integrity
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
```

**Note:** Even with this method, you still need to register the token in Firebase Console. This just lets you control which token is used instead of auto-generating one per device.

## 🧪 Testing App Check

### Verify It's Working

1. **Build and install** debug APK
2. **Check logcat** for these messages:
   ```
   JellyfinApplication: Firebase App Check initialized with DEBUG provider
   JellyfinApplication: Check logcat for 'DebugAppCheckProvider' to find your debug token
   DebugAppCheckProvider: Enter this debug token into the Firebase console:
   DebugAppCheckProvider: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
   ```
3. **Register the token** in Firebase Console
4. **Use Firebase services** (Crashlytics, Analytics, etc.)
5. **No errors** = App Check is working! ✅

### If You See Errors

**Error:** "App Check token was invalid"
- **Cause:** Debug token not registered in Firebase Console
- **Fix:** Copy token from logcat and add it to Firebase Console

**Error:** "App Check failed to initialize"
- **Cause:** Missing Firebase configuration or google-services.json
- **Fix:** Ensure `google-services.json` is in `app/` directory

**Error:** "Play Integrity API failed" (release builds)
- **Cause:** App not published to Play Store or not signed correctly
- **Fix:** For internal testing, use debug builds or register SHA-256 in Firebase Console

## 📋 Quick Reference

| Build Type | Provider | Token Source | Registration Required |
|------------|----------|--------------|----------------------|
| **Debug** | DebugAppCheckProviderFactory | Auto-generated per device | ✅ Yes (Firebase Console) |
| **Release** | PlayIntegrityAppCheckProviderFactory | Play Integrity API | ❌ No (automatic) |

## 🔍 Logcat Filters

Useful logcat filters for debugging:

```bash
# See App Check initialization
adb logcat | findstr "FirebaseAppCheck"

# See debug token
adb logcat | findstr "DebugAppCheckProvider"

# See all Firebase logs
adb logcat | findstr "Firebase"

# See your app's logs
adb logcat | findstr "JellyfinApplication"
```

## 🚀 Summary (TL;DR)

1. ✅ **Code is already added** - App Check initialized in `JellyfinApplication.kt`
2. 🏃 **Run the app** (debug build)
3. 📋 **Copy the token** from logcat (filter: `DebugAppCheckProvider`)
4. 🌐 **Register in Firebase Console** → App Check → Manage debug tokens
5. ✅ **Done!** Your app can now use Firebase services without Play Store

---

**Need Help?**
- Firebase App Check Docs: https://firebase.google.com/docs/app-check
- Firebase Console: https://console.firebase.google.com
