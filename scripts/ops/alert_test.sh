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

# shellcheck disable=SC1090
source "$health"

decode_posted() {
  local line="$1"
  local body decoded
  body="${line#*content\":\"}"
  body="${body%\"*}"
  decoded="${body//\\n/$'\n'}"
  mapfile -t posted <<< "$decoded"
}

assert_posted_body() {
  local line="$1"
  decode_posted "$line"
  [[ "${posted[0]}" == "HuiYi告警" ]] || fail "posted first line is [${posted[0]:-}]"
  [[ "${posted[1]}" == "staging" ]] || fail "posted env is [${posted[1]:-}]"
  [[ "${posted[2]}" == "backend-down" ]] || fail "posted name is [${posted[2]:-}]"
  [[ "${posted[3]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || fail "posted time is [${posted[3]:-}]"
  [[ "${posted[4]}" == "down" ]] || fail "posted reason is [${posted[4]:-}]"
  [[ "${#posted[@]}" -eq 5 ]] || fail "posted line count is ${#posted[@]}, expected 5"
}

log_file="$tmp/notify.log"
state_file="$tmp/notify-state"
dedup=30
env_name=staging
failed=0
DINGTALK_WEBHOOK_URL=""
curl() { echo CURL_CALLED >> "$tmp/empty-curl"; }
notify backend-down "down"
grep -q 'webhook skipped' "$log_file" || fail "empty webhook did not log webhook skipped"
[[ ! -e "$tmp/empty-curl" ]] || fail "empty webhook called curl"
if [[ -f "$state_file" ]] && grep -q '^backend-down=' "$state_file"; then
  fail "empty webhook consumed the dedup window"
fi

log_file="$tmp/retry.log"
state_file="$tmp/retry-state"
dedup=30
env_name=staging
failed=0
DINGTALK_WEBHOOK_URL="https://example.invalid/robot"
posts=0
CURL_LOG="$tmp/retry-curl.txt"
: > "$CURL_LOG"
curl() {
  posts="$((posts + 1))"
  printf '%s\n' "$@" >> "$CURL_LOG"
  if [[ "$posts" -eq 1 ]]; then
    return 1
  fi
  return 0
}
notify backend-down "down"
if [[ -f "$state_file" ]] && grep -q '^backend-down=' "$state_file"; then
  fail "failed post stamped the dedup window"
fi
notify backend-down "down"
[[ "$posts" -eq 2 ]] || fail "failed post suppressed the next send, posts=$posts"
found_posts=0
while IFS= read -r line; do
  [[ "$line" == '{"msgtype":'* ]] || continue
  found_posts="$((found_posts + 1))"
  assert_posted_body "$line"
done < "$CURL_LOG"
[[ "$found_posts" -eq 2 ]] || fail "expected 2 posted bodies, found $found_posts"
notify backend-down "down"
[[ "$posts" -eq 2 ]] || fail "successful post did not start dedup, posts=$posts"
grep -q '^backend-down=' "$state_file" || fail "successful post did not stamp dedup"

echo "PASS"
