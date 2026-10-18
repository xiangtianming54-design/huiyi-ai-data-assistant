# HuiYi v1.0 · Stable Agent Assistant 迭代开发文档（仓库规格）

> 版本：`v1.0.0`  
> 状态：**规格已冻结，实施计划已写，P0 实现落在 `feature/v1.0-agent-stable`（尚未发版）**（P0 D01–D13 已拍板；P1 ReAct 只写入规格，本计划不含）  
> 基线：`huiyi-ai-data-assistant v0.9.0`（标签 `v0.9.0`）  
> 上版本状态：已交付内存 ToolRegistry、RouterPlanner、AgentRunner、5 个只读工具、Flyway V10 `tool_name`。  
> 对照原文：桌面《HuiYi-v1.0-Stable-Agent-Assistant-Development-Plan》（下称「桌面规划」）。  
> 其它来源：[roadmap.md](../../roadmap.md)；v0.9 规格；需求评审 D01–D13。  
> 详细技术设计（调用链、Schema、SSE 字节、类落点）：[2026-10-18-v10-stable-agent-assistant-technical.md](2026-10-18-v10-stable-agent-assistant-technical.md)。  
>
> 本文按桌面规划 **1–17 章同构改写**：保留分层、工具 Spec、Runtime、Budget、Guard、Metrics、测试、发布、验收等章节；凡与现网或已拍板冲突的，在该节写清「桌面原文 → 仓库适配」，不另起一套目录。

**改写原则（已拍板，不是建议）：** 方向跟桌面走，范围按 P0 落地。公开 Agent API、新 SSE 协议、新工具名、细粒度权限、评测页、20s 总超时 **不按原文一次做完**。

---

## 0. 桌面章节对照

| 桌面 | 仓库适配要点 |
|---|---|
| §1 定位 | 同一句话；补齐项落到现网入口，不新开 Agent Controller |
| §2 架构 | 图保留；`Agent Controller` = 现有 Chat/Analysis 门面 |
| §3 核心范围 | 「必须完成」映射到现网 API + 五工具 Schema + 叠加 SSE |
| §4 工具 | 对外仍五工具；`sales.*` / `chart.recommend` / `time.now` 标后期 |
| §5 Runtime | 扩展现有 Runner；ReAct 进 P1 |
| §6 Context | 分区表保留；8000 与百分比当示例不当验收 |
| §7 API | 默认仍 `chat/stream`、`analysis/query`；桌面四端点附录后期 |
| §8 Guard | 三面保留；编造判定、新权限码不做 |
| §9 Metrics | 名称避开 Micrometer `_total` 后缀；优先接 v0.8 已有指标 |
| §10 测试 | 五场景 e2e + 金标；Agent 多步/最大步数随 P1 |
| §11 库表 | 只加 `error_code` |
| §12 安全 | 权限映射现有 `workspace_member` |
| §13 发布 | 2–3 周，不按桌面第 2 周重做 Harness |
| §14 风险 | 保留，对策改成现网数字 |
| §15 验收 | 按 P0 勾选，桌面原文超额项改「后期」 |
| §16–17 简历/一页纸 | 按适配后的交付写，不把未做能力写成已做 |

---

## 1. 版本定位

### 1.1 一句话定位

桌面原文：

```text
HuiYi v1.0 是一个可稳定试用的企业智能数据助手：
它基于 Tool Harness 和最小 Agent Runtime，支持 RAG、Text-to-SQL、数据画像、图表推荐和多步工具调用，
并通过工作区隔离、权限控制、审计、Trace 和基础评测保证安全性与可维护性。
```

仓库适配：图表推荐仍合在 `data.profile` 的 `chart_type`（不单独工具）；多步工具调用 = P1 ReAct，五场景默认仍是 Router 固定步骤。「基础评测」= 现有 e2e + 少量后端金标，不是评测页。

### 1.2 与 v0.9 的关系

v0.9 已完成（与桌面 1.2 一致，不重做）：

```text
1. ToolRegistry；
2. AgentTool 接口；
3. ToolExecutor；
4. RouterPlanner；
5. 5 个只读工具；
6. assistant_run_steps.tool_name；
7. Chat / Analysis 内部委托 AgentRunner；
8. 工作区上下文在工具线程中恢复。
```

桌面写「v1.0 要补齐」九项，仓库映射：

