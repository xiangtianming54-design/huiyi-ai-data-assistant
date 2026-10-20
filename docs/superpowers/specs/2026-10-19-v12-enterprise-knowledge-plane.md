# HuiYi v1.2 · Enterprise Knowledge Plane（仓库规格）

> 版本：`v1.2.0`  
> 状态：**已发布**（标签 `v1.2.0`；P0 D01–D13 已拍板）  
> 技术设计：[2026-10-19-v12-enterprise-knowledge-plane-technical.md](2026-10-19-v12-enterprise-knowledge-plane-technical.md)  
> 实施计划：[../plans/2026-10-19-v12-enterprise-knowledge-plane.md](../plans/2026-10-19-v12-enterprise-knowledge-plane.md)  
> 基线：`huiyi-ai-data-assistant v1.1.0`（标签 `v1.1.0`）  
> 路线图名称：Enterprise Knowledge Plane。本版 **P0 只做工作区内知识空间 + 文档归属 + RAG 按空间过滤**。  
> 对照：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§12；[roadmap.md](../../roadmap.md)。  
> 拍板：D01–D13，见 §8。改写或实施时 **不得推翻** 该表。  
> 剩下来的：对齐桌面 §12 未完成项，进 **小版本** 或后续 **大版本**（v1.3+）。

**改写原则（已拍板）：** 方向跟路线图「知识空间、检索权限过滤」走；异步解析、对象存储、细权限码、前端空间 UI **不按桌面 §12 全文一次做完**。

---

## 1. 版本定位

把现网「工作区下一堆文档」升级成 **工作区内可管理的知识空间**：文档必属一个空间，检索可按空间收窄，未指定时搜该工作区全部 ACTIVE 空间。

它不是：

- 独立于工作区的知识产品；
- 异步解析 / 对象存储 / hash 去重 / 文档版本；
- 新的细权限码或空间成员表；
- 知识管理前端或聊天选空间 UI；
- ReAct、`/agent/runs`、Prompt 灰度、评测页。

现网入口仍是 `POST /api/v1/workspaces/{id}/chat/stream` 与 `.../analysis/query`。默认 Planner 仍是 Router。五工具名不变。上传仍 **同步** 走 Python sidecar（现网行为）。

---

## 2. P0 必须完成

1. **表 `knowledge_spaces`**（Flyway 下一号，现网最后是 `V11`，本版文件名实施计划钉为 `V12__...`）：
   - 字段：`id, workspace_id, name, description, status, created_by, created_at, is_default`
   - `status`：`ACTIVE` / `ARCHIVED`
   - `is_default`：每工作区恰好一条为真，由应用保证；**不加 MySQL 外键、不用级联**
   - 本版不做 `visibility=PRIVATE`；可见范围 = 该工作区成员
2. **默认空间**：每个工作区确保一个默认空间（没有则创建，名称「默认」）。存量 `knowledge_docs.knowledge_space_id` 为空的回填到该空间，随后该列 **NOT NULL**。
3. **空间 API**（挂在工作区下，见 §4）。
4. **文档归属**：现有 `/knowledge/docs*` 保留；上传可带 `spaceId`；旧 `/knowledge/upload` 不带则写入默认空间，本版 **不删**。
5. **检索过滤**：
   - `chat/stream` 请求体与 `knowledge.search` 增加可选 `spaceIds`
   - 不传 = 该工作区全部 ACTIVE 空间
   - 传入 = 只搜所列且属于本工作区的 **ACTIVE** 空间
   - 非法 / 跨工作区 / 已归档 id **不得命中**，不回退成「搜全部」
   - `analysis/query` 不改
   - `workspaceId` / `userId` 仍禁止出现在工具入参；租户只从 `WorkspaceContextHolder` 取
6. **Python 向量 + BM25**：chunk metadata 增加 `knowledge_space_id`；检索带 `workspace_id` + 空间 id 列表。新上传写入；存量按文档表回填。Java **不得**只靠召回后再滤来保证隔离。
7. **评测**：`testdata/eval/` 增加空间金标，走现有 `EvalGoldenRunner` 与 CI job `eval-goldens`。现有 12 条保留。夹具仍禁止 `workspaceId` / `userId`。
8. **审计**：空间创建 / 改名 / 归档写入现有 `audit_events`（动作名实施计划钉）。文档上传 / 归档审计保留。

---

## 3. P0 明确不做

- 异步解析、`status` / `retry` 文档 API、`async_tasks` / `ingest_jobs`
- 对象存储、`storage_key`、删文件落盘以外的 OSS 清理
- `file_hash` 去重、文档版本
- 细权限码、空间成员、`visibility=PRIVATE`
- 前端：空间管理、知识页空间下拉、聊天选空间（现有 `KnowledgeView` 继续走旧上传 → 默认空间）
- 空间恢复接口、硬删空间、归档空间时级联归档/删除文档或向量
- 任务失败告警
- ReAct、`/agent/runs`、Prompt 版本/灰度、评测页 / `/eval`、新评测表
- 改 `analysis/query` 协议

