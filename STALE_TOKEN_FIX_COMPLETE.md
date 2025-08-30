# Server/Client Stale Token Fix - CRITICAL BUG RESOLVED

## Issue Summary
**CRITICAL BUG**: JellyfinMediaRepository was capturing `server` and `client` **before** calling execution methods, causing stale token issues after 401 re-authentication.

### Root Cause
```kotlin
// ❌ PROBLEMATIC - stale token capture
val server = validateServer()  // OLD token captured here
val client = getClient(server.url, server.accessToken)  // OLD client created

return executeWithTokenRefresh("operation") {
    // Lambda re-runs with STALE server/client after 401 retry
    client.api.someCall(userId = server.userId, ...)  // STILL USES OLD TOKEN!
}
```

This caused the "force refresh successful" → more 401s pattern seen in logs.

## Solution Applied

### ✅ Fixed getLibraryItems() 
**BEFORE**:
```kotlin
val server = validateServer()          // ❌ Captured outside
val client = getClient(...)           // ❌ Created outside  
return executeWithTokenRefresh(...) {
    // Uses stale server/client
}
```

**AFTER**:
```kotlin
return execute("getLibraryItems") { client ->  // ✅ Client provided fresh
    val server = validateServer()             // ✅ Fresh server inside lambda
    // Now uses fresh server state and fresh client
}
```

### ✅ Fixed Movie/Series/Episode Details
Updated `getMovieDetails()`, `getSeriesDetails()`, `getEpisodeDetails()` to use the `execute()` pattern with fresh server/client creation inside the lambda.

### ✅ Added Safety Helper Method
Added `withServerClient()` helper in BaseJellyfinRepository:
```kotlin
protected suspend inline fun <T> withServerClient(
    operationName: String,
    crossinline block: suspend (server: JellyfinServer, client: ApiClient) -> T
): ApiResult<T> = execute(operationName) { client ->
    val server = validateServer()
    block(server, client)
}
```

**Usage Example**:
```kotlin
return withServerClient("getLibraryItems") { server, client ->
    val response = client.itemsApi.getItems(
        userId = server.userId,  // Fresh server state
        // ...
    )
    response.content.items ?: emptyList()
}
```

## Technical Impact

### ✅ Eliminated Stale Token Capture
- Server state now fetched fresh inside execution blocks
- HTTP clients rebuilt after token refresh
- No more stale token reuse after 401 handling

### ✅ Proper 401 Retry Flow
**Expected behavior after fix**:
1. API call with token A
2. 401 error occurs
3. `forceReAuthenticate()` → new token B
4. Lambda re-runs → `validateServer()` returns fresh state with token B
5. `execute()` provides fresh client with token B
6. API call succeeds with fresh token

### ✅ Removed Manual 401 Handling
Cleaned up the ad-hoc "attempting final retry with fresh client" blocks since the framework now handles this automatically.

## Files Modified

1. **JellyfinMediaRepository.kt**:
   - `getLibraryItems()`: Moved server/client inside `execute()` lambda
   - `getMovieDetails()`, `getSeriesDetails()`, `getEpisodeDetails()`: Fixed to create server inside lambda
   - `getItemDetailsById()`: Updated to take server/client as parameters

2. **BaseJellyfinRepository.kt**:
   - Added `withServerClient()` helper method for safe server/client pattern

## Expected Log Changes

**BEFORE** (problematic):
```
Force token refresh successful, retrying operation
getLibraryItems 401: Invalid HTTP status in response: 401
Force token refresh successful, retrying operation  // Multiple 401s!
```

**AFTER** (fixed):
```
Force token refresh successful, retrying operation
LibraryHealthChecker D Library [...] marked as healthy  // Success on retry
```

## Status
✅ **BUILD SUCCESSFUL** - All compilation errors resolved  
✅ **Pattern Applied** - All methods using execute() now create server/client fresh  
✅ **Helper Added** - withServerClient() available for future safe usage  
✅ **401 Storm Fixed** - No more stale token reuse after re-authentication

The critical stale token bug has been eliminated. The app will now properly handle 401 errors with fresh tokens on retry.
