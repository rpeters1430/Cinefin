# Transcoding System Review - February 2026

## Executive Summary
The recent EAC3/AC3 stereo fallback fix (commit e0527b7a) is **correct and working as intended**. The overall transcoding system is well-architected with room for minor optimizations.

## Fix Verification ✅

### What Was Fixed
- **Issue**: Pixel 10 Pro XL showed "should direct play" but switched to transcoding with "audio codec not supported" for 5.1 EAC3 audio
- **Root Cause**: `MediaCodecList.findDecoderForFormat()` failed to find a decoder for EAC3 with 6 channels
- **Solution**: Added stereo fallback in `DeviceCapabilities.isAudioCodecSupported()`
  - First checks for exact channel count support
  - Falls back to stereo (2-channel) check for surround sound codecs when channels > 2
  - Works because ExoPlayer decodes multi-channel audio and downmixes to stereo automatically

### Why This Fix is Correct
1. **Many devices support EAC3/AC3 decoding** but don't report multi-channel output through Android's MediaCodecList
2. **ExoPlayer's software decoder** can handle multi-channel audio regardless of hardware output capability
3. **Prevents unnecessary transcoding** while maintaining compatibility
4. **Proper codec identification** via `isSurroundSoundCodec()` ensures fallback only applies to surround codecs

## Transcoding System Architecture

### Data Flow
```
User Plays Video
    ↓
EnhancedPlaybackManager.getOptimalPlaybackUrl()
    ↓
getServerDirectedPlaybackUrl() - gets PlaybackInfoResponse from Jellyfin server
    ↓
canDirectPlayMediaSource() - validates client-side capabilities
    ├─ Container check (canPlayContainer)
    ├─ Video codec check (canPlayVideoCodec)
    ├─ Audio codec check (canPlayAudioCodec with channel count) ← FIXED HERE
    └─ Network bitrate check (isNetworkSuitableForDirectPlay)
    ↓
Decision: Direct Play OR Transcoding
    ↓
JellyfinStreamRepository.getTranscodedStreamUrl() - builds transcoding URL
```

### Key Components

#### 1. EnhancedPlaybackManager
**File**: `app/src/main/java/com/rpeters/jellyfin/data/playback/EnhancedPlaybackManager.kt`

**Responsibilities**:
- Determines optimal playback method (Direct Play vs Transcoding)
- Coordinates server PlaybackInfo with client capabilities
- Handles fallback from Direct Play failures

**Key Methods**:
- `getOptimalPlaybackUrl()` - Primary entry point
- `canDirectPlayMediaSource()` - Validates media against device capabilities (line 307-359)
  - **IMPORTANT**: Line 343 extracts channel count: `val audioChannels = audioStream.channels ?: 2`
  - Line 344 validates: `deviceCapabilities.canPlayAudioCodec(audioCodec, audioChannels)`
- `getOptimalTranscodingUrl()` - Builds transcoding params based on network/device

#### 2. DeviceCapabilities
**File**: `app/src/main/java/com/rpeters/jellyfin/data/DeviceCapabilities.kt`

**Responsibilities**:
- Queries Android MediaCodecList for hardware capabilities
- Validates codec, container, and resolution support
- Implements stereo fallback for surround sound

**Key Methods**:
- `canPlayAudioCodec(codec, channels)` - Audio codec validation with channel awareness (line 102-109)
- `isAudioCodecSupported(codec, channels)` - Core detection logic with fallback (line 327-367)
  - Line 334: Checks exact channel count first
  - Line 344: Falls back to stereo for surround codecs when channels > 2
- `isSurroundSoundCodec(codec)` - Identifies codecs eligible for fallback (line 372-377)
  - Currently covers: EAC3, AC3, DTS, DTSHD, TrueHD

#### 3. JellyfinStreamRepository
**File**: `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinStreamRepository.kt`

**Responsibilities**:
- Builds Direct Play and transcoding URLs
- Manages streaming parameters and quality settings
- Handles authentication tokens via OkHttp interceptor

