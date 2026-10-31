# 压测报告

本文件是三场景骨架。k6 尚未对 staging 执行。实测单元格保持「未对 staging 执行」。不要把空单元格填成达标数字。

登录体为 `{"username","password"}`，token 在 `data.accessToken`，请求头为 `Authorization: Bearer`。口令只来自环境变量 `K6_BASE_URL`、`K6_USERNAME`、`K6_PASSWORD`、`K6_WORKSPACE_ID`。仓库不保存口令。本任务不执行 k6，也不把 k6 放进 GitHub Actions。

事后填写时：

- `huiyi.async.failed` 读 `GET /actuator/metrics/huiyi.async.failed`，压测前记一笔，压测后记一笔。
- 慢查询条数读 staging MySQL 数据卷内的 `/var/lib/mysql/slow.log`（`long_query_time=1`）。prod 不打开慢查询日志。
- 容器内存峰值看 backend 与 python 容器。
- `sql_validate_latency_ms` 读 `/actuator/metrics/sql_validate_latency_ms`。目标 < 300ms。若百分位未开，记录 MAX 与 COUNT，并在备注说明。

## 三场景

| 场景 | 脚本 | 负载 | 目标 | 实测 P95 | 错误率 | 模型名 | 降级声明 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RAG | `scripts/ops/k6/rag.js` | 10 秒爬到 20 VU，再保持 5 分钟 | `http_req_waiting` p95 < 3000；`http_req_duration` p95 < 6000。问句「什么是有效订单？」，路径 `/api/v1/workspaces/${K6_WORKSPACE_ID}/chat/stream` | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 |
| Text-to-SQL | `scripts/ops/k6/text2sql.js` | 10 秒爬到 10 VU，再保持 5 分钟 | `http_req_duration` p95 < 10000。问句「上个月销售额最高的 10 个商品是什么？」，路径以 `/analysis/query` 结尾 | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 |
| SSE | `scripts/ops/k6/sse.js` | 30 秒爬到 50 VU，再保持 3 分钟 | `http_req_failed` rate < 0.01；响应体含 `data:`（只把 2xx 正文计入 `sse_data_frames`，rate == 1；2xx 缺帧失败，超时和非 2xx 不计入该 rate）；无中途断流。问句「什么是有效订单？」，路径 `/chat/stream` | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 | 未对 staging 执行 |

## 伴随观测

| 项 | 实测 |
| --- | --- |
| `huiyi.async.failed` 前 | 未对 staging 执行 |
| `huiyi.async.failed` 后 | 未对 staging 执行 |
| 慢查询条数 | 未对 staging 执行 |
| backend 容器内存峰值 | 未对 staging 执行 |
| python 容器内存峰值 | 未对 staging 执行 |

## 不可降级

LLM 延迟（首字 3s、RAG 总时长 6s、Text-to-SQL 10s）可以事后填写降级声明，写明模型名、实测 P95，以及一句调参或接受该模型延迟的说明。错误率、断流、危险 SQL、校验 Timer 有数这四栏没有这条退路。

| 栏 | 目标 | 实测 |
| --- | --- | --- |
| 错误率 | < 1%（`http_req_failed` rate < 0.01） | 未对 staging 执行 |
| 断流 | SSE 无中途断流；只把 2xx 正文计入 `sse_data_frames`，含 `data:` 时 rate == 1。超时和非 2xx 不计入该 rate | 未对 staging 执行 |
| 危险 SQL | `setup` 发送「删除所有销售订单。」，响应正文必须包含「该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。」 | 未对 staging 执行 |
| 校验 Timer 有数 | `sql_validate_latency_ms` 有数，目标 < 300ms | 未对 staging 执行 |

## 降级声明

只用于 LLM 延迟。当前：未对 staging 执行。
