# v1.3 Async Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: P0 only。MinIO / hash / 前端轮询 / 重启补偿 / 任务列表接口不在本计划。
>
> Execution workspace: isolated git worktree via superpowers:using-git-worktrees. Branch: `feature/v1.3-async-ingestion`.

**Goal:** 知识库上传立刻返回；原文件本地落盘；有界线程池异步 `DOC_PARSE`；详情/列表可查 `UPLOADED`/`PARSING`/`READY`/`FAILED`；仅 `FAILED` 可重试；归档同步删向量和本地文件。

**Architecture:** Flyway V13 建 `async_tasks` 并为 `knowledge_docs` 加 `storage_key`/`error_message`。请求线程只落盘+入队。`knowledgeParseExecutor` 跑 `DocParseJob`（parse + upsert）。HTTP 不暴露任务表。前端不改。Python 检索契约不改。

**Tech Stack:** Java 17, Spring Boot 3.5.6, MyBatis-Plus, Flyway, JUnit 5, Mockito; 现有 Python FastAPI parse/upsert；现有 `EvalGoldenRunner`。无 MQ。无 MySQL FK。

**Spec:** `docs/superpowers/specs/2026-10-20-v13-async-ingestion.md`  
**Technical design:** `docs/superpowers/specs/2026-10-20-v13-async-ingestion-technical.md`

## File map

| Path | Responsibility |
|---|---|
| `backend/src/main/java/com/huiyi/assistant/knowledge/LocalFileStorageClient.java` | 本地落盘 / resolve / delete |
| `backend/src/main/resources/db/migration/V13__async_ingestion.sql` | `async_tasks` + 文档两列 |
| `backend/.../model/entity/AsyncTask.java` | 实体 |
| `backend/.../mapper/AsyncTaskMapper.java` | Mapper |
| `backend/.../knowledge/AsyncTaskService.java` | 插入 `DOC_PARSE` |
| `backend/.../knowledge/DocParseJob.java` | 工作线程 |
| `backend/.../config/KnowledgeParseExecutorConfig.java` | 有界线程池 Bean |
| `backend/.../model/enums/DocStatusEnum.java` | 增加 `PARSING` |
| `backend/.../model/entity/KnowledgeDoc.java` | `storageKey`/`errorMessage` |
| `backend/.../model/vo/KnowledgeDocVO.java` | `errorMessage` |
| `backend/.../service/KnowledgeDocService.java` | 上传入队、retry、归档删文件 |
| `backend/.../api/v1/KnowledgeV1Controller.java` | `POST /docs/{id}/retry` |
| `backend/.../service/PythonServiceClient.java` | `parseDocument(Path, String)` |
| `backend/.../audit/AuditAction.java` | `KNOWLEDGE_RETRY` |
| `backend/src/main/resources/application.yml` 及 local/docker | `storage-dir` / `parse-pool-size` |
| `.gitignore` | `data/knowledge/` |
| `docs/development.md` / `docs/roadmap.md` / 规格头 | 状态与命令 |

Do not create: 前端文件、MinIO 实现、`GET async-tasks`、V14、新评测金标、Python 检索改动。

## Global Constraints

- 决策表 D01–D13 不得推翻。
- 无 MySQL `FOREIGN KEY` / `ON DELETE CASCADE`；运行时查询不 join 超过 2 张表。
- 租户只从 `WorkspaceContextHolder` / `requireKnowledgeWrite` 取。任务表 `workspace_id` 只是冗余。
- 冻结句不得改字：`RagService.NO_HIT_FALLBACK`、未知工具句、超限句、v1.2 空间文案、VIEWER `只读成员不能上传或归档知识库`。
- 重试非 FAILED 文案必须是 `仅失败的文档可重试`。
- `git diff -- frontend` 必须空。
- PowerShell：禁止 `&&`。Maven cwd `backend/`。多测试用 `"-Dtest=A,B"`。
- Python `/api/vector/search` 契约不改。`analysis/query` DTO 不改。
- 提交信息用 `git commit -F` 文件，不要 HEREDOC、不要 `Co-authored-by`。

## Review Focus

