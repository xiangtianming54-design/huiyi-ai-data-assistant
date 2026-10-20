# HuiYi v1.3 · Async Ingestion（仓库规格）

> 版本：`v1.3.0`  
> 状态：**实施中**（P0 D01–D13 已拍板）  
> 技术设计：[2026-10-20-v13-async-ingestion-technical.md](2026-10-20-v13-async-ingestion-technical.md)  
> 实施计划：[../plans/2026-10-20-v13-async-ingestion.md](../plans/2026-10-20-v13-async-ingestion.md)  
> 基线：`huiyi-ai-data-assistant v1.2.0`（标签 `v1.2.0`）  
> 路线图名称：桌面 §13 Async Ingestion & Object Storage。本版 **P0 只做上传立刻返回 + 本地落盘 + `DOC_PARSE` 异步 + 状态可查 + 失败重试**。  
> 对照：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§13；[roadmap.md](../../roadmap.md)。  
> 拍板：D01–D13，见 §8。改写或实施时 **不得推翻** 该表。  
> 剩下来的：对齐桌面 §13 未完成项，进 **小版本** 或后续 **大版本**（v1.4+）。

**改写原则（已拍板）：** 方向跟路线图「异步解析」走；MinIO、hash 去重、文档版本、任务列表接口、失败告警、前端 **不按桌面 §13 全文一次做完**。

---

## 1. 版本定位

把现网「上传请求里同步 parse + upsert」改成 **上传先落盘、立刻返回文档（含 `status`），后台线程池跑 `DOC_PARSE`**。客户端用现有文档详情/列表看 `UPLOADED` / `PARSING` / `READY` / `FAILED`，失败可 `retry`。

它不是：

- MinIO / 对象存储 profile；
- `file_hash` 去重、`doc_version`、独立 `parse_status` 列；
- `GET .../async-tasks`、MQ、进程重启后自动补偿；
- 任务类型 `DOC_DELETE` / `KNOWLEDGE_REINDEX` / `EMBEDDING_BACKFILL` / `EVAL_RUN` / `EXPORT_REPORT`；
- 知识管理前端或状态轮询 UI；
- ReAct、`/agent/runs`、Prompt 灰度、评测页。

现网入口仍是 `POST /api/v1/workspaces/{id}/chat/stream` 与 `.../analysis/query`。默认 Planner 仍是 Router。五工具名不变。知识空间 CRUD、RAG 按 ACTIVE 空间过滤、上传路径前缀 **保持 v1.2**。

---

## 2. P0 必须完成

1. **上传立刻返回**：`POST .../knowledge/upload?spaceId=` 在原文件落盘、插入文档行、写入一条 `DOC_PARSE` 任务并提交线程池后返回。请求线程 **不得** 调 Python parse/upsert。返回体仍是 `KnowledgeDocVO`，此时 `status=UPLOADED`。
2. **本地落盘**：HTTP 返回前必须把文件写到可配本地目录。路径按 workspace/doc 分子目录。文件保留到文档 `ARCHIVED` 再删。无 MinIO。
3. **状态机**：`knowledge_docs.status` 复用现列。排队=`UPLOADED`，解析中=`PARSING`（枚举补这项），成功=`READY`，失败=`FAILED`，归档=`ARCHIVED`。不新增 `parse_status`。
4. **`async_tasks`**（Flyway **V13**）：本版只写 `task_type=DOC_PARSE`。重试新开一条。HTTP **不** 暴露任务列表；客户端只看文档 `status`（及失败时的 `errorMessage`）。
5. **状态可查**：现有 `GET .../knowledge/docs` 与 `GET .../knowledge/docs/{id}` 返回 `status` 与可选 `errorMessage`（仅失败有值）。
6. **失败重试**：`POST .../knowledge/docs/{id}/retry`。仅 `FAILED` 可重试；权限与上传相同。其它状态 400。
7. **归档**：保持现网同步：本次请求删向量、`ARCHIVED`、删本地原文件。不实现 `DOC_DELETE`。
8. **评测**：不加新金标；现有 `eval-goldens` 必须继续过。异步用 Java 单测覆盖。
9. **审计**：上传成功入队仍记 `KNOWLEDGE_UPLOADED`；归档仍记 `KNOWLEDGE_ARCHIVED`。可增加重试动作名（实施计划钉）。

---

## 3. P0 明确不做

