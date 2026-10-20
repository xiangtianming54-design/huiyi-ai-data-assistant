# HuiYi v1.3 · Async Ingestion 技术设计

> 版本：`v1.3.0`  
> 状态：**已发布**（标签 `v1.3.0`）  
> 产品规格：[2026-10-20-v13-async-ingestion.md](2026-10-20-v13-async-ingestion.md)  
> 基线代码：`v1.2.0`（标签 `v1.2.0`）  
> 口径：D01–D13。本文只写 **怎么改代码**，不重新开范围。

P1 / 小版本（MinIO、hash、重启补偿、前端轮询）只在 §10 留接口，本周不开工。

---

## 1. 设计目标与差量

在 **不换聊天/分析入口、不改前端、不改 Python 向量契约、不改空间 API** 的前提下：

1. 上传 HTTP 在落盘 + 写库 + 入队后返回 `UPLOADED`；
2. 有界线程池跑 parse + upsert；文档 `PARSING` → `READY`/`FAILED`；
3. `FAILED` 可 retry；归档同步删向量和本地文件。

| 桌面 §13 说法 | 本版技术落点 |
|---|---|
| 上传即返回 PENDING | 返回现有 `status=UPLOADED`（不新增 PENDING 文档状态） |
| `parse_status` PENDING/RUNNING/READY/FAILED | **不上**；枚举补 `PARSING` |
| `GET .../knowledge-docs/{id}/status` | **不上**；用现有 `GET .../knowledge/docs/{id}` |
| `GET .../async-tasks` | **不上**；表只给后端 |
| StorageClient + MinIO profile | 仅本地磁盘实现 |
| V13 async_tasks + V14 文档列 | **只 V13**；文档只加 `storage_key`/`error_message` |
| hash / doc_version / 告警 | 不上 |
| 删除延迟清理 | 归档当次删本地文件 |
| 线程池、不引入 MQ | 采用 |

禁止平行再造一套 RAG。parse/upsert 仍走 `PythonServiceClient` → 现有 `/api/documents/parse` 与 `/api/vector/upsert`。Python sidecar **本版不改检索契约**。

---

## 2. 现网基线（代码事实）

| 类 / 文件 | 本版角色 |
|---|---|
| `KnowledgeV1Controller` | `/knowledge/upload\|docs` 保留；**加** `POST /docs/{id}/retry` |
| `KnowledgeDocService.upload` | 今日请求内 `parseDocument` + `upsertVectors`；本版改为落盘+入队 |
| `KnowledgeDocService.archiveDoc` | 今日删向量 + `ARCHIVED`；本版再删本地文件 |
| `PythonServiceClient.parseDocument(MultipartFile)` | 工作线程改为从磁盘读文件再调；可保留 Multipart 重载或加 `Path` 重载 |
| `DocStatusEnum` | `UPLOADED, PARSED, READY, FAILED, ARCHIVED`；实体注释已写 `PARSING` 但枚举没有。本版 **加 `PARSING`**，`PARSED` 保留不在主路径使用 |
| `KnowledgeDoc` | 无 `storageKey`/`errorMessage` 字段 |
| `KnowledgeDocVO` | 无 `errorMessage` |
| `FlywayMigrationFilesTest` | **断言不含 `async_tasks`** → 本版必须改成 **必须含** |
| Flyway | 最后一号 `V12__knowledge_spaces.sql` |
| 前端 | 本版 `git diff -- frontend` 必须空 |

冻结句（不得改字）：

- `RagService.NO_HIT_FALLBACK`
- `RagService.SEARCH_DEGRADED_FALLBACK`
- v1.0 未知工具句 / 超限句 / 修改拒绝句
- v1.2 `默认知识空间不可归档` / `不能向已归档空间上传` / `知识空间不存在或不属于当前工作区` / `知识空间名称已存在`
- 现网 VIEWER 写知识：`只读成员不能上传或归档知识库`

---

## 3. 目标调用链

### 3.1 上传（请求线程）

```text
POST /knowledge/upload?spaceId=
  → requireKnowledgeWrite
  → KnowledgeSpaceService.requireActiveSpace(...)     // v1.2 不变
  → insert knowledge_docs status=UPLOADED, chunkCount=0
  → LocalFileStorageClient.save(workspaceId, docId, file) → storage_key
  → update storage_key
  → insert async_tasks DOC_PARSE PENDING
  → knowledgeParseExecutor.execute(DocParseJob)
  → audit KNOWLEDGE_UPLOADED
  → return VO status=UPLOADED
```

