# HuiYi v0.9 · Tool Harness & Agent Runtime 开发文档

> 版本：v0.9.0  
> 状态：待评审（v0.8.0 已发布后起草）  
> 基线：`huiyi-ai-data-assistant v0.8.0`（标签 `v0.8.0`）  
> 来源：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§9；[roadmap.md](../../roadmap.md)

周期：3–4 周。

定位：把现有 RAG、Text-to-SQL、数据画像和图表推荐收成统一工具，并实现最小 Agent Runtime。  
v0.8 的请求级 Trace 保留；本版补 **Agent Run Trace**（工具调用链），不是再做一套 Request ID。

---

## 1. 意图

v0.8 能回答「这次 HTTP 请求在哪一层失败、花了多少 token」，但编排仍写死在 `ChatService` / `AnalysisService`。v0.9 只做工具化与最小循环，不推翻可观测、不补 Grafana。

## 2. 目标

- `ToolRegistry` 可注册 / 发现工具；未知工具不导致进程崩溃。
- 每个工具有 Schema、权限、超时、危险级别。
- Agent 至少能调用知识检索、SQL 生成、SQL 校验、SQL 执行、数据画像（及图表推荐）。
- 支持 Router 模式与 ReAct 模式；危险工具默认不暴露。
- 一次 Agent Run 有完整 Trace（步骤、工具名、成败、耗时），可与 v0.8 的 `request_id` / `run_id` 关联。
- SSE 能推送 Agent 步骤事件，而不只是 token。

## 3. 非目标

- 完整多 Agent、MCP、插件市场（v1.4+）。
- Grafana / 告警大盘（v1.3）。
- 评测集、多模型路由（v1.1）。
- 补做 v0.7 P1 UI、v0.8 P2 调试面板。
- 换向量库、K8s、拆分租户表。

## 4. 约束

- 继续 Java 主编排 + Python Sidecar。
- 不降低 v0.7 工作区隔离与未登录 401。
- 不删除 v0.8 的 Request ID / Metrics / `llm_calls`。
- 密钥仍只走环境变量。
- 五场景与企业隔离 e2e 仍须通过。

## 5. 范围（对照桌面 §9）

`AgentRunner` / `AgentState` / `ToolSpec` / `AgentTool` / `ToolRegistry` / `ToolExecutor`；内置 `KnowledgeSearch`、`SqlGenerate`、`SqlValidate`、`SqlExecute`、`DataProfile`、`ChartRecommend`；`RouterPlanner` 与 `ReActPlanner`（`plan_execute` 可放到 v1.0）；`AgentTraceCollector`。

## 6. 验收（草案）

```text
[ ] ToolRegistry 可注册工具；
[ ] 未知工具不会导致系统崩溃；
[ ] 每个工具有 Schema、权限、超时；
[ ] Agent 可以调用至少 5 个工具；
[ ] Agent Run 有完整 Trace；
[ ] 支持 Router 模式；
[ ] 支持 ReAct 模式；
[ ] 危险工具不会被暴露；
[ ] 旧五个核心场景不回归；
[ ] 不降低工作区隔离与未登录 401。
```

## 7. 明确留给更后版本

MCP、多 Agent、Grafana、评测平台、完整账单、生产多环境。
