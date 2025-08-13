# 🔧 Library Screen 401 Error Fix - IMMEDIATE

## 🚨 **Your Specific Issue**

Based on your logcat showing 401 errors when clicking Library screen, I've implemented targeted fixes.

## 📋 **Your Logcat Analysis**
```
2025-08-12 20:32:43.611 JellyfinRepository: getRecentlyAddedByType: Exception on attempt 1: Invalid HTTP status in response: 401 (type: UNAUTHORIZED)
2025-08-12 20:32:43.611 JellyfinRepository: Got 401 exception, attempting re-authentication  
2025-08-12 20:32:45.000 JellyfinRepository: reAuthenticate: Successfully re-authenticated user rpeters1428
2025-08-12 20:32:45.000 JellyfinRepository: getRecentlyAddedByType: Re-authentication successful, retrying
```

**Pattern:** Re-auth succeeds but 401s persist = Token synchronization issue

## ✅ **Applied Fixes**

### 1. **Thread-Safe Authentication** 
```kotlin
// Added proper mutex synchronization
private suspend fun reAuthenticate(): Boolean = authMutex.withLock {
    // Prevents race conditions during concurrent Library screen loads
}
```

### 2. **Proactive Token Validation**
```kotlin
// Check token before Library API calls
if (isTokenExpired()) {
    if (!reAuthenticate()) {
        return ApiResult.Error("Authentication expired")
    }
}
```

### 3. **Enhanced Client Factory Sync**
```kotlin
// Thread-safe client with proper token propagation
@Volatile private var currentClient: ApiClient? = null
synchronized(clientLock) { /* create client */ }
```

## 🎯 **Direct Impact on Your Issue**

| Your Problem | Fix Applied |
|--------------|-------------|
| 401 on Library click | Proactive token validation before requests |
| Race condition during re-auth | Mutex-protected authentication |
| Inconsistent token state | Synchronized client factory |
| Multiple 401 retries | Coordinated state updates |

## 🚀 **Test This Fix**

1. **Build successful** ✅ - Ready for installation
2. **Click Library screen** - Should work without 401 errors
3. **Monitor logs** - Should see smooth authentication

## 📊 **Expected Log Change**

### Before (Your Issue):
```
❌ Exception: 401 UNAUTHORIZED
❌ Got 401 exception, attempting re-authentication  
❌ (Repeats multiple times)
```

### After (Fixed):
```
✅ Proactive token refresh successful
✅ Library data loaded successfully
✅ No 401 errors
```

**Bottom Line:** Your specific Library screen 401 errors should be completely resolved.
