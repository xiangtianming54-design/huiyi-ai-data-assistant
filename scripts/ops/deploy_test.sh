#!/usr/bin/env bash
# update_env_keys, rollback_target, and deploy.sh against a temporary git repo.
# A break this file catches: secret lines rewritten, duplicate version keys,
# a failed deploy overwriting the last healthy sha, or checkout of this worktree.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
lib="$root/scripts/ops/lib/deploy.sh"
entry="$root/scripts/ops/deploy.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

run_status() {
  set +e
  "$@"
  local status=$?
  set -e
  printf '%s' "$status"
}

deploy_capture() {
  local out="$1"
  local err="$2"
  shift 2
  set +e
  "$@" >"$out" 2>"$err"
  local status=$?
  set -e
  printf '%s' "$status"
}

count_prefix() {
  local file="$1"
  local prefix="$2"
  local n
  n="$(grep -c "^${prefix}" "$file" || true)"
  printf '%s' "$n"
}

assert_one_key() {
  local file="$1"
  local key="$2"
  local value="$3"
  local n
  n="$(count_prefix "$file" "${key}=")"
  [[ "$n" == "1" ]] || fail "$key count in $file is $n"
  grep -qx "${key}=${value}" "$file" || fail "$file missing ${key}=${value}"
}

assert_prefix_bytes() {
  local original="$1"
  local updated="$2"
  local bytes
  bytes="$(wc -c < "$original" | tr -d '[:space:]')"
  head -c "$bytes" "$updated" | cmp -s - "$original" || fail "original env bytes changed"
}

tmp="$(mktemp -d)"
wt_head="$(git -C "$root" rev-parse HEAD)"
wt_branch="$(git -C "$root" rev-parse --abbrev-ref HEAD)"
cleanup() {
  local now
  now="$(git -C "$root" rev-parse HEAD 2>/dev/null || true)"
  if [[ -n "${now}" && -n "${wt_head}" && "$now" != "$wt_head" ]]; then
    git -C "$root" checkout "$wt_branch" >/dev/null 2>&1 || git -C "$root" checkout "$wt_head" >/dev/null 2>&1 || true
    echo "FAIL: restored worktree after unexpected checkout" >&2
  fi
  rm -rf "$tmp"
}
trap cleanup EXIT

if [[ ! -f "$lib" ]]; then
  fail "function or file does not exist: $lib"
fi

# shellcheck disable=SC1090
source "$lib"

for fn in update_env_keys rollback_target probe_alive; do
  if ! declare -F "$fn" >/dev/null 2>&1; then
    fail "$fn is not defined"
  fi
done

envf="$tmp/env"
printf '%s\n' 'MYSQL_ROOT_PASSWORD=secret' 'JWT_SECRET=abc' > "$envf"
cp "$envf" "$envf.before"
update_env_keys "$envf" deadbeef v1.4.0
assert_prefix_bytes "$envf.before" "$envf"
grep -qx 'MYSQL_ROOT_PASSWORD=secret' "$envf" || fail "MYSQL_ROOT_PASSWORD was dropped"
grep -qx 'JWT_SECRET=abc' "$envf" || fail "JWT_SECRET was dropped"
assert_one_key "$envf" GIT_COMMIT deadbeef
assert_one_key "$envf" APP_VERSION v1.4.0
update_env_keys "$envf" deadbeef v1.4.0
assert_prefix_bytes "$envf.before" "$envf"
assert_one_key "$envf" GIT_COMMIT deadbeef
assert_one_key "$envf" APP_VERSION v1.4.0

look="$tmp/look.env"
printf '%s\n' '# GIT_COMMIT=do-not-touch' 'NOT_GIT_COMMIT=keep' 'MYSQL_ROOT_PASSWORD=secret' 'JWT_SECRET=abc' > "$look"
update_env_keys "$look" deadbeef v1.4.0
printf '%s\n' \
  '# GIT_COMMIT=do-not-touch' \
  'NOT_GIT_COMMIT=keep' \
  'MYSQL_ROOT_PASSWORD=secret' \
  'JWT_SECRET=abc' \
  'GIT_COMMIT=deadbeef' \
  'APP_VERSION=v1.4.0' > "$tmp/look.expect"
