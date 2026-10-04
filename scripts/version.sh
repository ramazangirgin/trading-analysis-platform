#!/usr/bin/env bash
# The platform's one version (docs/coding-convention/repository-versioning-and-releases.md).
#
# gradle.properties holds it; the frontend's package.json and ta-runner's ta_runner/__init__.py
# carry copies, kept in sync by this script. ta-runner's pyproject.toml reads its version from
# __init__.py (dynamic), so a version bump changes neither pyproject.toml nor uv.lock and the
# ta-runner image keeps its cached dependency layer.
#
#   scripts/version.sh                       print the version
#   scripts/version.sh bump major|minor|patch  raise it in every file (mise run version:bump ...)
#   scripts/version.sh set X.Y.Z             set it in every file
#   scripts/version.sh check-sync            fail if the files disagree
#   scripts/version.sh check-bump <ref>      fail unless the version is higher than in <ref> (the
#                                            pull request's base) and than the latest vX.Y.Z tag,
#                                            and its tag does not exist yet (CI, pull requests)
set -euo pipefail

cd "$(dirname "$0")/.."

GRADLE=gradle.properties
FRONTEND=artifact/frontend/package.json
RUNNER=artifact/ta-runner/ta_runner/__init__.py
SEMVER='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

die() {
  echo "version: $*" >&2
  exit 1
}

gradle_version() { sed -n 's/^version=//p' "${1:-$GRADLE}"; }
frontend_version() { sed -n 's/^  "version": "\(.*\)",$/\1/p' "$FRONTEND"; }
runner_version() { sed -n 's/^__version__ = "\(.*\)"$/\1/p' "$RUNNER"; }

valid() { [[ $1 =~ $SEMVER ]]; }

# 0 if $1 > $2 (both X.Y.Z).
greater() {
  local -a a b
  IFS=. read -r -a a <<<"$1"
  IFS=. read -r -a b <<<"$2"
  for i in 0 1 2; do
    ((a[i] > b[i])) && return 0
    ((a[i] < b[i])) && return 1
  done
  return 1
}

# Replaces the line matching $2 in file $1 with $3; fails unless exactly one line matched.
replace_line() {
  local file=$1 pattern=$2 line=$3 tmp
  [ "$(grep -c -- "$pattern" "$file")" = 1 ] || die "$file: expected one line matching $pattern"
  tmp=$(mktemp)
  awk -v pattern="$pattern" -v line="$line" '$0 ~ pattern { print line; next } { print }' "$file" >"$tmp"
  cat "$tmp" >"$file"
  rm "$tmp"
}

set_version() {
  valid "$1" || die "not a MAJOR.MINOR.PATCH version: $1"
  replace_line "$GRADLE" '^version=' "version=$1"
  replace_line "$FRONTEND" '^  "version": ' "  \"version\": \"$1\","
  replace_line "$RUNNER" '^__version__ = ' "__version__ = \"$1\""
  echo "$1"
}

check_sync() {
  local version
  version=$(gradle_version)
  valid "$version" || die "$GRADLE: version=$version is not MAJOR.MINOR.PATCH (no -SNAPSHOT on main)"
  [ "$(frontend_version)" = "$version" ] || die "$FRONTEND has $(frontend_version), $GRADLE has $version"
  [ "$(runner_version)" = "$version" ] || die "$RUNNER has $(runner_version), $GRADLE has $version"
}

check_bump() {
  local base_ref=$1 version base latest_tag
  check_sync
  version=$(gradle_version)

  base=$(git show "$base_ref:$GRADLE" | gradle_version /dev/stdin)
  base=${base%-SNAPSHOT}
  greater "$version" "$base" ||
    die "$version is not higher than $base on $base_ref: bump it with mise run version:bump major|minor"

  latest_tag=$(git tag --list 'v*' --sort=-v:refname | grep -E "^v${SEMVER:1}" | head -n 1 || true)
  if [ -n "$latest_tag" ]; then
    greater "$version" "${latest_tag#v}" || die "$version is not higher than the latest tag $latest_tag"
  fi
  if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
    die "the tag v$version already exists"
  fi
  echo "Version $version (base $base, latest tag ${latest_tag:-none})"
}

bump() {
  local -a v
  check_sync
  IFS=. read -r -a v <<<"$(gradle_version)"
  case ${1:-} in
    major) set_version "$((v[0] + 1)).0.0" ;;
    minor) set_version "${v[0]}.$((v[1] + 1)).0" ;;
    patch) set_version "${v[0]}.${v[1]}.$((v[2] + 1))" ;;
    *) die "usage: $0 bump major|minor|patch" ;;
  esac
}

case ${1:-get} in
  get) gradle_version ;;
  bump) bump "${2:-}" ;;
  set) set_version "${2:-}" ;;
  check-sync) check_sync && echo "$(gradle_version) in every file" ;;
  check-bump) check_bump "${2:?usage: $0 check-bump <base ref>}" ;;
  *) die "unknown command: $1 (get, bump, set, check-sync, check-bump)" ;;
esac
