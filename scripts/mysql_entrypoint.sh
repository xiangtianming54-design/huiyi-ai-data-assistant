#!/usr/bin/env bash
# The official image runs docker-entrypoint-initdb.d only on an empty data
# directory. After that entrypoint execs the final mysqld, run the same
# password script again so an existing volume follows RO_DB_PASSWORD.
set -uo pipefail

child=0

stop_child() {
  if [[ "$child" -ne 0 ]] && kill -0 "$child" 2>/dev/null; then
    kill -TERM "$child" 2>/dev/null || true
    wait "$child" 2>/dev/null || true
  fi
}

on_signal() {
  stop_child
  exit 0
}

trap on_signal TERM INT

/usr/local/bin/docker-entrypoint.sh "$@" &
child=$!

ready=0
i=0
while [[ "$i" -lt 180 ]]; do
  i=$((i + 1))
  if ! kill -0 "$child" 2>/dev/null; then
    wait "$child"
    exit $?
  fi
  cmd=""
  if [[ -r "/proc/${child}/cmdline" ]]; then
    cmd="$(tr '\0' ' ' < "/proc/${child}/cmdline" 2>/dev/null || true)"
  fi
  if [[ "$cmd" == *mysqld* ]]; then
    if mysqladmin --protocol=socket --user=root --password="${MYSQL_ROOT_PASSWORD}" ping --silent >/dev/null 2>&1; then
      ready=1
      break
    fi
  fi
  sleep 1
done

if [[ "$ready" -ne 1 ]]; then
  echo "mysql entrypoint: mysqld did not become ready" >&2
  stop_child
  exit 1
fi

stripped="$(mktemp)"
tr -d '\r' < /docker-entrypoint-initdb.d/03_ro_password.sh > "$stripped"
aligned=0
i=0
while [[ "$i" -lt 5 ]]; do
  i=$((i + 1))
  if bash "$stripped"; then
    aligned=1
    break
  fi
  sleep 2
done
rm -f "$stripped"

if [[ "$aligned" -ne 1 ]]; then
  echo "mysql entrypoint: huiyi_ro password was not aligned" >&2
  stop_child
  exit 1
fi

wait "$child"
exit $?
