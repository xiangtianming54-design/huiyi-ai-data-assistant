# 压测报告

2026-11-08 18:32–18:47 对本机 `huiyi-staging` 重跑。对话模型是智谱 `glm-5.2`（`https://open.bigmodel.cn/api/paas/v4`）。文档分块和检索向量走硅基流动 `BAAI/bge-m3`。口令只来自环境变量。

`sse.js` 每次只发一条 `chat/stream`，然后 `sleep` 300 秒，让这个虚拟用户占住连接，不再空转。上一轮把对话和向量都指到硅基流动、并且 SSE 收完就立刻重发，那组数字作废。

## 三场景

| 场景 | 脚本 | 负载 | 目标 | 实测 P95 | 错误率 | 模型名 | 降级声明 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RAG | `scripts/ops/k6/rag.js` | 10 秒爬到 20 VU，再保持 5 分钟 | `http_req_waiting` p95 < 3000；`http_req_duration` p95 < 6000 | waiting 15592ms；duration 16096ms | 0%（1289 次请求；checks 1288/1288） | glm-5.2 | 超过首字 3 秒和总时长 6 秒。接受 `glm-5.2` 这次的延迟 |
| Text-to-SQL | `scripts/ops/k6/text2sql.js` | 10 秒爬到 10 VU，再保持 5 分钟 | `http_req_duration` p95 < 10000 | duration 19615ms | 0%（672 次请求；checks 670/670） | glm-5.2 | 超过 10 秒。接受 `glm-5.2` 这次的延迟 |
| SSE | `scripts/ops/k6/sse.js` | 30 秒爬到 50 VU，再保持 3 分钟；每个虚拟用户只发一条后挂起 | `http_req_failed` rate < 0.01；2xx 正文的 `sse_data_frames` rate == 1 | duration 9347ms | 0%（51 次请求，含 1 次登录；50 条 SSE 全部 2xx） | glm-5.2 | 错误率已低于 1%，这条不需要降级 |

k6 退出码：RAG 99，Text-to-SQL 99，SSE 0。99 只因为延迟阈值，不是错误率。

## 伴随观测

| 项 | 实测 |
| --- | --- |
| `huiyi.async.failed` 前 | 这轮压测开始前没有读 |
| `huiyi.async.failed` 后 | 4.0。2026-11-08T10:58:04Z 读 `/actuator/metrics/huiyi.async.failed` |
| 慢查询条数 | 0。`/var/lib/mysql/slow.log` 只有 mysqld 启动头，没有 `Query_time` |
| backend 容器内存峰值 | 876.1 MiB。cgroup `memory.peak`，从这轮 GLM 容器启动算起，覆盖 18:32–18:47 的压测 |
| python 容器内存峰值 | 225.3 MiB。同一口径 |

## 不可降级

| 栏 | 目标 | 实测 |
| --- | --- | --- |
| 错误率 | < 1% | 通过。三个场景的 `http_req_failed` 都是 0 |
| 断流 | 2xx 正文计入 `sse_data_frames`，含 `data:` 时 rate == 1 | 通过。50/50，rate 为 1 |
| 危险 SQL | setup 发送「删除所有销售订单。」，正文含指定拒绝句 | 通过。Text-to-SQL 能跑完，setup 没有因拒绝文案失败 |
| 校验 Timer 有数 | `sql_validate_latency_ms` 有数，目标 < 300ms | 有数。COUNT 146，TOTAL_TIME 2.069 秒，平均 14.2 毫秒。分位没开，读取时 MAX 已衰减为 0，给不出 p95。平均远低于 300 毫秒 |

## 降级声明

只用于 LLM 延迟。模型 `glm-5.2`。RAG 首字 p95 15.6 秒、总时长 p95 16.1 秒，Text-to-SQL 总时长 p95 19.6 秒。SSE 单条总时长 p95 9.3 秒。这次接受该模型的延迟。错误率和断流不在这条声明里。

重跑前有一次 Text-to-SQL 因容器访问 `open.bigmodel.cn` 失败返回「AI 服务暂不可用」，紧接着同一问返回 10 行。压测全程错误率仍是 0。
