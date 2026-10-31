#!/usr/bin/env bash
# A service with State running and empty Health is up. State exited is not.
# Health healthy is still up. Fixtures stand in for docker compose ps.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
script="$root/scripts/ops/restore.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ ! -f "$script" ]]; then
  fail "function or file does not exist: $script"
fi

# shellcheck disable=SC1090
source "$script"

if ! declare -F all_healthy >/dev/null 2>&1; then
  fail "all_healthy is not defined"
fi

assert_up() {
  local output="$1"
  local service="$2"
  if ! all_healthy "$output" "$service"; then
    fail "expected $service to count as up: [$output]"
  fi
}

assert_down() {
  local output="$1"
  local service="$2"
  if all_healthy "$output" "$service"; then
    fail "expected $service to count as down: [$output]"
  fi
}

# backend has no healthcheck: State running, Health empty.
assert_up "backend running" backend
assert_down "backend exited" backend
# mysql still publishes Health healthy. starting and unhealthy stay down.
assert_up "mysql running healthy" mysql
assert_down "mysql running starting" mysql
assert_down "mysql running unhealthy" mysql

stack=$'mysql running healthy\npython-service running healthy\nbackend running'
if ! all_healthy "$stack" mysql backend python-service; then
  fail "running backend with empty Health should make the stack count as up"
fi

exited=$'mysql running healthy\npython-service running healthy\nbackend exited'
if all_healthy "$exited" mysql backend python-service; then
  fail "exited backend should not make the stack count as up"
fi

if ! declare -F compose_ps_format >/dev/null 2>&1; then
  fail "compose_ps_format is not defined"
fi
format="$(compose_ps_format)"
[[ "$format" == "{{.Service}} {{.State}} {{.Health}}" ]] || fail "ps format is [$format]"
if grep -F -q '{{.Service}} {{.Health}}' "$script"; then
  fail "restore.sh still requests Health without State"
fi

echo "PASS"