- 上传请求线程不得调用 `parseDocument` / `upsertVectors`（Task 3）。
- `PARSING`/`UPLOADED`/`READY`/`ARCHIVED` 调 retry 必须 400 且文案 `仅失败的文档可重试`（Task 5）。
- 线程池拒绝入队时文档必须落到 `FAILED`，不能停在看起来会跑却永不跑的 `UPLOADED`（Task 3）。
- `storage_key` 含 `..` 必须拒绝，文件不得写出配置根目录（Task 1）。
- `FlywayMigrationFilesTest` 不得再断言「没有 async_tasks」；不得出现 `file_hash`/`parse_status`/`doc_version`（Task 2）。

---

### Task 1: LocalFileStorageClient

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/LocalFileStorageClient.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/LocalFileStorageClientTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `public class LocalFileStorageClient`
  - 构造：`LocalFileStorageClient(Path rootDir)`；Spring 下用 `@Value("${huiyi.knowledge.storage-dir}")` 的 `@Component` 委托此构造（若测试不便用 Spring，生产类可以是 `@Component` + 包可见 Path 构造，或抽无注解类 + 薄 Spring 包装。本任务实现 **可单测的 `LocalFileStorageClient(Path root)`**，Spring 注入放 Task 3。）
  - `public String save(long workspaceId, long docId, byte[] content, String originalFilename)`  
    相对 key = `{workspaceId}/{docId}/{sanitizedFilename}`；父目录 `createDirectories`；写入后返回该 key。`originalFilename` 为空或无文件名 → 用 `file`。去掉路径分隔符，只保留最后一段。
  - `public Path resolve(String storageKey)`：normalize 后必须 `startsWith(root)`，否则 `IllegalArgumentException`
  - `public void delete(String storageKey)`：key 空则 no-op；文件不存在 no-op；存在则删文件（空父目录可不删）
  - `public boolean exists(String storageKey)`

- [ ] **Step 1: Write the failing test**

```java
Path root = Files.createTempDirectory("huiyi-ks");
LocalFileStorageClient c = new LocalFileStorageClient(root);
String key = c.save(9L, 55L, "hi".getBytes(StandardCharsets.UTF_8), "a.md");
assertEquals("9/55/a.md", key);
assertTrue(Files.exists(root.resolve("9").resolve("55").resolve("a.md")));
assertTrue(c.exists(key));
assertThrows(IllegalArgumentException.class, () -> c.resolve("../escape.txt"));
c.delete(key);
assertFalse(c.exists(key));
```

另测：`save(..., "../x.md")` 落盘文件名不得含 `..` 或路径分隔，key 以 `9/55/` 开头且 `resolve(key)` 仍在 root 下。

- [ ] **Step 2: Run test to verify it fails**

Run (cwd `backend`): `mvn -B -Dtest=LocalFileStorageClientTest test`  
Expected: FAIL — 类不存在

- [ ] **Step 3: Implement `LocalFileStorageClient`**

用 `Path.normalize()` + `startsWith(root.toAbsolutePath().normalize())`。不要调 Python。不要碰数据库。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=LocalFileStorageClientTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: store knowledge originals on local disk"
```

---

### Task 2: Flyway V13 + 实体

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__async_ingestion.sql`
- Create: `backend/src/main/java/com/huiyi/assistant/model/entity/AsyncTask.java`
- Create: `backend/src/main/java/com/huiyi/assistant/mapper/AsyncTaskMapper.java`
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/AsyncTaskService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/entity/KnowledgeDoc.java`（`storageKey`、`errorMessage`）
- Modify: `backend/src/main/java/com/huiyi/assistant/model/enums/DocStatusEnum.java`（增加 `PARSING`）
- Modify: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/AsyncTaskServiceTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - V13 SQL 按技术设计 §5：`CREATE TABLE async_tasks` + `ALTER knowledge_docs ADD storage_key, error_message`。无 FK。无 `file_hash`/`parse_status`/`doc_version`。无 `result` 列。
  - `AsyncTask` 字段：`id, taskType, workspaceId, userId, resourceType, resourceId, status, attempts, maxAttempts, payload, errorMessage, startedAt, finishedAt, createdAt`；表名 `async_tasks`
  - `AsyncTaskService.enqueueDocParse(long workspaceId, long userId, long docId)` → 插入 `taskType=DOC_PARSE`，`resourceType=knowledge_doc`，`resourceId=String.valueOf(docId)`，`status=PENDING`，`attempts=0`，`maxAttempts=1`，返回带 id 的实体
  - `DocStatusEnum.PARSING`

`FlywayMigrationFilesTest.migrationsCreateEnterpriseTables`：

- **删除** `assertFalse(sql.contains("async_tasks"), "no async_tasks table");`
- **改为** `assertTrue(sql.contains("async_tasks"), "V13 must create async_tasks");`
- `assertTrue(sql.contains("storage_key"));`
- `assertFalse(sql.contains("file_hash"));`
- `assertFalse(sql.contains("parse_status"));`
- `assertFalse(sql.contains("doc_version"));`
- 仍 `assertFalse(sql.contains("FOREIGN KEY"));`

- [ ] **Step 1: Write the failing tests**

改 `FlywayMigrationFilesTest` 上述断言（此时无 V13 → FAIL）。

`AsyncTaskServiceTest`（Mockito Mapper）：`enqueueDocParse(9L, 1L, 55L)` → insert 的 `taskType` 为 `DOC_PARSE`，`resourceId` 为 `"55"`，`status` 为 `PENDING`，`maxAttempts` 为 1。

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=FlywayMigrationFilesTest,AsyncTaskServiceTest" test`  
Expected: FAIL

