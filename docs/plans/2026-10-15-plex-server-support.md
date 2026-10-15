# Plan: Plex Server Support

**Date**: 2026-10-15
**Status**: Draft for review. Nothing is implemented yet. Phase 0 (spike against a real Plex Media
Server) decides the open items marked **[P0]**.

## Approach

Add Plex as a third backend using the seam Emby proved: a `PlexApiClient` that subclasses the
Jellyfin SDK's `ApiClient` and answers the SDK's typed calls by talking to Plex, returning
Jellyfin-shaped models. `OptimizedClientFactory` hands it out for a Plex session, so repositories,
ViewModels and screens keep consuming `BaseItemDto`.

The difference from Emby is size, not shape. Emby is a sibling of Jellyfin, so a route table plus a
JSON *normalizer* was enough. Plex shares nothing: different routes, a `MediaContainer`/`Metadata`
response shape, millisecond times, numeric `ratingKey`s, account-based sign-in through plex.tv, and a
different transcoder. So `PlexApiClient` needs real per-route *handlers* and a hand-written
Plex → `BaseItemDto` mapper, and the URL builders that bypass `request()` (images, streams) need a
Plex branch.

## Goal and success criteria

Done means: a user signs in with a Plex account, picks one of their servers (owned or shared), and
it appears as a saved profile alongside Jellyfin and Emby ones; they can browse libraries, see home
rows, open a movie or episode, play it (direct play and transcode) with progress and watched state
visible in Plex's own apps, on the phone UI, with Jellyfin and Emby behaviour unchanged.

Evidence:

- `./gradlew.bat testDebugUnitTest` passes, including new `data/plex` tests driven by JSON fixtures
  captured from a real PMS (mapper, query translation, ID codec, timeline reporting).
- Manual checklist on a real PMS (Phase 4 exit): PIN sign-in, server picker, home rows populate,
  open a movie, direct play, forced transcode, resume position and watched state visible in
  app.plex.tv.
- The same checklist on Jellyfin and Emby shows no regression.

## Scope

- **In (first release)**: plex.tv PIN sign-in, server list from the account (owned and shared),
  best-connection selection (local / remote / relay), Plex profiles in the existing switcher,
  movie and TV libraries, home rows (continue watching, next up / on deck, recently added),
  item details, images, search, similar items, played/unplayed, video playback (direct play and
  transcode), progress reporting, intro/credits skip from Plex markers, feature gating, phone and
  tablet UI.
- **Out (first release)**: favorites (Plex has no per-item favorite; see KD7), Plex Home user
  switching, watchlist / Discover, Live TV & DVR, SyncPlay, Cinefin server plugin, admin features
  beyond what the app already gates, Seerr-style requests through Plex.
- **Later phases**: Android TV UI in this app, Chromecast, downloads, music, photos, playlists,
  collections, GDM LAN discovery, Plex Home users, CinefinTV.

## What the code looks like today (verified 2026-10-15, commit `01146c4`)

| Fact | Where |
|------|-------|
| `BaseItemDto` appears 1,062 times across 124 files; 110 of 299 UI files import the SDK | UI, ViewModels, repositories |
| SDK API call sites live in 6 repository classes plus `ConnectionOptimizer` | `data/repository/` |
| Distinct SDK endpoints used (excluding SyncPlay / Quick Connect): about 20 | see table under KD3 |
| `libraryApi.getItems` is called 32 times, with 15 distinct parameters (`userId`, `includeItemTypes`, `recursive`, `sortBy`, `fields`, `sortOrder`, `limit`, `parentId`, `startIndex`, `searchTerm`, `filters`, `ids`, `personIds`, `artistIds`, `albumArtistIds`) | repositories |
| `EmbyApiClient` subclasses `ApiClient`, intercepts `request()`, has a route table of ~15 entries | `data/emby/EmbyApiClient.kt` |
| `OptimizedClientFactory` picks the Emby client by `ServerType` of the active session | `di/OptimizedClientFactory.kt:45-62` |
| `ServerType` is an enum (`JELLYFIN`, `EMBY`) with capability getters (`supportsSyncPlay`, `supportsCinefinPlugin`) | `data/model/ServerType.kt` |
| `ServerProfile` already carries `serverType` and Emby Connect fields | `data/model/ServerProfile.kt` |
| Item IDs are `UUID`s; `ServerIdCodec` maps numeric Emby IDs to marker UUIDs (`EMBY` prefix) and back | `data/emby/ServerIdCodec.kt` |
| Image and stream URLs are string-built (16 sites) and bypass `request()` | `JellyfinStreamRepository`, `VideoPlayerMetadataManager` |
| Outbound URLs are decoded once, centrally, in the auth interceptor (`ServerIdCodec.decodeAllIn`) | `network/JellyfinAuthInterceptor.kt:71` |
| Interceptor sends `X-Emby-Token` / `MediaBrowser` auth headers on every request | `network/JellyfinAuthInterceptor.kt` |
| Time values are in 100 ns ticks throughout (104 `ticks` references) | app-wide |
| Emby is behind `FeatureFlags.Experimental.ENABLE_EMBY_SUPPORT` | `core/FeatureFlags.kt:64` |

