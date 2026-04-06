#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT_DIR/app/build.gradle.kts"
BUNDLE_PATH="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
GRADLE_RELEASE_CMD=("./gradlew" "--no-daemon" "bundleRelease")

SKIP_PUSH=0
SKIP_RELEASE=0
DRY_RUN=0
SHOW_CHANGELOG=1
YES=0
TAG_PREFIX="v"

usage() {
  cat <<'EOF'
Usage: ./scripts/release-android.sh [options]

Bumps Android release versions, builds a signed AAB, commits the version bump,
creates a git tag, pushes the branch + tag, and publishes a GitHub release with
generated release notes.

Options:
  --skip-push     Do not push the commit/tag to the remote.
  --skip-release  Do not create the GitHub release.
  --dry-run       Print the planned release actions without changing anything.
  --no-changelog  Skip the generated changelog preview.
  --yes           Skip the confirmation prompt.
  --tag-prefix X  Use a custom tag prefix instead of "v".
  -h, --help      Show this help text.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --allow-dirty)
      echo "--allow-dirty is no longer needed; the script now prompts when the tree is dirty." >&2
      ;;
    --skip-push)
      SKIP_PUSH=1
      ;;
    --skip-release)
      SKIP_RELEASE=1
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --no-changelog)
      SHOW_CHANGELOG=0
      ;;
    --yes)
      YES=1
      ;;
    --tag-prefix)
      shift
      [[ $# -gt 0 ]] || { echo "Missing value for --tag-prefix" >&2; exit 1; }
      TAG_PREFIX="$1"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_command git
require_command perl
require_command gh

if [[ ! -f "$BUILD_FILE" ]]; then
  echo "Build file not found: $BUILD_FILE" >&2
  exit 1
fi

cd "$ROOT_DIR"

dirty_status="$(git status --porcelain)"
include_dirty_changes=0
if [[ -n "$dirty_status" ]]; then
  echo "Working tree has uncommitted changes."
  echo "$dirty_status"
  echo
  if [[ $DRY_RUN -eq 1 || $YES -ne 1 ]]; then
    read -r -p "Include the current changes in the release commit? [y/N] " include_dirty_confirm
    if [[ "$include_dirty_confirm" =~ ^[Yy]$ ]]; then
      include_dirty_changes=1
    else
      echo "Release cancelled. Commit or stash your changes first if you do not want them in the release."
      exit 1
    fi
  else
    echo "Refusing to auto-include dirty changes in non-interactive mode."
    echo "Run without --yes to confirm, or commit/stash first."
    exit 1
  fi
fi

current_branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$current_branch" == "HEAD" ]]; then
  echo "Detached HEAD is not supported for releases." >&2
  exit 1
fi

current_version_code="$(perl -ne 'print "$1\n" if /versionCode\s*=\s*(\d+)/' "$BUILD_FILE" | head -n1)"
current_version_name="$(perl -ne 'print "$1\n" if /versionName\s*=\s*"([^"]+)"/' "$BUILD_FILE" | head -n1)"

if [[ -z "$current_version_code" || -z "$current_version_name" ]]; then
  echo "Failed to read versionCode/versionName from $BUILD_FILE" >&2
  exit 1
fi

if [[ "$current_version_name" =~ ^(.*[^0-9])?([0-9]+)$ ]]; then
  version_prefix="${BASH_REMATCH[1]}"
  version_suffix="${BASH_REMATCH[2]}"
else
  echo "versionName '$current_version_name' does not end in a numeric segment to increment." >&2
  exit 1
fi

new_version_code=$((current_version_code + 1))
new_version_suffix=$((10#$version_suffix + 1))
new_version_name="${version_prefix}${new_version_suffix}"
new_tag="${TAG_PREFIX}${new_version_name}"
commit_message="release: ${new_tag}"

if git rev-parse "$new_tag" >/dev/null 2>&1; then
  echo "Tag already exists locally: $new_tag" >&2
  exit 1
fi

if gh release view "$new_tag" >/dev/null 2>&1; then
  echo "GitHub release already exists: $new_tag" >&2
  exit 1
fi

if [[ $SKIP_RELEASE -ne 1 ]]; then
  gh auth status >/dev/null
fi

repo_slug="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
if [[ -z "$repo_slug" ]]; then
  echo "Failed to resolve GitHub repository slug." >&2
  exit 1
fi

generate_release_notes() {
  local tag_name="$1"
  local target_commitish="$2"
  gh api \
    --method POST \
    -H "Accept: application/vnd.github+json" \
    "/repos/${repo_slug}/releases/generate-notes" \
    -f tag_name="$tag_name" \
    -f target_commitish="$target_commitish" \
    --jq .body
}

cat <<EOF
Release summary
  Repository:  $repo_slug
  Branch:      $current_branch
  Include dirty changes: $([[ $include_dirty_changes -eq 1 ]] && echo "yes" || echo "no")
  versionCode: $current_version_code -> $new_version_code
  versionName: $current_version_name -> $new_version_name
  Tag:         $new_tag
  Bundle:      $BUNDLE_PATH
EOF

if [[ $SHOW_CHANGELOG -eq 1 && $SKIP_RELEASE -ne 1 ]]; then
  echo
  echo "Generated changelog preview"
  echo "----------------------------------------"
  generate_release_notes "$new_tag" "$current_branch" || true
  echo "----------------------------------------"
fi

if [[ $DRY_RUN -eq 1 ]]; then
  echo
  echo "Dry run complete. No files, commits, tags, or releases were created."
  exit 0
fi

if [[ $YES -ne 1 ]]; then
  read -r -p "Proceed with release? [y/N] " confirm
  if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "Release cancelled."
    exit 0
  fi
fi

backup_file="$(mktemp)"
cp "$BUILD_FILE" "$backup_file"
release_committed=0

cleanup() {
  if [[ $release_committed -eq 0 && -f "$backup_file" ]]; then
    cp "$backup_file" "$BUILD_FILE"
  fi
  rm -f "$backup_file"
}
trap cleanup EXIT

perl -0pi -e "s/versionCode\\s*=\\s*\\d+/versionCode = $new_version_code/; s/versionName\\s*=\\s*\"[^\"]+\"/versionName = \"$new_version_name\"/;" "$BUILD_FILE"

if [[ $include_dirty_changes -eq 1 ]]; then
  git add -A
else
  git add "$BUILD_FILE"
fi
git commit -m "$commit_message"
release_committed=1

echo "Building signed release bundle..."
if ! "${GRADLE_RELEASE_CMD[@]}"; then
  cat >&2 <<EOF

Release build failed after creating the local release commit.
  Commit: $commit_message
  Branch: $current_branch

The version bump commit was intentionally kept in place because the release flow commits before building.
Fix the build issue, then rerun the release or adjust the commit manually if needed.
EOF
  exit 1
fi

if [[ ! -f "$BUNDLE_PATH" ]]; then
  echo "Release bundle not found after build: $BUNDLE_PATH" >&2
  exit 1
fi

git tag -a "$new_tag" -m "$new_tag"

if [[ $SKIP_PUSH -ne 1 ]]; then
  git push origin "$current_branch"
  git push origin "$new_tag"
fi

if [[ $SKIP_RELEASE -ne 1 ]]; then
  gh release create "$new_tag" "$BUNDLE_PATH" \
    --title "$new_tag" \
    --generate-notes
fi

echo
echo "Release complete."
echo "  versionCode: $new_version_code"
echo "  versionName: $new_version_name"
echo "  tag:         $new_tag"
echo "  bundle:      $BUNDLE_PATH"
