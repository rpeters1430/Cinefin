# Transcoding Diagnostics Tool - User Guide

## Purpose

This tool helps you identify which videos in your Jellyfin library need transcoding and why, making it easy to test different playback scenarios systematically.

## How to Access

1. Open the Jellyfin Android app
2. Navigate to **Profile** tab (bottom navigation)
3. Tap **Settings**
4. Scroll down and tap **Transcoding Diagnostics** (under Playback Settings)

## What It Shows

### Summary Card
- **Total Videos**: All movies and episodes found (up to 500 of each)
- **Direct Play**: Videos that can play natively without transcoding (✅ green)
- **Needs Transcoding**: Videos that require server-side transcoding (❌ red)

### Video Analysis Cards

Each video shows:

#### Basic Info
- **Video Name**
- **Playback Method**: `DIRECT PLAY` (green) or `TRANSCODE` (red)

#### Technical Specs
- **Video Codec**: H.264, H.265 (HEVC), VP9, AV1, etc.
- **Audio Codec**: AAC, AC3, DTS, TrueHD, etc.
- **Container**: MP4, MKV, AVI, TS, etc.
- **Resolution**: 1080p, 4K, 720p, etc.

#### Transcoding Reasons

If a video needs transcoding, it will show specific reasons:
- `Video codec 'HEVC' not hardware supported` - Device can't decode this video codec
- `Audio codec 'AC3' needs transcoding to AAC` - Android needs AAC audio
- `Container 'MKV' requires remuxing to MP4/TS` - Container format not supported
- `Very high bitrate (120Mbps) may need transcoding` - Bitrate might exceed network/device capabilities

## Codec Support Reference

### Video Codecs

✅ **Universally Supported (Direct Play)**
- H.264 (AVC) - Most common, works everywhere

⚠️ **Device-Dependent (May Need Transcoding)**
- H.265 (HEVC) - Requires hardware decoder (available on newer devices)
- VP9 - Google codec, hardware support varies
- AV1 - New codec, limited hardware support

❌ **Usually Needs Transcoding**
- VC-1
- MPEG-2
- Older codecs without Android support

### Audio Codecs

✅ **Supported (Direct Play)**
- AAC - Preferred, widely supported
- MP3 - Older but supported
- Opus - Modern, efficient

❌ **Needs Transcoding to AAC**
- AC3 (Dolby Digital)
- E-AC3 (Dolby Digital Plus)
- DTS / DTS-HD
- TrueHD (Dolby TrueHD)
- FLAC (lossless)
- PCM (uncompressed)

### Containers

✅ **Supported (No Remuxing Needed)**
- MP4 / M4V - Best compatibility
- TS / M2TS - Transport Stream format
- 3GP / 3G2 - Mobile formats
- WebM - Web format

❌ **Needs Remuxing**
- MKV (Matroska) - Very common but needs remuxing
- AVI - Older format
- FLV - Flash video

## Testing Strategy

### 1. Test Different Codec Scenarios

Use the diagnostic tool to find videos with:

**H.264 Video + AAC Audio + MP4 Container**
→ Should Direct Play perfectly (baseline test)

**H.265 Video + AC3 Audio + MKV Container**
→ Tests multiple transcoding triggers (video codec, audio codec, container)

**4K Resolution + High Bitrate**
→ Tests network/performance handling

### 2. Focus on Problematic Videos

Look for videos marked "TRANSCODE" and note their specific issues:
- Test the position reset fix with videos that need transcoding
- Verify Direct Play works correctly for videos marked "DIRECT PLAY"

### 3. Systematic Testing Checklist

For each transcoding scenario:
- [ ] Video starts playing without errors
- [ ] Position is preserved during playback (no reset to start)
- [ ] Audio route changes don't reset position
- [ ] Seeking works correctly
- [ ] Resume from saved position works
- [ ] Progress is reported to server

## Known Device Variations

### Samsung Devices
- Generally excellent H.265 support
- S25 series: On-device AI, latest codecs

### Pixel Devices
- Strong VP9 support (Google codec)
- H.265 support varies by generation

### Older Devices (Pre-2019)
- May lack H.265 hardware decoder
- Stick with H.264 content for testing

## Technical Details

### How Detection Works

1. **Video Codec**: Checks MediaCodecList for decoder availability
2. **Audio Codec**: Android prefers AAC; others need transcoding
3. **Container**: ExoPlayer has limited container support; MKV typically needs remuxing
4. **Bitrate**: Very high bitrates (>80Mbps) may cause issues

### Why This Matters for Testing

The position reset bug (fixed in VideoPlayerViewModel) specifically affects **transcoded streams** because:
- HLS transcoding generates on-demand streams
- Codec flushes during transcoding lose position tracking
- Direct Play maintains position natively in local buffer

By identifying which videos transcode and why, you can:
- Test the fix with various transcoding scenarios
- Verify Direct Play isn't affected
- Understand which content works best on your server/device combo

## Limitations

- Shows up to 500 movies + 500 episodes (total 1000 videos max)
- Sorted by transcoding need (problematic videos first)
- Detection is based on Android device capabilities; actual server transcoding decisions may vary based on bandwidth settings

## Output Example

```
Library Summary
Total Videos:        237
Direct Play:         198  (✅ green)
Needs Transcoding:    39  (❌ red)

[Video Card - Red]
The Matrix Reloaded                    [TRANSCODE]
Video: HEVC          Container: MKV
Audio: AC3           Resolution: 1080p

Why transcoding needed:
• Video codec 'HEVC' not hardware supported
• Audio codec 'AC3' needs transcoding to AAC
• Container 'MKV' requires remuxing to MP4/TS

[Video Card - Green]
Inception                              [DIRECT PLAY]
Video: H264          Container: MP4
Audio: AAC           Resolution: 1080p
```

## Tips

1. **Start with Direct Play videos** to establish baseline behavior
2. **Pick 3-5 different transcoding scenarios** from the tool's output
3. **Focus on videos marked in red** with multiple transcoding reasons
4. **Note specific codec combinations** that cause issues
5. **Test position preservation** specifically with transcoded content

This tool gives you the information you need to systematically verify that the transcoding position reset fix works across different video formats!
