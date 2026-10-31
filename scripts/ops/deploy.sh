#!/usr/bin/env bash
# Deploy a git tag to staging or prod. On failure, check out the last healthy
# commit once, rewrite the two env keys, and build again. A second failure
# exits non-zero and does not check out anything else.
# Does not pull images, create tags, or push.
# HUIYI_DEPLOY_COMPOSE, when set, replaces docker compose and the alive probe.
# Production leaves it unset. Tests set it to echo.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/backup.sh"
# shellcheck disable=SC1091
source "$root/scripts/ops/lib/deploy.sh"

usage() {
  echo "usage: deploy.sh <staging|prod> <tag>" >&2
  exit 1
}

# Run docker compose, or the stand-in command named by HUIYI_DEPLOY_COMPOSE.
external() {
  if [[ -n "${HUIYI_DEPLOY_COMPOSE:-}" ]]; then
    "$HUIYI_DEPLOY_COMPOSE" "$@"
    return $?
  fi
  "$@"
}

run_compose() {
  external docker compose "${compose_args[@]}" "$@"
}

run_probe() {
  if [[ -n "${HUIYI_DEPLOY_COMPOSE:-}" ]]; then
    external probe_alive "$env_name"
    return $?
  fi
  probe_alive "$env_name"
}

# Steps 5–8 for whatever commit is checked out now.
release_steps() {
  local commit
  commit="$(git rev-parse HEAD)"
  update_env_keys "$env_file" "$commit" "$tag" || return 1
  run_compose build || return 1
  run_compose up -d || return 1
  run_probe || return 1
}

record_success() {
  local now
  now="$(git rev-parse HEAD)"
  rollback_target "$state_file" "$now" true >/dev/null
  exit 0
}

rollback_release() {
  if [[ "$rolled_back" -eq 1 ]]; then
    exit 1
  fi
  rolled_back=1
  echo "deploy failed; rolling back to ${target}" >&2
  if ! git checkout --detach "$target"; then
    exit 1
  fi
  if ! release_steps; then
    exit 1
  fi
  record_success
}

main() {
  local env_name="${1:-}"
  local tag="${2:-}"
  local line env_file current healthy target i state_file
  local rolled_back=0
  local -a compose_args=()

  if [[ "$#" -ne 2 ]]; then
    usage
  fi
  case "$env_name" in
    staging|prod) ;;
    *)
      echo "deploy.sh: refusing env ${env_name}" >&2
      exit 1
      ;;
  esac
  if [[ -z "$tag" ]]; then
    usage
  fi

  cd "$root"
  git fetch --tags

  line="$(compose_files "$env_name")"
  read -r -a compose_args <<< "$line"
  env_file=""
  for ((i = 0; i < ${#compose_args[@]}; i++)); do
    if [[ "${compose_args[i]}" == "--env-file" ]]; then
      env_file="${compose_args[i + 1]:-}"
    fi
  done
  if [[ -z "$env_file" ]]; then
    echo "compose_files did not include --env-file" >&2
    exit 1
  fi

  mkdir -p "$root/logs"
  state_file="$root/logs/ops-deploy-previous-${env_name}"
  current="$(git rev-parse HEAD)"
  healthy=false
  if run_probe; then
    healthy=true
  fi
  target="$(rollback_target "$state_file" "$current" "$healthy")"

  if ! git checkout --detach "$tag"; then
    echo "checkout of ${tag} failed; rolling back to ${target}" >&2
    rollback_release
  fi
  if ! release_steps; then
    rollback_release
  fi
  record_success
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
