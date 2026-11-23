#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS_FILE="$ROOT_DIR/gradle.properties"

BUMP="patch"
DRY_RUN=0
REMOTE="origin"
BRANCH=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--major|--minor|--patch] [--remote <name>] [--branch <name>] [--dry-run]

Rules:
- If APP_VERSION_NAME is pre-release (x.y.z-rcN / -alphaN / -betaN), bump N by 1.
- Otherwise default bump is patch; can override with --major or --minor.
- APP_VERSION_CODE is always incremented by 1.

Examples:
  $(basename "$0")
  $(basename "$0") --minor
  $(basename "$0") --major --remote origin --branch main
  $(basename "$0") --dry-run
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --major)
      BUMP="major"
      shift
      ;;
    --minor)
      BUMP="minor"
      shift
      ;;
    --patch)
      BUMP="patch"
      shift
      ;;
    --remote)
      REMOTE="${2:-}"
      [[ -n "$REMOTE" ]] || { echo "--remote requires a value" >&2; exit 1; }
      shift 2
      ;;
    --branch)
      BRANCH="${2:-}"
      [[ -n "$BRANCH" ]] || { echo "--branch requires a value" >&2; exit 1; }
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$PROPS_FILE" ]]; then
  echo "gradle.properties not found: $PROPS_FILE" >&2
  exit 1
fi

if [[ -z "$BRANCH" ]]; then
  BRANCH="$(git -C "$ROOT_DIR" rev-parse --abbrev-ref HEAD)"
fi

if [[ "$DRY_RUN" -eq 0 ]]; then
  if [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
    echo "Working tree is not clean. Commit/stash changes first." >&2
    exit 1
  fi
fi

current_name="$(awk -F= '/^APP_VERSION_NAME=/{print $2}' "$PROPS_FILE")"
current_code="$(awk -F= '/^APP_VERSION_CODE=/{print $2}' "$PROPS_FILE")"

if [[ -z "$current_name" || -z "$current_code" ]]; then
  echo "APP_VERSION_NAME or APP_VERSION_CODE missing in gradle.properties" >&2
  exit 1
fi

if ! [[ "$current_code" =~ ^[0-9]+$ ]]; then
  echo "APP_VERSION_CODE is not numeric: $current_code" >&2
  exit 1
fi

next_name=""
if [[ "$current_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-(rc|alpha|beta)([0-9]+)$ ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  channel="${BASH_REMATCH[4]}"
  seq="${BASH_REMATCH[5]}"
  next_name="${major}.${minor}.${patch}-${channel}$((seq + 1))"
else
  if [[ "$current_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
  else
    echo "APP_VERSION_NAME is not supported semver: $current_name" >&2
    echo "Expected: x.y.z or x.y.z-rcN/alphaN/betaN" >&2
    exit 1
  fi

  case "$BUMP" in
    major)
      next_name="$((major + 1)).0.0"
      ;;
    minor)
      next_name="${major}.$((minor + 1)).0"
      ;;
    patch)
      next_name="${major}.${minor}.$((patch + 1))"
      ;;
    *)
      echo "Unsupported bump type: $BUMP" >&2
      exit 1
      ;;
  esac
fi

next_code="$((current_code + 1))"
next_tag="v${next_name}"

if git -C "$ROOT_DIR" rev-parse "$next_tag" >/dev/null 2>&1; then
  echo "Tag already exists locally: $next_tag" >&2
  exit 1
fi

if git -C "$ROOT_DIR" ls-remote --exit-code --tags "$REMOTE" "refs/tags/$next_tag" >/dev/null 2>&1; then
  echo "Tag already exists on remote $REMOTE: $next_tag" >&2
  exit 1
fi

echo "Current APP_VERSION_NAME: $current_name"
echo "Next    APP_VERSION_NAME: $next_name"
echo "Current APP_VERSION_CODE: $current_code"
echo "Next    APP_VERSION_CODE: $next_code"
echo "Tag: $next_tag"
echo "Remote/Branch: $REMOTE/$BRANCH"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Dry-run only. No files changed."
  exit 0
fi

tmp_file="$(mktemp)"
awk -F= -v OFS== -v name="$next_name" -v code="$next_code" '
  /^APP_VERSION_NAME=/{ $2=name; print; next }
  /^APP_VERSION_CODE=/{ $2=code; print; next }
  { print }
' "$PROPS_FILE" > "$tmp_file"
mv "$tmp_file" "$PROPS_FILE"

git -C "$ROOT_DIR" add gradle.properties
git -C "$ROOT_DIR" commit -m "chore(release): $next_tag"
git -C "$ROOT_DIR" tag "$next_tag"
git -C "$ROOT_DIR" push "$REMOTE" "$BRANCH"
git -C "$ROOT_DIR" push "$REMOTE" "$next_tag"

echo "Release done: $next_tag"