请求线程 **verify never** `parseDocument` / `upsertVectors`。

落盘失败：文档标 `FAILED` + `error_message`，不入队，HTTP **500**（没有原文件则 retry 无意义）。  
线程池拒绝：文档与任务标 `FAILED`，仍返回 VO（此时已是失败态）。

### 3.2 工作线程 `DocParseJob`

```text
任务 RUNNING，attempts += 1，started_at=now
文档 status=PARSING，error_message=null
Path = storage.resolve(storage_key)
parseDocument(path, filename)
upsertVectors(result, workspaceId, docId, spaceId)
文档 READY，chunkCount，error_message=null
任务 SUCCEEDED，finished_at=now
```

任意异常：

```text
文档 FAILED，error_message=截断后的异常信息
任务 FAILED，error_message 同样，finished_at=now
不再自动重试（max_attempts=1）
```

### 3.3 重试

```text
POST .../knowledge/docs/{id}/retry
  → requireKnowledgeWrite
  → 文档本区存在
  → status!=FAILED → 400「仅失败的文档可重试」
  → storage_key 空或文件不存在 → 400「原文件不存在，无法重试」
  → status=UPLOADED，error_message=null
  → 新 async_tasks DOC_PARSE PENDING
  → 提交线程池
  → 返回 VO（UPLOADED）
```

审计：`KNOWLEDGE_RETRY`。

### 3.4 归档

```text
现网删向量 + ARCHIVED
  → LocalFileStorageClient.delete(storage_key)  // 失败只打日志
```

不创建 `DOC_DELETE` 任务。

---

## 4. Java 结构

新类型放现有包，不新 Maven 模块。

| 类型 | 职责 |
|---|---|
| `LocalFileStorageClient` | `String save(long workspaceId, long docId, MultipartFile file)`；`Path resolve(String storageKey)`；`void delete(String storageKey)` |
| `AsyncTask` / `AsyncTaskMapper` | 表 `async_tasks` |
| `AsyncTaskService` | `enqueueDocParse(...)` 插 PENDING 行 |
| `DocParseJob` | `Runnable`：状态迁移 + parse + upsert |
| `KnowledgeParseExecutorConfig` | `ThreadPoolTaskExecutor` Bean 名 `knowledgeParseExecutor` |
| `KnowledgeV1Controller` | 加 retry 映射 |

`KnowledgeDocService` 构造增加 `LocalFileStorageClient`、`AsyncTaskService`、`TaskExecutor`。单元测试用 **不执行** 的 mock `TaskExecutor` 测「立刻返回」；用 `Runnable::run` 或捕获后手动 `run()` 测工作线程。

### 4.1 线程池

```text
spring bean knowledgeParseExecutor
  core=huiyi.knowledge.parse-pool-size（默认 2）
  max=同 core
  queueCapacity=100
  threadNamePrefix=doc-parse-
  rejected: AbortPolicy → 调用方标 FAILED
```

禁止 `@Async` 无界默认池。禁止引入 Redis Stream / Rabbit。

### 4.2 `PythonServiceClient`

新增：

```text
ParseResultDTO parseDocument(Path file, String filename)
```

内部仍 POST multipart `/api/documents/parse`。现有 `parseDocument(MultipartFile)` 可委托到读 bytes，避免工作线程依赖已释放的 multipart。

### 4.3 错误文案（用户可见）

| 情况 | HTTP | 文案 |
|---|---|---|
| 非 FAILED 重试 | 400 | `仅失败的文档可重试` |
| 无原文件重试 | 400 | `原文件不存在，无法重试` |
| VIEWER 重试 | 403 | `只读成员不能上传或归档知识库` |
| 落盘失败 | 500 | `保存上传文件失败` |

`errorMessage` 写入 VO 的失败原因可用来自 Python/异常的 `getMessage()`，截断 1000 字（与 `AssistantRunRecorder` 同口径）。

### 4.4 审计

`AuditAction` 增加 `KNOWLEDGE_RETRY`。`resourceType=knowledge_doc`。

---

## 5. Flyway `V13__async_ingestion.sql`

禁止 `FOREIGN KEY`、禁止 `ON DELETE CASCADE`。`FlywayMigrationFilesTest`：

