# HuiYi v0.8 · Observability & Cost 开发文档

> 版本：v0.8.0  
> 状态：已发布（标签 `v0.8.0`）  
> 基线：`huiyi-ai-data-assistant v0.7.0`（标签 `v0.7.0`）  
> 来源：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§8；[roadmap.md](../../roadmap.md)  
>
> 未纳入：P2 调试面板、问题反馈携带 request_id、Python embedding/向量指标；Grafana；Agent Loop。v0.7 P1 UI 不滚进本版。

周期：2 周。

定位：本版本做 **请求级可观测性与成本统计**，不做完整 Agent Runtime。  
`Agent Trace / Tool Trace / Planner Trace` 延后到 v0.9。  
本版本不引入 Grafana 大盘；指标出口优先使用 `Actuator + Micrometer + /actuator/prometheus`。

---

## 1. 目标

建立请求级可观测体系，让每次 Chat / Analysis 调用可追踪、可统计、可审计，并能回答：

```text
1. 这次请求的 request_id 是什么；
2. 这次完整问答 / 分析链路的 run_id 是什么；
3. 请求在哪一层失败：Intent、Rewrite、RAG、SQL Generate、SQL Validate、SQL Execute、LLM Summary；
4. 本次 LLM 调用消耗了多少 Token；
5. 本次请求归属于哪个 workspace / user；
6. 当前基础指标是否反映成功率、失败率和耗时。
```

## 2. 核心能力

```text
1. Request ID；
2. Run ID / assistant_run_id；
3. 统一结构化日志；
4. Request Trace：Intent → Rewrite → RAG / SQL → LLM Summary；
5. LLM Token 统计；
6. LLM 成本字段与基础成本估算；
7. Metrics：Chat / RAG / LLM / SQL / Python Sidecar；
8. 健康检查增强。
```

边界说明：

```text
1. 本版本的 Run ID 表示一次完整 Chat / Analysis 链路；
2. 本版本的 Trace 是请求级 Trace，不是 Agent Run Trace；
3. Tool Registry、Tool Executor、Agent Loop、Planner Trace 延后到 v0.9；
4. 本版提供 /actuator/prometheus 或等价 Metrics 出口；
5. 不做 Grafana 大盘、复杂告警平台和生产监控中心。
```

## 3. 非目标

```text
1. 不做 Grafana；
2. 不做复杂告警平台；
3. 不做 Agent Loop；
4. 不做 Tool Registry；
5. 不做 Tool Executor；
6. 不做 Planner；
7. 不做 ReAct；
8. 不做 Plan-and-Execute；
9. 不做多模型路由；
10. 不做完整成本分摊和账单系统；
11. 不补做 v0.7 P1：用户状态 API、前端工作区切换 / 会话列表 UI。
```

## 4. 反目标

- 没有 `request_id` 的新审计事件（健康检查除外）。
- 把 token 用量写进普通 INFO 日志，或记录完整 Prompt 明文、密码、Token。
- 把请求级 Trace 做成 Agent Loop Trace，或为此提前引入 Tool Registry。
- 为了指标引入第二套用户体系。

## 5. 约束

- 继续 Java 主编排 + Python Sidecar；8001 仍只绑 `127.0.0.1`（或生产不映射）。
- `audit_events` 已在 v0.7 建立：不重建，只确保关键链路写入、可携带 `request_id`、不记录密码 / Token / 完整 Prompt。
- 密码、JWT、工作区隔离行为与 v0.7 保持兼容；本版不改 RBAC 语义。
- 日志与成本数据不得写入 git；密钥仍只走环境变量。
- `cost_amount` 允许默认 0；本版不要求配置真实模型单价（真实价格在 v1.1）。
- 五场景与企业隔离 e2e 仍须通过。

## 6. 数据库设计

### assistant_runs

记录一次完整 Chat / Analysis 链路。

```sql
CREATE TABLE assistant_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL UNIQUE,
  request_id VARCHAR(64) NOT NULL,
  session_id VARCHAR(64),
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  entrypoint VARCHAR(64) NOT NULL,
  question TEXT NOT NULL,
  intent VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  total_tokens INT DEFAULT 0,
  cost_amount DECIMAL(12,6) DEFAULT 0,
  total_latency_ms INT,
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_workspace_user_time (workspace_id, user_id, created_at),
  INDEX idx_request_id (request_id),
  INDEX idx_status_time (status, created_at)
);
```

### assistant_run_steps

记录当前固定链路中的关键步骤。

```sql
CREATE TABLE assistant_run_steps (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64),
  model VARCHAR(128),
  input_summary TEXT,
  output_summary TEXT,
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  total_tokens INT DEFAULT 0,
  latency_ms INT,
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_run_id (run_id),
  INDEX idx_step_type_status (step_type, status)
);
```

### llm_calls

记录每次 LLM 调用，可与 `request_id` / `run_id` 关联。

