#!/usr/bin/env bash
# prune_backups deletes timestamp directories older than the retain window
# and leaves a directory created at the current UTC time.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
lib="$root/scripts/ops/lib/backup.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ ! -f "$lib" ]]; then
  fail "function or file does not exist: $lib"
fi

# shellcheck disable=SC1090
source "$lib"

if ! declare -F prune_backups >/dev/null 2>&1; then
  fail "prune_backups is not defined"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

old_name="20200101T000000Z"
now_name="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$tmp/$old_name" "$tmp/$now_name"
echo old > "$tmp/$old_name/marker"
echo now > "$tmp/$now_name/marker"

prune_backups "$tmp" 7

if [[ -e "$tmp/$old_name" ]]; then
  fail "expected $old_name to be removed"
fi
if [[ ! -d "$tmp/$now_name" ]]; then
  fail "expected $now_name to remain"
fi
if [[ "$(cat "$tmp/$now_name/marker")" != "now" ]]; then
  fail "current backup directory was modified"
fi

shopt -s nullglob
remaining=("$tmp"/*/)
if [[ "${#remaining[@]}" -ne 1 ]]; then
  fail "expected 1 directory after prune, found ${#remaining[@]}"
fi

echo "PASS"
