# Emby fixtures

Response bodies used by the tests in `data/emby/`.

| File | Source |
|------|--------|
| `episode_with_markers.json` | Hand-written in the shape Emby returns for `/Users/{userId}/Items/{id}?Fields=Chapters`. Replace with a capture from a real server (checklist A1). |
| `episode_without_markers.json` | Hand-written; an episode whose chapters carry no intro/credits markers. |
| `user_views_mixed.json` | Hand-written `/Users/{userId}/Views` with a mixed Movies + Shows library. Replace with a capture (checklist A2). |