cmp -s "$tmp/look.expect" "$look" || fail "lookalike lines were not preserved byte for byte"

crlf="$tmp/crlf.env"
printf 'MYSQL_ROOT_PASSWORD=secret\r\nGIT_COMMIT=old\r\nJWT_SECRET=abc\r\nAPP_VERSION=old\r\n' > "$crlf"
update_env_keys "$crlf" deadbeef v1.4.0
printf 'MYSQL_ROOT_PASSWORD=secret\r\nGIT_COMMIT=deadbeef\r\nJWT_SECRET=abc\r\nAPP_VERSION=v1.4.0\r\n' > "$tmp/crlf.expect"
cmp -s "$tmp/crlf.expect" "$crlf" || fail "CRLF env was not a byte-for-byte key replace"
update_env_keys "$crlf" deadbeef v1.4.0
cmp -s "$tmp/crlf.expect" "$crlf" || fail "second CRLF update changed bytes"

dup="$tmp/dup.env"
printf '%s\n' 'GIT_COMMIT=one' 'MYSQL_ROOT_PASSWORD=secret' 'GIT_COMMIT=two' 'APP_VERSION=v1' 'JWT_SECRET=abc' 'APP_VERSION=v2' > "$dup"
update_env_keys "$dup" deadbeef v1.4.0
printf '%s\n' 'GIT_COMMIT=deadbeef' 'MYSQL_ROOT_PASSWORD=secret' 'APP_VERSION=v1.4.0' 'JWT_SECRET=abc' > "$tmp/dup.expect"
cmp -s "$tmp/dup.expect" "$dup" || fail "duplicate keys were not collapsed without touching other lines"

state="$tmp/state"
printf 'aaa\n' > "$state"
cp "$state" "$state.before"
out="$(rollback_target "$state" bbb false)"
[[ "$out" == "aaa" ]] || fail "unhealthy rollback_target printed [$out]"
cmp -s "$state.before" "$state" || fail "unhealthy rollback_target changed the state file"

bare="$tmp/bare-state"
printf 'aaa' > "$bare"
cp "$bare" "$bare.before"
out="$(rollback_target "$bare" bbb false)"
[[ "$out" == "aaa" ]] || fail "bare unhealthy rollback_target printed [$out]"
cmp -s "$bare.before" "$bare" || fail "bare unhealthy rollback_target changed the state file"

out="$(rollback_target "$state" ccc true)"
[[ "$out" == "ccc" ]] || fail "healthy rollback_target printed [$out]"
printf 'ccc\n' > "$tmp/state.expect"
cmp -s "$tmp/state.expect" "$state" || fail "healthy state file is not ccc"

empty="$tmp/empty-state"
: > "$empty"
cp "$empty" "$empty.before"
out="$(rollback_target "$empty" fff false)"
[[ "$out" == "fff" ]] || fail "empty unhealthy rollback_target printed [$out]"
cmp -s "$empty.before" "$empty" || fail "empty unhealthy rollback_target wrote a success record"

missing="$tmp/missing-state"
out="$(rollback_target "$missing" ggg false)"
[[ "$out" == "ggg" ]] || fail "missing unhealthy rollback_target printed [$out]"
[[ ! -e "$missing" ]] || fail "missing unhealthy rollback_target created $missing"

created="$tmp/created-state"
out="$(rollback_target "$created" hhh true)"
[[ "$out" == "hhh" ]] || fail "healthy create printed [$out]"
printf 'hhh\n' > "$tmp/created.expect"
cmp -s "$tmp/created.expect" "$created" || fail "healthy create did not write hhh"

[[ -f "$entry" ]] || fail "deploy.sh does not exist"
if grep -q 'compose_files()' "$entry" "$lib"; then
  fail "deploy scripts define compose_files"
fi
if ! grep -q 'backup.sh' "$entry" && ! grep -q 'backup.sh' "$lib"; then
  fail "deploy scripts do not source backup.sh"
fi

