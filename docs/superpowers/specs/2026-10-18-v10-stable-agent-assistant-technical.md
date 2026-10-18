# HuiYi v1.0 · Stable Agent Assistant 技术设计文档

> 版本：`v1.0.0`  
> 状态：**规格已冻结，实现尚未开工**  
> 产品规格（桌面规划同构改写）：[2026-10-18-v10-stable-agent-assistant.md](2026-10-18-v10-stable-agent-assistant.md)  
> 基线代码：`v0.9.0`（标签 `v0.9.0`）包 `com.huiyi.assistant.agent`  
> 口径：需求评审 D01–D13，见产品规格 §18。本文只写 **怎么改代码**，不重新开范围。

本文是 v1.0 **P0 技术设计**。P1 ReAct 只在 §16 留接口，本周不开工。

---

## 1. 设计目标与差量

在 **不换 HTTP 入口、不重写 ToolRegistry** 的前提下，把 v0.9 Router Runtime 补成：

1. 工具入参可按 JSON Schema 拒绝；
2. `/chat/stream` 在现有四事件上 **实时** 叠加 `tool.*`；
3. 组装 Prompt 有硬上限与裁剪顺序；
4. 输出剥密钥/堆栈；run 墙钟 90s 与 `error_code`。

| 桌面规划说法 | 本版技术落点 |
|---|---|
| Agent Controller + `/agent/runs` | 仍是 `ChatV1Controller` / 分析 Controller + `ChatService` / `AnalysisService` |
| 新 SSE 协议 | 现有 `SseEmitter.event().name(...)` 加三个事件名 |
| 7 个高层工具 | 仍五工具；Schema 填进现有 `ToolSpec.inputSchema` |
| ContextBuilder 新区 | 新组件 `ContextBudget`，挂到改写 / RAG / Text-to-SQL / 总结 Prompt |
| Guard 新权限码 | `ToolSpec.PERMISSION_WORKSPACE_MEMBER`；鉴权仍 `requireMember` |

---

## 2. 现网基线（代码事实）

实现必须从这些类往上加，禁止平行再造一套 Runner。

| 类 | 路径 | 本版角色 |
|---|---|---|
| `AgentRunner` | `agent/AgentRunner.java` | 唯一编排；`run` / `runAsk` / `invoke` |
| `RouterPlanner` | `agent/RouterPlanner.java` | `KNOWLEDGE_QA` → `knowledge.search`；`DATA_QUERY` → 四工具链 |
| `ToolRegistry` | 内存 Bean | 五工具已注册 |
| `ToolExecutor` | 固定 8 线程池；剥 `workspaceId`/`userId`；恢复 Workspace + Observability + MDC | 本版在 `execute` **前**加 Schema 校验 |
| `ToolSpec` | `(name, description, inputSchema, timeoutMs, dangerLevel, permissions)` | `inputSchema` 今日为 `"{}"` |
| `ToolResult` | `(ok, data, error)` | 增加约定错误码 `schema_invalid`（字段不改亦可） |
| `ToolContext` | `(workspaceId, userId, question)` | 租户唯一来源 |
| `ChatService.chatStream` | 先 `agentRunner.run`，再 `new SseEmitter`，再推 `session/token/references` | **必须改顺序**，否则 `tool.*` 无法实时 |
| `ChatService.SSE_TIMEOUT_MS` | 90s | 与 run 墙钟对齐 |
| `QueryRewriteService` | 近 10 轮（约 20 条）+ `chat_session_summaries` | Budget 的 History 来源 |
| `AssistantRun` | 无 `errorCode` | Flyway V11 + 实体字段 |

五工具超时（保持）：search 15s、generate 30s、validate 10s、execute 15s、profile 10s。  
SQL 链路加总 55s \< 90s，正常查数不会先被 run 墙打死。

当前 `ChatService.chatStream` 伪流程（**有问题，P0 要改**）：

```text
saveUserMessage
  → AgentRunner.run（工具已全部跑完，无 SSE）
  → new SseEmitter(90s)
  → event:session
  → event:token*
  → event:references
```

