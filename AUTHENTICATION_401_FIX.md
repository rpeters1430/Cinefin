# Authentication 401 Error Fix - Jellyfin Android App

## 🔧 **Problem Identified**

**Issue:** Persistent 401 authentication errors even after successful re-authentication attempts.

**Root Cause:** Token propagation failure due to variable capture in retry closures. The retry mechanism was successfully obtaining new tokens but continuing to use expired tokens from captured variables.

## 🐛 **Technical Analysis**

### The Bug Pattern
```kotlin
// ❌ PROBLEMATIC CODE: Captures variables outside retry closure
suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
    val server = _currentServer.value  // ⬅️ Captured at closure creation time
    val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
    
    return executeWithAuthRetry("getUserLibraries") {
        val client = getClient(server.url, server.accessToken)  // ⬅️ Uses old token!
        // ... rest of operation
    }
}
```

### What Was Happening
1. ✅ Initial authentication succeeds
2. ❌ Token expires (50-minute timeout)
3. ✅ `executeWithAuthRetry` detects 401 error
4. ✅ `reAuthenticate()` successfully gets new token
5. ✅ `_currentServer.value` updated with fresh token
6. ✅ `clientFactory.invalidateClient()` called
7. ❌ **BUT**: Retry operation still uses captured `server` variable with expired token
8. ❌ 401 error repeats indefinitely

### Log Evidence
```
JellyfinRepository: reAuthenticate: Successfully re-authenticated user rpeters1428
JellyfinRepository: getUserLibraries: Re-authentication successful, retrying
JellyfinRepository: InvalidStatusException: status code = 401, message = Invalid HTTP status in response: 401
```

## ✅ **Fix Implementation**

### Before (Broken)
```kotlin
suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
    val server = _currentServer.value  // Captured outside
    val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()

    return executeWithAuthRetry("getUserLibraries") {
        val client = getClient(server.url, server.accessToken)  // Uses old token
        // ...
    }
}
```

### After (Fixed)
```kotlin
suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
    val server = _currentServer.value
    if (server?.accessToken == null || server.userId == null) {
        return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
    }

    return executeWithAuthRetry("getUserLibraries") {
        // ✅ FIX: Always get current server state inside the retry closure
        val currentServer = _currentServer.value 
            ?: return@executeWithAuthRetry ApiResult.Error("Server not available", errorType = ErrorType.AUTHENTICATION)
        val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
            ?: return@executeWithAuthRetry ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
            
        val client = getClient(currentServer.url, currentServer.accessToken)  // Uses fresh token!
        // ...
    }
}
```

## 🔨 **Files Modified**

### 1. **JellyfinRepository.kt** - `getUserLibraries()` method
- **Lines:** ~346-372
- **Change:** Moved server state access inside retry closure
- **Impact:** Critical authentication retry method now uses fresh tokens

### 2. **JellyfinRepository.kt** - `getRecentlyAdded()` method  
- **Lines:** ~418-440
- **Change:** Moved server state access inside retry closure
- **Impact:** Recently added content loading now uses fresh tokens

## 🧪 **Validation Steps**

### Testing the Fix
1. **Build Verification:** ✅ `./gradlew assembleDebug` completes successfully
2. **Expected Behavior:**
   - Initial 401 error triggers re-authentication
   - Re-authentication obtains fresh token
   - Retry operations use the fresh token from `_currentServer.value`
   - Operations succeed without additional 401 errors
3. **Log Verification:** Should see successful operations after re-authentication

### Manual Testing
```bash
# Install and test the app
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.jellyfinandroid/.MainActivity
# Monitor logs: adb logcat | grep -E "(JellyfinRepository|401)"
```

## 🔍 **Technical Details**

### Variable Capture in Kotlin Closures
The issue demonstrates a subtle but critical Kotlin closure behavior:
- Variables captured in lambda/closure creation are **"frozen"** at that moment
- Even if the original variable reference changes, the closure retains the old value
- This is especially problematic in retry scenarios where state must be refreshed

### Authentication Flow Enhancement
```
1. API Call → 401 Error
2. executeWithAuthRetry() → reAuthenticate()
3. Fresh token → _currentServer.value updated
4. clientFactory.invalidateClient() → clears cached client
5. Retry closure → MUST use _currentServer.value (not captured variables)
6. getClient() → creates new client with fresh token
7. API Call → SUCCESS
```

## 🎯 **Impact**

### Before Fix
- ❌ Persistent 401 errors requiring app restart
- ❌ Poor user experience with authentication failures
- ❌ Infinite retry loops with expired tokens

### After Fix
- ✅ Seamless token refresh without user intervention
- ✅ Robust authentication that survives token expiration
- ✅ Improved app reliability and user experience

## 📋 **Additional Considerations**

### Future Prevention
1. **Code Review:** Always check variable capture in retry mechanisms
2. **Testing:** Include token expiration scenarios in testing
3. **Patterns:** Consider helper methods that always use current state

### Pattern to Follow
```kotlin
// ✅ CORRECT PATTERN: Access current state inside retry closure
return executeWithAuthRetry("operation") {
    val currentServer = _currentServer.value ?: return@executeWithAuthRetry ApiResult.Error(...)
    val client = getClient(currentServer.url, currentServer.accessToken)
    // ... operation
}
```

### Pattern to Avoid  
```kotlin
// ❌ AVOID: Capturing state outside retry closure
val server = _currentServer.value
return executeWithAuthRetry("operation") {
    val client = getClient(server.url, server.accessToken)  // Uses stale token!
    // ... operation
}
```

---

**Status:** ✅ **FIXED** - Authentication token propagation now works correctly  
**Build:** ✅ **VERIFIED** - Compiles successfully  
**Testing:** 🔄 **READY** - Ready for user testing