- **必须** `sql.contains("async_tasks")`
- **必须** `storage_key` 与 `knowledge_docs` 的 `error_message`（可用列名断言）
- **禁止** `FOREIGN KEY`
- **禁止** `file_hash`、`parse_status`、`doc_version`（防止误把 V14 搬进来）
- 仍禁止 `agent_runs` / `tool_invocations`

```sql
CREATE TABLE async_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_type VARCHAR(64) NOT NULL,
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  resource_type VARCHAR(64) NULL,
  resource_id VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 1,
  payload TEXT NULL,
  error_message TEXT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_async_ws_type (workspace_id, task_type),
  INDEX idx_async_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE knowledge_docs
  ADD COLUMN storage_key VARCHAR(512) NULL,
  ADD COLUMN error_message TEXT NULL;
```

`payload`/`result` JSON 类型桌面有；本版用 `TEXT` 或干脆不建 `result`（YAGNI）。不要为未实现的任务类型预插入数据。

存量 `storage_key` 保持 NULL：旧 READY 文档不能按文件 retry。

---

## 6. 配置与本地文件

`application.yml`（local/docker 同样可读环境变量）：

```yaml
huiyi:
  knowledge:
    backfill-vector-space-ids: true
    storage-dir: ${HUIYI_KNOWLEDGE_STORAGE_DIR:./data/knowledge}
    parse-pool-size: ${HUIYI_KNOWLEDGE_PARSE_POOL_SIZE:2}
```

仓库根或 `backend/` 的 `.gitignore` 增加 `data/knowledge/`（以实施时实际根为准）。Docker 卷本版不做 MinIO，本地目录即可；compose 不强制挂新 volume（P1）。

PowerShell：Maven cwd `backend/`。禁止 `&&`。多测试 `"-Dtest=A,B"`。

---

## 7. 测试矩阵（TDD 顺序）

| 测试 | 断言 |
|---|---|
| `LocalFileStorageClientTest` | save 后文件存在；delete 后不存在；resolve 不逃出根目录（`..` 拒绝） |
| `FlywayMigrationFilesTest` | 有 `async_tasks`；有 `storage_key`；无 FK；无 `file_hash`/`parse_status`/`doc_version` |
| `KnowledgeDocServiceUploadSpaceTest` | upload **不**调 python；insert `UPLOADED`；verify executor.execute；archived space 仍 400 |
| `DocParseJobTest` | run 成功 → READY + upsert 带 spaceId；parse 抛错 → FAILED + errorMessage |
| `KnowledgeDocServiceRetryTest` | FAILED 可入队；READY/PARSING/UPLOADED/ARCHIVED → 文案 `仅失败的文档可重试` |
| `KnowledgeDocServiceArchiveDeletesFileTest` | archive 调 storage.delete |
| `EvalGoldenRunner` | 16 条仍绿 |
| 前端 | `git diff -- frontend` 空 |

不新增 Python 测试文件（契约不变）。现有 Python 测试仍须绿。

---

## 8. 与 v1.2 测试的衔接

`KnowledgeDocServiceUploadSpaceTest.uploadWithoutSpaceIdUsesDefaultSpace` 今日 `verify parse + upsert`。本版改为：

- 仍 `requireActiveSpace(9L, null)` → space 100；
- insert 的 `knowledgeSpaceId==100`，`status==UPLOADED`；
- `verify(python, never()).parseDocument(any())`；
- `verify(python, never()).upsertVectors(any(), any(), any(), any())`；
- `verify(executor).execute(any())`。

工作线程路径用独立 `DocParseJobTest` 覆盖 upsert。

---

## 9. 明确不做（防止实施走偏）

- 不改 `analysis/query` DTO、五工具名、SSE、Router 默认
- 不改 Python `/api/vector/search` 空间过滤
- 不为评测新建 Controller / 表 / 金标文件
- 不把 `workspaceId` 写进任务 payload 当鉴权（租户仍只从登录上下文来；任务行上的 workspace_id 只是冗余便于排查）
- 运行时查询不 join 超过 2 张表、不建 FK
- 不实现扫 `PENDING` 的 `@Scheduled`
- 不改前端

---

## 10. P1 / 小版本接口（本周不实现）

- `StorageClient` MinIO profile
- `file_hash` / `doc_version` / `parse_status`
- `GET .../async-tasks`、`GET .../knowledge-docs/{id}/status`
- 启动时恢复 `UPLOADED`/`PARSING`
- `DOC_DELETE` 异步、延迟删文件、失败告警
- 前端状态轮询与重试按钮