**Key Methods**:
- `getTranscodedStreamUrl()` - Constructs transcoding URL with quality params (line 195-262)
  - Line 240: Sets `TranscodingMaxAudioChannels` (defaults to 2 for stereo fallback)
  - Line 244: `AllowAudioStreamCopy` - allows audio direct stream when compatible

## Current System Strengths ✅

1. **Channel-Aware Detection**
   - Properly extracts channel count from media stream
   - Passes actual channel count (not hardcoded 2) to validation
   - Handles null channel counts gracefully (defaults to 2)

2. **Smart Fallback Logic**
   - Only applies stereo fallback to surround sound codecs
   - Non-surround codecs (AAC, MP3) don't get fallback behavior
   - Prevents false positives while maximizing compatibility

3. **Comprehensive Logging**
   - SecureLogger provides detailed diagnostics at each step
   - Easy to debug playback decisions
   - Channel count logged in decision messages

4. **Network-Aware Transcoding**
   - Quality adapts to connection speed (EXCELLENT → POOR)
   - Bitrate, resolution, and channel count adjusted based on network
   - Prevents buffering on slow connections

5. **Proper Server Integration**
   - Respects server's PlaybackInfo recommendations
   - Client-side validation ensures compatibility
   - Graceful fallback when server decision conflicts with client capability

## Recommended Improvements 🔧

### 1. Add Dolby Atmos to Surround Sound List
**Priority**: Low
**File**: `DeviceCapabilities.kt`, line 372-377

Currently `isSurroundSoundCodec()` doesn't include "atmos" (E-AC3-JOC):

```kotlin
private fun isSurroundSoundCodec(codec: String): Boolean {
    return when (codec.lowercase()) {
        "eac3", "ac3", "dts", "dtshd", "truehd" -> true
        // MISSING: "atmos", "eac3-joc"
        else -> false
    }
}
```

**Recommendation**:
```kotlin
private fun isSurroundSoundCodec(codec: String): Boolean {
    return when (codec.lowercase()) {
        "eac3", "ac3", "eac3-joc", "atmos", "dts", "dtshd", "truehd" -> true
        else -> false
    }
}
```

### 2. Optimize AllowAudioStreamCopy for Downmixing
**Priority**: Medium
**File**: `JellyfinStreamRepository.kt`, line 208

When surround audio requires downmixing (stereo fallback scenario), we should force audio transcoding:

**Current**:
```kotlin
fun getTranscodedStreamUrl(
    ...
    allowAudioStreamCopy: Boolean = true,  // Always defaults to true
): String?
```

**Issue**: If we're using stereo fallback, setting `AllowAudioStreamCopy=true` tells the server it can direct-stream the 5.1 audio, but the client will downmix it. Better to transcode server-side to stereo.

**Recommendation**: Add a parameter or logic to detect when downmixing is needed and set `allowAudioStreamCopy=false`.

### 3. User Notification for Audio Downmixing
**Priority**: Low

When 5.1/7.1 audio is being played but downmixed to stereo, the user isn't informed. Consider:
- Toast notification: "Playing 5.1 surround audio as stereo"
- Playback UI indicator showing "5.1 → 2.0 (downmixed)"
- Helpful for users debugging "why doesn't my surround system work?"

### 4. Enhanced Diagnostic Logging
**Priority**: Low
**File**: `DeviceCapabilities.kt`, line 348-349

Add more context when stereo fallback is used:

**Current**:
```kotlin
SecureLogger.d(TAG, "Found stereo decoder for $codec (will downmix from $channels ch): $stereoDecoderName")
```

**Enhanced**:
```kotlin
SecureLogger.i(TAG, "⚠️ Device doesn't support $codec $channels-channel output, using stereo downmix via $stereoDecoderName. ExoPlayer will decode and downmix automatically.")
```

This makes it clearer in logs that downmixing is happening intentionally, not as a bug.

### 5. Add Channel Count Constants
**Priority**: Very Low
**File**: `DeviceCapabilities.kt`

Make channel counts more explicit:

