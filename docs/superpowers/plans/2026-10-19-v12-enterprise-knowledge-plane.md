# v1.2 Enterprise Knowledge Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: 已完成并发布。标签 `v1.2.0`。P0 only。异步解析 / OSS / 前端空间 UI / 细权限不在本计划。
>
> Execution workspace: isolated git worktree via superpowers:using-git-worktrees. Branch: `feature/v1.2-knowledge-plane`.

**Goal:** 工作区内可管理知识空间；文档必属一个空间；RAG 按 ACTIVE 空间 id 列表在 Python 侧过滤；空间隔离金标进入现有 `eval-goldens`。

**Architecture:** Flyway V12 建 `knowledge_spaces` 并回填文档列。Java `KnowledgeSpaceResolver` 算出检索 id 列表；未指定则传入该工作区全部 ACTIVE id。Python Chroma/BM25 用 `workspace_id` + `knowledge_space_id $in`。旧 `/knowledge/upload` 保留。前端不改。

**Tech Stack:** Java 17, Spring Boot 3.5.6, MyBatis-Plus, Flyway, JUnit 5, Mockito; Python 3.11 FastAPI + Chroma + 现有 BM25；现有 `EvalGoldenRunner`。无新 Maven JSON-Schema 库。无 MySQL FK。

**Spec:** `docs/superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane.md`  
**Technical design:** `docs/superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane-technical.md`

## File map

| Path | Responsibility |
|---|---|
| `backend/src/main/resources/db/migration/V12__knowledge_spaces.sql` | 建表、默认空间、文档列 NOT NULL |
| `backend/.../knowledge/KnowledgeSpaceResolver.java` | `null`/空列表/交集/ACTIVE 过滤 |
| `backend/.../model/entity/KnowledgeSpace.java` | 实体 |
| `backend/.../mapper/KnowledgeSpaceMapper.java` | Mapper |
| `backend/.../knowledge/KnowledgeSpaceService.java` | ensureDefault / CRUD / archive |
| `backend/.../api/v1/KnowledgeSpaceV1Controller.java` | 空间 HTTP |
| `backend/.../api/v1/KnowledgeV1Controller.java` | upload/list 可选 `spaceId` |
| `backend/.../service/KnowledgeDocService.java` | 写入 `knowledgeSpaceId`；列表按 ACTIVE 空间 |
| `backend/.../model/vo/KnowledgeDocVO.java` | 增加 `knowledgeSpaceId` |
| `backend/.../workspace/WorkspaceService.java` | create 后 ensureDefault |
| `backend/.../agent/AgentRequest.java` | 可选 `List<Long> spaceIds` |
| `backend/.../agent/ToolSchemas.java` | `knowledge.search` 可选 `spaceIds` |
| `backend/.../agent/AgentRunner.java` | 知识检索把 HTTP spaceIds 放入工具入参 |
| `backend/.../agent/tool/KnowledgeSearchTool.java` | resolve 后 `RagService.prepare` |
| `backend/.../service/RagService.java` | 空列表不调 Python；否则带空间列表检索 |
| `backend/.../service/PythonServiceClient.java` | search/upsert 带 `knowledge_space_id(s)` |
| `backend/.../model/dto/ChatRequestDTO.java` | 可选 `spaceIds` |
| `backend/.../model/dto/VectorSearchRequestDTO.java` | `knowledge_space_ids` |
| `backend/.../model/dto/ParseChunkDTO.Metadata` | `knowledgeSpaceId` |
| `backend/.../eval/EvalCase.java` / `EvalCaseLoader` / `EvalHarness` | `spaces` 标签；拒 `knowledgeSpaceId` |
| `backend/src/test/java/com/huiyi/assistant/eval/EvalGoldenRunner.java` | 空间 trap |
| `testdata/eval/iso-space-*.json` | 四条新金标 |
| `python-service/app/core/schema.py` | metadata + SearchRequest |
| `python-service/app/services/vector_store.py` | where `$in`；backfill update |
| `python-service/app/services/keyword_index.py` | 空间过滤 |
| `python-service/app/api/vector.py` | search 传列表；backfill 端点 |
| `docs/development.md` / 规格头 / roadmap | 命令与状态 |