| 桌面补齐项 | P0 | 说明 |
|---|---|---|
| 公开的 Agent API | 不上 | 现网两入口已委托同一 Runner（D02） |
| SSE Agent 事件 | 叠加 | 现有四事件保留，加 `tool.*`（D07） |
| ReAct Planner | P1 | 不进五场景默认，本周不开工（D04） |
| 结构化 Tool Schema | 做 | 五工具 JSON Schema + 运行时校验（D03、D12） |
| Tool Result 规范 | 做 | 短摘要落 TEXT，不落 JSON 大对象（D10） |
| Context Budget | 做 | 近 10 轮 + 摘要 + Prompt 硬上限（D08） |
| Guardrails | 做 | 现有角色；不做编造判定（D06） |
| 基础评测 | 收缩 | 无评测页；e2e + 金标（D09） |
| 稳定发布和回滚验证 | 做 | 发版清单按现网 tag 流程 |

### 1.3 不进入 v1.0 的内容

桌面 1.3 全部保留，并 **追加**（因 D01–D13）：

```text
1. 多 Agent；
2. MCP；
3. 插件市场；
4. 复杂评测平台；
5. Grafana 大盘；
6. Kubernetes；
7. 多模型路由；
8. Prompt 灰度平台；
9. 生产多环境；
10. 知识空间细粒度权限；
11. POST /agent/runs 四端点（桌面 §7，后期对齐）；
12. 新工具名 sales.query / sales.analyze / order.status / chart.recommend / time.now；
13. knowledge.read / sales.read 等新权限码；
14. 评测页、简单评测报告页面；
15. 独立表 tool_invocations / tool_registry；
16. 把 token 改名为 answer.token；
17. 新记忆表、反馈产品化；
18. v0.7 P1 UI、v0.8 P2 调试面板。
```

---

## 2. 目标架构

桌面原文图保留，**Agent Controller 适配为现有门面**，不新建一套：

```text
Vue3 Frontend
      ↓
Spring Boot API v1
      ↓
ChatV1Controller / AnalysisV1Controller     ← 桌面「Agent Controller」的现网落点
      ↓
ChatService / AnalysisService（门面）
      ↓
AgentRunner
      ↓
┌───────────────────────────────┐
│ Context Builder               │
│ Planner / Router              │  P0 = Router；ReAct 见 §5.4 P1
│ Tool Registry                 │  已有，扩展 Schema，不重写
│ Tool Executor                 │  已有，补校验与 run 总超时
│ Guardrails                    │  见 §8
│ Trace / Metrics / Audit       │  接 v0.8，加 error_code
└───────────────────────────────┘
      ↓
Builtin Tools（v0.9 五工具）
      ↓
RagService / TextToSqlService / Python Sidecar / MySQL
```

### 2.1 分层职责

与桌面 2.1 相同，补充现网边界：

| 层 | 负责 | 不负责 |
|---|---|---|
| Controller | 请求校验、认证上下文、SSE 输出 | Prompt 拼装、工具执行；不新建 `/agent/runs` |
| AgentRunner | Agent Loop、run 总超时、错误兜底 | 具体业务逻辑；P0 不做 ReAct 循环 |
| Planner / Router | 决定执行模式、选择工具 | 直接执行业务；P0 无 `auto` |
| ToolRegistry | 工具注册、发现 | 权限判断；本版无启用/禁用管理 API |
| ToolExecutor | Schema、超时、执行、Trace | Prompt 生成；租户 ID 不从入参读 |
| Tools | 执行具体业务能力 | 决定下一个工具 |
| ContextBuilder | 组装 Prompt、预算裁剪 | 执行 SQL |
| Guardrails | 输入 / 工具 / 输出安全 | 业务正确性、「是否编造」 |

---

## 3. v1.0 核心范围

### 3.1 必须完成（P0，对照桌面 3.1）

桌面 3.1 原文是 Agent 四端点 + ReAct + 全套 SSE 新名字 + 评测四类。仓库改写：

```text
1. Agent API（桌面四端点不上）：
   - POST /api/v1/workspaces/{workspaceId}/chat/stream；
   - POST /api/v1/workspaces/{workspaceId}/analysis/query；
   - 两入口继续委托同一 AgentRunner。

2. Agent Runtime（扩展，不重写）：
   - AgentRunner / AgentState / AgentStep / RouterPlanner / AgentRunResult；
   - ReActPlanner 见 §5.4，P1，本周不开工。

3. Tool Harness（在 v0.9 上补齐，不重做 Registry）：
   - 五工具结构化 ToolSpec / JSON Schema；
   - ToolRegistry / ToolExecutor 已有；
   - 权限 = workspace_member（含 VIEWER）；
   - 工具超时沿用 v0.9；
   - unknown tool 闸门已有，Schema 失败走同一用户句。

4. Context Harness：
   - System Rule / Business Glossary / Schema / Retrieved Knowledge /
     Conversation History / Current Question / Output Contract；
   - Token Budget = 组装 Prompt 硬上限（数字按模型窗口配置）。

5. SSE Agent Events（叠在现有流，不新协议）：
   - 保留：session、token、references、error；
   - 叠加：tool.started、tool.completed、tool.failed。

6. 基础评测（收缩）：
   - 旧五场景 e2e；
   - 未登录 401 / 跨工作区；
   - 后端金标：步骤 SSE、Schema REJECTED、预算超限、输出无堆栈。
```

