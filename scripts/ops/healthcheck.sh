#!/usr/bin/env bash
# Check backend, python, MySQL, and Gauge huiyi.async.failed.
# 本脚本不检查 Redis。也不检查磁盘、LLM 错误率或 P95。
# Running this file probes a compose stack. Sourcing scripts/ops/lib/alert.sh does not.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/backup.sh"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/alert.sh"

usage() {
  echo "usage: healthcheck.sh <dev|staging|prod>" >&2
  exit 1
}

append_log() {
  printf '%s\n' "$*" >> "$log_file"
}

# Read one HTTP response from inside backend. The JRE image has no HTTP client.
backend_get() {
  local path="$1"
  timeout 30 docker compose "${compose_args[@]}" exec -T backend \
    bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1; printf "GET %s HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" "$1" >&3; cat <&3' \
    _ "$path"
}

http_code() {
  local raw="${1//$'\r'/}"
  local line parts
  line="${raw%%$'\n'*}"
  read -r -a parts <<< "$line"
  printf '%s' "${parts[1]:-}"
}

http_body() {
  local raw="${1//$'\r'/}"
  if [[ "$raw" != *$'\n\n'* ]]; then
    printf '%s' "$raw"
    return 0
  fi
  printf '%s' "${raw#*$'\n\n'}"
}

# Log every failure. DingTalk only for a non-empty webhook outside the dedup window.
# The dedup clock starts after the webhook post succeeds.
notify() {
  local name="$1"
  local reason="$2"
  local now when body
  now="$(date +%s)"
  when="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  append_log "${when} ${env_name} ${name} ${reason}"
  if [[ -z "${DINGTALK_WEBHOOK_URL:-}" ]]; then
    append_log "webhook skipped"
    return 0
  fi
  if ! alert_due "$name" "$now" "$state_file" "$dedup"; then
    return 0
  fi
  body="$(dingtalk_body "$env_name" "$name" "$when" "$reason")"
  if post_dingtalk "$body" "$DINGTALK_WEBHOOK_URL"; then
    state_set "$name" "$now" "$state_file"
  else
    append_log "webhook post failed"
    failed=1
  fi
}

check_backend() {
  local raw code body state
  raw="$(backend_get /actuator/health)" || {
    notify backend-down "actuator health request failed"
    return 1
  }
  code="$(http_code "$raw")"
  body="$(http_body "$raw")"
  body="$(printf '%s' "$body" | tr -d '[:space:]')"
  state=""
  if [[ "$body" =~ \"status\":\"([A-Z_]+)\" ]]; then
    state="${BASH_REMATCH[1]}"
  fi
  if [[ "$code" != "200" || "$state" != "UP" ]]; then
    notify backend-down "actuator health is not UP"
    return 1
  fi
  return 0
}

check_python() {
  # Compose service name is python-service.
  if ! timeout 30 docker compose "${compose_args[@]}" exec -T python-service \
    python -c 'import sys, urllib.request
try:
    resp = urllib.request.urlopen("http://127.0.0.1:8001/health", timeout=5)
except Exception:
    sys.exit(1)
sys.exit(0 if resp.status == 200 else 1)
'; then
    notify python-down "python health is not HTTP 200"
    return 1
  fi
  return 0
}

check_mysql() {
  if [[ -z "${MYSQL_ROOT_PASSWORD:-}" ]]; then
    notify mysql-down "mysqladmin ping failed"
    return 1
  fi
  if ! timeout 30 docker compose "${compose_args[@]}" exec -T mysql \
    mysqladmin ping --host=127.0.0.1 --user=root --password="${MYSQL_ROOT_PASSWORD}" --silent; then
    notify mysql-down "mysqladmin ping failed"
    return 1
  fi
  return 0
}

check_async_failed() {
  local raw code body value
  raw="$(backend_get /actuator/metrics/huiyi.async.failed)" || {
    append_log "$(date -u +%Y-%m-%dT%H:%M:%SZ) ${env_name} huiyi.async.failed unreadable"
    return 1
  }
  code="$(http_code "$raw")"
  body="$(http_body "$raw")"
  body="$(printf '%s' "$body" | tr -d '[:space:]')"
  value=""
  if [[ "$body" =~ \"value\":([0-9]+([.][0-9]+)?) ]]; then
    value="${BASH_REMATCH[1]}"
  fi
  if [[ "$code" != "200" || -z "$value" ]]; then
    append_log "$(date -u +%Y-%m-%dT%H:%M:%SZ) ${env_name} huiyi.async.failed unreadable"
    return 1
  fi
  if metric_increased "$value" "$state_file"; then
    notify async-failed-increased "huiyi.async.failed increased to ${value}"
    return 1
  fi
  return 0
}

load_alert_env() {
  local file="$1"
  local key value
  [[ -n "$file" && -f "$file" ]] || return 0
  load_backup_env "$file"
  for key in DINGTALK_WEBHOOK_URL ALERT_DEDUP_MINUTES; do
    if [[ -n "${!key:-}" ]]; then
      continue
    fi
    value="$(env_file_value "$file" "$key" || true)"
    if [[ -n "$value" ]]; then
      printf -v "$key" '%s' "$value"
      export "$key"
    fi
  done
}

main() {
  local env_name line env_file i
  local -a compose_args=()

  if [[ "$#" -ne 1 ]]; then
    usage
  fi
  env_name="$1"
  case "$env_name" in
    dev|staging|prod) ;;
    *) usage ;;
  esac

  cd "$root"
  line="$(compose_files "$env_name")"
  read -r -a compose_args <<< "$line"
  env_file=""
  for ((i = 0; i < ${#compose_args[@]}; i++)); do
    if [[ "${compose_args[i]}" == "--env-file" ]]; then
      env_file="${compose_args[i + 1]:-}"
    fi
  done
  load_alert_env "$env_file"

  dedup="${ALERT_DEDUP_MINUTES:-30}"
  if [[ -z "$dedup" ]]; then
    dedup=30
  fi
  if [[ ! "$dedup" =~ ^[0-9]+$ ]]; then
    echo "ALERT_DEDUP_MINUTES must be a non-negative integer" >&2
    exit 1
  fi

  log_file="$root/logs/ops-alert.log"
  state_file="$root/logs/ops-alert-state"
  mkdir -p "$root/logs"
  failed=0

  export MSYS_NO_PATHCONV=1
  export MSYS2_ARG_CONV_EXCL='*'

  check_backend || failed=1
  check_python || failed=1
  check_mysql || failed=1
  check_async_failed || failed=1
  exit "$failed"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