---

## 3. 目标调用链

### 3.1 聊天 SSE（P0 正确顺序）

```text
鉴权 requireMember（含 VIEWER）
  → RequestTraceService.start("chat_stream", ...)
  → saveUserMessage
  → new SseEmitter(90s)
  → event:session
  → AgentRunner.run(request, listener, deadline)
        rewrite / intent
        每个 invoke：
          listener.onStarted(tool)
          Schema / 执行 / 超时
          listener.onCompleted | onFailed
          写 assistant_run_steps
        若 AGENT_LIMIT_EXCEEDED → 不进 token 流
  → （成功）event:token* → event:references → complete
  → （失败）event:error → complete
  → finish(status, intent, errorMessage) + error_code
```

`listener` 只给 **stream 入口**。`chat/ask`、`analysis/query` 传 `noop`。

### 3.2 分析 JSON

```text
鉴权 requireMember
  → start("analysis_query", ...)
  → AgentRunner.run(...)   // 无 SSE listener
  → 返回现有 Analysis JSON（无 toolCalls）
  → steps 已在库
```

### 3.3 单次工具 `invoke`

```text
strip tenant keys（已有）
  → JSON Schema validate（新增）
      失败：steps REJECTED，error=schema_invalid，用户句未知工具同款
  → registry.require
      未知：UnknownToolException → steps FAILED，用户句未知工具
  → 线程池 + 单工具 timeout（已有）
  → tool.execute(ToolContext from WorkspaceContextHolder)
  → sql.validate 且 valid=false：步骤 REJECTED（已有）
```

---

## 4. 包与类变更（建议落点）

不新建 `agent.controller`、不新建 `AgentRunsController`。

| 新增 / 改 | 职责 |
|---|---|
| `agent/ToolSchemas.java`（或各 Tool 内联） | 五个 JSON Schema 字符串常量 |
| `agent/ToolSchemaValidator.java` | 解析 `inputSchema`，校验 `Map` 入参 |
| `agent/ToolEventListener.java` | `onStarted/onCompleted/onFailed(String tool, String status)` |
| `agent/AgentDeadline.java` | run 起始 nano + `90_000`；超则抛/返回 limit |
| `agent/ContextBudget.java` | 分区估算、裁剪顺序、超限信号 |
| `agent/OutputGuard.java` | 剥离密钥形态与堆栈后再给用户 |
| `ToolExecutor.execute` | validate → 现有超时执行 |
| `AgentRunner.invoke` | 调 listener；检查 deadline；Schema 映射用户句 |
| `ChatService.chatStream` | 先 emitter 后 run |
| `AssistantRun.errorCode` + Mapper/Recorder | V11 |
| `RequestTraceService.finish` | 增加 `errorCode` 重载，旧调用传 null |
| `application.yml` | `huiyi.agent.run-timeout-ms: 90000`、`huiyi.agent.prompt-max-chars` |

前端 `frontend/src/api/chatStream.ts`：**P0 可不改**（未知 `event` 已忽略）。可选后续加 `onTool*`，非发版门闩。

JSON Schema 实现库由实施计划选定（须能校验 draft-07 或 2020-12 的 `type/object/required/properties/additionalProperties`）。验收是行为，不是库名。

---

## 5. 接口与类型

### 5.1 `ToolEventListener`

```java
public interface ToolEventListener {
    ToolEventListener NOOP = new ToolEventListener() {};

    default void onStarted(String toolName) {}
    default void onCompleted(String toolName, String status) {}
    default void onFailed(String toolName, String status) {}
}
```

`ChatService` 实现：把回调转到 `sendEvent`。`status` 仅 `SUCCESS` / `FAILED` / `REJECTED`。

### 5.2 `AgentRunner.run` 签名

保持现有 `run(AgentRequest)` 委托到带 listener 与 deadline 的重载，避免测试全碎：

```java
public AgentResult run(AgentRequest request) {
    return run(request, ToolEventListener.NOOP, AgentDeadline.defaultFromNow());
}

public AgentResult run(AgentRequest request, ToolEventListener listener, AgentDeadline deadline)
```

