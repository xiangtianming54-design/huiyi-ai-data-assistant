# HuiYi v0.9 · Tool Harness & Agent Runtime 开发文档

> 版本：v0.9.0  
> 状态：**口径已冻结**（P0 D01–D13、P1 D14–D21 均已拍板或本版关闭。实施计划已写，分支 `feature/v0.9-tool-harness`）  
> 基线：`huiyi-ai-data-assistant v0.8.0`（标签 `v0.8.0` → `bc9ce6d`）  
> 来源：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§9；[roadmap.md](../../roadmap.md)；需求评审决策 D01–D21  
>
> 未纳入本版：`POST /agent/runs`；ReAct / `ReActPlanner` / 护栏 / `time.now`；`plan_execute`；独立表 `tool_invocations` / `tool_registry`；工具注册管理 API；步骤可见 SSE；前端 `toolCalls`；多 Agent；MCP；Grafana；写库工具；v0.7 P1 UI；v0.8 P2 调试面板。

周期：3–4 周。

定位：把现有 RAG、Text-to-SQL、数据画像（含图表类型）收成统一工具，并用 **Router + 最小 Agent Runtime** 替换 `ChatService` / `AnalysisService` 里写死的编排。  
v0.8 的请求级 Trace 保留；本版补 **工具步骤 Trace**（写进现有 `assistant_run_steps`），不是再做一套 Request ID，也不是 ReAct 循环。

---

## 1. 目标

把五场景从「服务里写死的 if/else」收成「登录上下文 + Router + 可注册工具」，并保证排障仍能按 `request_id` / `run_id` 下钻到某一步调了哪个工具。

```text
1. 现网入口不变：POST /api/chat/stream、POST /api/analysis/query；
2. 两个入口内部委托同一 AgentRunner，禁止旧链路分叉；
3. 五场景默认 router，按意图走固定步骤，再调用已注册工具；
4. ToolRegistry 可注册 / 发现工具；未注册名由 Runner 接住，进程不崩；
5. 第一批五个只读工具：knowledge.search、sql.generate、sql.validate、sql.execute_readonly、data.profile；
6. workspace / user 只来自登录上下文；
7. 工具步骤写入 assistant_run_steps，与 v0.8 的 request_id / run_id / llm_calls 同一套 ID；
8. 危险工具根本不注册；VIEWER 仍可走现网问答与只读查数。
```

## 2. 核心能力

```text
1. ToolSpec / AgentTool / ToolRegistry / ToolExecutor；
2. AgentRunner / AgentState / RouterPlanner；
3. Chat / Analysis 门面委托同一 Runner；
4. 固定步骤：意图、改写、分解、去重、SQL 总结（不是可被随意点名的 Tool）；
5. 第一批只读工具（见 §7）；
6. 工具步骤 Trace：step_type + tool_name + 成败 + 耗时，落在 assistant_run_steps；
7. 未知工具失败闸门：Registry 可抛，Runner 必须捕获并记 Trace。
```

边界说明：

```text
1. Harness ≠ 默认 ReAct。本版没有模型自己点工具的循环；
2. Agent Run 复用 v0.8 的 run_id，不新建 agent_runs；
3. SSE 仍只推 token，步骤过程事后查库；
4. 前端入口与页面不改；
5. 不暴露 POST /agent/runs。
```

## 3. 非目标

```text
1. 不做 POST /agent/runs（桌面 §9.9，后期对齐）；
2. 不做 ReAct / ReActPlanner / 步数与总超时护栏 / time.now（桌面 §9.10，后期对齐）；
3. 不做 plan_execute（v1.0）；
4. 不做步骤可见 SSE、不展示 toolCalls（v1.0：在现有 /chat/stream 上叠加，不新协议）；
5. 不做独立表 tool_invocations、tool_registry 及管理 API（桌面 §15，后期对齐）；
6. 不做多 Agent、MCP、插件市场；
7. 不做 Grafana / 告警大盘；
8. 不做评测集、多模型路由、完整账单；
9. 不注册写库 SQL、删文件、exec、停用用户、退款等危险工具；
10. 不补做 v0.7 P1 UI、v0.8 P2 调试面板；
11. 不换向量库、不拆分租户表、不上 K8s。
```

