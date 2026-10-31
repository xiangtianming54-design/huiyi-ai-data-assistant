#!/usr/bin/env bash
# should_send, metric_increased, format_alert, and healthcheck.sh static checks.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
lib="$root/scripts/ops/lib/alert.sh"
health="$root/scripts/ops/healthcheck.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ ! -f "$lib" ]]; then
  fail "function or file does not exist: $lib"
fi

# shellcheck disable=SC1090
source "$lib"

for fn in should_send metric_increased format_alert post_dingtalk; do
  if ! declare -F "$fn" >/dev/null 2>&1; then
    fail "$fn is not defined"
  fi
done

run_status() {
  set +e
  "$@"
  local status=$?
  set -e
  printf '%s' "$status"
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

state="$tmp/state"
now=1700000000

status="$(run_status should_send backend-down "$now" "$state" 30)"
[[ "$status" == "0" ]] || fail "first should_send backend-down exited $status, expected 0"
grep -qx "backend-down=${now}" "$state" || fail "first should_send did not record ${now}"

cp "$state" "$state.before"
status="$(run_status should_send backend-down "$((now + 600))" "$state" 30)"
[[ "$status" == "1" ]] || fail "should_send 10 minutes later exited $status, expected 1"
cmp -s "$state.before" "$state" || fail "dedup window changed the state file"

status="$(run_status should_send backend-down "$((now + 1800))" "$state" 30)"
[[ "$status" == "0" ]] || fail "should_send at 30 minutes exited $status, expected 0"
grep -qx "backend-down=$((now + 1800))" "$state" || fail "30 minute send did not record the new timestamp"

fresh="$tmp/fresh"
status="$(run_status should_send backend-down "$now" "$fresh" 30)"
[[ "$status" == "0" ]] || fail "fresh should_send exited $status, expected 0"
status="$(run_status should_send backend-down "$((now + 1860))" "$fresh" 30)"
[[ "$status" == "0" ]] || fail "should_send 31 minutes later exited $status, expected 0"
grep -qx "backend-down=$((now + 1860))" "$fresh" || fail "31 minute send did not record the new timestamp"

metrics="$tmp/metrics"
status="$(run_status metric_increased 2 "$metrics")"
[[ "$status" == "1" ]] || fail "metric_increased baseline 2 exited $status, expected 1"
grep -qx "async-failed-value=2" "$metrics" || fail "baseline did not record async-failed-value=2"

status="$(run_status metric_increased 2 "$metrics")"
[[ "$status" == "1" ]] || fail "metric_increased 2 to 2 exited $status, expected 1"
grep -qx "async-failed-value=2" "$metrics" || fail "equal value changed async-failed-value"

status="$(run_status metric_increased 3 "$metrics")"
[[ "$status" == "0" ]] || fail "metric_increased 2 to 3 exited $status, expected 0"
grep -qx "async-failed-value=3" "$metrics" || fail "increase did not record async-failed-value=3"

status="$(run_status metric_increased 1 "$metrics")"
[[ "$status" == "1" ]] || fail "metric_increased 3 to 1 exited $status, expected 1"
grep -qx "async-failed-value=1" "$metrics" || fail "decrease did not record the latest async-failed-value"

status="$(run_status metric_increased 2 "$metrics")"
[[ "$status" == "0" ]] || fail "metric_increased 1 to 2 exited $status, expected 0"

output="$(format_alert staging backend-down down)"
mapfile -t lines <<< "$output"
[[ "${lines[0]}" == "HuiYi告警" ]] || fail "format_alert first line is [${lines[0]:-}]"
[[ "${lines[1]}" == "staging" ]] || fail "format_alert env line is [${lines[1]:-}]"
[[ "${lines[2]}" == "backend-down" ]] || fail "format_alert name line is [${lines[2]:-}]"
[[ "${lines[3]}" == "down" ]] || fail "format_alert reason line is [${lines[3]:-}]"
[[ "${#lines[@]}" -eq 4 ]] || fail "format_alert line count is ${#lines[@]}, expected 4"

curl_log="$tmp/curl.log"
CURL_LOG="$curl_log"
curl() {
  printf '%s\n' "$@" > "$CURL_LOG"
}

skipped="$(post_dingtalk "$(format_alert staging backend-down down)" "")"
[[ "$skipped" == "webhook skipped" ]] || fail "empty webhook printed [$skipped]"
[[ ! -e "$curl_log" ]] || fail "empty webhook called curl"

status="$(run_status post_dingtalk "$(format_alert staging backend-down down)" "https://example.invalid/robot")"
[[ "$status" == "0" ]] || fail "post_dingtalk exited $status, expected 0"
[[ -f "$curl_log" ]] || fail "webhook URL did not call curl"
curl_args="$(cat "$curl_log")"
[[ "$curl_args" == *'{"msgtype":"text","text":{"content":"HuiYi告警'* ]] || fail "curl payload was [$curl_args]"
[[ "$curl_args" == *'https://example.invalid/robot'* ]] || fail "curl URL missing from [$curl_args]"

[[ -f "$health" ]] || fail "healthcheck.sh does not exist"
grep -q 'mysqladmin' "$health" || fail "healthcheck.sh does not contain mysqladmin"
if grep -q 'redis-cli' "$health"; then
  fail "healthcheck.sh contains redis-cli"
fi
if grep -q 'df ' "$health"; then
  fail "healthcheck.sh contains df "
fi
if grep -q 'llm_failed' "$health"; then
  fail "healthcheck.sh contains llm_failed"
fi
grep -q 'huiyi.async.failed' "$health" || fail "healthcheck.sh does not contain huiyi.async.failed"
grep -q 'logs/ops-alert-state' "$health" || fail "healthcheck.sh does not contain logs/ops-alert-state"
grep -q 'ALERT_DEDUP_MINUTES' "$health" || fail "healthcheck.sh does not contain ALERT_DEDUP_MINUTES"
grep -q 'webhook skipped' "$health" || fail "healthcheck.sh does not contain webhook skipped"
grep -q 'scripts/ops/lib/backup.sh' "$health" || fail "healthcheck.sh does not source backup.sh"
if grep -q 'compose_files()' "$health"; then
  fail "healthcheck.sh defines compose_files"
fi
if grep -q 'curl' "$health"; then
  fail "healthcheck.sh calls curl; webhook posting belongs in post_dingtalk"
fi

echo "PASS"
