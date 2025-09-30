# Google TV PiP QA Notes

## Test Matrix
- **Devices**: Chromecast with Google TV (HD & 4K), Nvidia Shield Pro (Android TV 11).
- **Builds**: Debug (dev flags on) and release candidate.
- **Inputs**: Bluetooth remote, HDMI-CEC remote, Stadia controller (gamepad profile).

## Test Scenarios
1. **Enter PiP via UI control**
   - Focus `Picture-in-picture` button with D-pad and press OK.
   - Verify playback continues in PiP window.
   - Confirm controls dismiss once PiP is active.
2. **Enter PiP via Home button**
   - Start playback in full screen.
   - Press `Home`. App should transition to PiP and keep playing audio/video.
3. **Return from PiP**
   - From launcher, select the PiP window.
   - Confirm app restores focus to the play/pause control and resumes full-screen playback.
4. **Media key behavior in PiP**
   - While in PiP, press Play/Pause, Fast Forward, and Rewind.
   - Validate commands map to `VideoPlayerViewModel` actions (state updates in UI when app restored).
5. **Error handling**
   - Force network disconnect during PiP (disable Wi-Fi).
   - Ensure PiP window shows error toast when playback fails and app resumes gracefully.

## Observed Behavior
- Home and Assistant triggers consistently move the activity into PiP within 400 ms.
- Stadia controller media buttons map correctly through `TvKeyboardHandler`.
- HDMI-CEC remotes occasionally send duplicate `PlayPause`; debounce via view-model prevents state desync.

## Follow-ups
- Investigate PiP custom actions for skip intro/outro once API plumbing is available.
- Add telemetry for PiP enter/exit to monitor usage on production devices.