Do not create: 前端文件、`async_tasks`、评测页、`/agent/runs`、嵌套上传路径、空间恢复接口。

## Global Constraints

- 决策表 D01–D13 不得推翻。默认空间不可归档。
- 无 MySQL `FOREIGN KEY` / `ON DELETE CASCADE`；运行时查询不 join 超过 2 张表。
- 租户只从 `WorkspaceContextHolder` 取。Tool Schema 禁止 `workspaceId` / `userId`。
- 冻结句不得改字：`RagService.NO_HIT_FALLBACK`、未知工具句、超限句。
- 夹具禁止 `workspaceId` / `userId` / `knowledgeSpaceId`。
- `git diff -- frontend` 必须空。
- PowerShell：禁止 `&&`。Maven cwd `backend/`。多测试用 `"-Dtest=A,B"`。
- 上传仍同步 Python。`analysis/query` DTO 不改。

## Review Focus

- 未指定空间时 Java 必须把 ACTIVE id 列表传给 Python，不能省略字段（否则归档空间向量会漏出）（Task 5）。
- `requested=[]` 必须 `NO_HIT` 且不调 Python（Task 5）。
- 归档默认空间必须 400，文案 `默认知识空间不可归档`（Task 2）。
- 夹具含 `"knowledgeSpaceId"` 必须加载失败（Task 6）。
- 现有 12 条 eval 金标必须仍绿（Task 6）。

---

### Task 1: KnowledgeSpaceResolver

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/KnowledgeSpaceResolver.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeSpaceResolverTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `public final class KnowledgeSpaceResolver` 无状态；`public List<Long> resolve(List<Long> httpSpaceIds, List<Long> toolSpaceIds, List<Long> activeIds)`  
  - `httpSpaceIds`/`toolSpaceIds`：`null` = 未指定（视为 `activeIds` 的一份拷贝）；**空列表 = 无命中**  
  - 两边都非 null → 交集  
  - 再只保留 `activeIds` 中出现的 id，顺序与 `activeIds` 一致  
  - `activeIds` null 当空列表

- [ ] **Step 1: Write the failing test**

```java
KnowledgeSpaceResolver r = new KnowledgeSpaceResolver();
List<Long> active = List.of(100L, 101L);
assertEquals(List.of(100L, 101L), r.resolve(null, null, active));
assertEquals(List.of(), r.resolve(List.of(), null, active));
assertEquals(List.of(101L), r.resolve(List.of(101L, 999L), null, active));
assertEquals(List.of(), r.resolve(List.of(101L), List.of(100L), active));
assertEquals(List.of(101L), r.resolve(List.of(100L, 101L), List.of(101L), active));
```

- [ ] **Step 2: Run test to verify it fails**

Run (cwd `backend`): `mvn -B -Dtest=KnowledgeSpaceResolverTest test`  
Expected: FAIL — 类不存在

- [ ] **Step 3: Implement `resolve` in `KnowledgeSpaceResolver.java`**

纯内存；不要查库。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=KnowledgeSpaceResolverTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: resolve knowledge space ids for RAG filter"
```

---

### Task 2: Flyway V12 + KnowledgeSpaceService

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__knowledge_spaces.sql`
- Create: `backend/src/main/java/com/huiyi/assistant/model/entity/KnowledgeSpace.java`
- Create: `backend/src/main/java/com/huiyi/assistant/mapper/KnowledgeSpaceMapper.java`
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/KnowledgeSpaceService.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`（断言含 `knowledge_spaces`，不含 `FOREIGN KEY`，不含 `async_tasks`）
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeSpaceServiceTest.java`