## 4. 反目标

- 新旧两套编排并存（Chat/Analysis 自己跑、Runner 另跑）。
- 用 `/agent/runs` 替换现网聊天入口。
- 把 ReAct 做成五场景默认，或本版交付 ReAct「先关上」。
- 新建 `agent_runs` / MySQL 外键 / 级联删除。
- 工具入参或 Prompt 覆盖 `workspaceId` / `userId`。
- 未知工具套 RAG 未命中保底话术，或未捕获导致 500 把进程打穿。
- 丢掉 v0.8 的 `request_id` / `llm_calls` / prometheus，另起一套 ID。
- 把意图 / 改写 / 分解 / 去重 / SQL 总结收成可被随意点名的 Tool。
- 单独注册 `chart.recommend`，或对同一画像打两次 Python。
- 把 VIEWER 挡在只读 SQL / 问答之外。
- 注册危险工具再靠 `enabled=false` 或运行时拦截当「不暴露」。

## 5. 约束

- 继续 Java 主编排 + Python Sidecar。
- 不降低 v0.7 工作区隔离与未登录 401。
- 不删除 v0.8 的 Request ID / Metrics / `llm_calls`；工具步骤挂同一 `request_id` / `run_id`。
- 密钥仍只走环境变量。
- 数据模型遵守阿里手册：不用 MySQL FK / cascade；单条 SQL 关联不超过 3 张表。应用层用 `run_id` 关联。
- 五场景与企业隔离 e2e 仍须通过。
- 本版不改前端路由、不改默认聊天页。

## 6. 入口与编排

| 路径 | 本版角色 |
|---|---|
| `POST /api/chat/stream` | 默认聊天入口。SSE 仍只推 token。内部委托 `AgentRunner`。 |
| `POST /api/analysis/query` | 默认分析入口。非 SSE。内部委托同一 `AgentRunner`。 |
| `POST /agent/runs` | **不上**。后期对齐桌面 §9.9 时再挂同一 Runner，且仍不得取代上两行。 |

`ChatService` / `AnalysisService` 必须委托 Runner，不得保留可独立跑完五场景的旧编排。

默认执行模式：`router`。Router 根据意图走固定步骤，再调用 Registry 里的工具。没有 `react` / `auto` 本版开关。

`workspaceId` / `userId` 只从登录上下文注入 `AgentState`。工具 Schema 与 Prompt 不得出现可覆盖租户的字段。

## 7. 工具 Harness

### 运行时

- `ToolSpec`：name、schema、timeout、dangerLevel、permissions（本版权限语义 = 工作区成员，含 VIEWER）。
- `AgentTool`：实现调用。
- `ToolRegistry`：Spring Bean **内存**注册；本版不落库、无管理 API。
- `ToolExecutor`：按 spec 调工具、记超时。
- `AgentRunner`：接住 Registry 对未知工具的抛错，写入步骤 Trace，对用户返回**通用失败话术**（不是 RAG 未命中保底）。

查找失败的原因包括：名字未注册、已从本版清单剔除（危险工具）、拼写错误。Prompt 里注入工具列表**不能**替代这道闸门（本版 Router 也不靠模型点名工具）。

### 第一批工具（只读，工作区成员含 VIEWER 可调）

| 工具名 | 能力 | 说明 |
|---|---|---|
| `knowledge.search` | 知识检索 | 包现网 RAG |
| `sql.generate` | 生成只读 SQL | |
| `sql.validate` | 校验 SQL | |
| `sql.execute_readonly` | 执行只读 SQL | 不注册写库执行 |
| `data.profile` | 数据画像 | **一次** Python 调用，输出含画像与 `chart_type`。不注册 `chart.recommend` |