### 3.2 可选但不阻塞 v1.0

对照桌面 3.2：

```text
1. Plan-and-Execute — 仍不阻塞，本版不做；
2. 前端步骤时间线 — 可选（D07），现网忽略未知 SSE 即可验收；
3. LLM 成本估算 — v0.8 已有字段，本版不新做账单；
4. 简单评测报告页面 — 不做（D09）；
5. Prompt 版本字段 — 不阻塞，本版不加列；
6. 用户反馈接口 — 不阻塞，本版不做。
```

另：公开 `/agent/runs`、细粒度权限、独立图表工具 — 桌面写在「必须」，仓库改为 **后期**，不得挡 P0 发版。

---

## 4. 工具设计

### 4.1 LLM 可见工具

桌面原文要换成高层业务工具、不暴露底层 SQL 步骤。  
**已拍板 D03=B：对外仍是 v0.9 五工具。** 桌面 4.1 表作为后期目标，本版对照如下。

| 本版对外工具 | 对应桌面意图 | 超时（沿用 v0.9） |
|---|---|---|
| `knowledge.search` | 同桌面 | 15s |
| `sql.generate` | 桌面 `sales.query` 的生成段 | 30s |
| `sql.validate` | 桌面内部 `sql.validate` | 10s |
| `sql.execute_readonly` | 桌面内部 `sql.execute_readonly` | 15s |
| `data.profile` | 桌面 `data.profile` + `chart.recommend`（一次 Python，含 `chart_type`） | 10s |

本版 **不注册**：`sales.query`、`sales.analyze`、`order.status`、`chart.recommend`、`time.now`。点到这些名字 = 未知工具。

后期若做高层封装：内部调用现有 `sql.*`，超时按链路加总（≥55s），不得用桌面 5s。

### 4.2 底层内部能力

桌面要把 `sql.*` 藏进 `sales.query`。本版 **继续对外暴露** `sql.generate` / `sql.validate` / `sql.execute_readonly`，Router 按意图固定顺序调用，避免跳过校验（这点与桌面目的一致，实现不换名字）。

固定步骤仍不是 Tool：意图、改写、分解、去重、SQL 总结。

### 4.3 工具注册

继续 Spring Bean 内存注册（v0.9），不改成桌面示例里的新类名：

```java
toolRegistry.register(new KnowledgeSearchTool(...));
toolRegistry.register(new SqlGenerateTool(...));
toolRegistry.register(new SqlValidateTool(...));
toolRegistry.register(new SqlExecuteReadonlyTool(...));
toolRegistry.register(new DataProfileTool(...));
```

无管理 API、无落库 `tool_registry`、P0 无启用/禁用开关。

### 4.4 ToolSpec 结构

桌面 JSON 示例保留形状，字段对齐现有 Java `ToolSpec`（`inputSchema` 字符串、`timeoutMs`、`dangerLevel`、`permissions`）。权限码不用 `sales.read`。

```json
{
  "name": "sql.generate",
  "description": "根据当前问题生成只读 SELECT SQL，不执行。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "question": { "type": "string", "description": "用户问题或改写后的查询" }
    },
    "required": ["question"]
  },
  "permissions": ["workspace_member"],
  "dangerLevel": "read",
  "timeoutMs": 30000
}
```

五个工具都要有非空 JSON Schema。`output` 以 `ToolResult` 短摘要为准，不强制落 `output_schema` 到数据库。

入参不合 Schema：该步 `REJECTED`，不执行工具，用户句 `工具调用失败，请稍后重试。` 整次 run 走现网失败兜底，**不用** `AGENT_LIMIT_EXCEEDED`（D12）。

### 4.5 禁止暴露的工具

与桌面 4.5 相同，本版根本不注册：

```text
sql.execute_write
file.delete
system.exec
user.disable
payment.refund
data.drop
```

---

## 5. Agent Runtime 设计

### 5.1 AgentState

在 v0.9 已有状态上对齐桌面字段；P0 **不持久化** `mode`（列留给 P1）。`workspaceId` / `userId` 只从登录上下文来。