**Interfaces:**
- Consumes: Task 1 无直接依赖；MyBatis-Plus `BaseMapper<KnowledgeSpace>`
- Produces:
  - 实体字段：`id, workspaceId, name, description, status, isDefault, createdBy, createdAt`；表名 `knowledge_spaces`
  - `KnowledgeSpace ensureDefault(long workspaceId, long createdBy)` 幂等；名称「默认」；`isDefault=true`；`status=ACTIVE`
  - `KnowledgeSpace requireActiveSpace(long workspaceId, Long spaceId)`：`spaceId==null` → `ensureDefault`；否则本区 ACTIVE，否则 `BizException(400, "知识空间不存在或不属于当前工作区")`
  - `List<Long> listActiveIds(long workspaceId)`
  - `List<KnowledgeSpace> listActive(long workspaceId)`
  - `KnowledgeSpace create(long workspaceId, long userId, String name, String description)`：`isDefault=false`
  - `KnowledgeSpace update(long workspaceId, long spaceId, String name, String description)`：可改默认空间名称，不得改 `isDefault`
  - `void archive(long workspaceId, long spaceId)`：默认空间 → `BizException(400, "默认知识空间不可归档")`；否则 `status=ARCHIVED`；不改文档、不调 Python
  - 审计：`KNOWLEDGE_SPACE_CREATED` / `UPDATED` / `ARCHIVED`（先在 `AuditAction` 加常量）

V12 SQL 按技术设计 §5：CREATE + 每工作区 INSERT 默认空间 + UPDATE 文档空列 + `MODIFY knowledge_space_id BIGINT NOT NULL`。无 FK。默认空间 `created_by` 用 `workspaces.owner_id`，空则 `0`。

- [ ] **Step 1: Write the failing tests**

`FlywayMigrationFilesTest.migrationsCreateEnterpriseTables`：`sql.contains("knowledge_spaces")`；`!sql.contains("async_tasks")`。

`KnowledgeSpaceServiceTest`（Mockito Mapper + AuditService）：

- `ensureDefault` 两次 → 第二次不 `insert`
- `archive` 在 `isDefault=true` 上抛出，message 为 `默认知识空间不可归档`
- `listActiveIds` 不含已归档 id

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=FlywayMigrationFilesTest,KnowledgeSpaceServiceTest" test`  
Expected: FAIL

- [ ] **Step 3: Implement V12 + entity + mapper + service**

归档不要调 `PythonServiceClient`。`ensureDefault` 用 `workspace_id + is_default=1` 查询。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=FlywayMigrationFilesTest,KnowledgeSpaceServiceTest,KnowledgeSpaceResolverTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add knowledge_spaces table and default space service"
```

---

### Task 3: 空间 HTTP + 文档归属 + 新建工作区挂钩

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/api/v1/KnowledgeSpaceV1Controller.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/api/v1/KnowledgeV1Controller.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/KnowledgeDocService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/vo/KnowledgeDocVO.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/workspace/WorkspaceService.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/workspace/WorkspaceServiceTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeDocServiceUploadSpaceTest.java`

**Interfaces:**
- Consumes: Task 2 `KnowledgeSpaceService`
- Produces:
  - `POST /api/v1/workspaces/{workspaceId}/knowledge-spaces` → `requireKnowledgeWrite` + `create`
  - `GET .../knowledge-spaces` → `requireMember` + `listActive`
  - `PATCH .../knowledge-spaces/{spaceId}` body `name`/`description` → `update`
  - `PATCH .../knowledge-spaces/{spaceId}/status` body 与现网 `ChangeStatusRequest` 相同，`ARCHIVED` → `archive`
  - `POST .../knowledge/upload?spaceId=` 可选 Long；`KnowledgeDocService.upload(file, workspaceId, userId, spaceId)`
  - `GET .../knowledge/docs?spaceId=` 可选；缺省仅 ACTIVE 空间下未归档文档；非法/归档 spaceId → 空列表 200
  - `KnowledgeDocVO.knowledgeSpaceId`
  - upsert 调用改为带空间：见 Task 5 的 client 签名；本任务可先把 metadata 填上 `knowledgeSpaceId`，client 重载在 Task 5 接好。若 Task 5 未开始，本任务扩展 `upsertVectors(..., spaceId)` 为下一步预留，测试 verify metadata。
  - `WorkspaceService.create` 在 insert 成员后 `knowledgeSpaceService.ensureDefault(workspace.getId(), user.userId())`

