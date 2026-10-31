#!/usr/bin/env bash
# Shared alert helpers. Sourcing this file only defines functions.
# It does not read Docker, send a webhook, or write the ops log.

# Print the value for key in a KEY=value state file. Return 1 when missing.
state_get() {
  local key="$1"
  local file="$2"
  local line
  [[ -f "$file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "${line%%=*}" == "$key" ]]; then
      printf '%s' "${line#*=}"
      return 0
    fi
  done < "$file"
  return 1
}

# Replace key, or append it, and leave every other key in place.
state_set() {
  local key="$1"
  local value="$2"
  local file="$3"
  local dir tmp line found=0
  dir="$(dirname "$file")"
  mkdir -p "$dir"
  tmp="$(mktemp "${dir}/alert-state.XXXXXX")"
  if [[ -f "$file" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
      line="${line%$'\r'}"
      [[ -z "$line" ]] && continue
      if [[ "${line%%=*}" == "$key" ]]; then
        if [[ "$found" -eq 0 ]]; then
          printf '%s=%s\n' "$key" "$value" >> "$tmp"
          found=1
        fi
      else
        printf '%s\n' "$line" >> "$tmp"
      fi
    done < "$file"
  fi
  if [[ "$found" -eq 0 ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
  fi
  mv -f "$tmp" "$file"
}

# Exit 0 when name has no timestamp, or the age is at least dedup-minutes.
# Does not write the state file.
alert_due() {
  local name="$1"
  local now="$2"
  local file="$3"
  local minutes="$4"
  local prev="" age limit
  if [[ ! "$now" =~ ^[0-9]+$ || ! "$minutes" =~ ^[0-9]+$ ]]; then
    echo "should_send: now and dedup minutes must be non-negative integers" >&2
    return 1
  fi
  if [[ -f "$file" ]]; then
    prev="$(state_get "$name" "$file" || true)"
  fi
  if [[ -n "$prev" ]]; then
    age="$((now - prev))"
    limit="$((10#$minutes * 60))"
    if (( age < limit )); then
      return 1
    fi
  fi
  return 0
}

# Exit 0 and store now when a send is due. Exit 1 without changing the file
# while the window is open.
should_send() {
  local name="$1"
  local now="$2"
  local file="$3"
  alert_due "$@" || return 1
  state_set "$name" "$now" "$file"
  return 0
}

# Exit 0 only when new-value is greater than async-failed-value.
# The first observation is stored and does not alert.
# A later equal or smaller value is stored too, so the next rise is measured
# from the latest reading, and the function exits 1.
metric_increased() {
  local new="$1"
  local file="$2"
  local old=""
  if [[ ! "$new" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "metric_increased: value must be a number" >&2
    return 1
  fi
  if [[ -f "$file" ]]; then
    old="$(state_get async-failed-value "$file" || true)"
  fi
  if [[ -z "$old" ]]; then
    state_set async-failed-value "$new" "$file"
    return 1
  fi
  if awk -v n="$new" -v o="$old" 'BEGIN { exit !((n + 0) > (o + 0)) }'; then
    state_set async-failed-value "$new" "$file"
    return 0
  fi
  state_set async-failed-value "$new" "$file"
  return 1
}

# Four lines: keyword, environment, alert name, reason.
format_alert() {
  local env_name="$1"
  local name="$2"
  local reason="$3"
  printf 'HuiYi告警\n%s\n%s\n%s\n' "$env_name" "$name" "$reason"
}

# Posted DingTalk text: keyword, environment, alert name, time, reason.
dingtalk_body() {
  local env_name="$1"
  local name="$2"
  local when="$3"
  local reason="$4"
  printf 'HuiYi告警\n%s\n%s\n%s\n%s\n' "$env_name" "$name" "$when" "$reason"
}

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\r'/\\r}"
  printf '%s' "$s"
}

# Empty webhook prints "webhook skipped" and does not run curl.
# A URL posts DingTalk text JSON and returns curl's status.
post_dingtalk() {
  local body="$1"
  local webhook="${2-}"
  local escaped payload
  if [[ -z "$webhook" ]]; then
    printf '%s\n' "webhook skipped"
    return 0
  fi
  escaped="$(json_escape "$body")"
  payload="{\"msgtype\":\"text\",\"text\":{\"content\":\"${escaped}\"}}"
  curl -fsS -H "Content-Type: application/json" --data "$payload" "$webhook"
}