固定步骤（**不是 Tool**，写入 `assistant_run_steps`）：意图、改写、分解、去重、SQL 总结。

### 明确不注册

`sql.execute_write`、文件删除、exec、停用用户、支付退款，以及任何写库 / 副作用工具。点到这些名字 = 未知工具，走 Runner 闸门。

## 8. 数据库

不新建 `agent_runs`、`tool_invocations`、`tool_registry`。

扩展现有 `assistant_run_steps`（Flyway 接 v0.8 的 V9 之后，建议 `V10__assistant_run_steps_tool.sql`）：

```sql
ALTER TABLE assistant_run_steps
  ADD COLUMN tool_name VARCHAR(128) NULL COMMENT '工具名；固定步骤为空' AFTER step_type;
```

字段按粒度：

| 粒度 | 落点 |
|---|---|
| 一次问答 / 分析 | 现有 `assistant_runs`（`request_id` / `run_id` / workspace / user） |
| 某一步（固定步骤或一次工具调用） | `assistant_run_steps`（`step_type`、`tool_name`、status、latency、摘要、错误） |
| 一次 LLM 调用 | 现有 `llm_calls` |

无 MySQL FK。查询时应用层用 `run_id` 关联，单条 SQL 不超过 3 表。

## 9. Trace 与可观测

一次 Run 仍是 v0.8 的 `run_id`。步骤增加工具行，例如：

```text
intent → query_rewrite → tool:knowledge.search
intent → query_rewrite → tool:sql.generate → tool:sql.validate → tool:sql.execute_readonly → sql_summarize
intent → tool:data.profile
```

未知工具：步骤 `status=FAILED`，`tool_name` 为请求名，`error_message` 记内部原因；用户侧不回内部名清单。

保留 `/actuator/prometheus`、健康检查、Python 访问日志透传 ID。本版不强制新指标名；若加，只用工具调用次数 / 失败 / 耗时，名称不要自带 `_total`（Micrometer 会追加）。

## 10. API / SSE / 前端

- HTTP 路径、鉴权、RBAC 与现网一致：聊天与只读分析 = `requireMember`（含 VIEWER）；知识上传仍要写权限。
- `/chat/stream` 事件形态本版不变，只推 token。
- 前端不改入口、不展示 `toolCalls`、不切新 API。
- 后期体验（v1.0）：在现有 `/chat/stream` 上叠加可读步骤事件，不另做协议。

## 11. 验收标准

```text
[ ] ToolRegistry 可注册第一批五个只读工具（内存 Bean）；
[ ] 未知 / 未注册工具由 Runner 捕获，进程不崩，步骤 Trace 有失败行，用户侧通用失败；
[ ] 每个工具有 Schema、超时、dangerLevel；
[ ] Chat / Analysis 均委托同一 Runner，五场景默认 router；
[ ] 不存在可独立完成五场景的旧编排分叉；
[ ] 无 POST /agent/runs；
[ ] 无 ReActPlanner / time.now；
[ ] data.profile 一次 Python 调用即含 chart_type；
[ ] 危险工具未注册；点名走未知工具闸门；
[ ] workspace / user 只来自登录上下文；跨工作区请求仍被拒；
[ ] assistant_run_steps 能下钻到工具名、成败、耗时；无 tool_invocations / tool_registry 表；
[ ] request_id / run_id / llm_calls / prometheus 仍可用；
[ ] SSE 仍只推 token；前端入口不变；
[ ] VIEWER 仍可问答与只读查数；
[ ] 旧五个核心场景不回归；
[ ] 不降低工作区隔离与未登录 401。
```

## 12. 明确留给后期 / 后续版本

| 项 | 去向 |
|---|---|
| `POST /agent/runs` | 后期；同一 Runner，不得取代现网入口 |
| ReAct 第一版、护栏、`time.now` | 后期（桌面 §9.10） |
| `plan_execute` | v1.0 |
| 步骤可见 SSE / 前端 `toolCalls` | v1.0，复用 `/chat/stream` |
| `tool_invocations`、`tool_registry` 落库与管理 API | 后期（桌面 §15） |
| 多 Agent、MCP、Grafana、评测、账单、写库工具 | 更后版本 |
| v0.7 P1 UI、v0.8 P2 面板 | 不滚进本版 |

