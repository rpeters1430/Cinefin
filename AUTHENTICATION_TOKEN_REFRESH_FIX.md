# Authentication Token Refresh Fix

This document describes the changes made to fix the authentication token refresh issue in the Jellyfin Android app.

## Problem

The app was experiencing a 401 storm where retries would continue to use stale tokens, causing repeated authentication failures. The root causes were:

1. Server/client objects were created outside the retry block, capturing stale tokens
2. Tokens were being passed explicitly through call sites rather than being fetched at request time
3. No single-flight reauth mechanism to prevent concurrent authentication attempts

## Solution

### 1. Client Factory Changes

Modified `JellyfinClientFactory` to:
- Remove the `accessToken` parameter from `getClient()` method
- Implement a token interceptor that fetches the current token at request time
- Update `invalidateClient()` to properly handle server URL invalidation

### 2. Repository Changes

Updated `BaseJellyfinRepository` to:
- Fix `executeWithClient()` to use the new token-less `getClient()` method
- Enhance `executeWithTokenRefresh()` with better single-flight reauth handling
- Improve mutex-based synchronization for token refresh

### 3. Dependency Injection

Updated `NetworkModule` to:
- Remove circular dependency between `OptimizedClientFactory` and `JellyfinAuthRepository`

## Key Benefits

1. **Fresh Tokens**: Tokens are now fetched at request time rather than being cached
2. **Single-Flight Reauth**: Only one coroutine performs re-authentication at a time
3. **No Stale Tokens**: Retries now use fresh tokens after re-authentication
4. **Eliminated Circular Dependencies**: Improved dependency injection setup

## Files Modified

- `app/src/main/java/com/rpeters/jellyfin/di/NetworkModule.kt`
- `app/src/main/java/com/rpeters/jellyfin/di/OptimizedClientFactory.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/repository/common/BaseJellyfinRepository.kt`

## Testing

The changes have been verified to build successfully. The app should now properly handle token refresh scenarios without 401 storms.