`AgentRequest` P0 **不加** `mode`（D04/D10）。

### 5.3 Schema 失败错误码

`ToolExecutor` / `ToolResult.fail` 使用内部码：

| `ToolResult.error` | 步骤 status | 用户句 | `assistant_runs.error_code` |
|---|---|---|---|
| `schema_invalid` | `REJECTED` | `工具调用失败，请稍后重试。` | 空（不要 `AGENT_LIMIT_EXCEEDED`） |
| `timeout` | `FAILED` | 沿用 v0.9 各场景中文 | 空 |
| `UnknownToolException` | `FAILED` | 同上未知工具句 | 空 |
| run/budget 墙 | run `FAILED` | `当前问题较复杂，请缩小范围后重试。` | `AGENT_LIMIT_EXCEEDED` |

禁止把 Schema 失败写成 `AGENT_LIMIT_EXCEEDED`。禁止套 `RagService` 未命中保底。

---

## 6. 五工具 JSON Schema（P0 必须可校验）

原则：

- 租户只在 `ToolContext`，Schema **不得**出现 `workspaceId` / `userId`。
- `additionalProperties: false`。
- Router 今日传参已固定：search/generate 用空 Map（问题在 `ToolContext.question`）；validate/execute 用 `sql`；profile 用 `rows`。Schema 与此对齐，避免 Router 自己把自己 REJECTED。
- 工具内部仍以 `ToolContext.question` 为准，即使 Schema 允许可选 `question` 也不得覆盖租户。

### 6.1 `knowledge.search`

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "query": { "type": "string", "description": "可选；缺省用当前问题" }
  }
}
```

`execute` 仍 `ragService.prepare(context.question(), context.workspaceId())`。若传入 `query`，**忽略**（防 Prompt 改检索绕过当前问题）或仅当与 question 相同。推荐 **忽略**，保持 v0.9 语义。

Result `data`：`fallbackAnswer`、`prompt`、`references`（已有）。落库摘要：命中条数，不落 `prompt` 全文。

### 6.2 `sql.generate`

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {}
}
```

空对象合法。`execute` 用 `context.question()`。Result：`sql`（string|null）。

### 6.3 `sql.validate`

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["sql"],
  "properties": {
    "sql": { "type": "string", "minLength": 1 }
  }
}
```

缺 `sql` → `schema_invalid`（今日是 `missing sql` 的 `fail`，P0 改为校验阶段 REJECTED，用户句统一未知工具句）。  
`valid=false` 仍是 Result.ok + `valid:false`，步骤 REJECTED，用户句走现网拒绝句（白名单/修改），**不是**「工具调用失败」。

### 6.4 `sql.execute_readonly`

与 validate 相同：`required: ["sql"]`。超时内部码仍 `timeout`，用户句 `SQL 执行失败，请调整问题后重试`（v0.9）。

### 6.5 `data.profile`

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "rows": { "type": "array", "items": { "type": "object" } }
  }
}
```

缺 `rows` 视为 `[]`（与今日一致），不要 REJECTED。Result 含 `chartType`，禁止第二次 Python、禁止注册 `chart.recommend`。

### 6.6 校验算法

```text
schema = parse(tool.spec().inputSchema())
args' = stripTenant(args)
if !validator.validate(schema, args'):
    return fail("schema_invalid")   // 不调用 tool.execute
else:
    现有超时执行
```

非法 JSON Schema 字符串（启动期）：工具 Bean 初始化失败或单测失败，**不要**运行时静默当 `"{}"`。

---

## 7. SSE 协议（字节级）

路径、鉴权、Content-Type 不变。事件名大小写与下表 **完全一致**（Spring `name` 即 `event:` 行）。

### 7.1 保留

| event | data | 何时 |
|---|---|---|
| `session` | 会话 ID 纯文本 | **第一个**事件，在 run 之前 |
| `token` | 增量文本 | LLM 流或整段 answer 切片 |
| `references` | JSON 数组 | 正常结束前 |
| `error` | 中文纯文本 | 失败；其后 complete |