---

## 13. 需求评审决策日志（已冻结）

拍板方式：表格逐项确认（仓库规则 `decision-table-confirm`）。下表是已选项，不是待选建议。

### 13.1 P0

| 编号 | 决策项 | 选项 | 为何必须先定 | 评审建议 | 状态 |
|---|---|---|---|---|---|
| D01 | 现网聊天入口命运 | Chat/Analysis 做同一 Runner 的门面；替换为 `/agent/runs`；两套编排并存 | 决定从哪改代码、五场景走哪条 HTTP | 门面同一 Runner；新 API 不替代默认入口 | **已拍板：A**（`/chat/stream`、`/analysis/query` 为默认入口，内部委托同一 Runner；`/agent/runs` 不替代默认入口） |
| D02 | 默认执行模式 | `router`；`react`；`auto` | 决定五场景延迟、失败形态和回归口径 | 默认 `router`；`react` 不作为五场景默认 | **已拍板：A**（五场景默认 `router`；ReAct 本版不做，见 D15） |
| D03 | Run 存在哪 | 扩展 `assistant_runs/steps`；新建 `agent_runs/steps` 并外键；两套独立 `run_` | 决定 Flyway 和下钻排障 | Agent 挂 `request_id`；能扩展就不要平行造表 | **已拍板：A**（扩展现有表；应用层用 `run_id` 关联；不建 MySQL FK；不新建 `agent_runs`） |
| D04 | 本版 SSE 范围 | 只保留现有 token SSE；叠加工具步骤事件；新做 Agent SSE 协议 | §9 写了 SSE，§16 把 Streaming Agent 放在 v1.0 | 0.9 不新做 Agent SSE 协议 | **已拍板：A**（本版只保留现有推字；步骤过程写入 `assistant_run_steps`。后期：在现有 `/chat/stream` 叠加步骤事件，不新协议——v1.0 / D21） |
| D05 | 未知工具如何失败 | Registry 抛、Runner 捕获；`get` 返回空；未捕获 500 | §9.5 抛错与 §9.11「不崩进程」打架 | 内部可抛，边界必须接住并记 Trace | **已拍板：A**（Registry 可抛，Runner 必须捕获并记 Trace；用户侧通用失败话术，不套 RAG 未命中保底） |
| D06 | workspace / user 从哪来 | 只来自登录上下文；允许工具入参覆盖；两者合并 | 入参可改租户会打穿 v0.7 隔离 | 只来自登录上下文，LLM 参数不得带 `workspaceId` | **已拍板：A** |
| D07 | 意图 / 改写 / 分解 / 去重 / SQL 总结放哪 | Runner 固定步骤；全部收成 Tool；本版丢掉 | 它们不在第一批工具表里 | Router 内固定步骤，不交给 ReAct 临时发明 | **已拍板：A** |
| D08 | 是否允许两套编排 | Chat/Analysis 必须委托 Runner；允许旧链路与 Agent 分叉 | 分叉后工具化没有意义 | 禁止两套链路 | **已拍板：A** |
| D09 | v0.8 可观测是否保留 | `request_id` / `llm_calls` / prometheus 必须保留并接线；可替换重做 | 反目标：丢掉请求级 ID | 必须保留并让 Agent Trace 挂上 `request_id` | **已拍板：A** |
| D10 | 画像与图表几个工具 | 一个 Tool；两个 Tool 各调一次；两个名字共享一次 Python 调用 | 决定第一批清单 | 两个名字一次调用，或合并成一个并写死 | **已拍板：A**（仅 `data.profile`；不单独注册 `chart.recommend`） |
| D11 | `ToolSpec.permissions` 怎么落到 RBAC | 映射 VIEWER/MEMBER/OWNER；只用 `dangerLevel`；本版不做权限字段 | 桌面有字段无角色映射 | 知识检索 VIEWER；SQL 生成/校验/执行至少 MEMBER | **已拍板：A**（第一批只读工具 = 工作区成员含 VIEWER。作废「SQL 至少 MEMBER」） |
| D12 | 危险工具如何「不暴露」 | 根本不注册；注册但 `enabled=false`；Executor 运行时拦截 | 写库类工具验收依赖这条 | 本版不注册写库 / exec / 禁用用户类工具 | **已拍板：A** |
| D13 | 本版明确不做清单 | 冻结：`plan_execute`、多 Agent、MCP、Grafana、写库工具、v0.7 P1 UI、v0.8 P2 面板 | 不定围栏会被后续章节拖进本版 | 全部踢出 v0.9；`plan_execute` 仍归 v1.0 | **已拍板：A**（ReAct 见 D15=A；步骤 SSE 见 D04→v1.0） |

