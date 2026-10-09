# Mobile server discovery and Emby Connect

This increment targets phone/tablet sign-in. TV layouts remain unchanged.

- LAN discovery broadcasts `Who is JellyfinServer?` and `who is EmbyServer?` on UDP 7359,
  on separate sockets because replies do not identify the product. Each result carries its
  server type. Sockets close on cancellation/errors and scans have a fixed receive deadline.
- Mobile sign-in offers Emby Connect account login, linked-server selection, and a
  Skip — enter server manually action. Manual local Emby user authentication remains available.
- The Connect service returns local/remote addresses. Selection tries local then remote,
  verifies the server ID before sending the linked access key, exchanges it for a local token,
  and reads the local user. The account password and cloud token are not persisted.
- The per-server Connect user ID/access key are saved with the existing app-private session
  token/profile storage when Remember Login is enabled. They are used to exchange a fresh token
  on online launch, profile switching and re-authentication. Removing a profile removes its key.
- Account calls use a separate HTTPS client. Exchanges preserve server TLS/pinning/connectivity
  settings but omit the running-session auth interceptor, authenticator and HTTP logger.
  Credential-bearing requests do not follow redirects.
- Known Emby servers do not show Jellyfin Quick Connect. Manual URLs are probed after a debounce;
  the Quick Connect action also checks the server product before calling Jellyfin endpoints.
- Emby Connect entry follows the existing `enable_emby_support` rollout flag. Release support
  remains disabled by default.

Protocol references:
- https://dev.emby.media/doc/restapi/Locating-the-Server.html
- https://dev.emby.media/doc/restapi/Emby-Connect.html

## Validation

Added regression tests for discovery payloads/product identity, linked-server parsing,
exchange routing/authentication headers, rejection of mismatched server IDs and saved profiles.
`git diff --check` passes. The focused Gradle unit-test run could not start because the
Gradle distribution download is blocked by this environment's network access. No build or
live-server pass is claimed.

Before release: run the tests and try both servers on a phone, including LAN discovery,
Connect account sign-in, remote fallback, manual entry after Skip, restart, profile switching,
Remember Login off, invalid credentials, unavailable servers and Jellyfin Quick Connect.
