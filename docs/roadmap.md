# 版本路线图

来源：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》（企业 Agent 版本迭代开发文档）。  
仓库内本文是对照摘要；**版本顺序、周期、目标以该文档为准**，不另起一套路线。

基线标签：`v0.6.0`。能力对照见 [baseline.md](baseline.md)。

演进路径（文档 §1）：

```text
现有能力
  → 工具化 Tool Harness
  → 运行时 Agent Runtime
  → 企业治理 Enterprise Governance
  → 平台化 Enterprise Agent Platform
```

## 版本总览（文档 §4）

| 版本 | 名称 | 周期 | 主要目标 |
|---|---|---|---|
| **v0.6.1** | Baseline Hardening | 3–5 天 | 稳定当前基线，补齐发布流程 |
| **v0.7.0** | Enterprise Foundation | 2–3 周 | 用户、工作区、RBAC、审计、API v1、Flyway |
| **v0.8.0** | Observability & Cost | 2 周 | Trace、Metrics、Token 成本、统一日志 |
| **v0.9.0** | Tool Harness & Agent Runtime v1 | 3–4 周 | Tool Registry、Agent Loop、Run Trace、SSE |
| **v1.0.0** | Stable Agent Assistant | 2–3 周 | 上下文、记忆、Guardrails、反馈、稳定发布 |
| **v1.1.0** | Eval & Prompt Governance | 2–3 周 | 评测集、回归、Prompt/模型治理 |
| **v1.2.0** | Enterprise Knowledge Plane | 3–4 周 | 知识空间、异步解析、对象存储、权限过滤 |
| **v1.3.0** | Production Ready | 3–4 周 | staging/prod、备份、限流、压测、告警 |
| **v1.4.0+** | Platform & Advanced Agent | 4 周以上 | 语义层、MCP、多模型路由、插件化 |

## 已发布：v1.0.0 Stable Agent Assistant

标签 `v1.0.0`。规格：[2026-10-18-v10-stable-agent-assistant.md](superpowers/specs/2026-10-18-v10-stable-agent-assistant.md)。技术设计：[2026-10-18-v10-stable-agent-assistant-technical.md](superpowers/specs/2026-10-18-v10-stable-agent-assistant-technical.md)。计划：[2026-10-18-v10-stable-agent-assistant.md](superpowers/plans/2026-10-18-v10-stable-agent-assistant.md)。本版 P0：现网 `chat/stream` 叠加 `tool.*` SSE；五工具 JSON Schema；Context Budget；OutputGuard；90s run 帽；Flyway V11 `assistant_runs.error_code`。入口仍是 Chat/Analysis，默认 Router。

未纳入本版：ReAct；`POST /agent/runs`；前端 `toolCalls`；评测页；细粒度权限；MCP；写库工具。v0.7 P1 UI、v0.8 P2 调试面板仍不自动滚入。

上一发布：`v0.9.0` Tool Harness & Agent Runtime。

## 当前开发：v1.1.0 Eval & Prompt Governance

尚未开工。评测集、回归报告、Prompt/模型治理见路线图，不把未写规格的能力当已交付。

## 后续版本（摘要）

完整任务、表结构、API、验收以桌面开发文档对应章节为准。

| 版本 | 做什么 | 不提前做 |
|---|---|---|
| **v0.7.0** | 认证、工作区、成员、RBAC、资源归属、`/api/v1`、Flyway、审计、基础限流 | Agent Loop、换向量库、K8s |
| **v0.8.0** | Request/Run ID、Request Trace、Token/成本字段、Metrics、Python 访问日志、健康检查 | Grafana 大盘、Agent Loop、评测平台、多模型路由 |
| **v0.9.0** | 把 RAG / SQL / 画像收成 Tool，Router Runtime + 工具步骤 Trace | ReAct、`/agent/runs`、步骤 SSE、多 Agent、MCP |
| **v1.0.0** | ContextBuilder、TokenBudget、记忆与压缩、输入/工具/输出 Guard、反馈 | 生产多环境、语义层 |
| **v1.1.0** | 评测集、回归报告、Prompt/模型版本与灰度 | 知识空间改造 |
| **v1.2.0** | 知识空间、异步解析、对象存储、检索权限过滤 | 生产压测与告警大盘 |
| **v1.3.0** | local/dev/staging/prod、备份、限流、压测、告警 | 平台级插件市场 |
| **v1.4.0+** | 语义层、MCP、多模型路由、插件化 | — |

## 分支与发布（文档 §5）

目标模型：

```text
main        稳定发布
develop     日常集成
feature/*   功能
bugfix/*    缺陷
release/*   预发
hotfix/*    紧急修复
```

发布：`feature` → PR 到日常集成 → CI + Review → staging → `release/*` → `main` → tag。

**仓库现状：** GitHub 日常分支仍是 `dev`，CI 监听 `main` / `dev`。下一版 feature 按 `feature/v1.1-*` 这类命名；改名为 `develop` 单独立项，避免打断现有 PR。

版本号：Semantic Versioning。MAJOR = 不兼容 API/数据；MINOR = 兼容新功能；PATCH = 修复。

发版勾选见 [release-checklist.md](release-checklist.md)。

## 版本纪律

1. 每个版本先按桌面文档的目标 / 非目标 / 验收，再改代码。
2. 行为变更必须有回归测试；文档与代码同一 PR。
3. 业务口径、权限模型、对外承诺有疑问时先停，不猜。