```java
public class AgentState {
    private String runId;
    private String sessionId;
    private Long workspaceId;
    private Long userId;
    private String question;
    private String mode;          // P0 恒为 router；列与切换见 P1
    private List<AgentStep> steps;
    private Map<String, Object> workingMemory;
    private String status;
    private String error;
    private String errorCode;     // 如 AGENT_LIMIT_EXCEEDED
}
```

### 5.2 AgentStep

对齐桌面，落库仍用现有 `assistant_run_steps` TEXT 摘要，不把 `input`/`output` 整段 JSON 入库（D10）。

```java
public class AgentStep {
    private int stepNo;
    private String type;
    private String tool;          // 对应 tool_name
    private String status;        // SUCCESS / FAILED / REJECTED
    private String error;
    private long latencyMs;
}
```

SSE `tool.*` 的 `data` 只含 `tool`、可选 `status`，禁止完整 SQL / 密钥 / 堆栈 / Prompt。

### 5.3 RouterPlanner

适合（对照桌面，工具名改回现网）：

```text
口径 / FAQ（knowledge.search）；
销售排名与渠道分析（sql.* 固定链）；
数据画像（data.profile）；
低延迟、可回归的五场景。
```

流程与桌面 5.3 相同：意图 → 固定工具链 → 总结。五场景 **必须** 走这条，不得因 `mode=auto` 滑到 ReAct。

### 5.4 ReActPlanner（P1，本周不开工）

桌面 5.4 流程（Thought → Action → Observation）原样保留为规格。开工前还要再拍：`max_steps` 等数字（不把桌面 8/10/3 当作已通过）。

约束（已拍板 D04=C）：

```text
1. 五场景默认仍 router；
2. 仅显式 mode 或非五场景路径可走 react；
3. 届时 assistant_runs 加 mode；
4. P0 不得预埋默认 auto。
```

### 5.5 最大限制

桌面原文 `max_latency_ms=20000`、`max_total_tokens=8000` 会误杀现网 SQL。仓库改写：

| 项 | 桌面原文 | 本版 P0 |
|---|---|---|
| 单工具超时 | 如 sales.query 5s | 见 §4.1，不收紧 |
| SQL 链路 | （未单列） | 30+10+15 = 55s |
| `max_latency_ms`（run 墙钟） | 20000 | **90000**（≥ 链路加总，对齐现网 SSE 90s） |
| `max_total_tokens` | 8000 | 按模型窗口配置，不写死 |
| `max_steps` / `max_tool_calls` / `max_same_tool_calls` | 8 / 10 / 3 | **P1**，数字开工前再拍 |

超过 run 总上限或 Prompt 硬上限仍装不下：

```text
status = FAILED
error_code = AGENT_LIMIT_EXCEEDED
用户提示 = 当前问题较复杂，请缩小范围后重试。
```

单工具 `timeout` 仍走 v0.9 已有中文映射。JDBC 查询超时 5s 不在本版改。

---

## 6. Context Budget 设计

### 6.1 上下文分区

与桌面 6.1 相同，作为组装结构，不是验收百分比：

| 区块 | 用途 |
|---|---|
| System Rule | 助手身份、输出规则、安全规则 |
| Business Policy | 业务口径 |
| Database Schema | Text-to-SQL 可用表结构 |
| Retrieved Knowledge | RAG 检索片段 |
| Working Memory | 当前 Run 的中间结果 |
| Conversation History | 历史对话（近 10 轮 + 摘要） |
| Current Question | 当前问题 |
| Output Contract | 输出格式要求 |

### 6.2 Token 预算示例

桌面 6.2 的 `total=8000` 与分区百分比 **仅作示例，不当验收项**（D08）。硬上限写入配置/常量，按当前聊天模型上下文窗口留安全余量。不做精确 tokenizer，允许按字符或估算 token。

### 6.3 裁剪顺序

桌面原文先裁历史。已拍板：超限 **先裁知识/库表**。本版顺序：

```text
1. 先裁剪 Retrieved Knowledge（低分优先）；
2. 再裁剪 Database Schema；
3. 再压缩更早历史（近 10 轮窗口外已是摘要）；
4. 再压缩 Working Memory；
5. System Rule、安全规则、当前问题、Output Contract 不裁。
```

仍无法装下 → §5.5 `AGENT_LIMIT_EXCEEDED`。

### 6.4 摘要策略

与桌面 6.4 对齐，并 **复用 v0.6**，不新建记忆表：

```text
1. 保留最近 10 轮原文；
2. 更早内容压缩为摘要并持久化（chat_session_summaries）；
3. 保留实体、口径、时间、区域、渠道、指标；
4. 摘要失败降级：只用近 10 轮，不打断主问答。
```