向归档空间上传：`requireActiveSpace` 已 400，文案 `不能向已归档空间上传` 若 `requireActiveSpace` 通用文案不够，upload 路径在 status=ARCHIVED 时用该句。

- [ ] **Step 1: Write the failing tests**

`KnowledgeDocServiceUploadSpaceTest`：mock mapper / python / audit / `KnowledgeSpaceService`。

- `upload(file, 9L, 1L, null)` → `requireActiveSpace(9L, null)` 返回 id=100 的默认空间；insert 的 `KnowledgeDoc.knowledgeSpaceId==100`
- `requireActiveSpace` 抛 400「不能向已归档空间上传」时 upload 原样抛出
- `listDocs(9L, 1, 20, null)` 只包含 ACTIVE 空间文档（mock 两段查询或 service 内两次 mapper）

`WorkspaceServiceTest.createWorkspaceMakesCreatorOwner`：mock `KnowledgeSpaceService`，`verify(knowledgeSpaceService).ensureDefault(2L, 10L)`。

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=KnowledgeDocServiceUploadSpaceTest,WorkspaceServiceTest" test`  
Expected: FAIL

- [ ] **Step 3: Implement controllers + service + create hook**

Controller 继续从路径 `workspaceId` 鉴权，不要用 body 里的工作区。上传 `spaceId` 用 **query**，multipart 字段仍只 `file`。

列表：先 `listActiveIds`，再 `knowledge_docs` 条件 `workspaceId` + `status<>ARCHIVED` + `knowledgeSpaceId in ids`（指定 spaceId 时 in 单元素；该 id 不在 ACTIVE 则空列表）。两次查询，不 join。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=KnowledgeDocServiceUploadSpaceTest,WorkspaceServiceTest,KnowledgeSpaceServiceTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: expose knowledge space APIs and document affiliation"
```

---

### Task 4: Python 空间过滤与回填

**Files:**
- Modify: `python-service/app/core/schema.py`
- Modify: `python-service/app/services/vector_store.py`
- Modify: `python-service/app/services/keyword_index.py`
- Modify: `python-service/app/api/vector.py`
- Modify: `python-service/app/tests/test_workspace_isolation.py`（`_chunk` 增加 `knowledge_space_id`，HTTP search 带 `knowledge_space_ids`）
- Create: `python-service/app/tests/test_space_filter.py`
- Create: `python-service/app/tests/test_backfill_space_ids.py`

**Interfaces:**
- Consumes: 现有 `VectorStore.search_by_workspace`
- Produces:
  - `ChunkMetadata.knowledge_space_id: str | None`
  - 知识库 upsert：每个 chunk 必填 `knowledge_space_id`，同一请求不得混空间（比照现有 workspace 校验）
  - `SearchRequest.knowledge_space_ids: list[int] | None`
  - HTTP `POST /api/vector/search` 对 `huiyi_knowledge`：`knowledge_space_ids` 缺省或 `[]` → `hits=[]`，不查库
  - `VectorStore.search(..., knowledge_space_ids: list[int] | None)`：非空则 Chroma `where $and [workspace_id eq, knowledge_space_id $in str ids]`；空列表返回 `[]`
  - BM25 `KeywordIndex.search` 同样过滤 `knowledge_space_id`
  - `POST /api/vector/backfill-space-ids` body `{collection_name, assignments:[{workspace_id, doc_id, knowledge_space_id}]}`；`collection.update` 只改 metadata；BM25 同步；已相等则跳过

- [ ] **Step 1: Write the failing tests**

`test_space_filter.py`（FakeEmbedding + tmp_path，仿 `test_workspace_isolation`）：

- 同 workspace 两空间 upsert 后，`search_by_workspace(..., knowledge_space_ids=[10])` 不含空间 11 的 chunk id
- TestClient search `knowledge_space_ids: []` → `hits == []`
- TestClient 不传 `knowledge_space_ids`（仅 workspace）→ `hits == []`

`test_backfill_space_ids.py`：先 upsert 无空间或错误空间的 chunk（若 upsert 已强制空间，则先写入 space=10 再 backfill 为 11），get metadata 变为 11。

- [ ] **Step 2: Run tests**

Run (cwd `python-service`): `pytest app/tests/test_space_filter.py app/tests/test_backfill_space_ids.py -q`  
Expected: FAIL

- [ ] **Step 3: Implement schema / where / BM25 / backfill route**

现有 workspace 隔离测试的 `_chunk` 默认 `knowledge_space_id="100"`，其 HTTP search JSON 加上 `"knowledge_space_ids": [100]`，避免 Task 4 纪律误伤旧用例。

- [ ] **Step 4: Run tests**

Run: `pytest app/tests/test_space_filter.py app/tests/test_backfill_space_ids.py app/tests/test_workspace_isolation.py -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: filter Chroma and BM25 by knowledge space ids"
```

---

### Task 5: Java RAG 链路接上空间列表

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/ChatRequestDTO.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentRequest.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/ToolSchemas.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/KnowledgeSearchTool.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/RagService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/PythonServiceClient.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/VectorSearchRequestDTO.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/ParseChunkDTO.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/ChatService.java`（`chatStream` 把 DTO.spaceIds 传入 AgentRequest）
- Modify: `backend/src/test/java/com/huiyi/assistant/service/RagServiceTest.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/agent/tool/KnowledgeSearchToolTest.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/agent/ToolSchemaValidatorTest.java`（空对象仍合法；含 `workspaceId` 仍非法）
- Test: `backend/src/test/java/com/huiyi/assistant/agent/AgentRunnerKnowledgeSpaceTest.java`（或扩 `AgentRunnerTest` 一条）

