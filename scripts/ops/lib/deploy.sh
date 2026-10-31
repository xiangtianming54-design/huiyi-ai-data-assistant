#!/usr/bin/env bash
# Deploy helpers. Sourcing this file only defines functions.
# It does not fetch, check out, build, or probe a stack.

_deploy_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${_deploy_lib_dir}/backup.sh"

# Replace or append GIT_COMMIT and APP_VERSION. Every other line stays byte for byte.
# A repeated call does not add a second copy of either key.
update_env_keys() {
  local file="$1"
  local commit="$2"
  local version="$3"
  local out line bare had_nl
  local saw_commit=0
  local saw_version=0
  local wrote=0
  local ends_with_nl=1

  if [[ ! -f "$file" ]]; then
    printf 'GIT_COMMIT=%s\nAPP_VERSION=%s\n' "$commit" "$version" > "$file"
    return 0
  fi

  out="$(mktemp)"
  : > "$out"

  while true; do
    line=""
    had_nl=0
    if IFS= read -r line; then
      had_nl=1
    else
      if [[ -z "$line" ]]; then
        break
      fi
      had_nl=0
    fi

    bare="${line%$'\r'}"
    if [[ "$bare" =~ ^[[:space:]]*(export[[:space:]]+)?GIT_COMMIT= ]]; then
      if [[ "$saw_commit" -eq 0 ]]; then
        if [[ "$line" == *$'\r' ]]; then
          printf 'GIT_COMMIT=%s\r' "$commit" >> "$out"
        else
          printf 'GIT_COMMIT=%s' "$commit" >> "$out"
        fi
        if [[ "$had_nl" -eq 1 ]]; then
          printf '\n' >> "$out"
        fi
        saw_commit=1
        wrote=1
        ends_with_nl=$had_nl
      fi
      continue
    fi
    if [[ "$bare" =~ ^[[:space:]]*(export[[:space:]]+)?APP_VERSION= ]]; then
      if [[ "$saw_version" -eq 0 ]]; then
        if [[ "$line" == *$'\r' ]]; then
          printf 'APP_VERSION=%s\r' "$version" >> "$out"
        else
          printf 'APP_VERSION=%s' "$version" >> "$out"
        fi
        if [[ "$had_nl" -eq 1 ]]; then
          printf '\n' >> "$out"
        fi
        saw_version=1
        wrote=1
        ends_with_nl=$had_nl
      fi
      continue
    fi
    printf '%s' "$line" >> "$out"
    if [[ "$had_nl" -eq 1 ]]; then
      printf '\n' >> "$out"
    fi
    wrote=1
    ends_with_nl=$had_nl
  done < "$file"

  if [[ "$saw_commit" -eq 0 ]]; then
    if [[ "$wrote" -eq 1 && "$ends_with_nl" -eq 0 ]]; then
      printf '\n' >> "$out"
    fi
    printf 'GIT_COMMIT=%s\n' "$commit" >> "$out"
    wrote=1
    ends_with_nl=1
  fi
  if [[ "$saw_version" -eq 0 ]]; then
    if [[ "$wrote" -eq 1 && "$ends_with_nl" -eq 0 ]]; then
      printf '\n' >> "$out"
    fi
    printf 'APP_VERSION=%s\n' "$version" >> "$out"
  fi

  cat "$out" > "$file"
  rm -f "$out"
}

# Print the commit a failed deploy should return to.
# Healthy: write current and print it.
# Unhealthy with a saved sha: print that sha and leave the file untouched.
# Unhealthy with an empty or missing file: print current and do not record it.
rollback_target() {
  local state_file="$1"
  local current="$2"
  local healthy="$3"
  local existing=""

  if [[ "$healthy" == "true" ]]; then
    mkdir -p -- "$(dirname -- "$state_file")"
    printf '%s\n' "$current" > "$state_file"
    printf '%s\n' "$current"
    return 0
  fi

  if [[ -f "$state_file" && -s "$state_file" ]]; then
    IFS= read -r existing < "$state_file" || true
    existing="${existing%$'\r'}"
  fi
  if [[ -n "$existing" ]]; then
    printf '%s\n' "$existing"
    return 0
  fi
  printf '%s\n' "$current"
}

probe_http_code() {
  local raw="${1//$'\r'/}"
  local line parts
  line="${raw%%$'\n'*}"
  read -r -a parts <<< "$line"
  printf '%s' "${parts[1]:-}"
}

probe_http_body() {
  local raw="${1//$'\r'/}"
  if [[ "$raw" != *$'\n\n'* ]]; then
    printf '%s' "$raw"
    return 0
  fi
  printf '%s' "${raw#*$'\n\n'}"
}

# Backend actuator, python /health, and mysqladmin ping. Async failure is not a probe failure.
probe_backend() {
  local raw code body state
  raw="$(timeout 10 docker compose "$@" exec -T backend \
    bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1; printf "GET %s HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" "$1" >&3; cat <&3' \
    _ /actuator/health)" || return 1
  code="$(probe_http_code "$raw")"
  body="$(probe_http_body "$raw")"
  body="$(printf '%s' "$body" | tr -d '[:space:]')"
  state=""
  if [[ "$body" =~ \"status\":\"([A-Z_]+)\" ]]; then
    state="${BASH_REMATCH[1]}"
  fi
  [[ "$code" == "200" && "$state" == "UP" ]]
}

probe_python() {
  timeout 10 docker compose "$@" exec -T python-service \
    python -c 'import sys, urllib.request
try:
    resp = urllib.request.urlopen("http://127.0.0.1:8001/health", timeout=5)
except Exception:
    sys.exit(1)
sys.exit(0 if resp.status == 200 else 1)
'
}

probe_mysql() {
  local password="$1"
  shift
  [[ -n "$password" ]] || return 1
  timeout 10 docker compose "$@" exec -T mysql \
    mysqladmin ping --host=127.0.0.1 --user=root --password="${password}" --silent
}

probe_alive() {
  local env_name="${1:-}"
  local root line env_file i
  local -a compose_args=()
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
  line="$(compose_files "$env_name")" || return 1
  read -r -a compose_args <<< "$line"
  env_file=""
  for ((i = 0; i < ${#compose_args[@]}; i++)); do
    if [[ "${compose_args[i]}" == "--env-file" ]]; then
      env_file="${compose_args[i + 1]:-}"
    fi
  done
  (
    cd "$root" || exit 1
    export MSYS_NO_PATHCONV=1
    export MSYS2_ARG_CONV_EXCL='*'
    password=""
    if [[ -n "${MYSQL_ROOT_PASSWORD:-}" ]]; then
      password="$MYSQL_ROOT_PASSWORD"
    elif [[ -n "$env_file" && -f "$env_file" ]]; then
      password="$(env_file_value "$env_file" MYSQL_ROOT_PASSWORD || true)"
    fi
    probe_backend "${compose_args[@]}" || exit 1
    probe_python "${compose_args[@]}" || exit 1
    probe_mysql "$password" "${compose_args[@]}" || exit 1
  )
}
