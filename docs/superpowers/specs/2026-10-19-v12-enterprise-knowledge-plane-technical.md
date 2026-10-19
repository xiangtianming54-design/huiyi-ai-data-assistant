# HuiYi v1.2 · Enterprise Knowledge Plane 技术设计

> 版本：`v1.2.0`  
> 状态：**技术设计已写**（实施计划已写；实现进行中）  
> 产品规格：[2026-10-19-v12-enterprise-knowledge-plane.md](2026-10-19-v12-enterprise-knowledge-plane.md)  
> 基线代码：`v1.1.0`（标签 `v1.1.0`）  
> 口径：D01–D13。本文只写 **怎么改代码**，不重新开范围。

P1 / 小版本（异步解析、对象存储、细权限、前端空间 UI）只在 §10 留接口，本周不开工。

---

## 1. 设计目标与差量

在 **不换聊天/分析入口、不改前端、不拆向量 collection** 的前提下：

1. 每个工作区有 `knowledge_spaces` 行，文档 `knowledge_space_id` 非空；
2. 上传/列表可按空间收窄，旧 `/knowledge/upload` 仍写入默认空间；
3. RAG 在 Python 向量 + BM25 **检索时**按 `workspace_id` + 空间 id 列表过滤；
4. 空间隔离金标进入现有 `eval-goldens`。

| 桌面 §12 说法 | 本版技术落点 |
|---|---|
| 知识空间表 | Flyway `V12__knowledge_spaces.sql` |
| 嵌套上传 `.../spaces/{id}/documents/upload` | **不上**；现网 `/knowledge/upload?spaceId=` |
| 异步任务 / OSS / hash | 不上 |
| 检索过滤器 JSON | Java 解析出 ACTIVE id 列表，Python `knowledge_space_ids` |
| 权限过滤 | 工作区 RBAC + ACTIVE 空间列表，无新权限码 |
| 前端知识治理 | 不上（D08） |

禁止平行再造一套 RAG。检索必须继续走 `RagService` → `PythonServiceClient` → `/api/vector/search`。

---

## 2. 现网基线（代码事实）

| 类 / 文件 | 本版角色 |
|---|---|
| `KnowledgeV1Controller` | `/knowledge/upload|docs` 保留；加可选 `spaceId` |
| `KnowledgeDocService.upload(file, workspaceId, userId)` | 增加 `spaceId`；upsert 时写入空间 metadata |
| `KnowledgeDoc.knowledgeSpaceId` | 列已在 V3，一直为 null；本版回填后 NOT NULL |
| `RagService.prepare(question, workspaceId)` | 增加空间 id 列表参数 |
| `KnowledgeSearchTool` | 今日 `prepare(question, workspaceId)`，忽略工具入参 |
| `AgentRequest` | `(entrypoint, question, sessionId)`，无空间 |
| `AgentRunner.runKnowledge` | `invoke(searchName, Map.of(), ...)` 空参数 |
| `ToolContext` | `(workspaceId, userId, question)` |
| `ToolSchemas.KNOWLEDGE_SEARCH` | 仅可选 `query` |
| `ChatRequestDTO` | `sessionId` + `question` |
| `PythonServiceClient.searchVectors(..., workspaceId)` | 请求体无空间列表 |
| `VectorStore.search_by_workspace` | Chroma `where workspace_id`；BM25 同样 |
| `ChunkMetadata` | `workspace_id` / `doc_id`，无空间 |
| `WorkspaceService.create` | 建工作区 + OWNER 成员，无默认空间 |
| `EvalHarness` | 工作区标签 `default→9, A→1, B→2` |
| `EvalGoldenRunner.isolateTrap` | 非 workspace 2 则把 `trap` 灌进 `fallbackAnswer` |
| `EvalCaseLoader` | 原始 JSON 含 `"workspaceId"` / `"userId"` 即失败 |
| Flyway | 最后一号 `V11__assistant_runs_error_code.sql` |

冻结句（不得改字）：

- `RagService.NO_HIT_FALLBACK`
- `RagService.SEARCH_DEGRADED_FALLBACK`
- v1.0 未知工具句 / 超限句 / 修改拒绝句

