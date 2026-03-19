# TV Focus Contracts

This document starts Milestone 1 from `docs/plans/TV_ROADMAP.md` by defining explicit focus contracts for the core TV routes that are already implemented in code.

## `tv_home`
- **Initial focus target:** hero carousel when featured items exist; otherwise the first available content rail.
- **Left-edge behavior:** move focus to the navigation drawer.
- **Right-edge behavior:** stay within the current rail/hero content.
- **Back behavior:** defer to the app shell / system back behavior.
- **Empty-state focus target:** refresh action in the empty state.
- **Focus restore behavior:** restore the last focused item/rail inside the `tv_home` screen namespace.

## `tv_library/*`
- **Initial focus target:** the first visible grid card.
- **Left-edge behavior:** move focus to the navigation drawer.
- **Right-edge behavior:** continue within the grid when possible; otherwise stay on the current item.
- **Back behavior:** return to the previous route.
- **Empty-state focus target:** refresh action in the empty state.
- **Focus restore behavior:** restore the last focused grid item and scroll position for the library route key.

## `tv_search`
- **Initial focus target:** search text field.
- **Left-edge behavior:** from the filter row or results grid, move focus back toward the search field before exiting the screen.
- **Right-edge behavior:** continue through filters/results.
- **Back behavior:** return to the previous route with the current query preserved.
- **Empty-state focus target:** search field.
- **Focus restore behavior:** restore query, active filter, and the last focused result item.

## `tv_item/{itemId}`
- **Initial focus target:** primary play/resume action.
- **Left-edge behavior:** stay within the action rail/related content.
- **Right-edge behavior:** continue through actions and related rails.
- **Back behavior:** return to the launching route and restore focus to the launching item.
- **Empty-state focus target:** primary action, or back if content failed to load.
- **Focus restore behavior:** restore the last focused action or related item when returning from nested navigation.

## `tv_settings`
- **Initial focus target:** the first settings destination/action.
- **Left-edge behavior:** move focus to the navigation drawer when the shell is visible.
- **Right-edge behavior:** continue through settings content.
- **Back behavior:** close nested panels first, then return to the previous route.
- **Empty-state focus target:** the first available settings action.
- **Focus restore behavior:** restore the last focused settings item within the route.

## Sign-in routes
### `tv_server_connection`
- **Initial focus target:** the first incomplete text field, otherwise the connect action.
- **Left-edge behavior:** stay within the form.
- **Right-edge behavior:** stay within the form.
- **Back behavior:** system default.
- **Empty-state focus target:** server URL field.
- **Focus restore behavior:** restore the last focused form field or button.

### `tv_quick_connect`
- **Initial focus target:** server URL field until configured, otherwise the get-code action.
- **Left-edge behavior:** stay within the form.
- **Right-edge behavior:** stay within the form.
- **Back behavior:** cancel quick connect and return to the previous route.
- **Empty-state focus target:** server URL field.
- **Focus restore behavior:** restore the last focused form field or button.
