#!/usr/bin/env bash
# Shared backup helpers. Sourcing this file only defines functions.
# It does not read Docker, write a backup, or install a schedule.

compose_files() {
  local env_name="${1:-}"
  local env_file
  case "$env_name" in
    dev) env_file=".env" ;;
    staging) env_file=".env.staging" ;;
    prod) env_file=".env.prod" ;;
    *)
      echo "compose_files: expected dev, staging, or prod" >&2
      return 1
      ;;
  esac
  printf '%s\n' "-f docker-compose.yml -f compose.${env_name}.yml --env-file ${env_file}"
}

# Read one KEY=value from a compose env file. Prints the raw value.
# Matching single or double quotes around the value are removed.
env_file_value() {
  local file="$1"
  local key="$2"
  local line value
  [[ -f "$file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    if [[ "$line" =~ ^[[:space:]]*(export[[:space:]]+)?${key}=(.*)$ ]]; then
      value="${BASH_REMATCH[2]}"
      if [[ ${#value} -ge 2 && "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
        value="${value:1:${#value}-2}"
      elif [[ ${#value} -ge 2 && "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
        value="${value:1:${#value}-2}"
      fi
      printf '%s' "$value"
      return 0
    fi
  done < "$file"
  return 1
}

# Shell values win when non-empty. Otherwise take the env file.
# Empty file values stay unset so callers can apply their own defaults.
load_backup_env() {
  local file="$1"
  local key value
  for key in MYSQL_ROOT_PASSWORD BACKUP_DIR BACKUP_RETAIN_DAYS; do
    if [[ -n "${!key:-}" ]]; then
      continue
    fi
    value=""
    if [[ -f "$file" ]]; then
      value="$(env_file_value "$file" "$key" || true)"
    fi
    if [[ -n "$value" ]]; then
      export "${key}=${value}"
    fi
  done
}

# Delete child directories named YYYYMMDDTHHMMSSZ whose UTC time is
# strictly older than retain-days. Names that do not match stay put.
prune_backups() {
  local dir="$1"
  local retain_days="$2"
  local now cutoff name path when iso
  local -a entries=()
  local nullglob_was=0

  if [[ ! -d "$dir" ]]; then
    echo "prune_backups: not a directory: $dir" >&2
    return 1
  fi
  if [[ ! "$retain_days" =~ ^[0-9]+$ ]]; then
    echo "prune_backups: retain-days must be a non-negative integer" >&2
    return 1
  fi

  now="$(date -u +%s)"
  cutoff="$((now - 10#$retain_days * 86400))"

  if shopt -q nullglob; then
    nullglob_was=1
  fi
  shopt -s nullglob
  entries=("$dir"/*)
  if (( nullglob_was == 0 )); then
    shopt -u nullglob
  fi

  for path in "${entries[@]}"; do
    [[ -d "$path" && ! -L "$path" ]] || continue
    name="$(basename "$path")"
    [[ "$name" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || continue
    iso="${name:0:4}-${name:4:2}-${name:6:2}T${name:9:2}:${name:11:2}:${name:13:2}Z"
    if ! when="$(date -u -d "$iso" +%s 2>/dev/null)"; then
      continue
    fi
    if (( when < cutoff )); then
      rm -rf -- "$path"
    fi
  done
}

# Exit 2 when the target stack is healthy and the caller did not pass yes.
# A normal return lets the caller continue; exit 0 would stop restore.sh.
refuse_overwrite() {
  local healthy="${1:-}"
  local yes="${2:-}"
  if [[ "$healthy" == "true" && "$yes" != "true" ]]; then
    echo "refusing to overwrite a healthy stack; pass --yes to continue" >&2
    exit 2
  fi
  return 0
}
