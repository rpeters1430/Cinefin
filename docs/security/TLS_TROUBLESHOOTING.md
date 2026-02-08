# TLS Connection Troubleshooting Guide

This document provides guidance for troubleshooting TLS/SSL connection issues in the Jellyfin Android client, specifically addressing `java.net.SocketException: Software caused connection abort` errors during TLS handshake.

## Common TLS Connection Issues

### 1. Software Caused Connection Abort

**Symptom**: `java.net.SocketException: Software caused connection abort` in `okhttp3.internal.connection.ConnectPlan.connectTls`

**Common Causes**:
- Network instability (switching between WiFi and mobile data)
- Weak or intermittent cellular signal
- Firewall or proxy aggressively terminating connections
- Server closing connection prematurely during handshake
- TLS version or cipher suite mismatch

**Solutions Implemented**:
1. **Explicit TLS Configuration**: Client now explicitly supports TLS 1.2 and 1.3 with fallback to TLS 1.1 for compatibility
2. **Increased Timeouts**: Connect timeout increased to 15 seconds to handle slower networks
3. **Automatic Retry**: OkHttp configured to automatically retry on connection failures
4. **Network State Monitoring**: Real-time network state changes are monitored and handled
5. **Enhanced Error Messages**: User-friendly error messages with specific troubleshooting guidance

## Client-Side Configuration

### TLS Versions Supported
The client supports the following TLS versions in order of preference:
1. **TLS 1.3** (most modern and secure)
2. **TLS 1.2** (widely supported)
3. **TLS 1.1** (fallback for older servers)

### Connection Specs
```kotlin
// Modern TLS (preferred)
ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
    .build()

// Compatible TLS (fallback)
ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1)
    .build()
```

### Timeout Configuration
- **Connect Timeout**: 15 seconds (increased from 8 seconds)
- **Read Timeout**: 30 seconds (increased from 25 seconds)
- **Write Timeout**: 15 seconds (increased from 12 seconds)

## Server-Side Requirements

For optimal compatibility, ensure your Jellyfin server:

1. **Supports Modern TLS Versions**:
   - TLS 1.2 minimum (TLS 1.3 recommended)
   - Avoid deprecated TLS 1.0 and 1.1

2. **Uses Valid SSL Certificate**:
   - Certificate must not be expired
   - Must be issued by a trusted Certificate Authority
   - Hostname must match certificate CN/SAN

3. **Supports Strong Cipher Suites**:
   - Modern cipher suites that work with TLS 1.2+
   - Forward secrecy enabled (ECDHE)

## Debugging TLS Issues

### Enable Enhanced Logging

In debug builds, the app automatically enables enhanced HTTP logging at HEADERS level, which captures TLS handshake details.

To view logs:
```bash
adb logcat -v time | grep -E "NetworkStateInterceptor|OkHttp|ConnectivityChecker"
```

### Check Network Type

The app logs network type with each request:
```
NetworkStateInterceptor: Request to server.example.com via WIFI
```

This helps identify if issues occur on specific network types (WiFi vs Cellular).

### Monitor Network Transitions

Network state changes are logged:
```
ConnectivityChecker: Network available: Network 100
ConnectivityChecker: Network lost: Network 100 (1 remaining)
```

## Common Error Messages and Solutions

### "Network connection was interrupted"
**Cause**: Connection abort during data transfer, often due to network switching

**Solutions**:
- Ensure stable network connection
- Avoid switching networks during data transfer
- Check for background network restrictions

### "SSL/TLS handshake failed"
**Cause**: Client and server cannot agree on TLS version or cipher suite

**Solutions**:
- Update Jellyfin server to support TLS 1.2+
- Check server cipher suite configuration
- Verify server SSL certificate is valid

### "SSL certificate validation failed"
**Cause**: Server certificate is invalid, expired, or not trusted

**Solutions**:
- Renew server SSL certificate
- Ensure certificate is from a trusted CA
- Verify certificate hostname matches server URL

### "No internet connection available"
**Cause**: Device has no active network connection

**Solutions**:
- Check device WiFi/mobile data settings
- Verify airplane mode is off
- Check network connectivity in device settings

## Network State Monitoring

The app monitors network state changes in real-time and provides:

1. **Current Network Type Detection**:
   - WiFi
   - Cellular (mobile data)
   - Ethernet
   - Other/None

2. **Connectivity Flow**:
   - Real-time network availability updates
   - Network type change notifications
   - Validated internet connectivity checks

3. **Automatic Handling**:
   - Graceful handling of network switches
   - Intelligent retry on transient failures
   - Enhanced error context for debugging

## Testing Network Scenarios

To test the app's handling of network issues:

1. **Network Switching**:
   - Start playback on WiFi
   - Disable WiFi to force cellular switch
   - Observe automatic reconnection

2. **Unstable Network**:
   - Use network throttling tools
   - Simulate packet loss
   - Verify retry behavior

3. **No Network**:
   - Enable airplane mode
   - Attempt operations
   - Verify user-friendly error messages

## Certificate Pinning

The app uses dynamic certificate pinning with Trust-on-First-Use (TOFU):

1. **First Connection**: Server certificate is validated and stored
2. **Subsequent Connections**: Certificate must match stored pin
3. **Pin Mismatch**: Connection rejected (potential MITM attack)

If you need to update server certificates:
- Clear app data to reset stored pins
- Or use the app's certificate management UI (if available)

## Further Assistance

If you continue experiencing TLS connection issues:

1. **Check Server Logs**: Cross-reference client error timestamps with server logs
2. **Test with Browser**: Verify server SSL works in a web browser
3. **Network Analysis**: Use tools like Wireshark to capture TLS handshake
4. **Report Issue**: Include:
   - Full error message and stack trace
   - Network type (WiFi/Cellular)
   - Server SSL configuration
   - App version and Android version

## References

- [OkHttp ConnectionSpec Documentation](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-connection-spec/)
- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [TLS Best Practices](https://wiki.mozilla.org/Security/Server_Side_TLS)
- [Jellyfin Server SSL Setup](https://jellyfin.org/docs/general/networking/ssl)