- MinIO / 对象存储 profile、延迟文件清理任务
- `file_hash` 去重、`doc_version`、`parse_status` 列
- 失败告警、任务超过 `max_attempts` 的自动告警
- 任务类型：`DOC_DELETE` / `KNOWLEDGE_REINDEX` / `EMBEDDING_BACKFILL` / `EVAL_RUN` / `EXPORT_REPORT`
- `GET .../async-tasks`、桌面 `GET .../knowledge-docs/{id}/status` 新前缀
- MQ / Kafka；定时扫表抢任务；进程重启后自动把 `UPLOADED`/`PARSING` 接着跑
- 前端：列表状态徽章、轮询、重试按钮
- ReAct、`/agent/runs`、Prompt 版本/灰度、评测页 / `/eval`、新评测表
- 改 `analysis/query` 协议、改 RAG/空间 API 契约

P1 / 小版本 / 后续大版本（只写不本周开工）：MinIO；hash 去重与版本；重启补偿；前端轮询；其余任务类型。

---

## 4. API

工作区前缀均为 `/api/v1/workspaces/{workspaceId}`。路径里的 `{workspaceId}` 与上下文必须一致，否则 403。

### 4.1 文档上传 / 查询 / 重试

| 方法 | 路径 | 谁 | 行为 |
|---|---|---|---|
| `POST` | `/knowledge/upload` | 写权限 | 可选 query `spaceId`。落盘 + 插文档 + 入队 `DOC_PARSE` 后立刻返回，`status=UPLOADED`。缺省空间仍写默认空间（v1.2）。向归档空间上传仍 400。 |
| `GET` | `/knowledge/docs` | 成员 | 现网筛选不变。`status` 含 `UPLOADED`/`PARSING`/`READY`/`FAILED`（未归档）。`errorMessage` 失败才有值。 |
| `GET` | `/knowledge/docs/{docId}` | 成员 | 同上，本工作区文档。 |
| `POST` | `/knowledge/docs/{docId}/retry` | 写权限 | 仅 `FAILED`：新开 `DOC_PARSE`，文档回到 `UPLOADED`。`UPLOADED`/`PARSING`/`READY`/`ARCHIVED` → 400。VIEWER → 现网 403。 |
| `PATCH` `/status` · `DELETE` | `/knowledge/docs/{docId}` | 写权限 | 现网归档：删向量 + `ARCHIVED` + **删本地原文件**。 |

不做：`GET .../async-tasks`；不做 `.../knowledge-docs/` 新前缀。

`KnowledgeDocVO` 增加 `errorMessage`（可为 null）。`knowledgeSpaceId` 保持 v1.2。

### 4.2 聊天 / 空间

不改。`chat/stream` 与 `knowledge.search` 的 `spaceIds`、空间 CRUD 维持 v1.2。未 `READY` 的文档本来就不在向量里，检索行为不变。

---

## 5. 数据与存储

### 5.1 `async_tasks`（逻辑结构，SQL 由实施计划钉为 V13）

```text
id BIGINT PK
task_type VARCHAR(64) NOT NULL          -- 本版只写 DOC_PARSE
workspace_id BIGINT NOT NULL
user_id BIGINT NOT NULL
resource_type VARCHAR(64)               -- knowledge_doc
resource_id VARCHAR(64)                 -- 文档 id
status VARCHAR(32) NOT NULL DEFAULT 'PENDING'   -- PENDING / RUNNING / SUCCEEDED / FAILED
attempts INT DEFAULT 0
max_attempts INT DEFAULT 1              -- 本版不自动重试；用户 retry 新开行
payload TEXT NULL                       -- 可空；禁止当租户键
error_message TEXT NULL
started_at DATETIME NULL
finished_at DATETIME NULL
created_at DATETIME
```

不加 MySQL 外键、不用级联。本版不写其它 `task_type`。

### 5.2 `knowledge_docs` 增强（同一 V13，不做 V14）

```text
storage_key VARCHAR(512) NULL
error_message TEXT NULL
```

不加 `file_hash`、`parse_status`、`doc_version`。存量行 `storage_key` 可空（旧同步上传的文档不能 retry 原文件，本版不回填二进制）。

### 5.3 本地文件

- 根目录可配（实施计划钉键名，默认 `./data/knowledge`）。
- `storage_key` 为相对根目录的路径，建议 `{workspaceId}/{docId}/{originalFilename}`。
- 上传时 MultipartFile 必须在返回前写完；之后 parse 只读该路径。
- 文档 `ARCHIVED` 时删除该文件；删除失败只打日志，不阻断归档（向量已删）。
- `.gitignore` 忽略该数据目录（实施计划钉）。

---

## 6. 状态与重试

```text
upload
  → 落盘 + insert UPLOADED + async_tasks PENDING + 提交线程池
  → HTTP 返回 UPLOADED
  → 池内：文档 PARSING，任务 RUNNING
  → parse + upsert（metadata 仍带 knowledge_space_id）
  → 成功：文档 READY，任务 SUCCEEDED，清空 error_message
  → 失败：文档 FAILED，任务 FAILED，写入 error_message
```