**Interfaces:**
- Consumes: Task 1 `resolve`；Task 2 `listActiveIds`；Task 4 JSON 字段名 `knowledge_space_ids` / `knowledge_space_id`
- Produces:
  - `AgentRequest(String entrypoint, String question, String sessionId, List<Long> spaceIds)`；三参构造 `spaceIds=null`
  - `ChatRequestDTO.spaceIds` 可选 `List<Long>`
  - `runKnowledge`：`invoke(searchName, request.spaceIds()==null ? Map.of() : Map.of("spaceIds", request.spaceIds()), ...)`
  - `KnowledgeSearchTool` 构造注入 `RagService` + `KnowledgeSpaceService` + `KnowledgeSpaceResolver`
    - `requested = arguments.containsKey("spaceIds") ? parseLongList(arguments.get("spaceIds")) : null`
    - `effective = resolver.resolve(requested, null, spaceService.listActiveIds(context.workspaceId()))`
    - `ragService.prepare(context.question(), context.workspaceId(), effective)`
  - `RagContext prepare(String question, Long workspaceId, List<Long> knowledgeSpaceIds)`  
    - `knowledgeSpaceIds==null || isEmpty()` → `NO_HIT_FALLBACK`，**verify python 未被调用**  
    - 否则 `searchVectors(query, topK, workspaceId, knowledgeSpaceIds)`
  - 无空间参数的 `prepare(question)` / `prepare(question, workspaceId)` 测试改为三参并传入 `List.of(100L)`（生产路径不得再走「只按 workspace 搜」）
  - `PythonServiceClient.upsertVectors(parseResult, workspaceId, docId, spaceId)` 给每个 chunk metadata 写 `knowledge_space_id`
  - `ToolSchemas.KNOWLEDGE_SEARCH` 增加可选 `spaceIds` 整数数组；`additionalProperties: false`

- [ ] **Step 1: Write the failing tests**