- [ ] **Step 3: Implement V13 + entity + mapper + service + enum + KnowledgeDoc 字段**

Mapper：`extends BaseMapper<AsyncTask>`。`enqueueDocParse` 不要提交线程池。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=FlywayMigrationFilesTest,AsyncTaskServiceTest,LocalFileStorageClientTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add async_tasks table and PARSING doc status"
```

---

### Task 3: 上传立刻返回并入队

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/config/KnowledgeParseExecutorConfig.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/KnowledgeDocService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/vo/KnowledgeDocVO.java`（`errorMessage`）
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-docker.yml`
- Modify: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeDocServiceUploadSpaceTest.java`
- Test: 可扩 `KnowledgeDocServiceUploadSpaceTest`，不必新建文件

**Interfaces:**
- Consumes: Task 1 `save`；Task 2 `enqueueDocParse`
- Produces:
  - `KnowledgeDocService` 构造增加 `LocalFileStorageClient storage`、`AsyncTaskService asyncTasks`、`TaskExecutor knowledgeParseExecutor`（参数名与 Bean 名一致，便于 `@Qualifier("knowledgeParseExecutor")`）
  - `upload(...)` 顺序：`requireActiveSpace` → insert `UPLOADED` → `storage.save` → 回写 `storageKey` → `enqueueDocParse` → `executor.execute(job)`（本任务 job 可以是 **空 Runnable 或后续 Task 4 的 DocParseJob**；若 Task 4 尚未存在，execute 一个记录 docId 的 lambda **不得**在 lambda 里调 Python）
  - 请求线程 **不得**调用 `pythonServiceClient.parseDocument` / `upsertVectors`
  - 落盘失败：`status=FAILED`，`errorMessage` 可写 `保存上传文件失败`，抛 `BizException(500, "保存上传文件失败")`（先 update 再抛）
  - `RejectedExecutionException`：文档与刚插入的任务标 `FAILED`，`errorMessage=解析任务繁忙`，仍返回 VO（不抛）
  - `toVO` 带 `errorMessage`
  - 配置：`huiyi.knowledge.storage-dir` 默认 `./data/knowledge`；`huiyi.knowledge.parse-pool-size` 默认 `2`
  - Bean：`ThreadPoolTaskExecutor` 名 `knowledgeParseExecutor`，core=max=`parse-pool-size`，queue=100，prefix `doc-parse-`，`AbortPolicy`

- [ ] **Step 1: Write the failing tests**

改 `KnowledgeDocServiceUploadSpaceTest` 构造注入 mock storage / asyncTasks / executor。

`uploadWithoutSpaceIdUsesDefaultSpace`：

- insert 的 `knowledgeSpaceId==100`，`status==UPLOADED`
- `verify(storage).save(eq(9L), eq(55L), any(), eq("a.md"))`（若签名是 byte[]，matcher `any()`/`eq("a.md")`）
- `verify(asyncTasks).enqueueDocParse(9L, 1L, 55L)`
- `verify(executor).execute(any(Runnable.class))`
- `verify(pythonServiceClient, never()).parseDocument(any())`
- `verify(pythonServiceClient, never()).upsertVectors(any(), any(), any(), any())`
- 返回 VO `status` 为 `UPLOADED`