---

## 3. 目标调用链

### 3.1 确保默认空间

```text
WorkspaceService.create 成功 insert 之后
  → KnowledgeSpaceService.ensureDefault(workspaceId, ownerUserId)

任意文档写 / 检索需要默认空间时
  → 同一 ensureDefault（幂等：已有 is_default=1 则返回）
```

Flyway V12 给 **已有** 工作区插入默认空间并回填文档，不依赖这次启动。

### 3.2 上传

```text
POST /knowledge/upload?spaceId=（可选）
  → requireKnowledgeWrite
  → KnowledgeSpaceService.requireActiveSpace(workspaceId, spaceId)
        spaceId 空 → ensureDefault
        有值但外区 / ARCHIVED / 不存在 → 400
  → insert knowledge_docs（knowledge_space_id NOT NULL）
  → Python parse（同步，现网）
  → upsertVectors(..., workspaceId, docId, spaceId)
        每个 chunk.metadata.knowledge_space_id = str(spaceId)
```

### 3.3 聊天检索（D04+D05+D09）

```text
ChatV1Controller.chatStream(ChatRequestDTO.spaceIds)
  → AgentRunner.run(AgentRequest(..., httpSpaceIds))
  → runKnowledge：
        invoke("knowledge.search",
               httpSpaceIds == null ? Map.of() : Map.of("spaceIds", httpSpaceIds),
               ...)
  → ToolExecutor.stripTenant（只剥 workspaceId/userId，保留 spaceIds）
  → KnowledgeSearchTool：
        effective = KnowledgeSpaceResolver.resolve(workspaceId, httpFromArgs, toolFromArgs)
        空列表 → RagService 直接 NO_HIT，不调 Python
        否则 prepare(question, workspaceId, effectiveIds)
  → Python /api/vector/search
        workspace_id + knowledge_space_ids
        Chroma where $and(workspace, knowledge_space_id $in)
        BM25 同样过滤
```

`resolve` 规则（与规格 §4.3 一致）：

1. HTTP 侧来自 `AgentRequest.spaceIds`（经工具入参带入）；工具侧来自模型填的 `spaceIds`。
2. 缺省方（`null`）=「本工作区全部 ACTIVE id」（查 `knowledge_spaces`）。
3. 两边都有 → 交集。
4. 再滤：必须属于当前 `workspaceId` 且 `status=ACTIVE`。
5. 结果为空 → 无命中，**不**回退成搜全部。
6. JSON 里出现的非法 / 跨区 / 归档 id **静默剔除**，不 500。

`analysis/query` 继续 `new AgentRequest("analysis_query", q, s)` 三参，`spaceIds=null`。

未指定空间时 **Java 仍传完整 ACTIVE 列表** 给 Python，禁止「只按 workspace、不按空间」的检索——否则已归档空间的向量会被命中（D07 向量仍在）。

### 3.4 归档空间

```text
PATCH .../knowledge-spaces/{id}/status { "status": "ARCHIVED" }
  → requireKnowledgeWrite
  → is_default=1 → 400「默认知识空间不可归档」
  → status=ARCHIVED
  → 不改 knowledge_docs，不调向量删除
```

之后默认检索的 ACTIVE 列表不含该 id，Python `$in` 自然打不中。

---

## 4. Java 结构

新类型放现有包，不新 Maven 模块。

| 类型 | 职责 |
|---|---|
| `KnowledgeSpace` 实体 | 表 `knowledge_spaces` |
| `KnowledgeSpaceMapper` | MyBatis-Plus；无 XML 关联查询 |
| `KnowledgeSpaceService` | CRUD、`ensureDefault`、`requireActiveSpace`、`listActiveIds`、归档 |
| `KnowledgeSpaceV1Controller` | §4.1 四个端点 |
| `KnowledgeSpaceResolver` | HTTP ∩ 工具 → ACTIVE id 列表（纯逻辑，便于单测） |
| `KnowledgeSpaceVectorBackfill` | 启动时把文档表的 space id 推给 Python（幂等） |