---

## 7. API 设计

桌面 §7 四个 `/agent/runs*` 端点 **P0 不上**（D02）。默认入口与协议如下。后期若上桌面 7.1–7.4，必须挂同一 `AgentRunner`，且 **不得** 取代本节两入口。

### 7.1 聊天流式（默认，对应桌面「流式运行」）

```http
POST /api/v1/workspaces/{workspaceId}/chat/stream
Accept: text/event-stream
```

保留：

```text
event: session
event: token
event: references
event: error
```

叠加（现网前端可忽略）：

```text
event: tool.started      data: {"tool":"sql.generate"}
event: tool.completed    data: {"tool":"sql.generate","status":"SUCCESS"}
event: tool.failed       data: {"tool":"sql.validate","status":"REJECTED"}
```

不增加（桌面有、本版故意不加）：`run.started`、`planner.decided`、`answer.token`、`answer.completed`、`run.completed`、`run.failed`。失败仍用现网 `event: error`。

`tool.failed` 覆盖未知工具、Schema `REJECTED`、超时、执行失败。

### 7.2 分析同步（默认，对应桌面「同步运行」的现网落点）

```http
POST /api/v1/workspaces/{workspaceId}/analysis/query
Content-Type: application/json
```

请求/响应 JSON **形态不变**。步骤只写 `assistant_run_steps`。响应体 **不加** `toolCalls`（D11）。桌面 7.1 那种带 `toolCalls` 的 Agent 响应留给后期 `/agent/runs`。

### 7.3–7.4 查询 Run / Trace（桌面原文，后期）

```http
GET /api/v1/workspaces/{workspaceId}/agent/runs/{runId}
GET /api/v1/workspaces/{workspaceId}/agent/runs/{runId}/trace
```

P0 不实现。排障继续用现有 `request_id` / `run_id` + `assistant_runs` / `assistant_run_steps` / `llm_calls`。

---

## 8. Guardrails

### 8.1 Input Guard

桌面拦截清单适配为 **现网已有 + 本版可测**，不做新语义层：

| 桌面项 | 本版 |
|---|---|
| 删除 / 修改 / 更新请求 | 已有危险 SQL / 意图拦截；写库工具不注册 |
| Prompt 注入 | 不把用户句写入 System Rule 可覆盖区；「忽略之前规则」不提升权限 |
| 越权请求 | 未登录 401；跨工作区拒绝；租户只来自路径 + JWT |
| 业务外请求 | 沿用现网兜底句，不新做业务外分类器 |
| 敏感数据请求 | 不注册查密码类工具；输出面再剥密钥 |

不做：新权限码；把「今天天气怎么样」单独做成必须完成的分类验收。

### 8.2 Tool Guard

每个工具执行前（对齐桌面 8.2，权限改现网）：

```text
1. 工具是否存在（未知 → Runner 闸门）；
2. 本版无 enabled 开关，未注册即不可用；
3. 调用方须为 workspace_member（含 VIEWER）；
4. workspace_id / user_id 只来自认证上下文；
5. 参数是否符合 Schema（D12）；
6. danger_level：第一批均为 read；写/exec 未注册；
7. run 墙钟是否已超 90s；
8. 单工具是否超过自身 timeoutMs。
```

最大调用次数保护随 P1 ReAct，P0 不验收 `max_tool_calls`。

### 8.3 Output Guard

| 桌面项 | 本版 |
|---|---|
| 敏感字段 / 密钥 | 剥离，禁止原样输出 |
| 是否编造数据 | **不做**（D06），不当验收 |
| SQL 错误堆栈 | 剥离，用户侧现网失败句 |
| JSON 是否合法 | SSE `data` 必须是合法 JSON 对象 |
| Markdown 是否可渲染 | 不新增验收 |
| 是否遗漏引用来源 | RAG 仍推 `references`；无命中走现网保底，不把未知工具套成未命中 |

---

## 9. Metrics

桌面 9.x 名称自带 `_total`，与 v0.8「Micrometer 会追加 `_total`」冲突。本版：

- 保留 `/actuator/prometheus`、v0.8 的 Chat/RAG/LLM/SQL 指标，不另起一套 ID。
- P0 **若加** 新指标，名称不要自带 `_total`。建议：`huiyi_agent_run`（status）、`huiyi_agent_limit_exceeded`（reason=timeout|budget）、`huiyi_tool_call`（tool、status）、`huiyi_tool_schema_rejected`。
- `agent_react_*`、`agent_max_steps_exceeded_*` 随 P1。
- 不强制桌面 9.1–9.4 全量新名；能复用 v0.8 `sql_*` / `rag_*` / `llm_*` 则不重复注册。

