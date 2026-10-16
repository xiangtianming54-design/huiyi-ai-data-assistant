# v0.6 基线说明

日期：2026-10-16  
对应标签：`v0.6.0`  
仓库：https://github.com/xiangtianming54-design/huiyi-ai-data-assistant

本文记录 **v0.6 已交付能力**，作为 v0.6.1 之后所有迭代的对照基线。后续版本只在此之上增量，不把已完成能力再包装成新里程碑。

## 1. 产品定位（当前）

会意（HuiYi）当前是 **企业经营数据智能分析助手**：Java 主服务编排 + Python Sidecar 检索/校验，支持知识库问答与只读数据分析。

演进目标见桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》：在现有能力上做成企业级 Agent Harness，而不是推翻重写。v0.6 **还不是** 该平台：无用户/工作区/RBAC，无 Tool Registry / Agent Loop，无系统化 Trace / 成本，无评测集。

## 2. 已交付能力

| 能力 | 状态 | 说明 |
|---|---|---|
| 知识库上传 / 解析 / 分块 / 向量入库 | 已交付 | Markdown / PDF / Word；段落优先分块 |
| RAG 问答（SSE） | 已交付 | 引用按文件去重；无命中确定性兜底，不编造口径 |
| 混合检索 | 已交付（v0.6） | 仅 `huiyi_knowledge`：向量 + BM25 字符 bigram + RRF（k=60）；`intent_examples` 仍纯向量 |
| Text-to-SQL | 已交付 | 四道防线：意图动词拦截 → sqlglot AST → `huiyi_ro` 只读执行 → 5s / 500 行 / 审计 |
| 统一对话入口 | 已交付（v0.2） | 知识走 SSE；数据查询在气泡内嵌 SQL / 表 / 图 |
| 多轮改写 | 已交付（v0.3） | RAG 与 Text-to-SQL 共用改写 |
| 意图识别 | 已交付（v0.4） | 规则 → embedding 示例句投票 → LLM 兜底；**不是** Tool Calling |
| 复合问题分解 | 已交付（v0.5） | 最多拆 2 路并行 |
| 误拆去重 | 已交付（v0.5.1） | Prompt 约束 + 主题合并 + embedding cosine ≥ 0.85 |
| 近 10 轮 + 会话摘要 | 已交付（v0.6） | 最近约 20 条原文；更早轮次写入 `chat_session_summaries`；摘要失败不阻断问答 |
| 五场景演示 | 已交付 | 见 [demo.md](demo.md) |
| Compose 一键启动 | 已交付 | MySQL / Redis / Java / Python / 前端 |
| GitHub Actions CI | 已交付 | `main` / `dev` 的 push 与 PR：`mvn verify`、`pytest --cov`、`npm run build` |

## 3. 当前主要缺口（文档 §2.2；不要当 v0.6 缺陷）

| 问题 | 计划版本 |
|---|---|
| 缺少用户、工作区、RBAC；资源无明确归属 | v0.7.0（已交付，标签 `v0.7.0`） |
| 无统一 Tool Registry / Agent Loop | v0.9.0 |
| 无系统化 Trace / Metrics / Cost | v0.8.0 |
| 无评测集 / 回归报告；Prompt 散落在资源文件 | v1.1.0 |
| Docker Compose 偏本地；无 staging / prod、备份、回滚 | v1.3.0 |
| 无租户 / 配额；知识空间与权限过滤未完成 | v1.2.0 / v1.3.0 |

v0.6.1 **明确不做**：用户体系、租户、重构 `ChatService`、切换向量库、Kubernetes。

## 4. 仓库与发布

| 项 | 记录 |
|---|---|
| 默认分支 | `main`（含 v0.6 能力） |
| 基线标签 | `v0.6.0` |
| 许可证 | MIT |
| 密钥 | `.env` 已在 `.gitignore`；入库只用 `.env.example` 空模板 |
| Git 提交作者 | 本仓库今后提交 `user.name=xianglonghai`（仅今后提交，不改写已推送历史） |

## 5. 健康检查

服务启动后：

```bash
curl http://localhost:8080/api/health   # Java → {"code":0,"message":"ok"}
curl http://localhost:8001/health       # Python → {"status":"ok"}
```

前端：http://localhost:5173

## 6. 演示场景（已记录）

完整步骤与截图见 [demo.md](demo.md)。自动化：`node scripts/e2e-m5.js`。

| # | 场景 | 提问 |
|---|---|---|
| 1 | 业务口径问答（RAG） | 什么是有效订单？ |
| 2 | 销售额排名（Text-to-SQL） | 上个月销售额最高的 10 个商品是什么？ |
| 3 | 渠道分析 | 华东地区各渠道的有效订单量是多少？ |
| 4 | 安全拦截 | 删除所有销售订单。 |
| 5 | 知识库未命中兜底 | 公司年假有多少天？ |

## 7. 测试覆盖（基线快照）

以 CI 与本地命令为准，本快照来自 v0.6 开源就绪记录：

| 端 | 命令 | 基线 |
|---|---|---|
| Python | `pytest --cov=app --cov-report=term-missing`（`python-service`） | 约 97% 行覆盖 |
| Java | `mvn -B verify`（`backend`） | 56+ 单元测试（含 v0.6 混合检索与会话摘要用例） |
| 前端 | `npm run build` | 构建通过（无独立单测门槛） |

合并 PR 前至少保证上述三条 CI job 绿灯。新代码应带回归测试；不把覆盖率数字当成产品功能。

## 8. 关键设计文档

- [architecture.md](architecture.md) — 分层与数据流
- [api.md](api.md) — 接口契约
- [development.md](development.md) — 本地开发与 v0.2–v0.6 变更
- [pitfalls.md](pitfalls.md) — 开发踩坑（现象 / 原因 / 处理 / 以后）
- [v0.6-design.md](v0.6-design.md) — 混合检索与会话压缩
- [roadmap.md](roadmap.md) — v0.6.1 起的版本路线
- [release-checklist.md](release-checklist.md) — 发版核对

## 9. 当前优先版本

**v0.6.1 Baseline Hardening**（3–5 天）：只补文档、模板、基线记录与仓库卫生，**不改产品行为**。完成后按 [roadmap.md](roadmap.md) 进入 **v0.7.0 Enterprise Foundation**（用户、工作区、RBAC、审计、API v1、Flyway，约 2–3 周）。