P1 / 小版本 / 后续大版本（只写不本周开工）：异步解析与对象存储；细权限；前端空间选择器；空间恢复；桌面 §12.7 其余验收项。

---

## 4. API

工作区前缀均为 `/api/v1/workspaces/{workspaceId}`。路径里的 `{workspaceId}` 与上下文必须一致，否则 403。

### 4.1 空间

| 方法 | 路径 | 谁 | 行为 |
|---|---|---|---|
| `POST` | `/knowledge-spaces` | OWNER / ADMIN / EDITOR | 创建非默认空间；`is_default=0`，`status=ACTIVE` |
| `GET` | `/knowledge-spaces` | 成员（含 VIEWER） | 只列本工作区 `ACTIVE` 空间 |
| `PATCH` | `/knowledge-spaces/{spaceId}` | OWNER / ADMIN / EDITOR | 改 `name` / `description`；可改默认空间的名称，不得改 `is_default` |
| `PATCH` | `/knowledge-spaces/{spaceId}/status` | OWNER / ADMIN / EDITOR | 仅非默认空间可改为 `ARCHIVED`；默认空间 → 业务错误，不得归档 |

归档后：默认列表与 RAG 不再出现该空间；其下文档状态与向量 **不变**；指定已归档 `spaceId` 检索或列表视为无命中 / 空列表。本版无恢复接口。

不做桌面嵌套上传路径 `.../knowledge-spaces/{spaceId}/documents/upload`（留给后期，避免废掉现网入口）。

### 4.2 文档（现网路径保留）

| 方法 | 路径 | 谁 | 行为 |
|---|---|---|---|
| `POST` | `/knowledge/upload` | 写权限 | 可选 `spaceId`（form 或 query，实施计划钉一种）；缺省 → 默认空间；必须属于本工作区且 ACTIVE，否则业务错误。仍同步解析 + 写入向量（metadata 含 `knowledge_space_id`） |
| `GET` | `/knowledge/docs` | 成员 | 可选 `spaceId`。缺省：ACTIVE 空间下、文档未归档。指定 ACTIVE 空间：该空间未归档文档。指定已归档 / 外区 id：空列表，不串区 |
| `GET` | `/knowledge/docs/{docId}` | 成员 | 本工作区文档；响应带 `knowledgeSpaceId` |
| `PATCH` `/status` · `DELETE` | `/knowledge/docs/{docId}` | 写权限 | 现网归档语义不变（文档 `ARCHIVED` + 删该文档向量） |

`KnowledgeDocVO` 增加 `knowledgeSpaceId`（及列表筛选所需最小字段）。不改分析接口。

### 4.3 聊天检索

`ChatRequestDTO` 增加可选 `spaceIds`（`List<Long>`）。`knowledge.search` JSON Schema 增加可选 `spaceIds` 数组；`additionalProperties` 仍为 false；**不得**把 `workspaceId` / `userId` 放进 Schema。

有效空间列表：

1. HTTP `spaceIds` 与工具 `spaceIds`：缺省方视为「本工作区全部 ACTIVE」。
2. 两边都有 → **交集**。
3. 结果只保留当前工作区 ACTIVE 空间。
4. 过滤后为空 → 检索无命中（沿用现网 RAG 无命中句 `RagService.NO_HIT_FALLBACK`），**不**扩大为搜全部。

---

## 5. 数据与向量

### 5.1 `knowledge_spaces`（逻辑结构，SQL 由实施计划钉）

```text
id BIGINT PK
workspace_id BIGINT NOT NULL
name VARCHAR(128) NOT NULL
description VARCHAR(255) NULL
status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
is_default TINYINT NOT NULL DEFAULT 0
created_by BIGINT NOT NULL
created_at DATETIME
```

索引建议：`(workspace_id, status)`；名称在工作区内 uniqueness 由实施计划钉（应用层或唯一索引，禁止外键）。

### 5.2 文档列

回填后 `knowledge_docs.knowledge_space_id BIGINT NOT NULL`。不加 `file_hash` / `storage_key` / `parse_status`。

### 5.3 向量 / BM25

- upsert：每个知识 chunk 的 metadata 必须有 `workspace_id`、`doc_id`、`knowledge_space_id`（与文档表一致，禁止混空间）。
- search：`workspace_id` 必填；`knowledge_space_ids` 为 id 列表，**必须**在向量与关键词两侧同时过滤。
- 存量：按 `doc_id` 从文档表回填 metadata；交付验收时，旧文档按空间过滤必须生效。
- 仍一个默认 collection（`huiyi_knowledge`），**不**按空间拆 collection。
- 归档空间：不把该空间 id 放入检索列表即可；不删向量。归档 **文档** 仍删该文档向量（现网）。

---

## 6. 评测（D11）

