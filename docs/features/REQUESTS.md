# Media Requests

## Overview

The Requests feature lets a user browse for content that isn't in their
Jellyfin library yet and request it be added, without touching a browser or a
separate app. It is the largest screen in the codebase
(`RequestsScreen.kt`, ~1,841 lines) and is entirely user-configured — there is
no feature flag gating it. The "Requests" tab in the bottom navigation is
hidden until the user has enabled and configured at least one supported
backend in Settings.

## Backends

There is no single "Requests API." The app talks directly to up to three
independent, optional, self-hosted services, plus one Jellyfin server plugin
used only for credential import:

| Backend | Purpose | Files |
|---|---|---|
| **Jellyseerr / Overseerr** | Primary path: search, discover (trending/upcoming/popular), submit requests, view request history, cancel a request | `data/repository/SeerrRepository.kt`, `SeerrApiService.kt`, `data/model/SeerrModels.kt` |
| **Sonarr** | Direct alternative to Jellyseerr for requesting TV shows (quality-profile lookups, series add) | `data/repository/SonarrRepository.kt`, `SonarrApiService.kt` |
| **Radarr** | Direct alternative to Jellyseerr for requesting movies (quality-profile lookups, movie add) | `data/repository/RadarrRepository.kt`, `RadarrApiService.kt` |
| **"Cinefin" Jellyfin plugin** (optional) | A custom, self-hosted Jellyfin server plugin used **only** to auto-import Seerr/Sonarr/Radarr URLs and API keys onto the device — see `RequestsViewModel.kt` around lines 196–244. Actual request traffic never goes through it; the app calls Seerr/Sonarr/Radarr directly. | `data/repository/CinefinPluginRepository.kt`, `CinefinPluginApiService.kt`, `data/model/CinefinPluginModels.kt` |

Jellyseerr/Overseerr auth uses an `X-Api-Key` header against a user-supplied
base URL. Sonarr/Radarr are the same shape (base URL + API key). The Cinefin
plugin reuses the app's existing authenticated Jellyfin `OkHttpClient` since
it lives on the same server.

## Configuring it

Settings → **Media Requests** (`ui/screens/settings/MediaRequestSettingsScreen.kt`,
route `media_request_settings`) is the primary configuration screen, with
independently toggleable sections for Seerr/Overseerr/Jellyseerr, Sonarr, and
Radarr — each with a URL field, API key field, enable toggle, and a
"test connection" action — plus a "Cinefin Server Plugin" section that can
auto-import all three services' settings in one step if the plugin is
installed on the user's Jellyfin server.

There's also a simpler, older `SeerrSettingsScreen.kt` (Jellyseerr-only)
still reachable via the `SeerrSettings` route; `MediaRequestSettingsScreen`
is the one to extend for new backend options.

API keys are stored via `EncryptedPreferences` (AES-256-GCM / Android
Keystore); URLs and enabled-flags go through a plain DataStore
(`data/preferences/SeerrPreferences.kt`, `ArrPreferences.kt` +
their `*PreferencesRepository` wrappers).

## Visibility (no feature flag)

`core/FeatureFlags.kt` and `RemoteConfigRepository` have nothing
Requests-related — this is not remote-config gated. Instead,
`ui/JellyfinApp.kt` computes:

```kotlin
requestsEnabled = seerrPreferences.isEnabled ||
    isPluginConfigured ||
    (sonarrPrefs.isValid && sonarrPrefs.isEnabled) ||
    (radarrPrefs.isValid && radarrPrefs.isEnabled)
```

and `BottomNavItem.bottomNavItems()` (`ui/navigation/NavRoutes.kt`) simply
omits the "Requests" tab when this is `false`. TV has its own entry point,
`ui/screens/tv/TvRequestsScreen.kt`.

## What a user can actually do

From `RequestsViewModel.kt` / `RequestsScreen.kt`:

- Search the Jellyseerr catalog (debounced ~500ms) or browse discover rails
  (trending, upcoming movies/TV, popular movies/TV) when there's no active
  query.
- Request a movie, or a TV season/episode, through whichever backend(s) are
  configured — with quality-profile and 4K selection where supported.
- See per-item TV availability, cross-checked against the local Jellyfin
  library plus anything already requested (concurrency-limited to 4 parallel
  checks via a `Semaphore`).
- View "My Requests" with status (Pending / Approved / Declined) and cancel a
  pending request.

There is **no** approve/deny UI anywhere in the app — this is a
requester-only surface. Approvals happen in the Jellyseerr admin panel
itself, outside this app.

## Maturity

Fully wired end-to-end across ~4,600 lines (11 core files); no
`TODO`/`FIXME` markers in any Requests file, and both `RequestsViewModel`
and `MediaRequestSettingsViewModel` have unit test coverage
(`RequestsViewModelTest.kt`, `MediaRequestSettingsViewModelTest.kt`). This
was simply missing a doc entry, not missing functionality.
