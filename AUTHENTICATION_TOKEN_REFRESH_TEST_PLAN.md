# Authentication Token Refresh Test Plan

This document outlines the test plan to verify that the authentication token refresh fixes are working correctly.

## Test Scenarios

### 1. Normal Token Refresh
- **Setup**: Log in to a Jellyfin server
- **Action**: Wait for token to expire (or simulate expiration)
- **Expected Result**: App should automatically refresh the token and continue working without user intervention

### 2. Concurrent Requests During Token Refresh
- **Setup**: Log in to a Jellyfin server
- **Action**: Simulate token expiration and trigger multiple concurrent API requests
- **Expected Result**: Only one re-authentication should occur, and all requests should eventually succeed with the new token

### 3. Failed Token Refresh
- **Setup**: Log in to a Jellyfin server
- **Action**: Simulate a failed token refresh (e.g., network error, invalid credentials)
- **Expected Result**: App should handle the error gracefully and prompt user to re-authenticate

### 4. Multiple 401 Errors
- **Setup**: Log in to a Jellyfin server
- **Action**: Simulate multiple consecutive 401 errors
- **Expected Result**: App should not enter a 401 storm; instead, it should refresh the token once and retry the operation

### 5. Rapid Succession Requests
- **Setup**: Log in to a Jellyfin server
- **Action**: Trigger multiple API requests in rapid succession
- **Expected Result**: All requests should use the current token and succeed

## Test Implementation

### Manual Testing
1. Use Android Studio to run the app in debug mode
2. Monitor logcat for authentication-related messages
3. Use network monitoring tools to observe HTTP requests
4. Verify that tokens are being refreshed appropriately

### Automated Testing
1. Create unit tests for the `executeWithTokenRefresh` method
2. Mock the `JellyfinAuthRepository` to simulate various token states
3. Verify that the single-flight mechanism works correctly
4. Test error handling scenarios

## Verification Metrics

1. **No 401 Storms**: Verify that repeated 401 errors don't occur after token refresh
2. **Single Re-authentication**: Confirm that only one re-authentication occurs even with concurrent requests
3. **Token Freshness**: Ensure that requests use the most recent token
4. **Error Handling**: Verify graceful handling of failed token refreshes
5. **Performance**: Confirm that token refresh doesn't significantly impact app performance

## Expected Log Messages

Look for these log messages in logcat to verify correct behavior:

- "HTTP 401 detected, attempting force token refresh"
- "Force token refresh successful, retrying operation"
- "Invalidated client for server: [server_url]"
- "Token already refreshed by another thread, retrying operation"

## Edge Cases to Test

1. **App Restart**: Verify that tokens are properly loaded after app restart
2. **Network Fluctuations**: Test behavior with intermittent network connectivity
3. **Server Restart**: Test behavior when the Jellyfin server is restarted
4. **Long Periods of Inactivity**: Test token refresh after the app has been idle for a long time