---

## 10. 测试计划

### 10.1 单元测试

| 模块 | 测试项（P0） |
|---|---|
| ToolRegistry | 注册、未知工具（v0.9 已有则保持） |
| ToolExecutor | 成功、失败、超时、**参数非法 REJECTED** |
| RouterPlanner | 知识问答、数据查询、画像；**不含**订单工具 / ReAct |
| ContextBuilder | Prompt 超限先裁知识/库表；近 10 轮 + 摘要仍在 |
| Guardrails | 未知工具话术、输出不含堆栈/密钥形态 |
| SSE | `tool.*` 事件名；`session`/`token` 仍在 |

ReActPlanner 多步 / 最大步数：P1 再补。

### 10.2 集成测试

| 场景 | 验证 |
|---|---|
| RAG 问答 | 能引用知识库；未知工具 **不** 套未命中保底 |
| 数据查询 | 生成 / 校验 / 执行 SQL（工具名 `sql.*`） |
| 数据画像 | 一次 Python，含图表类型 |
| 写操作 | 拒绝（未注册 + 现网 SQL 防线） |
| 未登录 | 401 |
| 跨工作区 | 拒绝 |

桌面「避免编造」不作为可自动验收项。

### 10.3 E2E 回归

保留旧场景（桌面 10.3 前五条，工具名按现网）：

```text
1. 口径问答；
2. 销售排名；
3. 渠道分析；
4. 危险 SQL 拦截；
5. 无命中兜底。
```

桌面新增 6–10 条改写：

```text
6. /chat/stream 出现 tool.* 且旧事件仍在（后端测即可）；
7. 图表类型仍来自 data.profile，无独立 chart.recommend；
8. Agent 多步 ReAct — P1；
9. 未知工具 / Schema 失败，用户句「工具调用失败，请稍后重试。」；
10. run 90s / Prompt 预算 → error_code=AGENT_LIMIT_EXCEEDED（后端测）；最大步数 — P1。
```

无评测页。不要求新的「Agent 查询销售」独立 HTTP。

---

## 11. 数据库与 Trace

不新建 `agent_runs`、`tool_invocations`、`tool_registry`。无 MySQL FK / cascade。应用层 `run_id` 关联，单条 SQL ≤ 3 表。

### 11.1 assistant_runs

桌面要加 `mode` / `agent_status` / `error_code` / `tool_call_count`。  
**已拍板 D10=C：P0 只加 `error_code`。** `mode` 留给 P1。`status` 已有，不重复 `agent_status`。token 整型已有，不加 `token_usage_json`。

Flyway 接 V10 之后：`V11__assistant_runs_error_code.sql`

```sql
ALTER TABLE assistant_runs
  ADD COLUMN error_code VARCHAR(64) NULL COMMENT '如 AGENT_LIMIT_EXCEEDED；无则空' AFTER status;
```

### 11.2 assistant_run_steps

已有 `tool_name`。桌面 JSON 三列 **不加**。继续 TEXT 摘要。

```text
不要记录完整 Prompt；
不要记录敏感参数；
不要记录完整结果大对象。
```

---

## 12. 安全设计

### 12.1 租户上下文

与桌面 12.1 相同，必须执行：

```text
workspaceId 必须来自路径和认证上下文；
userId 必须来自 JWT；
LLM 工具入参不得包含 workspaceId / userId；
工具执行时由系统注入租户上下文（含工具线程恢复，v0.9 已做）。
```

### 12.2 工具权限

桌面 12.2 的 `*.read` 码现网没有。本版：

| 工具 | 权限 |
|---|---|
| `knowledge.search` | `workspace_member`（含 VIEWER） |
| `sql.generate` | 同上 |
| `sql.validate` | 同上 |
| `sql.execute_readonly` | 同上 |
| `data.profile` | 同上 |

知识 **上传** 仍要写权限（v0.7），本版不放宽。

### 12.3 高危工具

与桌面 12.3 相同，见 §4.5。不靠 `enabled=false` 冒充不暴露。

---

## 13. 发布计划

### 13.1 分支

```text
feature/v1.0-agent-stable
main
tag v1.0.0
```

桌面 `release/v1.0.0` 若现网仍是 feature → main 发版，则不强制多一条 release 分支。GitHub 日常分支仍是 `dev` 时，feature 命名 `feature/v1.0-*`。

### 13.2 阶段

桌面 4 周且第 2 周重做 Harness。仓库 **2–3 周、扩展不重写**：

