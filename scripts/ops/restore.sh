#!/usr/bin/env bash
# Restore mysql.sql.gz, chroma.tar.gz, and knowledge.tar.gz into an environment.
# A healthy stack is left untouched unless --yes is passed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/backup.sh"

usage() {
  echo "usage: restore.sh <dev|staging|prod> <backup-dir> [--yes]" >&2
  exit 1
}

docker_compose() {
  docker compose "${compose_args[@]}" "$@"
}

# docker compose ps columns. Backend has no healthcheck, so Health is empty.
compose_ps_format() {
  printf '%s\n' '{{.Service}} {{.State}} {{.Health}}'
}

# Up when State is running and Health is empty or healthy.
# A service that is not running is down, including State exited.
ps_line_is_up() {
  local line="$1"
  local service state health
  read -r service state health <<< "$line"
  [[ "$state" == "running" ]] || return 1
  [[ -z "$health" || "$health" == "healthy" ]]
}

all_healthy() {
  local output="$1"
  shift
  local want line service found
  for want in "$@"; do
    found=0
    while IFS= read -r line; do
      line="${line%$'\r'}"
      [[ -z "$line" ]] && continue
      service="${line%% *}"
      if [[ "$service" == "$want" ]] && ps_line_is_up "$line"; then
        found=1
        break
      fi
    done <<< "$output"
    [[ "$found" -eq 1 ]] || return 1
  done
  return 0
}

wait_until_healthy() {
  local tries="$1"
  shift
  local wanted=("$@")
  local i ps_output
  for ((i = 1; i <= tries; i++)); do
    if ps_output="$(docker_compose ps --format "$(compose_ps_format)" 2>/dev/null)" \
      && all_healthy "$ps_output" "${wanted[@]}"; then
      return 0
    fi
    sleep 5
  done
  echo "timed out waiting for healthy services: ${wanted[*]}" >&2
  return 1
}

# The service container is stopped, so unpack with a one-off container that
# uses that service's mounts. --no-deps keeps compose from starting writers.
replace_tree() {
  local service="$1"
  local path="$2"
  local archive="$3"
  docker_compose run --rm --no-deps -T --entrypoint sh "$service" -c \
    'cd "$1" && find . -mindepth 1 -delete' sh "$path"
  docker_compose run --rm --no-deps -T --entrypoint tar "$service" \
    -xzf - -C "$path" < "$archive"
}

run_restore() {
  local env_name="${1:-}"
  local backup_dir="${2:-}"
  local yes="false"
  local line env_file ps_output healthy artifact i
  local -a compose_args=()

  if [[ "$#" -lt 2 || "$#" -gt 3 ]]; then
    usage
  fi
  case "$env_name" in
    dev|staging|prod) ;;
    *) usage ;;
  esac
  if [[ "$#" -eq 3 ]]; then
    [[ "$3" == "--yes" ]] || usage
    yes="true"
  fi
  if [[ ! -d "$backup_dir" ]]; then
    echo "backup directory not found: $backup_dir" >&2
    exit 1
  fi
  backup_dir="$(cd "$backup_dir" && pwd)"
  for artifact in mysql.sql.gz chroma.tar.gz knowledge.tar.gz; do
    if [[ ! -f "$backup_dir/$artifact" ]]; then
      echo "missing $artifact in $backup_dir" >&2
      exit 1
    fi
  done

  cd "$root"
  line="$(compose_files "$env_name")"
  read -r -a compose_args <<< "$line"
  env_file=""
  for ((i = 0; i < ${#compose_args[@]}; i++)); do
    if [[ "${compose_args[i]}" == "--env-file" ]]; then
      env_file="${compose_args[i + 1]}"
    fi
  done
  if [[ -z "$env_file" ]]; then
    echo "compose_files did not include --env-file" >&2
    exit 1
  fi
  load_backup_env "$env_file"
  if [[ -z "${MYSQL_ROOT_PASSWORD:-}" ]]; then
    echo "MYSQL_ROOT_PASSWORD is empty. Set it in the shell or in $env_file." >&2
    exit 1
  fi

  export MSYS_NO_PATHCONV=1
  export MSYS2_ARG_CONV_EXCL='*'

  if ! ps_output="$(docker_compose ps --format "$(compose_ps_format)")"; then
    echo "cannot inspect stack health; restore aborted" >&2
    exit 1
  fi
  healthy="false"
  if all_healthy "$ps_output" mysql backend python-service; then
    healthy="true"
  fi
  refuse_overwrite "$healthy" "$yes"

  echo "stopping backend and python-service" >&2
  docker_compose stop backend python-service

  echo "ensuring mysql is up for import" >&2
  docker_compose up -d mysql
  wait_until_healthy 60 mysql

  echo "importing mysql.sql.gz" >&2
  gzip -dc "$backup_dir/mysql.sql.gz" | docker_compose exec -T mysql \
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD"

  echo "restoring chroma and knowledge files" >&2
  replace_tree python-service /app/chroma_store "$backup_dir/chroma.tar.gz"
  replace_tree backend /var/huiyi/knowledge "$backup_dir/knowledge.tar.gz"

  echo "starting the stack" >&2
  docker_compose up -d
  wait_until_healthy 60 mysql backend python-service
  echo "restore complete" >&2
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  run_restore "$@"
fi