- 仅 `FAILED` 可 retry：新任务 PENDING，文档 `UPLOADED`，`error_message` 清空后再跑。
- `UPLOADED` / `PARSING` 禁止再入队（防双跑）。
- `READY` 再解析属后续 `KNOWLEDGE_REINDEX`，本版 400。
- 进程重启后卡住的 `UPLOADED`/`PARSING` **本版不恢复**。

---

## 7. 验收

- [ ] 上传接口在未完成 parse 时即返回，响应 `status=UPLOADED`；请求线程不调 Python parse/upsert。
- [ ] 后台完成后详情变为 `READY`，chunkCount 更新；失败为 `FAILED` 且 `errorMessage` 非空。
- [ ] `GET docs/{id}` 与列表能看到上述状态。
- [ ] `FAILED` 可 retry 后再次进入 `UPLOADED`→`PARSING`→`READY`/`FAILED`。
- [ ] 非 `FAILED` retry → 400，文案 `仅失败的文档可重试`。
- [ ] 原文件在本地按 `storage_key` 存在，直到归档被删。
- [ ] 归档仍删向量，并删除本地文件。
- [ ] 无 MinIO、无 `file_hash`/`doc_version`/`parse_status` 列、无 `GET async-tasks`。
- [ ] `git diff -- frontend` 空。
- [ ] 现有 `eval-goldens`（含 v1.2 空间金标）仍绿。
- [ ] `mvn -B test` 与 Python 现有测试仍绿。

---

## 8. 需求评审决策日志（已冻结）

拍板方式：表格逐项确认（仓库规则 `decision-table-confirm`）。下表是已选项，不是待选建议。

| 编号 | 决策项 | 已拍板 |
|---|---|---|
| D01 | v1.3 范围 | **B** P0：上传立刻返回 + 本地落盘 + `DOC_PARSE` 异步 + 状态可查 + 失败重试。MinIO、hash、`doc_version`、告警、`EVAL_RUN`/`EXPORT_REPORT` 不做 |
| D02 | HTTP | **B** 前缀不改。上传仍 `POST .../knowledge/upload?spaceId=` 立刻返回；状态用现有详情/列表；加 `POST .../knowledge/docs/{id}/retry`。不做 `GET .../async-tasks` |
| D03 | 文档状态 | **B** 不新增 `parse_status`。`UPLOADED`→`PARSING`→`READY`/`FAILED`；`ARCHIVED` 不变 |
| D04 | 异步任务落库 | **B** 建 `async_tasks`，本版只写 `DOC_PARSE`（重试新开一条）。HTTP 仍只看文档 `status` |
| D05 | 原文件存储 | **B** 本地目录；HTTP 返回前落盘；留到 `ARCHIVED` 再删。无 MinIO、无延迟清理任务 |
| D06 | 重试 | **B** 仅 `FAILED`；权限同上传；其它状态 400。不升版本号 |
| D07 | 执行器 | **B** 进程内有界线程池。无 MQ。重启不补偿 |
| D08 | 归档 | **B** 仍同步：删向量 + `ARCHIVED` + 删本地原文件。无 `DOC_DELETE` |
| D09 | 前端 | **B** 本版不改前端 |
| D10 | 库表 | **B** 仅 V13：`async_tasks` + `storage_key` + `error_message`。详情/列表返回 `errorMessage`。无外键 |
| D11 | 评测 | **B** 不加新金标；现有 `eval-goldens` 必须过 |
| D12 | 非目标 | **B** 见 §3 |
| D13 | 规格形态 | **B** 本文（短篇）；对照桌面 §13，不得推翻本表 |

依赖：

- D01=B + D12=B → 验收不得把 MinIO、hash、告警、任务列表写成必须完成。
- D02=B → 不得删除 `/knowledge/upload`，不得把 `/knowledge-docs/` 写成 P0 入口。
- D03=B → 禁止新增 `parse_status` 列；必须有 `PARSING` 可被详情读到。
- D05=B + D08=B → 归档必须删本地文件；成功解析后不得立刻删原文件（否则 retry 无文件）。
- D06=B → `READY`/`PARSING` retry 必须 400。
- D07=B → 禁止引入 MQ；禁止本版做扫表补偿。
- D09=B → 禁止本版改 `frontend/` 功能。
- D10=B → 禁止 V14；禁止 `file_hash` / `doc_version`。
- D11=B → 禁止为异步解析加金标文件；禁止关掉 `eval-goldens`。
