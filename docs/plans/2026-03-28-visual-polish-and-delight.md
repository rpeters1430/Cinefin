# Cinefin Visual Polish & Delight Plan

**Date**: March 28, 2026
**Status**: Draft / Proposed

## Goal
Transform Cinefin from a functional, well-designed app into a truly premium, "magical" experience by leveraging the latest Jetpack Compose features (Shared Element Transitions, Advanced Blur) and the Material 3 Expressive design system.

---

## ✅ Priority 1: Shared Element Transitions (The "Magic" Feel)
Status: **COMPLETED** (Core Infrastructure & Hero Sections)

### Tasks:
1.  [x] **Wrap NavHost in `SharedTransitionLayout`**: Done in `NavGraph.kt`.
2.  [x] **Provide `LocalAnimatedVisibilityScope`**: Done via `CompositionLocalProvider` in screens.
3.  [x] **Poster & Backdrop Transitions**:
    - [x] `ImmersiveHeroCarousel` to `ImmersiveMovieDetailScreen` backdrop.
    - [x] `MediaCard`, `PosterMediaCard`, and `RecentlyAddedCard` ready for shared elements.

---

## ✅ Priority 2: Glassmorphism & Advanced Blur (Modern Aesthetic)
Status: **COMPLETED**

### Tasks:
1.  [x] **Create `ExpressiveBlurSurface`**: Done in `ExpressiveBlur.kt`.
2.  [x] **Apply to Navigation**:
    - [x] `ExpressiveFloatingNavBar` now uses real-time blur on Android 12+.

---

## ✅ Priority 3: Expressive Haptics (Tactile Feedback)
Status: **COMPLETED**

### Tasks:
1.  [x] **Haptic Interaction Logic**: Done in `ExpressiveHaptics.kt`.
2.  [x] **Integration**:
    - [x] `ImmersiveMediaCard`, `MediaCard`, and `PosterMediaCard` all feature subtle haptic feedback.

---

## ✅ Priority 4: AI "Aura" UI (The "Magical" Assistant)
Status: **COMPLETED**

### Tasks:
1.  [x] **Pulsing Aura Effect**: Done in `AiAura.kt`.
2.  [x] **Apply to AI FAB**:
    - [x] AI Assistant FAB and AI Search toggle now feature a pulsing, multi-colored aura.

---

## 🟡 Priority 5: Consistency Audit
Status: **IN PROGRESS**

### Tasks:
1.  [ ] **Immersive Settings**: Planned for next phase.
2.  [ ] **Profile & About**: Planned for next phase.
3.  [ ] **Error & Empty States**: `ExpressiveErrorState` exists but could be further refined with shared transitions.

---

## 📊 Success Metrics
- **Visual Appeal**: High "delight" factor reported in user testing.
- **Smoothness**: Maintain 60/120fps during shared element transitions.
- **Accessibility**: Ensure haptics and blur don't compromise usability for users with sensitivities.

---

## Next Steps
1.  [ ] Prototype Shared Element Transitions in a branch.
2.  [ ] Create the `BlurrySurface` component.
3.  [ ] Add haptics to core interaction components.
