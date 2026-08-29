# Demo Mode — Instructions for Google Play Reviewers

This app requires a Jellyfin media server to sign in. Since reviewers don't have access to
a personal server, the app includes a self-contained **Demo Mode** that bypasses the server
connection step entirely and loads bundled sample content — no server, no account, and no
personal media required.

## What to paste into the Play Console "App access" / reviewer notes field

> On the initial sign-in screen, tap the "Server URL" field and type `demo.mode`, then tap
> Connect. This bypasses server setup and loads a self-contained Demo Mode with sample
> library content and a short bundled sample video you can play to test video playback.
> No account, password, or server address is required.

## How it works (for maintainers)

- Entering the keyword `demo.mode` (case-insensitive, whitespace-trimmed) into the server URL
  field on `ServerConnectionScreen` short-circuits `ServerConnectionViewModel.connectToServer()`
  before any network call — see `DemoModeRepository.isTriggerKeyword`.
- Demo Mode routes to `DemoHomeScreen`, a self-contained mock library grid with no server
  dependency.
- Tapping any item opens `DemoVideoPlayerScreen`, which plays a short (~20s) sample clip
  bundled directly in the APK at `app/src/main/res/raw/demo_sample_clip.mp4` — playback works
  fully offline and does not depend on any external server or CDN staying online.
- The bundled clip is a trimmed excerpt of Blender Foundation's "Big Buck Bunny"
  (CC BY 3.0, https://peach.blender.org); attribution is shown on-screen during playback.
- Exiting Demo Mode (back arrow) returns to the normal server connection screen; no real
  session or credentials are ever touched.

## Keeping this current

If the trigger keyword or flow ever changes, update the reviewer notes text above to match
before the next Play Console submission — stale instructions will cause the exact review
failure this feature exists to prevent.