mkdir -p "$tmp/bin" "$tmp/empty-hooks"
cat > "$tmp/bin/docker" << 'EOF'
#!/usr/bin/env bash
echo "docker was invoked: $*" >&2
exit 97
EOF
chmod +x "$tmp/bin/docker"
cat > "$tmp/stub.sh" << 'EOF'
#!/usr/bin/env bash
set -u
mode="${HUIYI_STUB_MODE:-pass}"
count_file="${HUIYI_STUB_COUNT:?}"
log="${HUIYI_STUB_LOG:?}"
n=0
if [[ -f "$count_file" ]]; then
  n="$(cat "$count_file")"
fi
n=$((n + 1))
printf '%s' "$n" > "$count_file"
printf '%s\n' "$*" >> "$log"
if [[ "$mode" == "fail-all-builds" && "$*" == *" build" ]]; then
  exit 1
fi
if [[ "$mode" == "unhealthy-then-fail-build" ]]; then
  if [[ "$n" -eq 1 ]]; then
    exit 1
  fi
  if [[ "$*" == *" build" ]]; then
    bfile="${HUIYI_STUB_BUILDS:?}"
    b=0
    if [[ -f "$bfile" ]]; then
      b="$(cat "$bfile")"
    fi
    b=$((b + 1))
    printf '%s' "$b" > "$bfile"
    if [[ "$b" -eq 1 ]]; then
      exit 1
    fi
  fi
fi
exit 0
EOF
chmod +x "$tmp/stub.sh"
export PATH="$tmp/bin:${PATH}"
export GIT_TERMINAL_PROMPT=0

quiet_commit() {
  git -C "$1" \
    -c user.name=DeployTest \
    -c user.email=deploy-test@example.com \
    -c commit.gpgsign=false \
    -c "core.hooksPath=$tmp/empty-hooks" \
    commit -q -m "$2"
}

prepare_repo() {
  local name="$1"
  local n="$2"
  local i
  SRC="$tmp/src-$name"
  WORK="$tmp/work-$name"
  rm -rf "$SRC" "$WORK"
  mkdir -p "$SRC"
  git init -q "$SRC"
  for ((i = 1; i <= n; i++)); do
    printf 'commit %s\n' "$i" >> "$SRC/README"
    git -C "$SRC" -c core.autocrlf=false add README
    quiet_commit "$SRC" "c$i"
  done
  git clone -q "$SRC" "$WORK"
  mkdir -p "$WORK/scripts/ops/lib" "$WORK/logs"
  cp "$root/scripts/ops/lib/backup.sh" "$WORK/scripts/ops/lib/backup.sh"
  cp "$lib" "$WORK/scripts/ops/lib/deploy.sh"
  cp "$entry" "$WORK/scripts/ops/deploy.sh"
  printf '%s\n' 'MYSQL_ROOT_PASSWORD=secret' 'JWT_SECRET=abc' > "$WORK/.env.staging"
  printf '%s\n' 'MYSQL_ROOT_PASSWORD=secret' 'JWT_SECRET=abc' > "$WORK/.env.prod"
}

prepare_repo happy 2
c1="$(git -C "$WORK" rev-parse HEAD~1)"
printf 'three\n' >> "$SRC/README"
git -C "$SRC" -c core.autocrlf=false add README
quiet_commit "$SRC" c3
sha3="$(git -C "$SRC" rev-parse HEAD)"
if git -C "$WORK" cat-file -e "${sha3}^{commit}" 2>/dev/null; then
  fail "new commit was already in the clone"
fi

dev_before="$(git -C "$WORK" rev-parse HEAD)"
dev_status="$(deploy_capture "$tmp/dev.out" "$tmp/dev.err" env HUIYI_DEPLOY_COMPOSE=echo bash "$WORK/scripts/ops/deploy.sh" dev "$c1")"
[[ "$dev_status" != "0" ]] || fail "dev was accepted"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$dev_before" ]] || fail "dev checkout changed HEAD"
args_status="$(deploy_capture "$tmp/args.out" "$tmp/args.err" env HUIYI_DEPLOY_COMPOSE=echo bash "$WORK/scripts/ops/deploy.sh" staging)"
[[ "$args_status" != "0" ]] || fail "missing tag was accepted"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$dev_before" ]] || fail "missing tag changed HEAD"