```sql
CREATE TABLE llm_calls (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  session_id VARCHAR(64),
  workspace_id BIGINT,
  user_id BIGINT,
  purpose VARCHAR(64) NOT NULL,
  provider VARCHAR(64),
  model VARCHAR(128),
  prompt_template_code VARCHAR(64),
  prompt_template_version VARCHAR(64),
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  total_tokens INT DEFAULT 0,
  cost_amount DECIMAL(12,6) DEFAULT 0,
  latency_ms INT,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_request_id (request_id),
  INDEX idx_run_id (run_id),
  INDEX idx_workspace_time (workspace_id, created_at),
  INDEX idx_status_time (status, created_at)
);
```

Flyway（接 v0.7 的 V1–V6 之后）：

```text
V7__assistant_runs.sql
V8__assistant_run_steps.sql
V9__llm_calls.sql
```

不引入 `usage_events`。用量与成本字段落在 `assistant_runs` / `llm_calls`。

## 7. Request Trace 设计

一次 Chat / Analysis Run 输出请求级 Trace：

```json
{
  "request_id": "req_123",
  "run_id": "run_456",
  "session_id": "s_001",
  "workspace_id": 1,
  "user_id": 100,
  "entrypoint": "chat_stream",
  "question": "上个月华东销售额是多少？",
  "intent": "data_query",
  "status": "SUCCEEDED",
  "steps": [
    {
      "step_no": 1,
      "step_type": "intent",
      "method": "embedding",
      "status": "SUCCESS",
      "latency_ms": 120
    },
    {
      "step_no": 2,
      "step_type": "query_rewrite",
      "status": "SUCCESS",
      "latency_ms": 260
    },
    {
      "step_no": 3,
      "step_type": "sql_generate",
      "purpose": "text_to_sql",
      "model": "qwen2.5-7b",
      "status": "SUCCESS",
      "latency_ms": 1500,
      "prompt_tokens": 1200,
      "completion_tokens": 80
    },
    {
      "step_no": 4,
      "step_type": "sql_validate",
      "tool": "sql_guard",
      "status": "PASSED",
      "latency_ms": 40
    },
    {
      "step_no": 5,
      "step_type": "sql_execute",
      "status": "SUCCESS",
      "latency_ms": 320,
      "rows": 10
    },
    {
      "step_no": 6,
      "step_type": "llm_summarize",
      "purpose": "summarize",
      "status": "SUCCESS",
      "latency_ms": 900,
      "prompt_tokens": 800,
      "completion_tokens": 240
    }
  ],
  "token_usage": {
    "prompt_tokens": 2000,
    "completion_tokens": 320,
    "total_tokens": 2320
  },
  "cost_amount": 0,
  "total_latency_ms": 3140
}
```

说明：

```text
1. 本 Trace 不是 Agent Loop Trace；
2. 当前没有 Tool Registry，因此 `tool` 字段只表示既有固定链路能力；
3. Planner / Tool Registry / Agent Loop 的完整 Trace 延后到 v0.9。
```

## 8. Metrics

使用 `Micrometer + Spring Boot Actuator` 暴露 `/actuator/prometheus`。本版不做 Grafana 大盘。

### Chat Metrics

| 指标 | 说明 |
|---|---|
| `chat_requests_total` | Chat 请求总量 |
| `chat_success_total` | Chat 成功数量 |
| `chat_failed_total` | Chat 失败数量 |
| `chat_first_token_latency_ms` | SSE 首字延迟 |
| `chat_total_latency_ms` | Chat 总耗时 |

### RAG Metrics

| 指标 | 说明 |
|---|---|
| `rag_search_total` | RAG 检索总量 |
| `rag_search_failed_total` | RAG 检索失败数量 |
| `rag_search_latency_ms` | RAG 检索耗时 |
| `rag_no_hit_total` | RAG 无命中次数 |

### LLM Metrics

| 指标 | 说明 |
|---|---|
| `llm_request_total` | LLM 请求总量 |
| `llm_success_total` | LLM 成功数量 |
| `llm_failed_total` | LLM 失败数量 |
| `llm_prompt_tokens_total` | Prompt Token 总量 |
| `llm_completion_tokens_total` | Completion Token 总量 |
| `llm_total_tokens_total` | Token 总量 |
| `llm_cost_total` | LLM 成本 |
| `llm_latency_ms` | LLM 耗时 |

### SQL Metrics

| 指标 | 说明 |
|---|---|
| `sql_generate_total` | SQL 生成总量 |
| `sql_generate_failed_total` | SQL 生成失败数量 |
| `sql_validate_total` | SQL 校验总量 |
| `sql_validate_success_total` | SQL 校验通过数量 |
| `sql_validate_rejected_total` | SQL 拒绝数量 |
| `sql_execute_total` | SQL 执行总量 |
| `sql_execute_success_total` | SQL 执行成功数量 |
| `sql_execute_failed_total` | SQL 执行失败数量 |
| `sql_timeout_total` | SQL 超时数量 |
| `sql_rows_limited_total` | SQL 返回行数被限制数量 |
| `sql_execute_latency_ms` | SQL 执行耗时 |

### Python Sidecar Metrics

| 指标 | 说明 |
|---|---|
| `python_request_total` | Python 调用总量 |
| `python_success_total` | Python 调用成功数量 |
| `python_failed_total` | Python 调用失败数量 |
| `python_request_latency_ms` | Python 调用耗时 |
| `document_parse_failed_total` | 文档解析失败数量 |
| `embedding_failed_total` | Embedding 调用失败数量 |