夹具继续放 `testdata/eval/*.json`。空间用 **标签** 声明（如 `spaces: ["default"]` / `["extra"]`），由 runner 注入真实 id；**禁止**夹具写死 `workspaceId` / `userId` / 数字 `knowledgeSpaceId`。

至少覆盖（文件名实施计划钉）：

| 场景 | 期望 |
|---|---|
| 同工作区两空间 | 指定 A 不得出现 B 的陷阱内容 |
| 不传 `spaceIds` | 可命中多空间内容 |
| 指定一空间 | 只命中该空间 |
| 归档空间 | 不命中（含指定其 id） |
| 跨工作区 spaceId | 不串区 |

现有五场景 / Agent 金标 / 工作区隔离用例保持通过。判过规则仍走 v1.1 结构化对照。

---

## 7. 验收

- [ ] 新工作区（或迁移后）有且仅有一个默认空间；默认空间不能归档。
- [ ] 存量文档均有 `knowledge_space_id`；旧 `/knowledge/upload` 仍可用并落入默认空间。
- [ ] 可创建第二个 ACTIVE 空间并向其上传；列表可按 `spaceId` 分开。
- [ ] 未传 `spaceIds` 的 `chat/stream` 能检索到两空间文档；只传空间 A 时看不到空间 B。
- [ ] 归档空间 A 后，默认检索与指定 A 均无 A 的命中；A 下文档行仍在且未自动 `ARCHIVED`。
- [ ] 跨工作区 `spaceId` 无命中。
- [ ] Python 检索请求带空间列表；仅 Java 事后过滤的实现视为不合格。
- [ ] `eval-goldens` 含本版空间金标且失败挡合并；现有 12 条仍绿。
- [ ] `git diff -- frontend` 空（或仅无意改动，P0 禁止功能改前端）。
- [ ] 无 `async_tasks`、无对象存储列、无 `/agent/runs`、无评测页。
- [ ] `mvn -B test` 与 Python 相关测试仍绿。

---

## 8. 需求评审决策日志（已冻结）

拍板方式：表格逐项确认（仓库规则 `decision-table-confirm`）。下表是已选项，不是待选建议。

| 编号 | 决策项 | 已拍板 |
|---|---|---|
| D01 | v1.2 范围 | **B** P0：工作区内知识空间 + 上传/列表/归档 + RAG 按空间过滤。异步解析、对象存储、细权限码 → P1 / 小版本 / 后续大版本 |
| D02 | 空间 API 怎么挂 | **B** `POST/GET .../knowledge-spaces`；文档仍走 `/knowledge/docs*`；上传带 `spaceId`；旧 `/knowledge/upload` 写入默认空间且不删；`status`/`retry` 不进 P0 |
| D03 | 默认空间与存量 | **B** 每工作区确保一个默认空间；空列回填；旧上传不带 `spaceId` → 默认空间；默认空间本版不可删除/归档 |
| D04 | 未指定空间时检索 | **B** 不带则搜该工作区全部 ACTIVE；带则只搜所列且属于本工作区的空间；跨区不串 |
| D05 | 空间 id 怎么传 | **B** `chat/stream` 与 `knowledge.search` 可选 `spaceIds`；`analysis/query` 不改 |
| D06 | 空间 RBAC | **A** 与文档写权限同一套：OWNER / ADMIN / EDITOR 可建/改/归档非默认空间；VIEWER 可列表、可检索。无新权限码 |
| D07 | 归档空间连带 | **B** 空间只改 `ARCHIVED`；文档与向量保留；已归档空间退出默认列表与 RAG；本版无恢复接口 |
| D08 | 前端 | **B** 本版不改前端 |
| D09 | 过滤落点 | **B** Python 向量 + BM25 metadata 带 `knowledge_space_id` 并按列表过滤；存量回填；不得只靠 Java 事后滤 |
| D10 | 数据落点 | **B** 新表 `knowledge_spaces`（含 `is_default`）；回填后 `knowledge_space_id` NOT NULL；不建异步/OSS/hash 列 |
| D11 | 评测 | **B** `testdata/eval/` 增加空间金标；现有 runner / `eval-goldens` |
| D12 | 非目标 | **B** 见 §3 |
| D13 | 规格形态 | **B** 本文（短篇）；对照桌面 §12，不得推翻本表 |

依赖：

- D01=B + D12=B → 规格验收不得把异步解析、OSS、告警写成必须完成。
- D02=B → 不得删除 `/knowledge/upload`，不得把嵌套上传路径写成 P0 唯一入口。
- D03=B + D07=B → 默认空间归档必须失败；非默认空间归档不得级联删文档。
- D04=B + D05=B → 不传 `spaceIds` 必须仍能知识问答。
- D08=B → 禁止本版改 `frontend/` 功能。
- D09=B → Python sidecar 本版 **必须** 改检索/写入契约。
- D10=B → 禁止 Flyway 建 `async_tasks`。
- D11=B → 空间隔离必须进 `eval-goldens`，夹具禁止租户键与数字空间 id。
