# Slim Expressive Navigation Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the vertical footprint of the phone navigation bar from ~96dp to ~60dp while maintaining its expressive design.

**Architecture:** Update `ExpressiveFloatingNavBar.kt` and `ExpressiveNavBarButton` with tighter padding and smaller height. Adjust `JellyfinApp.kt` to account for the reduced height in content padding calculations.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, Kotlin.

---

### Task 1: Update ExpressiveNavBarButton Dimensions

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveFloatingNavBar.kt`

- [ ] **Step 1: Modify `ExpressiveNavBarButton` to reduce height and padding**

Update `ExpressiveNavBarButton` to use a height of `36.dp` and reduce internal horizontal padding from `12.dp` to `8.dp`. Ensure the `clickable` surface still respects the `48.dp` minimum touch target.

```kotlin
@Composable
private fun ExpressiveNavBarButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        // Ensure 48dp touch target while visual height is 36dp
        modifier = Modifier.sizeIn(minHeight = 48.dp)
            .height(36.dp) 
            .width(if (selected) 110.dp else 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            if (selected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveFloatingNavBar.kt
git commit -m "ui: reduce ExpressiveNavBarButton height and padding"
```

---

### Task 2: Update ExpressiveFloatingNavBar Container

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveFloatingNavBar.kt`

- [ ] **Step 1: Modify `ExpressiveFloatingNavBar` container padding**

Reduce the outer `Surface` padding from `16.dp` to `6.dp` and ensure `spacedBy` is tight.

```kotlin
@Composable
fun ExpressiveFloatingNavBar(...) {
    // ...
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        modifier = Modifier.padding(6.dp), // Reduced from 16.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp) // Tighter internal padding
                .animateContentSize(...),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ...
        }
    }
}
```

- [ ] **Step 2: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveFloatingNavBar.kt
git commit -m "ui: reduce ExpressiveFloatingNavBar container padding"
```

---

### Task 3: Adjust App Layout Padding

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/JellyfinApp.kt`

- [ ] **Step 1: Update content padding calculation**

Adjust the `Column` padding in `JellyfinApp.kt` to reflect the smaller navigation bar.

```kotlin
// In JellyfinApp.kt
if (isCompactWidth) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Old: 168.dp + navBarPadding
    // New: MiniPlayer (72dp) + SlimNavBar (40dp) + Spacing (8dp) = 120dp
    Modifier.padding(bottom = 120.dp + navBarPadding)
}
```

- [ ] **Step 2: Update FloatingNavBar bottom padding**

Reduce the bottom padding on the `ExpressiveFloatingNavBar` call from `12.dp` to `4.dp`.

```kotlin
ExpressiveFloatingNavBar(
    // ...
    modifier = Modifier.padding(bottom = 4.dp) // Reduced from 12.dp
)
```

- [ ] **Step 3: Commit changes**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/JellyfinApp.kt
git commit -m "ui: adjust app shell padding for slim nav bar"
```

---

### Task 4: Verification and Polish

- [ ] **Step 1: Verify layout on Phone (Compact Width)**

Check that the bar is visible, properly padded from the system navigation bar, and that content is correctly padded above it.

- [ ] **Step 2: Verify touch targets**

Ensure that even with the visual reduction, clicking the edges of the buttons still triggers navigation (respecting the 48dp minimum).

- [ ] **Step 3: Run existing UI tests**

Run: `gradlew connectedAndroidTest` (if an emulator is available) or manually verify the navigation flow.

- [ ] **Step 4: Final Commit**

```bash
git commit --allow-empty -m "docs: complete slim navigation bar implementation"
```
