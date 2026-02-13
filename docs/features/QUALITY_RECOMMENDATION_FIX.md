# Quality Recommendation Dialog Fix

## Issue Summary

The Quality Recommendation dialog was incorrectly showing "Low bandwidth (0Mbps)" during transcoding playback (see screenshot.png).

## Root Cause

In `AdaptiveBitrateMonitor.kt` (lines 141-159), the code had **two critical bugs**:

### Bug 1: Using Track Bitrate Instead of Network Bandwidth ❌

```kotlin
val bandwidthEstimate = exoPlayer.currentTracks.groups
    .firstNotNullOfOrNull { group ->
        group.getTrackFormat(0).bitrate.takeIf { it > 0 }
    }
```

**Problem**: This was extracting the **media track's encoded bitrate** (how the video/audio is compressed), NOT the **network bandwidth estimate** (how fast data is being downloaded).

**Result**: Comparing media bitrate against network bandwidth thresholds (`CRITICAL_BANDWIDTH_THRESHOLD = 2_000_000`) makes no sense. A 1080p video might be encoded at 8 Mbps, but that doesn't mean the network is slow.

### Bug 2: Integer Division Showing 0Mbps ❌

```kotlin
reason = "Low bandwidth (${bandwidthEstimate / 1_000_000}Mbps)",
```

**Problem**: Integer division in Kotlin truncates decimals:
- If `bandwidthEstimate = 500_000` (500 Kbps)
- Then `500_000 / 1_000_000 = 0` (integer division)
- Displayed as "Low bandwidth (0Mbps)" even though it's actually 0.5 Mbps

**Correct approach** would be:
```kotlin
reason = "Low bandwidth (${"%.1f".format(bandwidthEstimate / 1_000_000.0)}Mbps)",
```

## The Fix

**Removed the entire incorrect bandwidth check** (lines 141-159) from `AdaptiveBitrateMonitor.kt`.

### Why This Fix is Correct

1. **Buffering-based detection is more reliable**: The monitor already detects quality issues through:
   - Sustained buffering (>5 seconds)
   - Multiple buffering events (3+ in 30 seconds)
   - These are actual playback problems, not hypothetical bandwidth estimates

2. **Network quality should come from ConnectivityChecker**: The `AdaptiveBitrateMonitor` already has access to `ConnectivityChecker` which properly assesses network conditions. If we need network-based recommendations, we should use that.

3. **ExoPlayer bandwidth estimation is complex**: Properly accessing ExoPlayer's internal bandwidth estimator requires:
   - Custom `LoadControl` or `BandwidthMeter` integration
   - Tracking over time (not a single snapshot)
   - Understanding the difference between current bandwidth vs estimated bandwidth

4. **The removed code was never working correctly**: It was triggering false recommendations based on media encoding bitrate, not actual network performance.

## What Remains

After the fix, the `AdaptiveBitrateMonitor` still provides quality recommendations based on:

### ✅ Sustained Buffering Detection
```kotlin
if (currentBufferingDuration >= SUSTAINED_BUFFERING_THRESHOLD_MS) {
    // Recommend quality downgrade after 5+ seconds of buffering
}
```

### ✅ Frequent Buffering Detection
```kotlin
if (consecutiveBufferingEvents >= 3) {
    // Recommend quality downgrade after 3+ buffering events
}
```

### ✅ Respects User Preferences
- Only triggers when `transcodingQuality == AUTO` (manual selection is respected)
- Only applies during transcoding (can't downgrade direct play)
- Minimum 60 seconds between recommendations

## Testing

### Before Fix
- Screenshot shows "Low bandwidth (0Mbps)" dialog appearing incorrectly
- Likely triggered by comparing track bitrate against network thresholds

### After Fix
- Quality recommendations only appear during actual buffering events
- No spurious "0Mbps" messages
- More reliable detection of playback quality issues

## Related Components

- **AdaptiveBitrateMonitor.kt** - Monitors playback and suggests quality changes
- **VideoPlayerDialogs.kt** - Displays the QualityRecommendationNotification UI
- **VideoPlayerViewModel.kt** - Integrates the monitor with ExoPlayer

## Future Improvements (Optional)

If we want to add proper network bandwidth-based recommendations:

1. **Use ConnectivityChecker for network quality**:
   ```kotlin
   val networkQuality = connectivityChecker.getNetworkQuality()
   if (networkQuality == ConnectivityQuality.POOR) {
       // Recommend lower quality
   }
   ```

2. **Integrate ExoPlayer's BandwidthMeter properly**:
   ```kotlin
   // During ExoPlayer setup
   val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
   val exoPlayer = ExoPlayer.Builder(context)
       .setBandwidthMeter(bandwidthMeter)
       .build()

   // In monitoring loop
   val estimatedBandwidth = bandwidthMeter.getBitrateEstimate()
   ```

3. **Display bandwidth in Mbps with decimals**:
   ```kotlin
   val mbps = "%.1f".format(bandwidth / 1_000_000.0)
   reason = "Low bandwidth (${mbps}Mbps)"
   ```

## Conclusion

✅ **Fix removes incorrect logic that was causing spurious quality recommendations**

The AdaptiveBitrateMonitor now focuses on what it does best: detecting actual playback buffering issues and recommending quality adjustments when needed.

---
**Fixed by**: Claude Code (Sonnet 4.5)
**Date**: February 13, 2026
**Related Issue**: Quality dialog showing "Low bandwidth (0Mbps)" during transcoding