### 13.2 P1

| 编号 | 决策项 | 选项 | 为何放到 P1 | 评审建议 | 状态 |
|---|---|---|---|---|---|
| D14 | `POST /agent/runs` 本版是否上线 | 上且与门面同一 Runner；不上；只内部调用 | 桌面 §9.9 有该 API，五场景不依赖它 | 可上，但不得成为默认聊天入口 | **已拍板：B**（本版不上该 HTTP。对齐桌面 §9.9 补进后期，且仍不得取代默认聊天入口） |
| D15 | ReAct 本版是否交付 | 不做；做但默认关；做且默认开 | 新失败模式，不挡 Router 重构 | 做则默认关 | **已拍板：A**（只交 Router。对齐桌面 §9.10 的 ReAct 第一版补进后期） |
| D16 | ReAct 护栏 | 最大步数 / 总超时 / 禁止工具集 / 无护栏 | 仅当 D15 选择「做」 | 步数与超时硬上限 | **本版关闭**（D15=A）。随 ReAct 列入后期 |
| D17 | `time.now` 是否第一批工具 | 随 P0 工具集；随 ReAct；不做 | Router 用不上 | 跟 D15 | **本版关闭**（D15=A）。随 ReAct 列入后期 |
| D18 | 工具调用落哪张表 | 新表 `tool_invocations`；写入 `assistant_run_steps`；只打日志 | §15 有该表，P0 只需能下钻步骤 | P0 先用步骤 Trace | **已拍板：B**（写入 `assistant_run_steps`。对齐桌面 §15 `tool_invocations` 补进后期） |
| D19 | `tool_registry` 内存还是落库 | Spring Bean 内存注册；落库；再加管理 API | §15 有表，运行时规划是 Java Map | P0 内存注册 | **已拍板：A**（内存注册。对齐桌面 §15 表和管理 API 补进后期） |
| D20 | 前端改多少 | 不改入口；展示 `toolCalls`；切到 `/agent/runs` | 取决于 D01/D14/D04 | P0 不改入口 | **已拍板：A**（不改入口、不展示工具列表。「切到 `/agent/runs`」因 D14=B 本版关闭。`toolCalls` 与步骤 SSE 一并列入 v1.0） |
| D21 | 若步骤流式提前到 0.9：事件协议 | 在现有 SSE 加 `step` 事件；新 stream 端点；不同时做 | 仅当 D04 选择「叠加步骤事件」 | 默认随 D04 放到 v1.0 | **本版关闭**（D04=A）。列入 v1.0：现有 `/chat/stream` 叠加步骤事件，不新协议 |

### 13.3 依赖（已执行）

- D15=A → D16、D17 本版关闭。
- D04=A → D21 本版关闭。
- D14=B → 不暴露 `POST /agent/runs`；D20「切到 `/agent/runs`」关闭。
- D18=B → 无 `tool_invocations` 表。
- D19=A → 无 `tool_registry` 表 / 管理 API。
- D20=A → 前端本版零改。
