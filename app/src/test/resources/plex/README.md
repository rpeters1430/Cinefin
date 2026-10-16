# Plex test fixtures

`plextv/` holds plex.tv (`clients.plex.tv`) responses.

| File | Source |
|------|--------|
| `pin_create.json` | Real capture, 2026-10-15: `POST /api/v2/pins?strong=false` |
| `pin_poll_pending.json` | Real capture, 2026-10-15: `GET /api/v2/pins/{id}` before approval |
| `pin_not_found.json` | Real capture, 2026-10-15: `GET /api/v2/pins/{id}` for an unknown or expired PIN (404) |
| `unauthenticated.json` | Real capture, 2026-10-15: `/api/v2/user` and `/api/v2/resources` without a valid token (401) |
| `pin_poll_approved_standin.json` | Hand-written stand-in: a poll after approval (needs a real account to capture) |
| `user_standin.json` | Hand-written stand-in for `GET /api/v2/user` |
| `resources_standin.json` | Hand-written stand-in for `GET /api/v2/resources?includeHttps=1&includeRelay=1` |

In the real captures the generated client identifier is replaced with `cinefin-test-client` and the
IP geolocation block (`location`) is removed. Replace each stand-in with a real capture during the
Phase 0 spike (see `docs/plans/2026-10-15-plex-and-server-features-checklist.md`).

The PMS OpenAPI description Plex published on 2026-10-15 is in
`docs/reference/plex/pms-openapi-2026-10-15.json`.