### 7.2 新增

`data` 必须是 **单行 JSON 对象**（不要拆成多行 data）。禁止 SQL 全文、密钥、堆栈、Prompt。

```text
event: tool.started
data: {"tool":"sql.generate"}

event: tool.completed
data: {"tool":"sql.generate","status":"SUCCESS"}

event: tool.failed
data: {"tool":"sql.validate","status":"REJECTED"}
```

| 字段 | 类型 | 必填 |
|---|---|---|
| `tool` | 工具名字符串 | 是 |
| `status` | `SUCCESS` / `FAILED` / `REJECTED` | completed/failed 是；started 不要 |

未知工具：`tool` 用请求名，`status=FAILED`。  
Schema：`status=REJECTED`。  
`valid=false`：`tool.failed` + `REJECTED`（不是 completed）。

### 7.3 成功顺序示例（数据查询）

```text
event: session
event: tool.started     sql.generate
event: tool.completed   SUCCESS
event: tool.started     sql.validate
event: tool.completed   SUCCESS
event: tool.started     sql.execute_readonly
event: tool.completed   SUCCESS
event: tool.started     data.profile
event: tool.completed   SUCCESS
event: token            ...
event: references       []
```

知识问答：`knowledge.search` 的 started/completed 出现在 **第一颗 token 之前**。

### 7.4 明确不发

`run.started`、`planner.decided`、`answer.token`、`answer.completed`、`run.completed`、`run.failed`。  
run 失败只用现网 `error`。

### 7.5 实现注意

- `sendEvent` 已吞 `IOException`（客户端断开）。listener 不得因发送失败中断工具执行。
- ASYNC dispatcher `permitAll`（v0.7 坑）保持。
- 禁止把 `token` 改名为 `answer.token`。

---

## 8. Context Budget

### 8.1 配置

```yaml
huiyi:
  agent:
    run-timeout-ms: 90000
    prompt-max-chars: 24000   # 近似上限，不当 8000 token 验收
```

`24000` 字符是 **默认近似**（约 6k–8k token 量级），可按模型窗口改配置。验收：超限走裁剪顺序；仍超则 `AGENT_LIMIT_EXCEEDED`。不做 tokenizer，不当桌面百分比。

### 8.2 分区与估算

对即将送入 LLM 的字符串按块估算 `length()`（字符）：

| 块 | 来源 |
|---|---|
| System Rule | 各 `prompts/*.md` 模板固定段 |
| Business Policy | 口径/glossary 注入 |
| Database Schema | `SchemaService` 白名单文本 |
| Retrieved Knowledge | RAG 片段 |
| Working Memory | 本 run 工具短摘要（P0 可为空） |
| Conversation History | 改写用的摘要 + 近 10 轮 |
| Current Question | 当前问题 |
| Output Contract | 模板里的输出格式段 |

`total = sum(块)`。若 `total <= prompt-max-chars` 直接用。

### 8.3 裁剪顺序（D08，覆盖桌面「先裁历史」）

```text
while total > max:
  1. 丢掉 Retrieved Knowledge 中分数最低的片段（无分则从末尾）
  2. 压缩 / 截断 Database Schema（保留表名清单优先于列细节）
  3. 丢掉近 10 轮窗口外已摘要所对应的残余；近 10 轮从最旧一轮开始丢
  4. 缩短 Working Memory
  永不裁：System 安全规则、当前问题
  若只剩不可裁块仍超限 → AGENT_LIMIT_EXCEEDED
```

摘要策略 **继续** `QueryRewriteService`：近 10 轮原文、更早进 `chat_session_summaries`、失败降级不打断。不新建记忆表。

### 8.4 挂载点

| Prompt | 调用方 | 挂 Budget |
|---|---|---|
| 改写 | `QueryRewriteService.rewrite` | History + Question |
| RAG | `RagService.prepare` 拼装的 `prompt` | Knowledge + System |
| Text-to-SQL | `TextToSqlService.generateSql` | Schema + Policy + Question |
| SQL 总结 | `AgentRunner.summarize` | rows 已 `limit 20`；再截 profile JSON |

