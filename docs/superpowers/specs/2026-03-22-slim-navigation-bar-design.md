# Design Doc: Slim Expressive Navigation Bar

**Date:** 2026-03-22
**Status:** Approved
**Topic:** Reducing the vertical footprint of the phone navigation bar while maintaining its expressive design.

## 1. Overview
The current floating navigation bar in Cinefin is perceived as too tall, consuming excessive screen real estate on mobile devices. This design outlines a "Slim" version that reduces the vertical footprint by approximately 35-40% through more compact padding and button dimensions.

## 2. Goals
- Reduce the total height of the navigation bar from ~96dp to ~60dp (excluding system insets).
- Maintain the "Expressive" design language (icon-only for unselected, icon+label for selected).
- Preserve accessibility and tap target usability.
- Ensure smooth animations during item selection.

## 3. Architecture & Components

### 3.1 Component: `ExpressiveFloatingNavBar`
The main container will be updated with tighter padding and a smaller floating offset.

**Changes:**
- **Outer Padding:** Reduced from `16.dp` to `6.dp`.
- **Surface Tonal Elevation:** Maintained at `6.dp` for visual depth.
- **Floating Offset:** The bottom padding in the parent `Column` (in `JellyfinApp.kt`) will be reduced from `12.dp` to `4.dp`.

### 3.2 Component: `ExpressiveNavBarButton`
Individual buttons within the bar will be scaled down.

**Changes:**
- **Height:** Reduced from `48.dp` to `36.dp`.
- **Unselected Width:** Fixed at `36.dp` (matching height).
- **Selected Width:** Dynamic (approx `100.dp` - `110.dp`), reduced from `120.dp`.
- **Internal Padding:** Reduced from `12.dp` to `8.dp`.
- **Typography:** Shift from `MaterialTheme.typography.labelLarge` to `MaterialTheme.typography.labelMedium`.
- **Icon Size:** Maintained at `24.dp` for readability and touch targets.

## 4. Implementation Details

### 4.1 Layout Updates
The `Row` layout within `ExpressiveFloatingNavBar` will use `Arrangement.spacedBy(4.dp)` to keep buttons tightly grouped without overlapping.

### 4.2 App Shell Adjustments (`JellyfinApp.kt`)
The content padding calculation in `JellyfinApp.kt` needs to be updated to account for the reduced height of the navigation bar to ensure content is not obscured or excessively padded.

**Current Padding:** `168.dp + navBarPadding` (MiniPlayer 72dp + FloatingNavBar 80dp + spacing).
**New Padding:** `120.dp + navBarPadding` (MiniPlayer 72dp + SlimNavBar 40dp + spacing).

## 5. Testing Strategy

### 5.1 Manual Verification
- **Visual Check:** Verify the bar looks balanced and the labels are legible in the smaller "pill".
- **Interaction Check:** Ensure tap targets remain responsive and the selection animation is smooth.
- **Overlap Check:** Verify that the bar does not overlap critical content or the system navigation bar in a way that hinders usability.

### 5.2 Automated Tests
- **Screenshot Tests:** Compare the new slim design against the baseline to ensure dimensions match the spec.
- **Compose UI Tests:** Verify that clicking an item still triggers the correct navigation route.

## 6. Success Criteria
- Navigation bar total height is significantly reduced.
- No regression in navigation functionality.
- UI remains visually consistent with Material 3 Expressive principles.
