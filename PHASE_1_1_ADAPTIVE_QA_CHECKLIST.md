# Phase 1.1 Adaptive Layout QA Checklist

## TV 1080p (Landscape)
- Launch the Jellyfin Android TV app on a 1920×1080 display or emulator.
- Verify the home screen uses carousel rows with focusable cards sized for TV (large spacing, 56–64 dp gutters).
- Confirm that D-pad navigation preserves focus position when moving between carousels and returning from detail screens.
- Ensure libraries row appears after media carousels and respects TV padding.
- Validate that error, empty, and loading states still appear full-screen.

## Tablet Portrait (e.g., 1280×800)
- Open the app on a tablet in portrait orientation.
- Confirm the layout switches to dual-pane grid mode with at least two columns and a visible detail pane on the right.
- Focus different cards and verify the detail pane updates without losing grid focus.
- Rotate back to TV layout (force landscape) and ensure initial focus returns to the last section.

## Tablet Landscape / Desktop-width (e.g., 2560×1600)
- Use landscape orientation or desktop window with expanded width.
- Ensure the grid expands to additional columns (3–4) while the detail pane remains visible.
- Verify spacing/padding tighten appropriately compared to TV mode.
- Check that navigation drawer/rail choices align with the width class.

## Foldable / Book Posture
- Test on a foldable emulator (e.g., Pixel Fold) in book or tabletop posture.
- Confirm the app treats the posture as tablet/dual-pane: grid on one side, detail pane on the other.
- Close and reopen the device to ensure focus state persists across posture changes.
- Validate navigation gestures or D-pad still target the focused grid item after posture transitions.

## General Regression Checks
- Switch between tablet and TV layouts and confirm focus restoration selects the previously focused carousel/grid item.
- Verify carousel item sizing adapts (200–240 dp width) based on form factor and orientation.
- Confirm there are no crashes when `WindowLayoutInfo` reports no folding features.
