# Offline Startup Handling

This document describes how the Jellyfin Android app handles startup when there's no internet connection.

## Problem

Users reported that the app would crash or behave unexpectedly when:
1. Device has no internet connection when app starts
2. Internet connection is lost during initial app load
3. Network becomes available after app has already started

## Solution

We've implemented comprehensive offline startup handling with the following features:

### 1. Offline Indicator Banner

**Component**: `ui/components/OfflineIndicator.kt`

A persistent banner appears at the top of all screens when the device is offline:
- Material 3 error colors (red background)
- WiFi off icon
- Clear message: "No internet connection. Some features may be unavailable."
- Smooth animated appearance/disappearance

### 2. App-Level Network Monitoring

**Location**: `ui/JellyfinApp.kt`

The root app composable now monitors network state:
- Injects `ConnectivityChecker` via Hilt EntryPoint
- Observes real-time network connectivity using `Flow`
- Shows/hides offline banner based on network state
- Logs network state changes for debugging

```kotlin
val isOnline by connectivityChecker.observeNetworkConnectivity()
    .collectAsStateWithLifecycle(initialValue = connectivityChecker.isOnline())

OfflineIndicatorBanner(isVisible = !isOnline)
```

### 3. Network-Aware Auto-Login

**Location**: `ui/viewmodel/ServerConnectionViewModel.kt`

Auto-login now checks network connectivity:

**Offline at Startup:**
1. Check if device is online before auto-login
2. If offline, skip auto-login and show error message
3. Start observing network connectivity
4. When network becomes available, automatically retry login

**Implementation:**
```kotlin
if (!connectivityChecker.isOnline()) {
    // Skip auto-login and show error
    _connectionState.value = _connectionState.value.copy(
        errorMessage = "No internet connection. Please check your network and try again.",
    )
    
    // Observe network and retry when online
    connectivityChecker.observeNetworkConnectivity()
        .collect { isOnline ->
            if (isOnline && shouldRetryAutoLogin()) {
                // Retry auto-login
            }
        }
}
```

## User Experience

### Scenario 1: Start App Offline

1. **User Action**: Opens app with airplane mode on
2. **App Behavior**:
   - App loads successfully (no crash)
   - Red banner appears: "No internet connection..."
   - Auto-login is skipped
   - Login screen shows with saved credentials pre-filled
   - Error message: "No internet connection. Please check your network..."

3. **User Action**: Disables airplane mode
4. **App Behavior**:
   - Banner disappears
   - Auto-login automatically retries
   - User is logged in and navigated to home screen

### Scenario 2: Lose Connection After Start

1. **User Action**: Opens app while online
2. **App Behavior**:
   - App loads normally
   - Auto-login succeeds
   - User is at home screen

3. **User Action**: Enables airplane mode
4. **App Behavior**:
   - Red banner appears at top
   - User can still browse cached content
   - Attempts to load new content show appropriate errors

5. **User Action**: Disables airplane mode
6. **App Behavior**:
   - Banner disappears
   - App functionality fully restored

### Scenario 3: Unstable Network

1. **User Action**: Uses app on weak cellular connection
2. **App Behavior**:
   - Banner appears/disappears as connection fluctuates
   - User is aware of connectivity issues
   - No silent failures or crashes

## Technical Implementation

### Network State Detection

Uses `ConnectivityChecker` which provides:
- `isOnline()`: Synchronous check for current state
- `observeNetworkConnectivity()`: Flow of connectivity changes
- `getNetworkType()`: WiFi, Cellular, Ethernet, etc.

Checks both:
- `NET_CAPABILITY_INTERNET`: Network has internet connectivity
- `NET_CAPABILITY_VALIDATED`: Internet is validated and working

### Auto-Retry Logic

The ViewModel maintains a Flow observer that:
1. Only activates when offline at startup
2. Checks multiple conditions before retry:
   - Network is now online
   - User has "Remember Login" enabled
   - Not already connected
   - Not currently connecting
   - Has saved password available
3. Attempts auto-login exactly once per network restore
4. Cancels itself after successful login

### State Management

Connection state includes:
- `isConnected`: Successfully authenticated
- `isConnecting`: Currently attempting connection
- `errorMessage`: User-facing error description
- `rememberLogin`: User preference
- `hasSavedPassword`: Credentials available
- `savedServerUrl`: Server to connect to
- `savedUsername`: Username to use

## Testing

### Manual Testing

1. **Offline Start**:
   - Enable airplane mode
   - Open app
   - Verify banner appears and auto-login is skipped
   - Disable airplane mode
   - Verify banner disappears and auto-login succeeds

2. **Network Switch**:
   - Open app on WiFi
   - Switch to cellular mid-use
   - Verify app continues working
   - Verify banner doesn't appear (still online)

3. **Connection Loss**:
   - Open app while online
   - Enable airplane mode
   - Verify banner appears
   - Verify error messages are clear
   - Disable airplane mode
   - Verify functionality restores

### Automated Testing

**Location**: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelOfflineTest.kt`

Tests cover:
- Auto-login skipped when offline
- Auto-retry triggered when network available
- Normal auto-login when online
- Connection state updates correctly

## Future Enhancements

Potential improvements:

1. **Offline Mode**: Full offline content browsing
2. **Sync Queue**: Queue requests while offline, sync when online
3. **Smart Retry**: Exponential backoff for retry attempts
4. **Network Quality**: Adapt behavior based on connection quality
5. **User Control**: Let users disable auto-retry if desired

## Related Files

### Core Implementation
- `app/src/main/java/com/rpeters/jellyfin/ui/components/OfflineIndicator.kt` - Banner component
- `app/src/main/java/com/rpeters/jellyfin/ui/JellyfinApp.kt` - App-level monitoring
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModel.kt` - Auto-login logic
- `app/src/main/java/com/rpeters/jellyfin/network/ConnectivityChecker.kt` - Network detection

### Testing
- `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelOfflineTest.kt` - Unit tests
- `app/src/test/java/com/rpeters/jellyfin/network/ConnectivityCheckerTest.kt` - Network tests

### Previous Work
- `docs/TLS_TROUBLESHOOTING.md` - TLS connection issues
- `docs/TLS_FIX_SUMMARY.md` - TLS fix implementation details
- `app/src/main/java/com/rpeters/jellyfin/network/NetworkStateInterceptor.kt` - Request-level network checking
