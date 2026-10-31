#!/usr/bin/env bash
# ro_alter_sql prints nothing when RO_DB_PASSWORD is unset, and one ALTER USER
# statement otherwise. A single quote in the password is written as ''.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
script="$root/scripts/init_ro_password.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ ! -f "$script" ]]; then
  fail "function or file does not exist: $script"
fi

# Runs ro_alter_sql in a child so a top-level exit inside the sourced file
# cannot skip later assertions. Password is an argument, not the parent env.
# Sets CAPTURE_STATUS and CAPTURE_OUT. CAPTURE_OUT includes a __SOURCED__
# marker printed only after source returns.
capture() {
  local mode="$1"
  local password="$2"
  local stdout
  set +e
  stdout="$(
    bash --noprofile --norc -c '
      set -euo pipefail
      mode="$1"
      password="$2"
      script="$3"
      if [[ "$mode" == "set" ]]; then
        export RO_DB_PASSWORD="$password"
      else
        unset RO_DB_PASSWORD
      fi
      # shellcheck disable=SC1090
      source "$script"
      printf "__SOURCED__\n"
      if ! declare -F ro_alter_sql >/dev/null 2>&1; then
        echo "ro_alter_sql is not defined" >&2
        exit 1
      fi
      ro_alter_sql
    ' bash "$mode" "$password" "$script"
  )"
  CAPTURE_STATUS=$?
  set -e
  CAPTURE_OUT=$stdout
}

body_after_source() {
  local body="${CAPTURE_OUT#*__SOURCED__}"
  body="${body//$'\r'/}"
  body="${body//$'\n'/}"
  printf '%s' "$body"
}

assert_unset_is_silent() {
  capture unset ""
  [[ "$CAPTURE_STATUS" -eq 0 ]] || fail "unset RO_DB_PASSWORD exited $CAPTURE_STATUS, output [$CAPTURE_OUT]"
  [[ "$CAPTURE_OUT" == *"__SOURCED__"* ]] || fail "ro_alter_sql is not defined (source did not return): [$CAPTURE_OUT]"
  local body
  body="$(body_after_source)"
  [[ -z "$body" ]] || fail "unset RO_DB_PASSWORD expected no output, got [$body]"
}

assert_sql() {
  local password="$1"
  local expected="$2"
  capture set "$password"
  [[ "$CAPTURE_STATUS" -eq 0 ]] || fail "RO_DB_PASSWORD set exited $CAPTURE_STATUS, output [$CAPTURE_OUT]"
  [[ "$CAPTURE_OUT" == *"__SOURCED__"* ]] || fail "ro_alter_sql is not defined (source did not return): [$CAPTURE_OUT]"
  local body
  body="$(body_after_source)"
  [[ "$body" == "$expected" ]] || fail "expected [$expected] got [$body]"
}

assert_direct_exec() {
  local tmp capture status args
  tmp="$(mktemp -d)"
  capture="$tmp/capture"
  cat > "$tmp/mysql" << 'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" > "${MYSQL_CAPTURE_FILE}"
EOF
  chmod +x "$tmp/mysql"

  set +e
  env -u RO_DB_PASSWORD MYSQL_CAPTURE_FILE="$capture" PATH="$tmp:/usr/bin:/bin" \
    bash "$script" >/dev/null
  status=$?
  set -e
  [[ "$status" -eq 0 ]] || fail "direct exec with empty RO_DB_PASSWORD exited $status"
  [[ ! -e "$capture" ]] || fail "direct exec with empty password invoked mysql"

  set +e
  env RO_DB_PASSWORD="a'b" MYSQL_ROOT_PASSWORD='secret' MYSQL_CAPTURE_FILE="$capture" PATH="$tmp:/usr/bin:/bin" \
    bash "$script" >/dev/null
  status=$?
  set -e
  [[ "$status" -eq 0 ]] || fail "direct exec with password exited $status"
  [[ -f "$capture" ]] || fail "direct exec did not invoke mysql"
  args="$(<"$capture")"
  [[ "$args" == *"--user=root"* ]] || fail "mysql args missing --user=root: [$args]"
  [[ "$args" == *"--password=secret"* ]] || fail "mysql args missing --password=secret: [$args]"
  [[ "$args" == *"IDENTIFIED BY 'a''b'"* ]] || fail "mysql SQL missing escaped password: [$args]"
  rm -rf "$tmp"
}

assert_unset_is_silent
assert_rejected() {
  local password="$1"
  capture set "$password"
  [[ "$CAPTURE_STATUS" -ne 0 ]] || fail "password [$password] should be refused, got [$CAPTURE_OUT]"
  local body
  body="$(body_after_source)"
  [[ "$body" != *"ALTER USER"* ]] || fail "refused password still emitted SQL [$body]"
}

assert_sql "abc" "ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY 'abc';"
assert_sql "a'b" "ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY 'a''b';"
assert_sql $'a\\b' "ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY 'a\\\\b';"
assert_sql $'foo\\' "ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY 'foo\\\\';"
assert_rejected $'a\nb'
assert_direct_exec

echo "PASS"