## 9. 日志规范

### Java

结构化字段至少包含：`timestamp`、`level`、`service`、`request_id`、`run_id`、`session_id`、`workspace_id`、`user_id`、`step_type`、`status`、`latency_ms`、`message`。

```text
level=INFO service=huiyi-backend request_id=req_123 run_id=run_456 workspace_id=1 user_id=100 step_type=sql_validate status=PASSED latency_ms=40 message="SQL guard passed"
```

### Python

至少透传并记录：`request_id`、`run_id`、`workspace_id`、`user_id`、`path`、`method`、`status`、`latency_ms`。

```text
request_id=req_123 run_id=run_456 workspace_id=1 path=/api/vector/search method=POST status=200 latency_ms=180
```

注意：不记录密码、access/refresh token、完整 Prompt 明文、完整用户敏感数据；错误堆栈可记录，但应避免敏感参数展开。

## 10. 开发任务

### 后端

| 任务 | 优先级 | 说明 |
|---|---|---|
| Request ID Filter | P0 | 解析 / 生成 / 回写 `X-Request-Id` |
| Run ID Generator | P0 | 每次 Chat / Analysis 生成 run_id |
| WorkspaceContext 扩展 | P0 | 携带 request_id / run_id |
| AssistantRunRecorder | P0 | 写入 assistant_runs |
| AssistantStepRecorder | P0 | 写入 assistant_run_steps |
| LlmCallRecorder | P0 | 记录模型、Token、耗时、状态 |
| Cost Calculator | P1 | 按 model 价格计算 cost_amount，可先默认 0 |
| Micrometer Metrics | P1 | 暴露 `/actuator/prometheus` |
| Actuator Health 增强 | P1 | 检查 MySQL / Redis / Python |
| Python Client 指标 | P1 | 记录调用耗时、成功、失败 |
| SQL Guard 指标 | P1 | 记录校验通过 / 拒绝 |
| SQL Execute 指标 | P1 | 记录执行成功 / 失败 / 超时 / 行数限制 |
| SSE 首字延迟指标 | P1 | 记录首字延迟 |

### Python

| 任务 | 优先级 | 说明 |
|---|---|---|
| Request ID Middleware | P0 | 接收、生成、回写 request_id |
| Run ID Middleware | P0 | 接收 Java 传入的 run_id |
| Structured Access Log | P1 | 记录 path / status / latency / request_id / run_id |
| Python Metrics | P1 | 暴露基础 metrics 或通过 Java 侧统计 |
| Embedding 指标 | P2 | 记录成功率、耗时 |
| Vector Search 指标 | P2 | 记录耗时、命中数 |

### 前端

| 任务 | 优先级 | 说明 |
|---|---|---|
| 保存 X-Request-Id | P1 | 请求失败时可展示 / 反馈 |
| 问题反馈携带 request_id / run_id | P2 | 便于后续排查 |
| 可选调试面板 | P2 | 展示 run_id、耗时、失败步骤 |

## 11. 配置

```env
OBSERVABILITY_ENABLED=true
METRICS_ENABLED=true
LLM_COST_ENABLED=true
LLM_COST_DEFAULT=0
TRACE_STEPS_ENABLED=true
TRACE_MAX_STEPS=20
```

说明：v0.8 不要求配置真实模型单价；`cost_amount` 允许默认 0；后续 v1.1 接入模型价格配置后即可补齐真实成本。

## 12. 验收标准

```text
[x] 每个 HTTP 请求都有 request_id；
[x] 响应头回写 X-Request-Id；
[x] 每次 Chat / Analysis 生成 run_id；
[x] run_id 贯穿 Java、Python、LLM 和审计记录；
[x] assistant_runs 记录一次完整链路；
[x] assistant_run_steps 记录关键步骤；
[x] llm_calls 记录模型、Token、耗时和状态；
[x] llm_calls 包含 cost_amount 字段（本版默认 0）；
[x] 暴露 /actuator/prometheus 或等价 Metrics；
[x] Chat 成功 / 失败 / 耗时指标可用；
[x] RAG 命中、无命中、耗时指标可用；
[x] SQL 生成、校验、执行、拒绝、失败、超时指标可用；
[x] Python 调用成功、失败、耗时指标可用；
[x] SSE 首字延迟可统计；
[x] Java / Python 日志可通过 request_id 关联；
[x] 健康检查能区分 MySQL / Redis / Python 异常；
[x] 不引入 Grafana 大盘；
[x] 不实现 Agent Loop / Tool Registry / Agent Trace；
[x] 旧五个核心场景不回归（e2e-m5 / e2e-v07）；
[ ] CI 全绿（推 main 后由 GitHub Actions 确认；本机无 gh）。
```

## 13. 明确留给 v0.9+

Tool Registry、Tool Executor、Agent Loop、Agent Trace、ReAct、SSE Agent 事件。  
v0.8 的 Trace 是 **请求级调用链**（Intent → Rewrite → RAG/SQL → LLM Summary），不是 Agent Run Trace。
