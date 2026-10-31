#!/usr/bin/env bash
# Static shape checks for the three k6 scenarios and the load-test report.
# Does not run k6.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
k6="$root/scripts/ops/k6"
report="$root/docs/ops/load-test-report.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "function or file does not exist: $1"
}

require_grep() {
  local file="$1"
  local needle="$2"
  grep -F -q -- "$needle" "$file" || fail "$(basename "$file") does not contain: $needle"
}

reject_password_literal() {
  local file="$1"
  if grep -E -q "K6_PASSWORD=['\"][^'\"]+['\"]" "$file"; then
    fail "$(basename "$file") assigns a K6_PASSWORD literal"
  fi
  if grep -E -q "password: ['\"][^'\"]+['\"]" "$file"; then
    fail "$(basename "$file") contains a password literal"
  fi
}

assert_order() {
  local file="$1"
  local earlier="$2"
  local later="$3"
  local earlier_line later_line
  earlier_line="$(grep -n -F -- "$earlier" "$file" | head -n 1 | cut -d: -f1)"
  later_line="$(grep -n -F -- "$later" "$file" | head -n 1 | cut -d: -f1)"
  [[ -n "$earlier_line" ]] || fail "$(basename "$file") does not contain: $earlier"
  [[ -n "$later_line" ]] || fail "$(basename "$file") does not contain: $later"
  [[ "$earlier_line" -lt "$later_line" ]] || fail "$(basename "$file") has '$later' before '$earlier'"
}

require_common() {
  local file="$1"
  require_grep "$file" "K6_BASE_URL"
  require_grep "$file" "K6_USERNAME"
  require_grep "$file" "K6_PASSWORD"
  require_grep "$file" "K6_WORKSPACE_ID"
  require_grep "$file" "export function setup"
  require_grep "$file" "/api/v1/auth/login"
  require_grep "$file" "data.accessToken"
  require_grep "$file" "Authorization"
  require_grep "$file" "Bearer"
  require_grep "$file" "fail("
  reject_password_literal "$file"
}

rag="$k6/rag.js"
text2sql="$k6/text2sql.js"
sse="$k6/sse.js"

require_file "$rag"
require_file "$text2sql"
require_file "$sse"
require_file "$report"

require_common "$rag"
require_common "$text2sql"
require_common "$sse"

require_grep "$rag" "duration: '10s'"
require_grep "$rag" "duration: '5m'"
require_grep "$rag" "target: 20"
require_grep "$rag" "http_req_waiting: ['p(95)<3000']"
require_grep "$rag" "http_req_duration: ['p(95)<6000']"
require_grep "$rag" "/chat/stream"
require_grep "$rag" "什么是有效订单？"
assert_order "$rag" "duration: '10s'" "duration: '5m'"

require_grep "$text2sql" "duration: '10s'"
require_grep "$text2sql" "duration: '5m'"
require_grep "$text2sql" "target: 10"
require_grep "$text2sql" "http_req_duration: ['p(95)<10000']"
require_grep "$text2sql" "/analysis/query"
require_grep "$text2sql" "上个月销售额最高的 10 个商品是什么？"
require_grep "$text2sql" "删除所有销售订单。"
require_grep "$text2sql" "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。"
assert_order "$text2sql" "duration: '10s'" "duration: '5m'"
assert_order "$text2sql" "export function setup" "删除所有销售订单。"
assert_order "$text2sql" "删除所有销售订单。" "export default function"

require_grep "$sse" "duration: '30s'"
require_grep "$sse" "duration: '3m'"
require_grep "$sse" "target: 50"
require_grep "$sse" "http_req_failed: ['rate<0.01']"
require_grep "$sse" "data:"
require_grep "$sse" "/chat/stream"
require_grep "$sse" "什么是有效订单？"
assert_order "$sse" "duration: '30s'" "duration: '3m'"

require_grep "$report" "未对 staging 执行"
require_grep "$report" "rag.js"
require_grep "$report" "text2sql.js"
require_grep "$report" "sse.js"
require_grep "$report" "实测 P95"
require_grep "$report" "错误率"
require_grep "$report" "模型名"
require_grep "$report" "huiyi.async.failed"
require_grep "$report" "慢查询"
require_grep "$report" "内存峰值"
require_grep "$report" "降级声明"
require_grep "$report" "断流"
require_grep "$report" "危险 SQL"
require_grep "$report" "校验 Timer"
require_grep "$report" "sql_validate_latency_ms"
require_grep "$report" "没有这条退路"
require_grep "$report" "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。"

placeholder_count="$(grep -F -c "未对 staging 执行" "$report" || true)"
[[ "$placeholder_count" -ge 10 ]] || fail "load-test-report.md has ${placeholder_count} copies of 未对 staging 执行, expected at least 10"

if grep -F -q "已通过" "$report"; then
  fail "load-test-report.md contains 已通过"
fi

workflows="$root/.github/workflows"
if [[ -d "$workflows" ]]; then
  if grep -R -I -n -E '(^|[^A-Za-z0-9_])k6([^A-Za-z0-9_]|$)' "$workflows"; then
    fail "k6 must not be referenced from GitHub Actions"
  fi
fi

echo PASS
