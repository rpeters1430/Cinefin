# Cinefin Design System

This file is the source of truth for how Cinefin looks, moves, and talks. It covers the mobile app only; CinefinTV has its own focus-driven rules and should borrow tokens from here, not layouts.

**For coding agents:** read this before creating or changing any composable. If a rule here conflicts with an existing screen, follow this file and flag the screen. If something isn't covered, match the nearest existing pattern and add a note under "Open decisions" rather than inventing a new one.

---

## 1. Principles

1. **The artwork is the interface.** Posters, backdrops, and logos carry the color and personality. Chrome stays quiet so the library looks like the user's library, not like our app.
2. **Lights down.** Cinefin is used in the evening, on the couch, often in a dark room. Dark theme is the default and the one we design first. Nothing flashes bright white.
3. **One tap to play.** Any screen that shows a playable item offers a direct path to play or resume it. Browsing never gets in the way of watching.
4. **Material 3, applied with intent.** Use M3 components and tokens; don't restyle them into something else. Customize through the theme, not per-call overrides.
5. **Honest about the server.** Loading, offline, and error states say exactly what's happening with the Jellyfin server and what the user can do about it.

---

## 2. Color

### 2.1 Base palette ("projection booth")

Cool slate surfaces with a single warm accent taken from a tungsten projector lamp. The accent is used sparingly: primary actions, progress, and selection. Everything else is neutral so artwork-derived color (2.3) has room.

| Token | Hex | Use |
|---|---|---|
| `booth` | `#12151B` | Dark background |
| `booth-raised` | `#1B1F27` | Cards, sheets, nav bar |
| `booth-high` | `#262B35` | Menus, dialogs, pressed surfaces |
| `screen` | `#E9ECF2` | Primary text on dark |
| `dimmer` | `#98A0AE` | Secondary text, inactive icons |
| `tungsten` | `#F2B45A` | Primary accent (play, progress, selection) |
| `tungsten-deep` | `#3D2A0C` | Text/icons on `tungsten`; tonal containers |
| `signal-red` | `#FF6B6B` | Errors, destructive actions |

Light theme exists for users who want it but is not the design target. Derive it from the same seed (`tungsten`) using Material Theme Builder and check contrast after generating.

### 2.2 Mapping to Material 3

```kotlin
private val CinefinDark = darkColorScheme(
    primary = Color(0xFFF2B45A),
    onPrimary = Color(0xFF3D2A0C),
    primaryContainer = Color(0xFF5A3F14),
    onPrimaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF12151B),
    onBackground = Color(0xFFE9ECF2),
    surface = Color(0xFF12151B),
    onSurface = Color(0xFFE9ECF2),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1B1F27),
    surfaceContainerHigh = Color(0xFF262B35),
    onSurfaceVariant = Color(0xFF98A0AE),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF2A2F39),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
)
```

Rules:
- Always read colors from `MaterialTheme.colorScheme`. Never hard-code `Color(...)` in a screen or component.
- Surfaces use the `surfaceContainer*` roles for hierarchy instead of tonal elevation or shadows.
- Pure black (`#000000`) is used only behind the video player and in the optional AMOLED mode.

### 2.3 Artwork color (the one bold thing)

On item detail screens, the accent comes from the item's backdrop or poster instead of `tungsten`.

