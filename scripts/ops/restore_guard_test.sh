#!/usr/bin/env bash
# refuse_overwrite exits 2 only when the stack is healthy and --yes was not passed.
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

if ! declare -F refuse_overwrite >/dev/null 2>&1; then
  fail "refuse_overwrite is not defined"
fi

assert_status() {
  local healthy="$1"
  local yes="$2"
  local expected="$3"
  local status
  set +e
  ( refuse_overwrite "$healthy" "$yes" )
  status=$?
  set -e
  [[ "$status" -eq "$expected" ]] || fail "refuse_overwrite $healthy $yes exited $status, expected $expected"
}

assert_status true false 2
assert_status true true 0
assert_status false false 0

echo "PASS"