Budget 超限发生在 **调用 LLM 之前**。已发出的 `tool.*` 可保留；不要再开 token 流。用户句与 run 超时相同。

---

## 9. Guard 流水线

顺序：Input →（Deadline）→ Tool（Schema/未知/未注册危险）→ 执行 → Output。

### 9.1 Input

- HTTP：未登录 401；工作区成员校验（现网）。
- `workspaceId` 路径与 `WorkspaceContextHolder` 必须一致（现网）。
- 修改动词拦截：`AgentRunner.containsModificationIntent` 保持，步骤 `sql_validate REJECTED`，用户句 `该请求涉及数据修改操作...`。
- 不把用户原文拼进可覆盖 System 的位置。

### 9.2 Tool

见 §3.3。VIEWER 可调五只读工具。不引入 `sales.read`。危险名根本不注册。

### 9.3 Output（`OutputGuard`）

在写入 `event:token` / `event:error` / analysis `answer` / `saveAssistantMessage` 之前过一遍：

拦截或替换（命中则改为现网通用失败句，或删除该片段）：

```text
- 堆栈：堆栈帧、"at com.", "Exception in thread", Python Traceback
- 密钥形态：sk- 前缀、JWT 三段式超长、password=、API_KEY=
- JDBC / Python 原始异常 toString
```

**不做**「是否编造」分类器。  
RAG 无命中仍用现网保底；与未知工具话术不得混用。

---

## 10. 超时与 deadline

```text
runDeadline = startNano + run-timeout-ms(默认 90000)
每次 invoke 前：if now > deadline → 停止后续工具，run FAILED
单工具：仍 future.get(spec.timeoutMs)
SSE emitter：90s（已有），与 run 对齐，避免一边死一边还在跑
JDBC queryTimeout 5s：不改
```

Run 墙与 Budget 墙共用用户句和 `error_code=AGENT_LIMIT_EXCEEDED`。  
`error_message` 可写内部 `run_timeout` / `prompt_budget`（截断 1000 字，现有 trim），用户不可见。

---

## 11. 数据模型

### 11.1 Flyway `V11__assistant_runs_error_code.sql`

```sql
ALTER TABLE assistant_runs
  ADD COLUMN error_code VARCHAR(64) NULL COMMENT '如 AGENT_LIMIT_EXCEEDED；无则空' AFTER status;
```

- 不加 `mode` / `agent_status` / `tool_call_count`（P1 再议 `mode`）。
- steps **不加** JSON 列；继续 `input_summary` / `output_summary` TEXT。
- 无 FK、无 cascade、不新建 `agent_runs`。

### 11.2 实体与 Recorder

`AssistantRun.errorCode`；`AssistantRunRecorder.finish(..., errorCode)`；MyBatis 字段映射。  
`step` 摘要规则：工具名 + 成败；SQL 最多记「已生成/已拒绝」，**不**把完整 SQL 当用户可见 SSE。库内摘要若已有短 SQL，沿用 v0.8 截断，本版不扩大。

### 11.3 查询

排障：`assistant_runs` 按 `run_id` 或 `request_id`；steps 按 `run_id`。应用层两次查询，单条 SQL 不 join 超过 3 表。

---

## 12. 可观测

保持 v0.8 指标名（`chat_*`、`rag_*`、`sql_*`、`llm_*`）。新增时 **不要** 自带 `_total`：

| 建议名 | 标签 | 何时 |
|---|---|---|
| `huiyi_agent_limit_exceeded` | `reason=timeout\|budget` | run/budget 墙 |
| `huiyi_tool_schema_rejected` | `tool` | `schema_invalid` |
| `huiyi_tool_call` | `tool`,`status` | 每次 invoke 结束 |

`request_id` / `run_id` / `llm_calls` / `/actuator/prometheus` 不得替换。

---

## 13. 用户可见失败映射

