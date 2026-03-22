# Expressive Bento Grid Home Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a dynamic, Material 3 Expressive "Bento Box" layout for the Cinefin home screen to create a more curated and visually engaging experience.

**Architecture:** Refactor `ImmersiveHomeScreen.kt` to use a single `LazyVerticalGrid` with variable span logic. Extract card types into specialized components (`BentoFeaturedCard`, `BentoActionCard`, `BentoWideCard`) that leverage Material Expressive shapes and colors.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, Kotlin.

---

### Task 1: Define Bento Component Models and Spans

**Files:**
- Create: `app/src/main/java/com/rpeters/jellyfin/ui/screens/home/BentoModels.kt`

- [ ] **Step 1: Create `BentoModels.kt` to define grid item types and span logic**

```kotlin
package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.lazy.grid.GridItemSpan

sealed class BentoItemType {
    object Featured : BentoItemType()
    object Action : BentoItemType()
    object Wide : BentoItemType()
}

fun getBentoSpan(itemType: BentoItemType, columns: Int): GridItemSpan {
    return when (itemType) {
        BentoItemType.Featured -> GridItemSpan(columns)
        BentoItemType.Action -> GridItemSpan(1)
        BentoItemType.Wide -> GridItemSpan(columns)
    }
}
```

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/home/BentoModels.kt
git commit -m "feat: define Bento grid models and span logic"
```

---

### Task 2: Create Bento Card Components

**Files:**
- Create: `app/src/main/java/com/rpeters/jellyfin/ui/components/BentoCards.kt`

- [ ] **Step 1: Implement `BentoFeaturedCard`, `BentoActionCard`, and `BentoWideCard`**

Use `MaterialTheme.shapes.extraLarge` and variable background colors.

```kotlin
// ... (implementation details for BentoFeaturedCard, BentoActionCard, BentoWideCard)
```

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/BentoCards.kt
git commit -m "ui: implement Bento card components with expressive styling"
```

---

### Task 3: Implement ExpressiveBentoGrid Container

**Files:**
- Create: `app/src/main/java/com/rpeters/jellyfin/ui/screens/home/ExpressiveBentoGrid.kt`

- [ ] **Step 1: Implement `ExpressiveBentoGrid` using `LazyVerticalGrid`**

```kotlin
// ... (implementation of the grid with span logic and data mapping)
```

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/home/ExpressiveBentoGrid.kt
git commit -m "feat: implement ExpressiveBentoGrid container"
```

---

### Task 4: Integrate Bento Grid into ImmersiveHomeScreen

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreen.kt`

- [ ] **Step 1: Swap old home content with `ExpressiveBentoGrid`**

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreen.kt
git commit -m "refactor: integrate Bento grid into ImmersiveHomeScreen"
```

---

### Task 5: Verification and Polish

- [ ] **Step 1: Verify layout on Phone and Tablet**
- [ ] **Step 2: Verify smooth scrolling and transitions**
- [ ] **Step 3: Run existing UI tests**
- [ ] **Step 4: Final Commit**

```bash
git commit --allow-empty -m "docs: complete Bento grid home screen implementation"
```