`WorkspaceService.create` 在成员 insert + 审计之后调用 `ensureDefault`。注意避免循环依赖：`KnowledgeSpaceService` 只依赖 Mapper / Audit，不依赖 `WorkspaceService`。

### 4.1 `AgentRequest` / `ToolContext`

```text
AgentRequest(entrypoint, question, sessionId, List<Long> spaceIds)
  三参构造 = spaceIds 为 null（未指定）
  null：未指定
  空列表：调用方显式传了 []，resolve 后无命中

ToolContext(workspaceId, userId, question, List<Long> spaceIds)
  spaceIds 从 AgentRequest 拷入，invoke 时写入工具入参而不是扩 ToolContext 也行
```

推荐 **不改 ToolContext 形状**：`runKnowledge` 把 HTTP `spaceIds` 放进 `invoke` 的 arguments；`KnowledgeSearchTool` 只看 arguments + `KnowledgeSpaceService.listActiveIds(context.workspaceId())`。`AgentRunner.invoke` 今日用 `toolContextForQuestion`，不必为空间改租户记录。

这样 `AgentRequest` 仍要带上 `spaceIds`，否则 `runKnowledge` 拿不到 HTTP 列表。所有 `new AgentRequest(a,b,c)` 保持三参兼容。

`ChatService.chatStream`：`new AgentRequest("chat_stream", q, sessionId, request.getSpaceIds())`。`chat` / `chatAsk` 若仍走 Runner，同样带上 DTO 的 `spaceIds`；测试里没设则 null。

### 4.2 Schema

`ToolSchemas.KNOWLEDGE_SEARCH` 改为：

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "query": { "type": "string" },
    "spaceIds": {
      "type": "array",
      "items": { "type": "integer" }
    }
  }
}
```

禁止 `workspaceId` / `userId`。`ToolExecutor.stripTenant` 保持只剥这两个键。

### 4.3 文档 API 细节

- 上传：`@RequestParam(required=false) Long spaceId`，**query**，不改 multipart 字段名 `file`（前端不用改）。
- 列表：`@RequestParam(required=false) Long spaceId`。缺省 = `workspace_id=? AND status<>ARCHIVED AND knowledge_space_id IN (ACTIVE 空间)`。指定已归档/外区 → 空 `items`，HTTP 仍 200。
- `KnowledgeDocVO.knowledgeSpaceId`。
- 两表：先查 ACTIVE 空间 id，再查文档。**禁止**三表 join，禁止 FK。

### 4.4 错误文案（用户可见，实施时固定字符串）

| 情况 | HTTP | 文案 |
|---|---|---|
| 默认空间归档 | 400 | `默认知识空间不可归档` |
| 空间不存在 / 外区 | 400 | `知识空间不存在或不属于当前工作区` |
| 向归档空间上传 | 400 | `不能向已归档空间上传` |
| VIEWER 写空间 | 403 | 现网 `只读成员不能上传或归档知识库` |

不新增 `ResultCode` 枚举也可以，用现有 400/403 + 文案。

### 4.5 审计

`AuditAction` 增加：

- `KNOWLEDGE_SPACE_CREATED`
- `KNOWLEDGE_SPACE_UPDATED`
- `KNOWLEDGE_SPACE_ARCHIVED`

`resourceType=knowledge_space`。文档审计不变。

---

## 5. Flyway `V12__knowledge_spaces.sql`

禁止 `FOREIGN KEY`、禁止 `ON DELETE CASCADE`。`FlywayMigrationFilesTest` 增加断言含 `knowledge_spaces`，仍断言无 FK；断言 **不含** `async_tasks`。

```sql
CREATE TABLE knowledge_spaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  is_default TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ks_workspace_status (workspace_id, status),
  UNIQUE KEY uk_ks_workspace_name (workspace_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 每个已有工作区一个默认空间（名称「默认」；created_by 用 owner_id，无则 0）
INSERT INTO knowledge_spaces (workspace_id, name, status, is_default, created_by)
SELECT w.id, '默认', 'ACTIVE', 1, COALESCE(w.owner_id, 0)
FROM workspaces w
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_spaces s WHERE s.workspace_id = w.id AND s.is_default = 1
);