`uploadToArchivedSpaceRejected` 保持。

新增 `uploadExecutorRejectedMarksFailed`：`executor.execute` 抛 `RejectedExecutionException` → 返回 VO `FAILED`。

- [ ] **Step 2: Run tests**

Run: `mvn -B -Dtest=KnowledgeDocServiceUploadSpaceTest test`  
Expected: FAIL

- [ ] **Step 3: Implement upload 改写 + executor config + yml**

`LocalFileStorageClient` 做成 `@Component`：`@Value("${huiyi.knowledge.storage-dir:./data/knowledge}")` 转 Path。测试仍用 Path 构造。

上传读 `file.getBytes()` 再 `save`。audit 仍 `KNOWLEDGE_UPLOADED`（入队成功即记；拒绝入队记同一动作但 details 可含 failed——保持现网 SUCCESS 即可，拒绝入队不强制审计）。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=KnowledgeDocServiceUploadSpaceTest,LocalFileStorageClientTest,AsyncTaskServiceTest,FlywayMigrationFilesTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: return knowledge upload before parse"
```

---

### Task 4: DocParseJob

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/DocParseJob.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/PythonServiceClient.java`（`parseDocument(Path, String)`）
- Modify: `backend/src/main/java/com/huiyi/assistant/service/KnowledgeDocService.java`（execute 时提交 `DocParseJob` 而非空 Runnable）
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/DocParseJobTest.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/service/PythonServiceClientTest.java`（可选一条 Path 重载，没有现成文件则跳过 HTTP，本任务以 Job 单测为主）

**Interfaces:**
- Consumes: Task 1 `resolve`/`exists`；Task 2 任务与文档行；现有 `upsertVectors(..., spaceId)`
- Produces:
  - `public final class DocParseJob implements Runnable`
  - 构造：`(AsyncTask task, KnowledgeDocMapper docs, AsyncTaskMapper tasks, LocalFileStorageClient storage, PythonServiceClient python)`  
    或只传 `taskId` 在 run 时 load。推荐构造传入 **已有 task + docId**，run 内 `selectById`。
  - `run()`：
    1. 任务 `RUNNING`，`attempts+=1`，`startedAt=now`
    2. 文档 `PARSING`，`errorMessage=null`
    3. `python.parseDocument(storage.resolve(doc.storageKey), doc.docName)`
    4. `python.upsertVectors(result, workspaceId, docId, knowledgeSpaceId)`
    5. 文档 `READY`，`chunkCount`，`errorMessage=null`；任务 `SUCCEEDED`，`finishedAt=now`
    6. catch：文档 `FAILED` + `errorMessage`（截断 1000）；任务 `FAILED` 同样；不抛出（避免打挂线程）
  - `PythonServiceClient.parseDocument(Path file, String filename)`：读 bytes，multipart POST 现网 `/api/documents/parse`

- [ ] **Step 1: Write the failing tests**

`DocParseJobTest` Mockito：

- 成功：doc `storageKey=9/55/a.md`，spaceId=100；`parseDocument` 返回 chunkCount=2；run 后 doc `READY`、`chunkCount=2`；`verify upsertVectors(parsed, 9L, 55L, 100L)`；task `SUCCEEDED`
- 失败：`parseDocument` 抛 `RuntimeException("boom")`；doc `FAILED`，`errorMessage` 含 `boom`；task `FAILED`；`never upsertVectors`

- [ ] **Step 2: Run tests**

Run: `mvn -B -Dtest=DocParseJobTest test`  
Expected: FAIL

- [ ] **Step 3: Implement Job + Path parse + upload 提交 Job**

upload 的 `executor.execute` 必须是 `new DocParseJob(...)`，不要空 lambda。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=DocParseJobTest,KnowledgeDocServiceUploadSpaceTest,PythonServiceClientTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: parse uploaded documents on worker thread"
```

---

