## Summary

- What does this PR change and why?
- Link related issues (e.g., Closes #123)

## Type of change

- [ ] feat (new feature)
- [ ] fix (bug fix)
- [ ] docs (documentation only)
- [ ] refactor (no functional change)
- [ ] perf (performance improvement)
- [ ] test (add/fix tests)
- [ ] chore (build, tooling, deps)

## Screenshots/GIFs (UI changes)

<!-- If UI changed, add before/after screenshots or a short GIF. -->

## How to test

```bash
# Build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Coverage (HTML under app/build/reports)
./gradlew jacocoTestReport
```

## Checklist

- [ ] Follows Conventional Commits
- [ ] Branch named appropriately (feature/..., bugfix/..., hotfix/..., docs/...)
- [ ] Tests added/updated where applicable
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew lintDebug` passes
- [ ] Docs updated (README/CHANGELOG as needed)
- [ ] Affected areas and testing notes included

