# Authentication and Library Data Fetching Test Plan

This document outlines the test plan to verify that authentication, re-authentication, and library data fetching work correctly with our fixes.

## Test Scenarios

### 1. Authentication Flow
- **Setup**: Fresh app installation
- **Action**: Complete user authentication flow
- **Expected Result**: User is successfully authenticated and can access library data

### 2. Token Refresh on Expiration
- **Setup**: Authenticated user with expired token
- **Action**: Attempt to fetch library data
- **Expected Result**: Token is automatically refreshed and library data is fetched successfully

### 3. Concurrent Requests During Token Refresh
- **Setup**: Multiple concurrent API requests when token is expired
- **Action**: Trigger simultaneous library data requests
- **Expected Result**: Only one re-authentication occurs, all requests succeed with refreshed token

### 4. Failed Token Refresh
- **Setup**: Authenticated user with invalid credentials
- **Action**: Attempt to refresh token
- **Expected Result**: Appropriate error handling and user notification

### 5. Library Data Fetching
- **Setup**: Authenticated user
- **Action**: Fetch various types of library data (movies, TV shows, music)
- **Expected Result**: Correct data is fetched and displayed

### 6. Offline Library Access
- **Setup**: Previously fetched library data
- **Action**: Access library while offline
- **Expected Result**: Cached data is displayed

## Test Implementation

### Unit Tests
1. **AuthTokenRefreshTest**
   - Test single-flight re-authentication
   - Test concurrent request handling
   - Test token refresh failure handling
   - Test fresh token usage

2. **MediaRepositoryTokenRefreshTest**
   - Test getUserLibraries with token refresh
   - Test getLibraryItems with token refresh
   - Test getRecentlyAdded with token refresh

### Integration Tests
1. **AuthenticationFlowTest**
   - Test complete authentication flow
   - Test logout functionality
   - Test session persistence

2. **LibraryDataFetchTest**
   - Test fetching different library types
   - Test filtering and sorting
   - Test pagination

### Manual Testing
1. Use Android Studio to run the app in debug mode
2. Monitor logcat for authentication-related messages
3. Use network monitoring tools to observe HTTP requests
4. Verify that tokens are being refreshed appropriately

## Verification Metrics

1. **Authentication Success Rate**: 100% of valid authentication attempts should succeed
2. **Token Refresh Efficiency**: Only one re-authentication should occur for concurrent requests
3. **Data Accuracy**: Fetched library data should match server data
4. **Error Handling**: Appropriate error messages for failed operations
5. **Performance**: Token refresh should not significantly impact user experience

## Expected Log Messages

Look for these log messages in logcat to verify correct behavior:

- "HTTP 401 detected, attempting force token refresh"
- "Force token refresh successful, retrying operation"
- "Invalidated client for server: [server_url]"
- "Token already refreshed by another thread, retrying operation"

## Edge Cases to Test

1. **Network Interruptions**: Test behavior with intermittent network connectivity
2. **Server Restart**: Test behavior when the Jellyfin server is restarted
3. **Long Periods of Inactivity**: Test token refresh after the app has been idle
4. **Multiple Server Connections**: Test switching between different Jellyfin servers
5. **Large Library Collections**: Test performance with extensive media libraries