#!/usr/bin/env bash
# Backup MySQL database huiyi_demo, Chroma at /app/chroma_store, and knowledge
# files at /var/huiyi/knowledge. Redis is omitted. Running this file starts a
# backup; sourcing scripts/ops/lib/backup.sh does not.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/backup.sh"

BACKUP_DEST=""
BACKUP_COMPLETE=0

cleanup_incomplete_backup() {
  local target="${BACKUP_DEST:-}"
  [[ "${BACKUP_COMPLETE:-0}" -eq 1 ]] && return 0
  [[ -n "$target" ]] || return 0
  [[ "$(basename "$target")" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || return 0
  rm -rf -- "$target"
}

run_backup() {
  local env_name="${1:-}"
  local line env_file stamp base dest retain artifact i
  local -a compose_args=()

  if [[ "$#" -ne 1 ]]; then
    echo "usage: backup.sh <dev|staging|prod>" >&2
    exit 1
  fi
  case "$env_name" in
    dev|staging|prod) ;;
    *)
      echo "usage: backup.sh <dev|staging|prod>" >&2
      exit 1
      ;;
  esac

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

  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  base="${BACKUP_DIR:-./data/backups}"
  dest="$base/$env_name/$stamp"
  retain="${BACKUP_RETAIN_DAYS:-7}"
  BACKUP_DEST="$dest"
  BACKUP_COMPLETE=0
  trap cleanup_incomplete_backup EXIT

  mkdir -p "$dest"
  export MSYS_NO_PATHCONV=1
  export MSYS2_ARG_CONV_EXCL='*'
  echo "writing $dest" >&2

  docker compose "${compose_args[@]}" exec -T mysql \
    mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --databases huiyi_demo \
    | gzip -c > "$dest/mysql.sql.gz"

  docker compose "${compose_args[@]}" exec -T python-service \
    tar -C /app/chroma_store -czf - . > "$dest/chroma.tar.gz"

  docker compose "${compose_args[@]}" exec -T backend \
    tar -C /var/huiyi/knowledge -czf - . > "$dest/knowledge.tar.gz"

  for artifact in mysql.sql.gz chroma.tar.gz knowledge.tar.gz; do
    if [[ ! -s "$dest/$artifact" ]]; then
      echo "backup artifact is empty: $dest/$artifact" >&2
      exit 1
    fi
  done

  BACKUP_COMPLETE=1
  prune_backups "$base/$env_name" "$retain"
  printf '%s\n' "$dest"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  run_backup "$@"
fi