| 内部原因 | SSE `error` / analysis.answer | HTTP |
|---|---|---|
| 未知工具 / schema_invalid | `工具调用失败，请稍后重试。` | 200 + 事件或 JSON 业务失败（现网形态） |
| 修改意图 / 校验拒绝 | 现网安全拒绝句 | 同上 |
| generate 空 SQL | `未能理解您的问题...` | 同上 |
| 工具 timeout | v0.9 已有句 | 同上 |
| run 90s / Prompt 预算 | `当前问题较复杂，请缩小范围后重试。` | 同上 |
| 未登录 | （进不了 Runner） | 401 |
| 跨工作区 | 现网拒绝 | 403/业务错误（现网） |
| OutputGuard 命中 | 通用失败或剥离后文本 | 200 |

---

## 14. 测试矩阵（P0 金标）

现有套件（约 Java 130 / Python 115 / e2e-m5）必须保持绿。新增至少：

| 测试 | 断言 |
|---|---|
| `ToolSchemaValidatorTest` | 合法空对象通过；多 `workspaceId` 失败；validate 缺 sql 失败 |
| `ToolExecutorTest` | `schema_invalid` **不**调用 `AgentTool.execute` |
| `AgentRunner` Schema | 用户句精确等于 `UNKNOWN_TOOL_USER_MESSAGE`；步骤 REJECTED |
| `ChatService` SSE | 捕获 `SseEmitter`：先 `session`，再至少一次 `tool.started`，再 `token`；无 `answer.token` |
| `analysis` | JSON 无 `toolCalls` 字段 |
| `ContextBudgetTest` | 超限先丢掉 knowledge 再 schema；不可裁尽则发出超限 |
| `OutputGuardTest` | 含 `Exception in thread` / `sk-` 的文本不得出现在输出 |
| `AssistantRunRecorder` | finish 写入 `error_code=AGENT_LIMIT_EXCEEDED` |
| `FlywayMigrationFilesTest` | V11 存在且无 FK |
| e2e-m5 + 企业隔离 | 仍过 |

不做评测页。ReAct 最大步数不做 P0 测。

---

## 15. 发布与回滚

分支：`feature/v1.0-agent-stable` → `main` → tag `v1.0.0`。

回滚：

```text
1. 应用回退到 v0.9.0 镜像 / 标签；
2. error_code 列可留（NULL 兼容），不必 drop；
3. 前端未改协议则无需回退前端；
4. 文档声明回滚点 v0.9.0。
```

发版文档：`README`、`roadmap`、`docs/api.md`（补 `tool.*` 事件）、`development.md`、`release-checklist.md`。  
GitHub Release 页仍需浏览器创建（无 `gh` 则不强依赖）。

---

## 16. P1 ReAct（只设计，不开工）

```text
- 新类 ReActPlanner，不得改 RouterPlanner 五场景默认
- AgentRequest.mode 仅显式传入；HTTP 五场景不传则 router
- Flyway 再加 assistant_runs.mode
- max_steps 等数字开工前再拍板
- 禁止 P0 PR 预埋 mode=auto
```

公开 `/agent/runs*`、桌面 7 工具、评测页：不在 P1 必做清单，见产品规格 §15。

---

## 17. 非目标（实现禁区）

```text
POST /api/v1/workspaces/{id}/agent/runs 及其 stream/get/trace
把 token 改名为 answer.token
注册 sales.query / chart.recommend / time.now
knowledge.read 等新码
steps 上 JSON 列、agent_runs 表、MySQL FK
评测页
判断编造
重写 ToolRegistry / 第 2 周重做 Harness
```

---

## 18. 与产品规格的关系

| 产品规格章 | 本文 |
|---|---|
| §6–7 入口/SSE | §3、§7 |
| §4 工具 Schema | §6 |
| §5.5 / §8 超时 | §10 |
| §6 Context | §8 |
| §8 Guard | §9 |
| §11 库表 | §11 |
| §10 / §15 验收 | §14 |
| §18 决策 | 全部约束来源 |

冲突时以 **产品规格已拍板表 + 本文算法** 为准，不以桌面原文 20s / 8000 / `/agent/runs` 为准。