`RagServiceTest.emptySpaceIdsSkipPython`：`prepare("q", 9L, List.of())` → `NO_HIT_FALLBACK`；`verify(python, never()).searchVectors(any(), anyInt(), any(), any())`（按最终签名匹配）。

`KnowledgeSearchToolTest.usesContextWorkspaceNotArgument` 改为 verify `prepare("q", 2L, List.of(100L, 101L))`：stub `listActiveIds(2L)→[100,101]`，arguments 无 `spaceIds`。

`KnowledgeSearchToolTest.intersectsRequestedIds`：arguments `spaceIds=[101,999]`，active `[100,101]` → `prepare("q", 2L, List.of(101L))`。

`AgentRunnerKnowledgeSpaceTest`：`AgentRequest("chat_stream", "q", "s", List.of(101L))` + stub executor；verify execute 的 arguments 含 `spaceIds`。

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=RagServiceTest,KnowledgeSearchToolTest,AgentRunnerKnowledgeSpaceTest,ToolSchemaValidatorTest" test`  
Expected: FAIL

- [ ] **Step 3: Implement DTO / Runner / tool / client / RagService**

`searchVectors` 现有重载保留并委托到带 `List<Long>` 的新重载。`ChatService.chatStream`：`new AgentRequest("chat_stream", q, sessionId, request.getSpaceIds())`。`analysis` 继续三参。

补 Task 3 的 `upsertVectors` 四参。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=RagServiceTest,KnowledgeSearchToolTest,AgentRunnerTest,AgentRunnerKnowledgeSpaceTest,ToolSchemaValidatorTest,KnowledgeDocServiceUploadSpaceTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: pass space ids through chat RAG to Python search"
```

---

### Task 6: 空间评测金标

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/eval/EvalCase.java`（`List<String> spaces`，缺省 null）
- Modify: `backend/src/main/java/com/huiyi/assistant/eval/EvalCaseLoader.java`（原始文本含 `"knowledgeSpaceId"` 抛错）
- Modify: `backend/src/main/java/com/huiyi/assistant/eval/EvalHarness.java`（映射标签并传入 `AgentRequest` 第四参）
- Modify: `backend/src/test/java/com/huiyi/assistant/eval/EvalGoldenRunner.java`（`isolateTrap` 空间规则）
- Modify: `backend/src/test/java/com/huiyi/assistant/eval/EvalCaseLoaderTest.java`
- Create: `testdata/eval/iso-space-extra-isolated.json`
- Create: `testdata/eval/iso-space-unspecified-multi.json`
- Create: `testdata/eval/iso-space-archived-excluded.json`
- Create: `testdata/eval/iso-space-cross-workspace.json`

**Interfaces:**
- Consumes: Task 5 四参 `AgentRequest`
- Produces: 标签映射 `default→100`，`extra→101`，`archived→102`  
  - 夹具无 `spaces` 键 → `spaceIds=null`  
  - `spaces: []` → 空列表  
  - `spaces: ["default"]` → `List.of(100L)`  
  - 未知标签 → 加载或 run 时 `IllegalArgumentException`
  - 空间 trap：`data.space` 为标签；`data.trap` 为秘密串  
    - 请求 `spaceIds==null` 且 space 为 default/extra → 把 trap 写入 `fallbackAnswer`  
    - 请求 null 且 space=archived → 不泄漏  
    - 请求映射后的 id 集合不含该 space 的 id → 不泄漏  
  - 工作区 trap 旧逻辑保留（workspaceId≠2 泄漏）

金标内容（intent 均 `knowledge_qa`，entrypoint `chat_stream`，stub `knowledge.search` ok）：