UPDATE knowledge_docs d
JOIN knowledge_spaces s
  ON s.workspace_id = d.workspace_id AND s.is_default = 1
SET d.knowledge_space_id = s.id
WHERE d.knowledge_space_id IS NULL;

-- 无 workspace_id 的历史行（若还有）先挂到无法归属则保持；V4 后应已有 workspace
-- 回填后收紧
ALTER TABLE knowledge_docs
  MODIFY knowledge_space_id BIGINT NOT NULL;
```

`is_default` 每区一条由应用 + 本脚本 INSERT 保证，**不要** `UNIQUE(workspace_id, is_default)`（多个 `0` 会冲突）。

Join 仅出现在迁移脚本，运行时查询仍拆成两次。

---

## 6. Python sidecar

### 6.1 契约

`ChunkMetadata` 增加 `knowledge_space_id: str | None`。知识库 collection upsert：每个 chunk **必填**，且同一请求内不得混空间。

`SearchRequest` 增加 `knowledge_space_ids: list[int] | None`。知识库检索：

- `workspace_id` 仍必填；
- `knowledge_space_ids` 缺省或空列表 → **直接返回 `hits=[]`**（由 Java 保证「未指定」会传来完整 ACTIVE 列表；空 = 无命中）；
- where：

```text
$and: [
  { workspace_id: $eq str(workspace_id) },
  { knowledge_space_id: $in [str(id)...] }
]
```

禁止先全库 / 先按工作区 Top-K 再在 Python 外层丢弃（与现网 `search_by_workspace` 纪律相同）。

`KeywordIndex.search` 增加同样的 id 列表过滤。upsert 写入 `knowledge_space_id`。

`vector.py` `search`：把 `knowledge_space_ids` 传到 `VectorStore.search(...)`。

### 6.2 存量向量回填

新接口（仅内部，Java 调，不进对外 API v1）：

```http
POST /api/vector/backfill-space-ids
{
  "collection_name": "huiyi_knowledge",
  "assignments": [
    { "workspace_id": 1, "doc_id": 10, "knowledge_space_id": 100 }
  ]
}
```

实现：按 `workspace_id+doc_id` `collection.get`，`collection.update` 只改 metadata（保留 embedding）；同步改 BM25 落盘字段。已等于目标值的跳过。

Java `ApplicationRunner`（配置 `huiyi.knowledge.backfill-vector-space-ids`，默认 `true`）：读 `knowledge_docs` 分批 POST。失败打 error 日志，**不阻断**进程启动（本地无 Python 时与现网其它调用一致：检索时再降级）。测试用 Mock。

不在 Flyway 里碰 Chroma。

---

## 7. 评测夹具

### 7.1 Loader

原始 JSON 额外禁止 `"knowledgeSpaceId"`（数字租户同类键）。允许 `"spaces"` 标签数组。

`EvalCase` 增加 `List<String> spaces`：

- 缺省 / 键不出现 → `AgentRequest.spaceIds = null`（未指定）
- `[]` → 空列表（无命中）
- `["default"]` / `["extra"]` / `["archived"]` → runner 映射成固定 id

空间标签（**与工作区 id 无关**，同一映射用于 default/A/B 工作区）：

| 标签 | id | 语义（stub） |
|---|---|---|
| `default` | 100 | ACTIVE |
| `extra` | 101 | ACTIVE |
| `archived` | 102 | 已归档，resolve 后应被剔除 |

夹具禁止写死这些数字。

### 7.2 Harness

`new AgentRequest(entrypoint, question, sessionId, mappedSpaceIds)`。

现有 12 条不写 `spaces`，行为与今日相同。

### 7.3 Stub 隔离（`EvalGoldenRunner`）

扩展 `isolateTrap`：

- 工作区陷阱：保持现逻辑（`trap` + workspace≠2 则泄漏）。
- 空间陷阱：stub `data.space` 为标签（`extra` / `archived` / `default`）。  
  - 请求未指定空间（null）且 `space` 为 ACTIVE → **泄漏**（多空间可命中）。  
  - 请求未指定且 `space=archived` → **不泄漏**。  
  - 请求指定的标签集合不含该 `space` → **不泄漏**。  
  - 指定含该 ACTIVE 标签 → 泄漏（用于「指定 extra 能看到 extra」的正面用例可用 `answerContains` 而非 trap）。

`forbiddenSubstrings` 仍做负例。

### 7.4 必收录新夹具（id 固定）

| 文件 | 覆盖 |
|---|---|
| `iso-space-extra-isolated.json` | `spaces:["default"]`；stub 带 `space: extra` + trap `SPACE_EXTRA_SECRET`；forbidden 含该串 |
| `iso-space-unspecified-multi.json` | 不写 `spaces`；stub extra trap；`answerContains` 含陷阱串（未指定应能命中） |
| `iso-space-archived-excluded.json` | 不写 `spaces`；`space: archived` + trap `SPACE_ARCHIVED_SECRET`；forbidden 含该串 |
| `iso-space-cross-workspace.json` | `workspace:"B"` + `spaces:["extra"]`；stub trap 标记为工作区 A 秘密；forbidden 含 `WS_A_SECRET`（工作区隔离仍在） |

实施计划可合并「指定空间正面命中」到 JUnit（`KnowledgeSpaceResolverTest`），夹具以隔离负例为主，避免和 LLM 长答案耦合。

现有 `iso-workspace-b-isolated.json` 不得坏。

---

## 8. 测试矩阵（TDD 顺序建议）

| 测试 | 断言 |
|---|---|
| `KnowledgeSpaceResolverTest` | null∩null → 全部 ACTIVE；`[extra]∩null` → 仅 extra；跨区 id 剔除；归档剔除；空交集 → empty |
| `KnowledgeSpaceServiceTest` | ensureDefault 幂等；归档默认失败；归档后 listActive 不含 |
| `KnowledgeDocServiceUploadSpaceTest` | 无 spaceId → 默认；外区 400；归档空间 400；upsert metadata 带 space id |
| `KnowledgeV1Controller` / slice | query `spaceId`；列表过滤 |
| `KnowledgeSearchToolTest` | 把 arguments.spaceIds 交给 RagService，不把 99L workspace 传进去 |
| `RagServiceTest` | empty ids → `NO_HIT` 且 **不**调 Python；非空则 search 带 `knowledge_space_ids` |
| `AgentRunner` 知识路径 | HTTP spaceIds 进入 `knowledge.search` 入参 |
| `EvalCaseLoaderTest` | `"knowledgeSpaceId"` 拒载 |
| `EvalGoldenRunner` | 12 旧 + 4 新全绿 |
| Python `test_space_filter.py` | `$in` 过滤；空列表 → []；BM25 同步 |
| Python `test_backfill_space_ids.py` | update metadata 不丢 embedding 语义（可用 fake collection） |
| `FlywayMigrationFilesTest` | 有 `knowledge_spaces`；无 FK；无 `async_tasks` |

前端：`git diff -- frontend` 必须空。

---

## 9. 配置与本地

`application.yml`：

```yaml
huiyi:
  knowledge:
    backfill-vector-space-ids: true
```

PowerShell 本地测后端：`mvn -B "-Dtest=KnowledgeSpaceResolverTest,EvalGoldenRunner" test`（cwd `backend`）。禁止 `&&`。

Python：`pytest` 现有 workspace 隔离用例仍过，并加空间过滤。

---

## 10. P1 / 小版本接口（本周不实现）

- `POST .../knowledge-spaces/{id}/documents/upload`
- 文档 `GET status` / `POST retry`、`async_tasks`、对象存储、`file_hash`
- `visibility=PRIVATE`、空间成员、新权限码
- 空间恢复、硬删、归档时删向量
- 前端空间下拉、聊天 `spaceIds` 选择器
- 按空间拆 Chroma collection

---

## 11. 明确不做（防止实施走偏）

- 不改 `analysis/query` DTO
- 不改五工具名、SSE 事件名、Router 默认
- 不为评测新建 Controller / 表
- 不把 `workspaceId` 写进 Tool Schema
- 不在 Java 里「Python 搜整个工作区再 `hits.removeIf`」作为隔离实现
- 迁移脚本以外的运行时查询不 join 三表、不建 FK