src_head="$(git -C "$SRC" rev-parse HEAD)"
src_tags="$(git -C "$SRC" tag)"
work_tags="$(git -C "$WORK" tag)"
happy_status="$(deploy_capture "$tmp/happy.out" "$tmp/happy.err" env HUIYI_DEPLOY_COMPOSE=echo bash "$WORK/scripts/ops/deploy.sh" staging "$sha3")"
[[ "$happy_status" == "0" ]] || fail "happy deploy exited $happy_status: $(cat "$tmp/happy.err")"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$sha3" ]] || fail "happy deploy did not check out the fetched commit"
grep -q 'compose.staging.yml' "$tmp/happy.out" || fail "staging compose file was not used: $(cat "$tmp/happy.out")"
grep -q -- '--env-file .env.staging' "$tmp/happy.out" || fail "staging env file was not used: $(cat "$tmp/happy.out")"
probe_n="$(grep -c 'probe_alive staging' "$tmp/happy.out" || true)"
[[ "$probe_n" == "2" ]] || fail "probe_alive count is $probe_n: $(cat "$tmp/happy.out")"
build_n="$(grep -c ' build$' "$tmp/happy.out" || true)"
[[ "$build_n" == "1" ]] || fail "build count is $build_n: $(cat "$tmp/happy.out")"
up_n="$(grep -c 'up -d$' "$tmp/happy.out" || true)"
[[ "$up_n" == "1" ]] || fail "up count is $up_n: $(cat "$tmp/happy.out")"
if grep -q 'pull' "$tmp/happy.out"; then
  fail "deploy invoked pull: $(cat "$tmp/happy.out")"
fi
cp_before="$tmp/staging-before"
printf '%s\n' 'MYSQL_ROOT_PASSWORD=secret' 'JWT_SECRET=abc' > "$cp_before"
assert_prefix_bytes "$cp_before" "$WORK/.env.staging"
assert_one_key "$WORK/.env.staging" GIT_COMMIT "$sha3"
assert_one_key "$WORK/.env.staging" APP_VERSION "$sha3"
printf '%s\n' "$sha3" > "$tmp/state-happy.expect"
cmp -s "$tmp/state-happy.expect" "$WORK/logs/ops-deploy-previous-staging" || fail "healthy deploy did not record the new commit"
[[ "$(git -C "$SRC" rev-parse HEAD)" == "$src_head" ]] || fail "deploy pushed to origin"
[[ "$(git -C "$SRC" tag)" == "$src_tags" ]] || fail "deploy changed origin tags"
[[ "$(git -C "$WORK" tag)" == "$work_tags" ]] || fail "deploy created a git tag"

again_status="$(deploy_capture "$tmp/again.out" "$tmp/again.err" env HUIYI_DEPLOY_COMPOSE=echo bash "$WORK/scripts/ops/deploy.sh" staging "$sha3")"
[[ "$again_status" == "0" ]] || fail "second deploy of the same commit exited $again_status: $(cat "$tmp/again.err")"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$sha3" ]] || fail "second deploy moved HEAD"
assert_one_key "$WORK/.env.staging" GIT_COMMIT "$sha3"
assert_one_key "$WORK/.env.staging" APP_VERSION "$sha3"
assert_prefix_bytes "$cp_before" "$WORK/.env.staging"

prod_status="$(deploy_capture "$tmp/prod.out" "$tmp/prod.err" env HUIYI_DEPLOY_COMPOSE=echo bash "$WORK/scripts/ops/deploy.sh" prod "$sha3")"
[[ "$prod_status" == "0" ]] || fail "prod deploy exited $prod_status: $(cat "$tmp/prod.err")"
grep -q 'compose.prod.yml' "$tmp/prod.out" || fail "prod compose file was not used: $(cat "$tmp/prod.out")"
grep -q -- '--env-file .env.prod' "$tmp/prod.out" || fail "prod env file was not used: $(cat "$tmp/prod.out")"
assert_one_key "$WORK/.env.prod" GIT_COMMIT "$sha3"
assert_one_key "$WORK/.env.prod" APP_VERSION "$sha3"
cmp -s "$tmp/state-happy.expect" "$WORK/logs/ops-deploy-previous-prod" || fail "prod state file was not the deployed commit"
[[ "$(git -C "$root" rev-parse HEAD)" == "$wt_head" ]] || fail "worktree HEAD changed during echo deploy"

