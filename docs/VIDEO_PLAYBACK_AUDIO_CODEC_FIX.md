# Video Playback Audio Codec Error Fix

**Date**: February 2, 2026  
**Issue**: MediaCodecAudioRenderer errors causing playback failures with EAC3/AC3 audio codecs

## Problem Statement

Users reported playback errors when attempting to play videos with EAC3 (Enhanced AC-3) audio codec:

```
Playback error: MediaCodecAudioRenderer error, index=2, 
format=Format(3, English, video/x-matroska, audio/eac3, null, -1, en, 
[-1, -1, -1.0, null], [6, 48000]), format_supported=YES
```

### Root Cause

1. **Reported vs Actual Support**: Android's MediaCodec API reports that EAC3 is supported (`format_supported=YES`), but the actual MediaCodec implementation fails during initialization
2. **No Automatic Fallback**: When Direct Play fails with audio codec errors, ExoPlayer stops with an error instead of falling back to transcoding
3. **Device-Specific Issues**: Some Android devices have partial or buggy EAC3/AC3 decoder implementations that pass capability checks but fail at runtime

## Solution Implemented

### Automatic Transcoding Fallback

The fix implements intelligent error recovery that automatically retries playback with server-side transcoding when audio codec errors are detected:

#### 1. Error Detection (VideoPlayerViewModel.kt)

```kotlin
override fun onPlayerError(error: PlaybackException) {
    // Detect audio codec errors by checking error message
    val errorMsg = error.message ?: ""
    val isAudioCodecError = errorMsg.contains("MediaCodecAudioRenderer", ignoreCase = true) ||
            errorMsg.contains("audio/eac3", ignoreCase = true) ||
            errorMsg.contains("audio/ac3", ignoreCase = true) ||
            errorMsg.contains("audio codec", ignoreCase = true)
    
    // Retry with transcoding if it's an audio codec error
    if (isAudioCodecError && !isRetryingWithTranscoding && currentItemId != null) {
        // Automatic retry with transcoding
        retryWithTranscoding(itemId, itemName, currentPosition)
    }
}
```

#### 2. Transcoding Retry Logic

- **Position Preservation**: Saves current playback position before retry
- **Player Cleanup**: Properly releases the failed ExoPlayer instance
- **Forced Transcoding**: Calls `EnhancedPlaybackManager.getTranscodingUrl()` to get transcoded stream
- **Audio Conversion**: Server transcodes problematic audio (EAC3/AC3) to AAC
- **Seamless Resume**: Seeks to saved position in new transcoded stream

#### 3. Retry Loop Prevention

```kotlin
private var isRetryingWithTranscoding: Boolean = false
```

- Prevents infinite retry loops if transcoding also fails
- Only one automatic retry attempt per playback session
- Flag is reset when new playback is initiated

### Changes Made

#### VideoPlayerViewModel.kt

1. Added `isRetryingWithTranscoding` flag to track retry state
2. Enhanced `onPlayerError()` to detect and handle audio codec failures
3. Added `retryWithTranscoding()` method that:
   - Releases failed player
   - Gets transcoding URL from EnhancedPlaybackManager
   - Creates new ExoPlayer with transcoded stream
   - Resumes playback at saved position

#### EnhancedPlaybackManager.kt

1. Added `getTranscodingUrl()` public method for forced transcoding
2. Method guarantees AAC audio output for maximum compatibility
3. Uses existing transcoding parameter logic (network-aware bitrate selection)

## User Experience

### Before Fix
1. User clicks "Play Movie" on video with EAC3 audio
2. Video fails to play with cryptic error message
3. User must manually understand the issue and server settings
4. No way to play the video without server configuration changes

### After Fix
1. User clicks "Play Movie" on video with EAC3 audio
2. Direct Play attempts and fails (happens quickly, < 1 second)
3. Error message briefly shows: "Audio codec incompatible, switching to transcoding..."
4. Playback automatically retries with transcoding
5. Video plays successfully with AAC audio

**Result**: Seamless experience with automatic recovery

## Technical Details

### Affected Audio Codecs

The fix handles errors for the following problematic audio codecs:
- **EAC3** (Enhanced AC-3 / Dolby Digital Plus)
- **AC3** (Dolby Digital)
- Other audio codec failures detected by MediaCodecAudioRenderer

### Transcoding Parameters

When forced transcoding is triggered:
- **Audio Codec**: AAC (most universally supported)
- **Video Codec**: Based on network quality (H264 for medium/low, H265/VP9 for high)
- **Bitrate**: Adaptive based on network conditions (3-20 Mbps)
- **Container**: MP4 or HLS (m3u8)

### Network Considerations

- **High Quality Network** (WiFi/Ethernet): 1080p/4K transcoding with AAC
- **Medium Quality Network**: 1080p transcoding with AAC  
- **Low Quality Network**: 720p transcoding with AAC

## Testing

### Manual Testing Required

Due to device-specific nature of codec support, testing should include:

1. **Direct Play Success**: Video with AAC audio plays normally
2. **Automatic Fallback**: Video with EAC3 audio fails → retries → plays
3. **Position Preservation**: Playback resumes at correct position after retry
4. **Error Handling**: Clear error message shown during retry
5. **No Infinite Loops**: Second failure shows error without retry

### Test Devices

Priority testing on devices known to have EAC3 issues:
- Low/mid-range Android devices
- Devices with Android 8-10
- Devices with custom ROMs

### Test Content

Use videos with:
- ✅ EAC3 audio (primary test case)
- ✅ AC3 audio (secondary test case)  
- ✅ AAC audio (ensure no regression)
- ✅ Multiple audio tracks with mixed codecs

## Limitations

### Known Limitations

1. **One Retry Only**: System only attempts one automatic retry to prevent infinite loops
2. **Transcoding Required**: Solution requires transcoding-capable Jellyfin server
3. **Brief Interruption**: User sees brief error message during 1-2 second retry
4. **Increased Server Load**: Transcoding uses more server CPU/resources than Direct Play

### When Fix Won't Help

This fix does NOT address:
- Network connectivity errors
- Server configuration issues (transcoding disabled)
- Video codec errors (these use setEnableDecoderFallback)
- Authentication/authorization failures

## Future Improvements

### Potential Enhancements

1. **Pre-Emptive Detection**: Check codec support before attempting Direct Play
2. **User Preference**: "Always transcode audio" option in settings
3. **Better Error Messages**: More specific messages for different error types
4. **Codec Blacklist**: Remember problematic codecs and skip Direct Play automatically

### Server-Side Improvements

Consider enhancement to Jellyfin server:
- More accurate codec capability reporting
- Device-specific codec profiles
- Automatic codec conversion without full transcoding

## Related Issues

- Issue #XX: Video playback issues with EAC3 audio
- MediaCodec.createDecoderByType() failures on certain devices
- ExoPlayer issue regarding format_supported=YES but decoder fails

## References

- ExoPlayer DefaultRenderersFactory Documentation
- Android MediaCodec API Documentation  
- Jellyfin Transcoding Documentation
- EAC3/AC3 Codec Specifications

---

## Commit Information

**Commit**: `9888065`  
**Branch**: `copilot/fix-video-playback-issues`  
**Files Changed**: 2
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/playback/EnhancedPlaybackManager.kt`

**Lines Added**: ~210  
**Lines Removed**: ~4