- Extract with `androidx.palette` from the already-loaded Coil bitmap (don't fetch a second copy). Prefer the vibrant swatch, then dark vibrant, then fall back to `tungsten`.
- Run the extracted color through `material-kolor` (or equivalent) to build a full scheme, then wrap the detail screen in its own `MaterialTheme`. Components don't know the color changed.
- Enforce 4.5:1 contrast for text on the extracted `primary`. If it fails, fall back to `tungsten`.
- Crossfade the scheme over 300 ms when it resolves; never pop.
- Setting: "Match colors to artwork" (on by default). When off, detail screens use the base palette.

### 2.4 Dynamic color (Material You)

Offered as an opt-in setting, off by default, because it competes with artwork color. When on, it replaces the base palette everywhere except detail screens with artwork color enabled.

---

## 3. Typography

Two families, each with a job:

- **Barlow Semi Condensed** for titles. Movie and episode titles run long; a semi-condensed face fits them on one or two lines at phone widths without truncating the interesting part. It also nods to marquee lettering without being a costume.
- **Atkinson Hyperlegible Next** for everything else. Built for legibility at small sizes and low light, which is exactly where metadata, overviews, and settings get read.

Load both through downloadable Google Fonts (`androidx.compose.ui:ui-text-google-fonts`) with system sans as fallback.

| M3 role | Family | Size / line height | Weight | Used for |
|---|---|---|---|---|
| `displaySmall` | Barlow SC | 36 / 40 | 600 | Detail screen title (when no logo image) |
| `headlineMedium` | Barlow SC | 28 / 34 | 600 | Screen titles (Library, Search) |
| `headlineSmall` | Barlow SC | 24 / 30 | 600 | Section heads on detail screens |
| `titleLarge` | Barlow SC | 22 / 28 | 500 | Home row titles |
| `titleMedium` | Atkinson | 16 / 22 | 700 | Card titles, list item titles |
| `titleSmall` | Atkinson | 14 / 20 | 700 | Episode titles in lists |
| `bodyLarge` | Atkinson | 16 / 24 | 400 | Overviews, long text |
| `bodyMedium` | Atkinson | 14 / 20 | 400 | Default body |
| `bodySmall` | Atkinson | 12 / 16 | 400 | Metadata (year, runtime, rating) |
| `labelLarge` | Atkinson | 14 / 20 | 700 | Buttons |
| `labelMedium` | Atkinson | 12 / 16 | 700 | Chips, badges |

Rules:
- Sentence case everywhere. No all-caps labels or buttons.
- Overviews cap at about 65 characters per line; on wide layouts, constrain the text column rather than letting it stretch.
- Metadata is one line, separated by spacing (`16dp`), not by bullets or dots. Example: `2019   2h 12m   PG-13   ★ 7.8`.
- Never set text below 12sp. Respect the user's font scale; test at 1.3x.

---

## 4. Shape, spacing, elevation

### Spacing
4dp base grid. Use these values only: `4, 8, 12, 16, 24, 32, 48`.
- Screen horizontal padding: `16dp` (compact), `24dp` (medium and up)
- Gap between cards in a row: `12dp`
- Gap between home rows: `24dp`
- Detail screen section gap: `32dp`

Define these once in a `Spacing` object exposed through a `CompositionLocal`; don't scatter raw `.dp` literals.

### Shape
Radius follows size, so hierarchy reads at a glance:

| Element | Radius |
|---|---|
| Poster and thumbnail cards | `8dp` |
| Chips, badges | `8dp` |
| Buttons | full (pill) |
| Bottom sheets, dialogs | `24dp` top / all |
| Hero backdrop | `0dp` (full bleed) |

### Elevation
No drop shadows on cards. Hierarchy comes from surface container roles (2.2). The only shadow-like treatment is the scrim gradient on backdrops.

---

## 5. Layout

### Window size classes
Use `currentWindowAdaptiveInfo()` and branch on width class.

| Width | Nav | Poster columns (library grid) |
|---|---|---|
| Compact (<600dp) | Bottom `NavigationBar` | 3 |
| Medium (600–840dp) | `NavigationRail` | 5 |
| Expanded (>840dp) | `NavigationRail` (or drawer on large tablets) | Adaptive, min card width `140dp` |

Prefer `GridCells.Adaptive(minSize)` over fixed counts once the design is settled.

### Artwork aspect ratios
Always reserve space with the correct ratio before the image loads so nothing jumps.

| Type | Ratio | Where |
|---|---|---|
| Poster | 2:3 | Movies, series, library grids |
| Thumb / still | 16:9 | Episodes, continue watching, chapters |
| Square | 1:1 | Music albums, artists, collections of music |
| Backdrop | 16:9 (cropped to ~1.5:1 at top of detail) | Detail hero |
| Logo | Intrinsic, max height 80dp | Detail title replacement |

### Alignment
Left-aligned throughout. Centered text only in empty states and the login screen.

### Edge to edge
The app is edge-to-edge. Backdrops draw under the status bar; everything else respects `WindowInsets.safeDrawing`. Scrims keep status bar icons legible over artwork.

---

## 6. Components

Components live in `ui/components/` and are the only place visual decisions get made. Screens compose them.

### PosterCard
- Image with ratio from 5, `8dp` corners, `surfaceContainer` placeholder.
- Title (`titleMedium`, max 2 lines) and one metadata line (`bodySmall`, `onSurfaceVariant`) below the image, never overlaid on it.
- Watched: small check badge top-right. Unwatched episode count: number badge top-right. Only one badge at a time.
- Progress: 3dp `LinearProgressIndicator` flush with the image bottom, `primary` on `surfaceContainerHigh`.
- Tap opens detail. Long-press opens the item action sheet (Play, Mark watched, Add to favorites, Request more like this if *arr is connected).

### ContinueWatchingCard
- 16:9 thumb, progress bar, series name as title, `S2 · E5` style replaced by `Season 2, episode 5` on the metadata line, plus time remaining ("24 min left").
- Tap resumes playback directly. It does not open detail.

### MediaRow
- `titleLarge` row heading with an optional "See all" `TextButton` trailing.
- `LazyRow` with `contentPadding` equal to screen padding so the first card aligns with the heading and cards scroll edge to edge.
- Stable keys (item ID) on every row and grid.

### DetailHero
- Full-bleed backdrop, vertical scrim from transparent to `background` over the bottom 60%.
- Logo image if available, otherwise `displaySmall` title.
- Metadata line, then genre chips (max 3), then the primary action row.

### Primary action row
- `Button` for the main action: "Play", "Resume" (with time), or "Play S1 E1" for unstarted series. Uses `primary`.
- `FilledTonalIconButton`s for secondary actions: watched toggle, favorite, download, more. Each has a content description.
- One filled button per screen. Everything else is tonal, outlined, or text.

### Chips
- `AssistChip` for genres and tags (navigate to filtered library).
- `FilterChip` for library filters and sort.

### Request UI (Sonarr / Radarr / Overseerr)
- Appears only when the server plugin reports an *arr connection.
- Request status uses a single badge vocabulary: "Requested", "Downloading", "Available", "Failed". Same words in the badge, the toast, and the request list.
- Request actions confirm with a bottom sheet showing what will happen (quality profile, seasons) before sending.

### Loading
- Images: BlurHash from the Jellyfin API as the placeholder when available, crossfading to the image (Coil `crossfade(200)`). Otherwise solid `surfaceContainer`.
- Content: skeleton shapes matching the real layout. No full-screen spinners except during login and server discovery.

---

## 7. Screens

| Screen | Structure | Notes |
|---|---|---|
| Server & login | Centered column, max width 400dp | Server URL field accepts bare hostnames; show discovered servers as a list above the field. |
| Home | Continue watching, Next up, then one row per library, then Recently added per library | Row order follows the user's Jellyfin home settings where possible. |
| Library | Filter/sort chip bar (sticky), poster grid | Alphabet fast-scroller on the trailing edge for libraries over 100 items. |
| Detail (movie) | DetailHero, overview, cast row, similar row | Artwork color applies. |
| Detail (series) | DetailHero, season selector (`PrimaryScrollableTabRow`), episode list | Episode list items: 16:9 thumb, title, runtime, overview (2 lines, expandable). |
| Player | Black background, controls overlay | See 8. |
| Search | Search bar at top, results grouped by type (Movies, Shows, Episodes, People) | Show recent searches when empty. |
| Requests | List of request items with status badge | Pull to refresh. |
| Settings | Grouped `ListItem`s | Group headings in `titleSmall`, `primary` color. |

---

## 8. Player

- Controls fade in on tap, auto-hide after 3s of no interaction, stay visible while paused.
- Center: play/pause (64dp), with 10s back / 30s forward on either side. Double-tap left/right thirds to seek.
- Bottom: scrubber with chapter ticks and trickplay thumbnails (Jellyfin trickplay API) above the thumb while dragging.
- Top: back, title and episode, then cast, audio/subtitle, and more.
- Scrims: top and bottom gradients only, never a full-screen dim over the video.
- Swipe vertical on left edge for brightness, right edge for volume, with a small pill indicator; respect system gesture insets.
- Picture-in-picture on home or back gesture while playing, when the setting is on.
- Subtitles default to Atkinson Hyperlegible Next, white with a soft black outline; user-adjustable size and background.

---

## 9. Motion

Motion responds to what the user did. There are no ambient or decorative animations.

| Interaction | Motion |
|---|---|
| Card to detail | Shared element transition on the poster (`SharedTransitionLayout`), 350ms, emphasized easing |
| Tab and screen changes | M3 fade-through, 250ms |
| Sheets and dialogs | M3 defaults |
| Artwork color resolving | 300ms color crossfade |
| Image load | 200ms crossfade from placeholder |
| Player controls | 150ms fade |

Respect "Remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`): swap shared elements and slides for instant cuts.

---

## 10. Words

- Name things the way a viewer would: "Continue watching", "Next up", "Mark as watched". Not "Resume queue" or "Set played state".
- Buttons say exactly what happens, and the confirmation uses the same verb: "Request" → "Requested".
- Errors state what failed and what to do, without apologizing: "Can't reach your server at media.example.com. Check that it's running, then try again." with a "Try again" button.
- Empty states invite an action: "No favorites yet. Tap the heart on anything you want to find again."
- Durations: "2h 12m", "24 min left". Episodes in running text: "Season 2, episode 5". Compact badges may use "S2 E5".

---

## 11. Accessibility

- Contrast: 4.5:1 for text, 3:1 for icons and progress bars, including over artwork (scrims exist for this).
- Touch targets: 48dp minimum, even when the icon is 24dp.
- Every icon-only button has a `contentDescription`. Decorative images use `null`.
- Poster cards merge semantics into one node: "The Matrix, 1999, 50% watched, button".
- Test with TalkBack, 1.3x font scale, and display size "Large" before merging UI changes.

---

## 12. Rules for coding agents

Do:
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and the `Spacing` local for every visual value.
- Reuse components from `ui/components/`. If a new one is needed, add it there with a `@Preview` in dark and light, at compact and medium widths.
- Reserve image space with the correct aspect ratio before load.
- Provide stable keys in every lazy list and grid.
- Hoist state; components take data and lambdas, never ViewModels.

Don't:
- Hard-code colors, font sizes, or dp values in screens.
- Add drop shadows, gradients as decoration, or all-caps text.
- Add a second filled button to a screen.
- Overlay titles on posters.
- Use full-screen spinners for content loading.
- Introduce a new animation without adding it to section 9.

---

## 13. Open decisions

- [ ] Confirm or replace the proposed palette and fonts with Cinefin's current ones.
- [ ] Decide whether AMOLED black mode ships as a setting.
- [ ] Music libraries: define now-playing bar and full player, or keep music out of scope.
- [ ] Downloads/offline: define the downloads screen and offline badge.
- [ ] Decide which tokens CinefinTV shares (likely color and type only).