prepare_repo rollback 3
rb_c1="$(git -C "$WORK" rev-parse HEAD~2)"
rb_c3="$(git -C "$WORK" rev-parse HEAD)"
git -C "$WORK" checkout -q --detach "$(git -C "$WORK" rev-parse HEAD~1)"
printf '%s\n' "$rb_c1" > "$WORK/logs/ops-deploy-previous-staging"
rb_status="$(deploy_capture "$tmp/rb.out" "$tmp/rb.err" env \
  HUIYI_DEPLOY_COMPOSE="$tmp/stub.sh" \
  HUIYI_STUB_MODE=unhealthy-then-fail-build \
  HUIYI_STUB_COUNT="$tmp/rb.count" \
  HUIYI_STUB_LOG="$tmp/rb.log" \
  HUIYI_STUB_BUILDS="$tmp/rb.builds" \
  bash "$WORK/scripts/ops/deploy.sh" staging "$rb_c3")"
[[ "$rb_status" == "0" ]] || fail "rollback deploy exited $rb_status: $(cat "$tmp/rb.err") log=$(cat "$tmp/rb.log" 2>/dev/null || true)"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$rb_c1" ]] || fail "unhealthy stack did not roll back to the saved sha"
got_state="$(tr -d '\r\n' < "$WORK/logs/ops-deploy-previous-staging")"
[[ "$got_state" == "$rb_c1" ]] || fail "rollback state file is [$got_state]"
assert_one_key "$WORK/.env.staging" GIT_COMMIT "$rb_c1"
assert_one_key "$WORK/.env.staging" APP_VERSION "$rb_c3"
grep -qx 'MYSQL_ROOT_PASSWORD=secret' "$WORK/.env.staging" || fail "rollback dropped MYSQL_ROOT_PASSWORD"
grep -qx 'JWT_SECRET=abc' "$WORK/.env.staging" || fail "rollback dropped JWT_SECRET"
[[ "$(git -C "$root" rev-parse HEAD)" == "$wt_head" ]] || fail "worktree HEAD changed during rollback"

prepare_repo double 2
db_c1="$(git -C "$WORK" rev-parse HEAD~1)"
db_c2="$(git -C "$WORK" rev-parse HEAD)"
git -C "$WORK" checkout -q --detach "$db_c1"
db_status="$(deploy_capture "$tmp/db.out" "$tmp/db.err" env \
  HUIYI_DEPLOY_COMPOSE="$tmp/stub.sh" \
  HUIYI_STUB_MODE=fail-all-builds \
  HUIYI_STUB_COUNT="$tmp/db.count" \
  HUIYI_STUB_LOG="$tmp/db.log" \
  bash "$WORK/scripts/ops/deploy.sh" staging "$db_c2")"
[[ "$db_status" != "0" ]] || fail "double failure exited 0: $(cat "$tmp/db.out")"
[[ "$(git -C "$WORK" rev-parse HEAD)" == "$db_c1" ]] || fail "double failure checked out again, HEAD=$(git -C "$WORK" rev-parse HEAD)"
got_db="$(tr -d '\r\n' < "$WORK/logs/ops-deploy-previous-staging")"
[[ "$got_db" == "$db_c1" ]] || fail "double failure recorded [$got_db]"
assert_one_key "$WORK/.env.staging" GIT_COMMIT "$db_c1"
assert_one_key "$WORK/.env.staging" APP_VERSION "$db_c2"
grep -qx 'MYSQL_ROOT_PASSWORD=secret' "$WORK/.env.staging" || fail "double failure dropped MYSQL_ROOT_PASSWORD"
if grep -q 'pull' "$tmp/db.log"; then
  fail "double failure invoked pull: $(cat "$tmp/db.log")"
fi
[[ "$(git -C "$root" rev-parse HEAD)" == "$wt_head" ]] || fail "worktree HEAD changed during double failure"
[[ "$(git -C "$root" rev-parse --abbrev-ref HEAD)" == "$wt_branch" ]] || fail "worktree branch changed"

echo "PASS"
