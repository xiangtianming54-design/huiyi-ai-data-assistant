# HuiYi v0.8 · Observability & Cost 开发文档

> 版本：v0.8.0  
> 状态：待评审（v0.7.0 已发布后起草）  
> 基线：`huiyi-ai-data-assistant v0.7.0`（标签 `v0.7.0`）  
> 来源：[roadmap.md](../../roadmap.md) v0.8.0；[v0.7 开发文档](HuiYi-v0.7-Enterprise-Foundation-Development-Document-v2.md) §8.6 / §21  

---

## 1. 意图

v0.7 有了登录、工作区和审计表，但一次问答仍然很难回答三件事：**这次请求的 ID 是什么、链路在哪一层失败、这次 LLM 花了多少 token**。v0.8 只补可观测与成本，不把 RAG/SQL 收成 Tool，不上 Agent Loop。

## 2. 目标

- 每个进入 Java 的 HTTP 请求有稳定 `request_id`（可从请求头传入，否则服务端生成），响应头回写；审计行写入已有 `audit_events.request_id`。
- Java 调 Python、调 LLM 时带上同一 `request_id`；Python 日志能对上。
- 结构化日志（至少：时间、level、request_id、workspace_id、user_id、logger、message）。
- 问答 / 分析 / 改写 / 意图 等 LLM 调用记录：模型、prompt/completion token、耗时、成败。
- 健康检查能反映 MySQL、Redis、Python sidecar 是否可达（细节不泄露密钥）。
- Java→Python 调用有共享密钥或等价服务间认证，拒绝无凭证的 8001 调用。

## 3. 非目标

- Agent Loop、Tool Registry、把 `ChatService` 拆成 Runner（v0.9）。
- 评测集、Prompt 灰度平台、多模型路由（v1.1 / 更后）。
- staging/prod 多环境、备份编排、限流大盘（v1.3）。
- 补做 v0.7 P1：用户状态 API、前端工作区切换 / 会话列表 UI。
- 换向量库、K8s、拆分 `sales_orders` 租户表。

## 4. 反目标

- 没有 request_id 的新审计事件（健康检查除外）。
- Python 对公网开放且无服务间凭证。
- 把 token 用量写进普通 INFO 日志明文 prompt。
- 为了指标引入第二套用户体系。

## 5. 约束

- 继续 Java 主编排 + Python Sidecar；8001 仍只绑 `127.0.0.1`（或生产不映射）。
- `audit_events.request_id` 列已存在，先接线，不另起一张「请求表」除非现表不够。
- 密码、JWT、工作区隔离行为与 v0.7 保持兼容；本版不改 RBAC 语义。
- 日志与成本数据不得写入 git；密钥仍只走环境变量。
- 前端、e2e 若依赖健康检查 JSON 形状，需同步；五场景仍须通过。

## 6. 范围拆分（建议实现顺序）

1. **Request ID**：Filter 解析/生成 `X-Request-Id`，放入 MDC 与 `WorkspaceContext` 旁路；响应头回写；`AuditService.record` 带上。
2. **结构化日志**：Logback JSON 或 key=value 统一格式；本地仍可读。
3. **Python 透传**：Java WebClient 默认头带 `X-Request-Id`；Python 打同一字段；缺少服务间密钥时 401。
4. **LLM 成本**：在 `LlmService`（及 embedding 调用）记一次 usage；落表或落结构化日志，需可按 request_id 查出。
5. **健康检查增强**：`/api/health` 增加 mysql / redis / python 探测结果，失败不抛栈给匿名用户以外的细节。
6. **Prompt 版本化基础**：现有 classpath 模板增加稳定 `prompt_id` + 文件哈希或版本号，写入 LLM 成本记录；不做管理 UI。

## 7. 验收（草案）

```text
[ ] 一次 /api/v1/... 调用响应头有 X-Request-Id；
[ ] 同一次问答的 Java 日志、Python 日志、审计行 request_id 一致；
[ ] 无服务间凭证调 Python 核心接口失败；
[ ] 一次知识问答能查出该次 LLM token 用量；
[ ] 停 MySQL 或 Python 时健康检查能区分依赖失败；
[ ] mvn verify / pytest / frontend build / e2e-m5 仍通过；
[ ] 不降低工作区隔离与未登录 401。
```

## 8. 明确留给 v0.9+

Tool Registry、Agent Trace、ReAct、SSE Agent 事件。v0.8 的 Trace 是 **请求级调用链**（Java→Python→LLM），不是 Agent Run Trace。