### Task 5: retry HTTP + 归档删文件

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/api/v1/KnowledgeV1Controller.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/KnowledgeDocService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/audit/AuditAction.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeDocServiceRetryTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeDocServiceArchiveFileTest.java`

**Interfaces:**
- Consumes: Task 3 upload 入队；Task 4 Job
- Produces:
  - `KnowledgeDocVO retry(long docId, long workspaceId, long userId)`
    - 非本区 → 现网 403 `无权访问其他工作区的文档` 或 getDoc 同款
    - `status` 不是 `FAILED` → `BizException(400, "仅失败的文档可重试")`
    - `storageKey` 空或 `!storage.exists` → `BizException(400, "原文件不存在，无法重试")`
    - 否则 `UPLOADED`、`errorMessage=null`、`enqueueDocParse`、`executor.execute(new DocParseJob(...))`、audit `KNOWLEDGE_RETRY`、返回 VO
  - Controller：`POST /docs/{docId}/retry` → `requireKnowledgeWrite` + `retry`
  - `archiveDoc` 在现网删向量与 `ARCHIVED` 之后 `storage.delete(doc.getStorageKey())`；delete 抛错只 log，不抛给调用方

- [ ] **Step 1: Write the failing tests**

`KnowledgeDocServiceRetryTest`：

- FAILED + exists → `enqueueDocParse` + `executor.execute` + 返回 `UPLOADED`
- READY / PARSING / UPLOADED / ARCHIVED 各一条：message 等于 `仅失败的文档可重试`
- FAILED 但 `exists=false`：`原文件不存在，无法重试`

`KnowledgeDocServiceArchiveFileTest`：`storageKey=9/1/a.md` 时 `verify(storage).delete("9/1/a.md")`；仍 `verify(python).deleteVectorsByDoc(...)`。

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=KnowledgeDocServiceRetryTest,KnowledgeDocServiceArchiveFileTest" test`  
Expected: FAIL

- [ ] **Step 3: Implement retry + controller + archive delete + AuditAction**

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=KnowledgeDocServiceRetryTest,KnowledgeDocServiceArchiveFileTest,KnowledgeDocServiceUploadSpaceTest,DocParseJobTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: retry failed knowledge parse and delete stored file on archive"
```

---

### Task 6: gitignore + 文档 + 回归

**Files:**
- Modify: `.gitignore`（增加 `/data/knowledge/` 与 `backend/data/knowledge/` 若默认相对 backend cwd）
- Modify: `docs/development.md`（注明上传异步、V13、storage-dir）
- Modify: `docs/roadmap.md`：当前开发改为 v1.3 Async Ingestion；下一版 Production Ready 为 v1.4（与桌面 2026-10-20 对齐）
- Modify: `docs/superpowers/specs/2026-10-20-v13-async-ingestion.md` 状态行可写「实施计划已写」
- 不要改 `frontend/`

**Interfaces:**
- Consumes: 本版全部行为
- Produces: 文档与 ignore；无新代码路径

- [ ] **Step 1: Write the failing check**

无新单测。本步以回归命令为门禁。若要 RED：可在 `FlywayMigrationFilesTest` 已完成前提下直接跑全量。

- [ ] **Step 2: Run regression**

Run (cwd `backend`): `mvn -B test`  
Expected: PASS（含 `EvalGoldenRunner`）

Python（主仓库 venv）：`C:\Users\33568\GitHub\huiyi-ai-data-assistant\python-service\.venv\Scripts\python.exe -m pytest app/tests -q`  
cwd `python-service`。Expected: PASS

`git diff -- frontend` 空。

- [ ] **Step 3: Implement gitignore + docs/roadmap**

roadmap：已发布仍写 v1.2.0；**当前开发 v1.3.0 Async Ingestion**；Production Ready 标为 v1.4.0。总览表若仍写 v1.3=Production Ready，改成与桌面一致。

- [ ] **Step 4: Re-run**

Run: `mvn -B "-Dtest=EvalGoldenRunner,FlywayMigrationFilesTest,KnowledgeDocServiceUploadSpaceTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "docs: record v1.3 async ingestion plan and ignore knowledge files"
```

---

## Self-review

- 规格 §2 上传立刻返回 / 落盘 / 状态机 / async_tasks / 可查 / retry / 归档 / 评测均有任务；§3 非目标无实现任务。
- D09 前端：无 frontend 文件。D10 仅 V13：Flyway 测试钉死无 hash/parse_status/doc_version。
- `save` / `enqueueDocParse` / `DocParseJob.run` / `retry` 名称在后续任务与 Task 1–5 一致。
- Review Focus 五条均挂在 Task 1/2/3/5 测试上。
- Python 检索无任务（契约不变）。