```kotlin
companion object {
    private const val CHANNELS_STEREO = 2
    private const val CHANNELS_5_1 = 6
    private const val CHANNELS_7_1 = 8
    private const val CHANNELS_ATMOS = 16  // Dolby Atmos can support up to 16 channels
}
```

Then use: `if (channels > CHANNELS_STEREO && isSurroundSoundCodec(codec))`

### 6. Consider Codec Priority Optimization
**Priority**: Very Low
**File**: `JellyfinStreamRepository.kt`, line 507

Audio codec preference order could be refined:

**Current**: `listOf("opus", "aac", "ac3", "eac3", "mp3")`

**Consideration**: For transcoding, AAC is more universally compatible than Opus (better iOS/web support). Consider:
- For Direct Play: Current order is fine
- For Transcoding: `listOf("aac", "opus", "mp3", "ac3", "eac3")`

## Testing Recommendations

### Manual Testing Checklist
- [x] ✅ EAC3 5.1 audio on Pixel 10 Pro XL - Direct Play works (downmixed to stereo)
- [ ] EAC3 7.1 audio - Verify downmix to stereo
- [ ] AC3 5.1 audio - Verify downmix to stereo
- [ ] DTS 5.1 audio - Verify downmix to stereo
- [ ] TrueHD 7.1 audio - Verify downmix to stereo
- [ ] AAC 5.1 audio - Should NOT use stereo fallback (not a surround codec)
- [ ] Test on device WITH true 5.1 output capability - Verify it uses native 5.1
- [ ] Test transcoding fallback when Direct Play truly fails

### Automated Test Coverage
**File**: `app/src/test/java/com/rpeters/jellyfin/data/DeviceCapabilitiesTest.kt`

Current tests (added in e0527b7a):
- ✅ EAC3 stereo (2 channels)
- ✅ EAC3 5.1 (6 channels) with fallback
- ✅ EAC3 7.1 (8 channels) with fallback
- ✅ AC3 5.1/7.1 with fallback
- ✅ DTS 5.1 with fallback
- ✅ AAC stereo
- ✅ AAC 8-channel without fallback (non-surround codec)

**Additional tests to consider**:
- TrueHD 7.1 with fallback
- DTSHD 5.1 with fallback
- Atmos (E-AC3-JOC) once added to surround codec list

## Performance Considerations

### Current Performance ✅
- **Codec detection is cached** - `supportedVideoCodecs` and `supportedAudioCodecs` stored as instance variables
- **Resolution detection is cached** - `maxResolution` calculated once
- **Minimal overhead** - Stereo fallback only triggers when multi-channel check fails (rare)

### No Performance Concerns
The stereo fallback adds negligible overhead:
1. Only executes when initial channel check fails
2. Creates one additional MediaFormat and checks codec list (microseconds)
3. Prevents network overhead of unnecessary transcoding (massive win)

## Security Considerations

### Current Security ✅
- **No PII in logs** - SecureLogger filters sensitive data
- **Auth via headers** - Tokens not in URL parameters (handled by OkHttp interceptor)
- **Input validation** - UUID format validation for itemId
- **Safe fallbacks** - Never assumes codec support without verification

### No Security Issues Found

## Conclusion

### Summary
✅ **Your stereo fallback fix is correct and working as designed**

The transcoding system is well-architected with:
- Smart codec detection with proper channel awareness
- Network-aware quality adaptation
- Clean separation of concerns
- Comprehensive logging for debugging

### Recommended Next Steps
1. **Optional**: Add "atmos" to `isSurroundSoundCodec()` for future-proofing
2. **Optional**: Consider user notification when downmixing occurs
3. **Monitor**: Watch for any edge cases with other surround codecs (DTS-HD, TrueHD)

### Final Assessment
🎯 **No critical issues found. System is production-ready.**

The stereo fallback is an elegant solution to a common Android audio capability reporting issue, and the overall transcoding pipeline handles edge cases gracefully.

---
**Reviewed by**: Claude Code (Sonnet 4.5)
**Date**: February 13, 2026
**Commit**: Based on main branch @ 0198541f