| 阶段 | 内容 |
|---|---|
| 第 1 周 | V11 `error_code`；五工具 JSON Schema；Executor 校验；run 90s |
| 第 2 周 | `/chat/stream` 叠加 `tool.*`；Context Budget 裁剪；Output Guard |
| 第 3 周 | 金标 + e2e；文档；发版验证 |

ReAct / 公开 Agent API / 评测页不进这三周。

### 13.3 发布清单

对照桌面 13.3 改写：

```text
[ ] 默认入口仍是 chat/stream 与 analysis/query；无 /agent/runs；
[ ] SSE：旧四事件 + tool.started/completed/failed；
[ ] ToolRegistry 仍稳定（五工具，不重写）；
[ ] 工具 Schema 校验：非法入参 REJECTED；
[ ] 权限：workspace_member，VIEWER 可只读；
[ ] unknown tool 不崩溃；
[ ] run 总超时 / 预算超限 error_code 生效（最大步数不进 P0）；
[ ] 旧五场景不回归；
[ ] Java CI / Python CI / Frontend build 通过；
[ ] Flyway V11 已验证；
[ ] README / roadmap / API 文档更新；
[ ] tag v1.0.0 并推送。
```

---

## 14. 风险与应对

| 风险 | 影响 | 应对（适配后） |
|---|---|---|
| LLM 选择错误工具 | 查询失败 | P0 五场景走 Router 固定链，不靠模型点名 |
| SQL 不安全 | 泄露或破坏 | 不注册写工具、SELECT-only、`huiyi_ro`、行数限制 |
| ReAct 死循环 | 成本延迟 | P0 不上 ReAct；P1 再加步数墙 |
| Prompt 过长 | 超限 | Context Budget + v0.6 摘要；先裁知识/库表 |
| 20s 总超时误杀 | 正常 SQL 失败 | 不用桌面 20s，run 取 90s |
| 新 SSE 名弄断前端 | 聊天不更新 | 保留 `token`，只叠加 `tool.*` |
| 前端范围膨胀 | 延期 | 时间线可选，不挡发版 |
| 测试不足 | 不可信 | Schema / SSE / 预算 / Guard 各有后端测 + 五场景 e2e |

---

## 15. 验收标准

### 15.1 功能验收

对照桌面 15.1，按 P0 勾选：

```text
[ ] 无 POST /agent/runs；默认入口仍是 chat/stream 与 analysis/query；
[ ] SSE：session/token/references/error 仍在，并出现 tool.*；
[ ] Router 模式可用（五场景默认）；
[ ] ReAct 模式：P0 不验收；
[ ] ToolRegistry 仍为 5 个只读工具（不是桌面 7 个）；
[ ] 每个工具有 JSON Schema、超时；权限 = workspace_member；
[ ] 未知工具 / Schema 非法安全失败；
[ ] run 90s 与 Prompt 预算保护生效；最大步数 P1；
[ ] RAG 引用来源可用；SQL 结果可展示；图表类型来自 data.profile；
[ ] analysis/query 仍 JSON，无 toolCalls；
[ ] 旧五场景不回归；企业隔离不回归。
```

### 15.2 安全验收

```text
[ ] 未登录访问返回 401；
[ ] 跨工作区访问被拒；
[ ] 工具参数注入 workspaceId 无效；
[ ] 删除 / 写库请求被拒绝；
[ ] SQL 写操作被拒绝；
[ ] 步骤 Trace 能看到 tool_name（审计沿用 v0.7/v0.8，不新表）；
[ ] 用户输入和输出测例不含密钥/堆栈明文。
```

不做「编造检测」勾选。

### 15.3 工程验收

```text
[ ] Java CI 通过；
[ ] Python CI 通过；
[ ] Frontend build 通过；
[ ] ToolRegistry / ToolExecutor / RouterPlanner / Context 裁剪 / Guard 有单测；
[ ] tool SSE 有测试；
[ ] Trace / error_code 有测试；
[ ] API 文档、README、roadmap 更新。
```

无评测页勾选。前端时间线不作为工程门闩。

---

## 16. 简历项目描述

### 16.1 项目名

```text
HuiYi Stable Agent Assistant · 企业智能数据助手
```

### 16.2 技术栈

```text
Java 17、Spring Boot 3、MySQL、Redis、Chroma、Python、FastAPI、RAG、Router Agent Runtime、Docker、Vue3
```

（P0 简历不写已交付 ReAct / 公开 Agent API。SpringAI 若现网未用则不要写上。）

### 16.3 项目描述