| id | spaces | stub | expect |
|---|---|---|---|
| `iso-space-extra-isolated` | `["default"]` | `space: extra`，`trap: SPACE_EXTRA_SECRET`，fallback 无该串 | `forbiddenSubstrings: ["SPACE_EXTRA_SECRET"]` |
| `iso-space-unspecified-multi` | 省略 | `space: extra`，`trap: SPACE_EXTRA_SECRET` | `answerContains: ["SPACE_EXTRA_SECRET"]` |
| `iso-space-archived-excluded` | 省略 | `space: archived`，`trap: SPACE_ARCHIVED_SECRET` | `forbiddenSubstrings: ["SPACE_ARCHIVED_SECRET"]` |
| `iso-space-cross-workspace` | `["extra"]`，`workspace: B` | 现有工作区 trap `WS_A_SECRET`（保持 isolateTrap 工作区分支） | `forbiddenSubstrings: ["WS_A_SECRET"]` |

- [ ] **Step 1: Write the failing tests**

`EvalCaseLoaderTest`：JSON 含 `"knowledgeSpaceId":1` → `IllegalArgumentException`。

放入四条 json 后跑 `EvalGoldenRunner`（此时 trap 未改会红）。

- [ ] **Step 2: Run tests**

Run: `mvn -B "-Dtest=EvalCaseLoaderTest,EvalGoldenRunner" test`  
Expected: FAIL（新夹具未隔离或 loader 未拒键）

- [ ] **Step 3: Implement spaces 字段、harness 映射、isolateTrap 扩展**

现有 12 条 json 不要改。`EvalGoldenRunner` 构造 `AgentRunner` 处无需新 stub 工具名。

- [ ] **Step 4: Run tests**

Run: `mvn -B -Dtest=EvalGoldenRunner test`  
Expected: PASS（16 条全绿，写出 `target/eval-report.*`）

- [ ] **Step 5: Commit**

```text
git commit -m "test: add knowledge space isolation eval goldens"
```

---

### Task 7: 向量回填 Runner + 文档

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/knowledge/KnowledgeSpaceVectorBackfill.java`（`ApplicationRunner`）
- Modify: `backend/src/main/resources/application.yml`（或 `application-local.yml` / `application-docker.yml` 增加同键）
- Modify: `backend/src/main/java/com/huiyi/assistant/service/PythonServiceClient.java`（`backfillSpaceIds` POST）
- Test: `backend/src/test/java/com/huiyi/assistant/knowledge/KnowledgeSpaceVectorBackfillTest.java`
- Modify: `docs/development.md`（评测仍 `EvalGoldenRunner`；注明 V12 / 空间 API）
- Modify: `docs/superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane.md` 状态行：实施计划已写
- Modify: `docs/roadmap.md`：实施计划链接

**Interfaces:**
- Consumes: Task 4 backfill HTTP
- Produces: 配置 `huiyi.knowledge.backfill-vector-space-ids` 默认 `true`。`ApplicationRunner` 读 `knowledge_docs`（id, workspaceId, knowledgeSpaceId）分批 POST；Python 不可用只打日志，不抛导致启动失败。`false` 时 runner 不调 Python。

- [ ] **Step 1: Write the failing test**

`KnowledgeSpaceVectorBackfillTest`：flag true + mock mapper 一条文档 + mock client；`verify(client).backfillSpaceIds(...)`。flag false → never。

- [ ] **Step 2: Run test**

Run: `mvn -B -Dtest=KnowledgeSpaceVectorBackfillTest test`  
Expected: FAIL

- [ ] **Step 3: Implement runner + client method + docs**

不要把密钥写入文档。

- [ ] **Step 4: Run tests**

Run: `mvn -B "-Dtest=KnowledgeSpaceVectorBackfillTest,EvalGoldenRunner,FlywayMigrationFilesTest" test`  
Expected: PASS  
Python: `pytest app/tests/test_space_filter.py app/tests/test_workspace_isolation.py -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: backfill vector space metadata and document v1.2 plan"
```

---

## Self-review

- 规格 §2 表/默认空间/API/检索/Python/评测/审计均有任务；§3 非目标无实现任务。
- D08 前端：无 frontend 文件。D10 无 `async_tasks`：Flyway 测试钉死。
- `resolve` / `ensureDefault` / `prepare` / `searchVectors` 名称在后续任务与 Task 1–5 一致。
- Review Focus 五条均挂在 Task 2/5/6 测试上。