## Plex API facts and assumptions

### Verified from Plex's published material (2026-10-15)

- Plex now publishes an OpenAPI description of the PMS API at
  [developer.plex.tv/pms](https://developer.plex.tv/pms/). Plex says it is a foundation, not yet a
  complete mirror of the server. It covers `/library/sections`, `/library/metadata` (with
  `/children`, `/grandchildren`, `/images`), playlists, and `/photo/:/transcode`, but **not** the
  transcoder decision/start endpoints, hubs/on-deck paths, or `/api/v2/resources`.
- JSON is available with `Accept: application/json` (default is XML); Plex says new apps should
  use JSON.
- `X-Plex-Client-Identifier` is typically required; `X-Plex-Token` authenticates. `X-Plex-Product`,
  `-Version`, `-Platform`, `-Device` are customary. All X-Plex headers can also go in the query
  string (which is what image and stream URLs need).
- Sign-in: `POST https://clients.plex.tv/api/v2/pins`, the user approves the code, the app polls
  `GET /api/v2/pins/{id}`. Plex has added a **JWT** variant: the app registers an Ed25519 JWK when
  creating the PIN and signs a device JWT to collect a token; the JWT lasts about 7 days and is
  refreshed with a nonce flow (`/api/v2/auth/jwk`, `/auth/nonce`, `/auth/token`). Per a Plex forum
  thread, the PIN poll still returns a legacy `authToken` when no JWK is supplied, and that token
  still works against PMS. Whether that fallback will stay is not stated.
- **Remote streaming paywall**: since Nov 2025, streaming a personal server from outside the home
  network requires Plex Pass (owner) or Remote Watch Pass (viewer). Enforcement started on Roku,
  extends to other TV platforms in 2026, and a Plex developer said it will reach third-party API
  clients that offer remote streaming in 2026. Local streaming is unaffected.

### From memory, NOT yet verified **[P0]**

- `GET https://clients.plex.tv/api/v2/resources?includeHttps=1&includeRelay=1` lists the account's
  servers, each with a per-server `accessToken` (differs from the account token on shared servers)
  and a list of `connections` (`local`, `relay`, `protocol`, `uri`).
- `GET /identity` on a PMS returns `machineIdentifier` and `version` without auth; Jellyfin/Emby
  `/System/Info/Public` 404s on Plex. Default port 32400.
- Browsing: `/library/sections` (types `movie`, `show`, `artist`, `photo`),
  `/library/sections/{id}/all?type=&sort=&X-Plex-Container-Start=&X-Plex-Container-Size=`,
  `/library/metadata/{ratingKey}` (with `includeChapters=1`, `includeMarkers=1`,
  `includeExtras=1`), `/children`, `/allLeaves`, `/similar`.
- Home rows: `/hubs/continueWatching` (or `/hubs/home/continueWatching`), `/library/onDeck`,
  `/library/recentlyAdded`, `/library/sections/{id}/recentlyAdded`, `/hubs` for promoted hubs.
- Search: `/hubs/search?query=&limit=`.
- Watched state: `/:/scrobble` and `/:/unscrobble?identifier=com.plexapp.plugins.library&key={ratingKey}`.
- Progress: `/:/timeline?ratingKey=&key=&state=playing|paused|stopped&time=&duration=` (ms),
  every ~10 s and on state change.
- Playback: direct play from `Part.key` (`/library/parts/{id}/{changedAt}/file.{ext}`);
  transcode via `/video/:/transcode/universal/decision` then
  `/video/:/transcode/universal/start.m3u8` with `path=/library/metadata/{ratingKey}`, `session`,
  `directPlay`, `directStream`, `maxVideoBitrate`, `videoResolution`, `subtitles`, and a client
  profile (`X-Plex-Client-Profile-Extra` / `X-Plex-Client-Profile-Name`).
- Stream selection: `PUT /library/parts/{partId}?audioStreamID=&subtitleStreamID=` (persists per
  user).
- Images: `thumb`, `art`, `clearLogo` (newer PMS), resized with
  `/photo/:/transcode?url={path}&width=&height=`.
- Delete: `DELETE /library/metadata/{ratingKey}`; refresh: `/library/sections/{id}/refresh`.
- Intro and credits markers come back in metadata as `Marker` entries (`type=intro|credits`,
  `startTimeOffset`, `endTimeOffset`).

## Key Decisions (review first)

### KD1. Keep `BaseItemDto` as the app's item model

- **Choice**: same as Emby KD1. Plex data is mapped into SDK models (`BaseItemDto`,
  `PlaybackInfoResponse`, `MediaSourceInfo`, `MediaSegmentDto`, `UserDto`).
- **Discarded**: Cinefin-owned domain models. Still 1,062 uses in 124 files; a third backend
  doesn't change that arithmetic.
- **Cost**: Plex-only data (ratings sources, `Guid`s, editions, hub metadata) is dropped or
  squeezed into existing fields. If a fourth backend ever comes, revisit.

### KD2. Generalize `ServerIdCodec` with a per-backend marker

- **Choice**: move `ServerIdCodec` out of `data/emby` into `data/common/ids`, parameterized by a
  4-byte marker (`EMBY`, `PLEX`). Plex `ratingKey`s, part IDs and tag (person/genre) IDs are
  numeric, so they encode exactly like Emby IDs. `decodeAllIn` in the interceptor handles both
  markers.
- **Why**: one proven mechanism, already wired into the interceptor and tested.
- **Plex wrinkle**: people are tags, not items. Encode Plex tag IDs under a second marker
  (`PLXT`) so `getItemsByPerson(personId)` can tell a tag ID from a ratingKey and turn it into an
  `actor=` filter.

### KD3. `PlexApiClient`: per-route handlers inside the SDK seam

- **Choice**: `PlexApiClient : ApiClient` with a route table keyed on the SDK `pathTemplate`, like
  `EmbyRoute`, but each entry is a **handler** that makes one or more Plex calls and returns
  Jellyfin-shaped JSON built by `PlexMapper`. Repositories are untouched.
- **Discarded**: branching each repository method to a `PlexDataSource` (the original Emby KD4).
  Emby showed the seam beats ~80 branches; the mapper work is identical either way.
- **Routes to implement** (the app's whole SDK surface, minus SyncPlay / Quick Connect /
  MediaSegments-via-API which are handled by KD7):

  | SDK call | Plex |
  |----------|------|
  | `systemApi.getPublicSystemInfo` / `getSystemInfo` | `/identity`, `/` (synthesize `ProductName = "Plex Media Server"`) |
  | `userApi.getCurrentUser` | plex.tv `/api/v2/user` (cached at sign-in) |
  | `userViewApi.getUserViews` | `/library/sections` → `CollectionFolder` items |
  | `libraryApi.getItems` | query planner, see KD4 |
  | `libraryApi.getSimilarItems` | `/library/metadata/{rk}/similar` |
  | `libraryApi.getLocalTrailers` | `/library/metadata/{rk}/extras` filtered to trailers |
  | `libraryApi.deleteItem` / `refreshLibrary` | `DELETE /library/metadata/{rk}` / `/library/sections/all/refresh` |
  | `showApi.getNextUp` | `/library/onDeck` (or continueWatching hub), episodes only |
  | `userDataApi.markPlayedItem` / `markUnplayedItem` | `/:/scrobble` / `/:/unscrobble` |
  | `userDataApi.getItemUserData` | from `/library/metadata/{rk}` (`viewCount`, `viewOffset`) |
  | `userDataApi.mark/unmarkFavoriteItem` | not supported (KD7) |
  | `mediaInfoApi.getPostedPlaybackInfo` | `/video/:/transcode/universal/decision` → `PlaybackInfoResponse` (KD5) |
  | `sessionApi.reportPlaybackStart/Progress/Stopped` | `/:/timeline` (ticks → ms, state mapping) |
  | `sessionApi.getSessions` | `/status/sessions` (admin) or empty |
  | `mediaSegmentApi.getItemSegments` | `Marker`s from `/library/metadata/{rk}?includeMarkers=1` |
  | `authenticationApi.*` | not routed; sign-in is its own flow (KD6) |

- **Unhandled routes**: return a canned empty result and log once. In debug builds, throw, so a
  new SDK call added later for Jellyfin fails loudly in Plex testing instead of silently showing
  nothing.

### KD4. Translate `getItems` with a small query planner

`getItems` is the one route where Jellyfin's flexible query has to become a specific Plex
endpoint. Rather than a generic translator, recognise the shapes the app actually sends (32 call
sites, 15 parameters) and map each:

| Shape the app sends | Plex request |
|---------------------|--------------|
| `parentId` = library, `recursive`, `includeItemTypes` = Movie/Series, sort/paging | `/library/sections/{id}/all?type=1|2&sort=&X-Plex-Container-Start/Size` |
| `parentId` = series | `/library/metadata/{rk}/children` (seasons) |
| `parentId` = season | `/library/metadata/{rk}/children` (episodes) |
| `parentId` = series, `includeItemTypes` = Episode, `recursive` | `/library/metadata/{rk}/allLeaves` |
| `ids` | `/library/metadata/{rk1},{rk2},…` |
| `searchTerm` | `/hubs/search?query=` flattened, filtered by type |
| `filters` = IsResumable | `/hubs/continueWatching` |
| `sortBy` = DateCreated desc (recently added) | `/library/recentlyAdded` or section `recentlyAdded` |
| `filters` = IsFavorite | empty (KD7) |
| `personIds` | `/library/sections/{id}/all?actor={tagId}` across movie/show sections |
| `artistIds` / `albumArtistIds` | out of scope until music phase → empty + log |

- Sort mapping: `SortName`→`titleSort`, `DateCreated`→`addedAt`, `PremiereDate`→
  `originallyAvailableAt`, `CommunityRating`→`audienceRating`, `Random`→`random`,
  `DatePlayed`→`lastViewedAt`; order as `:desc`.
- `fields` is ignored (Plex returns what it returns); details calls always add
  `includeChapters=1&includeMarkers=1`.
- `TotalRecordCount` from Plex `totalSize` (requests send `X-Plex-Container-Size` so Plex reports
  it) — the paging sources depend on it.
- Planner lives in `PlexQueryPlanner` with a table-driven unit test per row above, using the
  actual parameter combinations captured from the repositories.

### KD5. Playback goes through a decision step, mapped to `PlaybackInfoResponse`

- **Choice**: `getPostedPlaybackInfo` calls the universal transcoder **decision** endpoint with a
  client profile built from the app's existing `JellyfinDeviceProfile` codec support. The result
  becomes one `MediaSourceInfo`:
  - direct play → `SupportsDirectPlay = true`, `Path`/direct URL = `Part.key` +
    `X-Plex-Token` + `download=0`.
  - transcode / direct stream → `SupportsTranscoding = true`,
    `TranscodingUrl = /video/:/transcode/universal/start.m3u8?…&session={uuid}`,
    `TranscodingSubProtocol = hls`.
  - `MediaStreams` from `Part.Stream[]` (streamType 1/2/3 → Video/Audio/Subtitle), indices kept so
    the existing track pickers work; `PlaySessionId` = the Plex `session` UUID.
- Audio/subtitle switch: `PUT /library/parts/{partId}?audioStreamID=&subtitleStreamID=` then
  re-request the decision, the same restart-the-stream pattern the Emby plan uses for
  progressive transcodes.
- `JellyfinStreamRepository` URL builders get a Plex branch for direct, transcode and download,
  each delegating to a `PlexUrlBuilder` so Plex URL knowledge stays in `data/plex`.
- On stop, also `GET /video/:/transcode/universal/stop?session=` so the server's transcode
  session doesn't linger.
- **[P0]** Confirm the decision endpoint's request/response on the test server, since it isn't in
  Plex's published spec.

### KD6. Sign-in: plex.tv PIN, then pick a server; one profile per (account, server)

- **Flow**: "Sign in with Plex" on the add-server screen → app creates a PIN → shows the 4-character
  code and opens `https://app.plex.tv/auth#?clientID=…&code=…` in a Custom Tab (with
  "or go to plex.tv/link" as a fallback for TV later) → polls until approved → fetches
  `/api/v2/resources` → lists servers (owned and shared, with online state) → user picks one →
  app picks the best connection (try local HTTPS, then remote, then relay, short timeouts in
  parallel) → profile saved.
- **Profile**: `ServerProfile` gains `plexAccountToken` (encrypted like other tokens, used only
  for plex.tv), `plexClientIdentifier` is app-wide; `accessToken` holds the **server** token from
  the resource; `serverId` = `machineIdentifier`; `serverUrl` = chosen connection URI. Store the
  other connection URIs too so a later network change can re-select without re-sign-in.
- **Token type** **[P0 decision]**: start with the legacy PIN token (works today, simplest). Spike
  the JWT/JWK flow in Phase 0: if Android Keystore Ed25519 (or Tink) works on our minSdk, ship
  JWT from the start to avoid a forced migration; otherwise ship legacy and track JWT as a
  follow-up with a refresh path already in `JellyfinAuthRefreshManager`'s shape.
- **Manual URL entry**: typing a Plex URL in the existing field detects Plex (`/identity`) and
  routes the user to "Sign in with Plex" instead of asking for a password. No username/password
  sign-in to Plex.
- Removing a Plex profile: sign-out call **[P0: confirm endpoint]** and delete its tokens.

### KD7. Capabilities: extend `ServerType`, gate what Plex lacks

`ServerType.PLEX`, and the existing getters grow:

| Capability | Jellyfin | Emby | Plex |
|------------|----------|------|------|
| `supportsSyncPlay` | yes | no | no |
| `supportsCinefinPlugin` | yes | no | no |
| `supportsQuickConnect` | yes | no | no |
| `supportsFavorites` | yes | yes | **no** (hide heart, hide Favorites row) |
| `supportsMediaSegments` | yes | later | yes (from markers) |
| `supportsPasswordSignIn` | yes | yes | no (PIN only) |
| `remoteStreamingMayRequirePass` | no | no | yes |

- **Favorites**: Plex has no per-item favorite. Watchlist is a plex.tv Discover feature with
  different IDs; mapping it is a later phase. Hide for v1 rather than half-work.
- **Remote paywall**: map Plex's refusal for remote playback to a specific, plainly worded error
  ("This server needs Plex Pass or Remote Watch Pass for streaming outside its home network")
  instead of a generic playback failure. **[P0]** capture what the refusal looks like (status code
  / body) with a non-Pass account off-LAN.

### KD8. Interceptor and HTTP headers

- `JellyfinAuthInterceptor` branches on the active profile's type: for Plex it sends
  `X-Plex-Token`, `X-Plex-Client-Identifier`, `X-Plex-Product=Cinefin`, `X-Plex-Version`,
  `X-Plex-Platform=Android`, `X-Plex-Device`, `Accept: application/json`, and **not** the Emby
  headers. URLs handed to ExoPlayer, Coil and Cast carry `X-Plex-Token` as a query parameter.
- plex.tv calls (`clients.plex.tv`) use a separate `PlexTvClient` with the account token, never
  the server token, and never the Jellyfin interceptor.

### KD9. Ship behind a Remote Config flag

`enable_plex_support` in `FeatureFlags.Experimental`, default **off** until Phase 4 exit. When
off, a Plex URL gets "Plex support is not available yet" instead of a Jellyfin version error.

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `data/common/ids/ServerIdCodec.kt` | Move + generalize | Marker-based numeric ID ↔ UUID for Emby and Plex |
| `data/plex/PlexApiClient.kt` | New | `ApiClient` subclass; route table of handlers |
| `data/plex/PlexQueryPlanner.kt` | New | `getItems` parameters → Plex request |
| `data/plex/PlexMapper.kt` | New | Plex `Metadata`/`Directory`/`Media`/`Part`/`Stream`/`Marker` → SDK models |
| `data/plex/PlexModels.kt` | New | `@Serializable` Plex DTOs (only the fields we use) |
| `data/plex/PlexUrlBuilder.kt` | New | Image, direct, transcode, download URLs |
| `data/plex/PlexTvClient.kt` | New | PIN, resources, user (plex.tv) |
| `data/plex/PlexConnectionSelector.kt` | New | Race the resource connections, pick best |
| `data/plex/PlexAuthDataSource.kt` | New | PIN flow state machine, token handling |
| `data/plex/PlexTimelineReporter.kt` | New (or inside handler) | Ticks → ms, state mapping, transcode stop |
| `data/model/ServerType.kt` | Modify | `PLEX`, detection via `/identity`, capability getters |
| `data/model/ServerProfile.kt` | Modify | Plex account token, alternate connection URIs |
| `di/OptimizedClientFactory.kt` | Modify | Hand out `PlexApiClient` for Plex sessions |
| `network/JellyfinAuthInterceptor.kt` | Modify | Plex header set; decode both markers |
| `data/repository/JellyfinStreamRepository.kt` | Modify | Delegate to `PlexUrlBuilder` for Plex |
| `ui/player/VideoPlayerMetadataManager.kt` | Modify | Plex image URL path |
| `ui/screens/ServerConnectionScreen.kt` + `ServerConnectionViewModel.kt` | Modify | "Sign in with Plex" entry, PIN screen, server picker |
| `ui/components/PlexSignInCard.kt`, `PlexServerPicker.kt` | New | PIN code display, server list |
| `ui/components/ServerTypeBadge.kt` | Modify | Plex badge |
| `core/FeatureFlags.kt` | Modify | `ENABLE_PLEX_SUPPORT` |
| `app/src/test/.../data/plex/*` + `resources/plex/*.json` | New | Fixture-driven tests |

## Phase 0: Spike and fixtures

Needs a real PMS (yours) and ideally a second account with a **shared** server and **no** Plex
Pass.

1. PIN sign-in by hand (curl); capture `/api/v2/pins`, `/api/v2/resources`, `/api/v2/user`.
2. Try the JWT/JWK variant; check Ed25519 availability on minSdk (Keystore vs Tink). Decide KD6.
3. Capture JSON for every route in the KD3 table, plus each `getItems` shape in KD4, into
   `app/src/test/resources/plex/`. Download the current OpenAPI spec from developer.plex.tv and
   keep the version it was fetched from.
4. Capture the transcode decision for: an h264/aac MP4 (direct play), an HEVC/TrueHD MKV
   (transcode), a file with image subtitles (burn-in). Confirm the `start.m3u8` parameters.
5. Confirm `Marker`s appear with `includeMarkers=1` and their units.
6. From off-LAN with the non-Pass account, record what remote playback refusal looks like.
7. Prototype: `PlexMapper` for one movie + one episode into `BaseItemDto`, and feed it to the
   existing details screen. If anything in the UI depends on a field Plex can't supply, list it.

**Exit**: every "[P0]" item answered and written back into this plan; KD3/KD4 still hold, or
revised.

## Phase 1: Plumbing (no user-visible change)

- Generalize `ServerIdCodec` (Emby tests keep passing unchanged; add Plex marker tests).
- `ServerType.PLEX` + capability getters; detection from `/identity`.
- `ServerProfile` fields with migration defaults (existing profiles load unchanged).
- `ENABLE_PLEX_SUPPORT` flag; Plex URL with flag off shows the "not available yet" message.
- Interceptor header branch (unit-tested with a fake profile).

**Exit**: all existing tests green; Jellyfin and Emby smoke test unchanged.

## Phase 2: Sign in and pick a server

- `PlexTvClient`, `PlexAuthDataSource` (PIN create → poll with backoff → token, cancelable,
  expires with the PIN).
- PIN UI: code, "Open plex.tv" button (Custom Tab), waiting state, expiry/retry.
- Server picker from resources: name, owner (yours / shared by X), online, connection type.
- `PlexConnectionSelector`: parallel probe of `/identity` on each connection URI, prefer local
  HTTPS → remote → relay; store all URIs.
- Profile saved and shown in the switcher with a Plex badge; switching uses the existing
  `switchProfile()` reset.

**Exit**: sign in, see servers, pick one, profile appears and survives app restart; switching to
a Jellyfin profile and back works.

## Phase 3: Browse

- `PlexModels` + `PlexMapper`: movie, show, season, episode, section, person (from `Role`),
  genres, studios, images (`ImageTags["Primary"]` = thumb timestamp so URLs cache-bust),
  `UserData` (`viewOffset` → `PlaybackPositionTicks`, `viewCount` → `Played`), runtime ms → ticks,
  `ParentId`/`SeriesId`/`SeasonId` from `parentRatingKey`/`grandparentRatingKey`.
- `PlexQueryPlanner` + handlers for views, items, details, similar, next up, search, played state.
- `PlexUrlBuilder` images via `/photo/:/transcode` sized to the app's requested max dims; Primary
  → `thumb`, Backdrop → `art`, Logo → `clearLogo` when present, series image for episodes →
  `grandparentThumb`.
- Hide favorites UI by capability.

**Exit**: home rows, library grids with paging, series → season → episode, details, search,
similar, mark played/unplayed all work on Plex.

## Phase 4: Play video

- Decision handler → `PlaybackInfoResponse` (KD5); direct play and HLS transcode URLs.
- Audio/subtitle selection via the parts `PUT` + re-decision.
- Timeline reporting on start, every 10 s, pause/resume, seek, stop; transcode stop on exit.
- Markers → `MediaSegmentDto` so the existing skip-intro/credits UI lights up.
- Resume from `viewOffset`.
- Remote paywall error mapping.

**Exit**: the manual checklist in "Goal and success criteria" passes; Jellyfin and Emby
checklists unchanged. Turn the flag on for debug builds.

## Phase 5: Gating, wording, polish

- Audit every Jellyfin-only entry point against the capability table (SyncPlay, plugin, Quick
  Connect, favorites, admin actions).
- Copy: replace "Jellyfin server" wording on shared screens with "server" where it now covers
  three products.
- Connection re-selection when the stored URI stops answering (e.g. leaving home Wi-Fi).

## Phase 6: Later increments (each its own plan entry)

Android TV UI in this app · Chromecast (Plex URLs are tokenised, so the receiver needs no Plex
knowledge, but `CastMediaLoadBuilder` needs the Plex branch) · downloads (`Part.key` with
`download=1`) · music · photos · playlists and collections · Plex Home user switching
(`/api/v2/home/users`, PIN-protected switch) · watchlist as favorites · GDM LAN discovery ·
JWT migration if Phase 0 chose legacy · CinefinTV.

## Phase 7: Rollout

Remote Config flag on for internal testers → beta track → default on. Watch Crashlytics for
`PlexApiClient` unhandled-route logs and mapper decode failures.

## Risks

| Risk | Mitigation |
|------|------------|
| Query planner misreads a `getItems` shape → wrong or empty rows | Table-driven tests from captured call sites; debug-build throw on unrecognised shapes |
| A new Jellyfin-side SDK call silently returns empty on Plex | Debug throw on unhandled routes; checklist run per release |
| Ticks vs milliseconds confusion (progress, markers, runtime) | Convert only in `PlexMapper` / timeline reporter; unit tests with known values |
| Plex token leaking to a Jellyfin/Emby host or vice versa | Interceptor chooses headers from the active profile's type and host; test |
| Transcoder decision endpoint is undocumented and may change | Fixtures + version noted; isolate in one handler |
| JWT becomes mandatory and legacy tokens stop working | Phase 0 decision; keep auth behind `PlexAuthDataSource` so it can swap |
| Remote streaming refused for non-Pass users from 2026 | Specific error message; local playback unaffected; don't promise remote in store copy |
| Shared-server token vs account token mix-up | Separate fields, separate clients (KD6, KD8) |
| `BaseItemDto` fields the UI assumes non-null that Plex can't fill | Phase 0 step 7 lists them before Phase 3 |

## Open Questions

1. Is favorites-hidden acceptable for v1, or should the heart map to Plex Watchlist from the start?
2. Should Plex Home (managed users) be in the first release? It's common in family setups.
3. JWT from day one if Phase 0 shows it's feasible, or legacy token first?
4. Do you have a non-Pass Plex account available for the Phase 0 remote-playback check?
5. Should this land in a shared module so CinefinTV can reuse `data/plex` later, or stay in `app`
   like Emby did?