```text
在 v0.9 Tool Harness 与 Router Runtime 上做稳定化：为五个只读工具补齐 JSON Schema 与失败闸门，
在现有聊天 SSE 上叠加工具步骤事件，用 Context Budget 约束 Prompt，并用现有工作区角色做输入/工具/输出护栏。
五场景默认仍走 Router，排障继续用 request_id / run_id / assistant_run_steps。
```

### 16.4 职责亮点

对照桌面 16.4，只写 P0 真实会做的：

```text
1. 延续 AgentRunner / RouterPlanner / ToolRegistry / ToolExecutor，不平行再造入口。

2. 将 RAG、Text-to-SQL、只读校验执行、数据画像（含图表类型）保持为标准 Tool，补齐 Schema、超时和 Result 摘要。

3. 简单问题走稳定 Router；ReAct 作为后续能力写入规格，不作为五场景默认。

4. Context Budget：近 10 轮 + 摘要 + 组装 Prompt 硬上限，超限先裁知识与库表。

5. Input / Tool / Output Guard：租户只来自登录上下文、未知工具与 Schema 闸门、剥离密钥与堆栈。

6. Trace：步骤带 tool_name；run 级 error_code；Metrics 接 v0.8 Prometheus。
```

---

## 17. 一页纸总结

### 当前状态

```text
v0.9 已有 ToolRegistry、RouterPlanner、AgentRunner、5 个只读工具。
```

### v1.0 缺口（桌面原文 → 本版怎么补）

```text
Agent API        → 现网两入口叠加能力，公开 /agent/runs 后期；
SSE Agent 事件   → 保留旧事件 + tool.*；
ReAct            → P1，不进默认；
结构化 Tool Schema → P0；
Context Budget   → P0（不写死 8000）；
Guardrails       → P0（现有角色，不判编造）；
基础评测         → e2e + 金标，无评测页；
稳定发布         → P0 发版清单。
```

### v1.0 目标

```text
稳定可试用的企业数据助手（现网入口，不换协议）。
```

### 核心原则

桌面原文保留，补一句现网约束：

```text
高层业务工具优先（后期再封装；本版先稳定 sql.* 链）；
底层步骤内部化（校验不可跳过）；
动态能力有限开放（ReAct 非默认）；
安全校验前置；
失败必须有 Trace；
范围宁小勿散；
叠加现有 /chat/stream，不新协议。
```

---

## 18. 需求评审决策日志（已冻结）

拍板方式：表格逐项确认（仓库规则 `decision-table-confirm`）。下表是已选项，不是待选建议。改写桌面规划时 **不得推翻** 下表。

| 编号 | 决策项 | 已拍板 |
|---|---|---|
| D01 | v1.0 范围 | **B** 先按 P0/P1 切开再写仓库规格，不按桌面全文 4 周开干 |
| D02 | 默认入口与协议 | **B** `chat/stream` + `analysis/query`；步骤 SSE 叠加；公开 Agent API 不进 P0 |
| D03 | 对外工具面 | **B** v0.9 五工具；只补 Schema/Result；不拆 `chart.recommend` |
| D04 | Planner / ReAct | **C** 五场景默认 Router；ReAct 进 P1（显式 mode 或非五场景），不进默认 |
| D05 | 超时 | **C** 单工具沿用 v0.9；run 总上限按链路加总（本规格取 90s） |
| D06 | Guard / 权限 | **B** 仅 `workspace_member`；拦写/未知工具/泄漏；不做编造判定 |
| D07 | 步骤 SSE | **B** 保留现有四事件；叠加 `tool.started/completed/failed`；前端时间线可选 |
| D08 | Context Budget | **C** 近 10 轮+摘要 + Prompt 硬上限；不写死 8000；分区比例不当验收 |
| D09 | 评测 | **C** 无评测页/平台；e2e + 少量后端金标 |
| D10 | 表结构 | **C** 只加 `assistant_runs.error_code`；P1 再加 `mode`；steps 不加 JSON |
| D11 | analysis 步骤 | **B** 仍 JSON；只写库；无 `toolCalls` |
| D12 | Schema 失败 | **B** 该步 REJECTED；用户句同未知工具；不用 `AGENT_LIMIT_EXCEEDED` |
| D13 | 是否写规格 | **B** 本文即仓库规格；P1 只写不本周开工 |

依赖：

- D01=B → 桌面公开 API / 新工具面 / 评测页不进 P0。
- D02=B → §7 不得把 `/agent/runs` 写成必须完成。
- D03=B → §4 桌面高层工具表是后期，不是本版注册清单。
- D04=C → P0 无 ReActPlanner；`mode` 列延到 P1。
- D07=B → 不得把 `token` 改名为 `answer.token`。
- D12=B → Schema 失败与 run 预算超限的 `error_code` 分流。
