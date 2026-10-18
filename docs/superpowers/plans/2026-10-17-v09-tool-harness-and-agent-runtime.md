# v0.9 Tool Harness & Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: 已完成并发布。标签 `v0.9.0`。未纳入：`POST /agent/runs`、ReAct、步骤 SSE、前端 `toolCalls`。
>
> Execution workspace: create an isolated git worktree via superpowers:using-git-worktrees before Task 1. Branch name: `feature/v0.9-tool-harness`.

**Goal:** Put RAG / Text-to-SQL / data profile behind a memory Tool Registry and one Router `AgentRunner`, with Chat and Analysis as facades — no ReAct, no `/agent/runs`, no frontend change.

**Architecture:** New `com.huiyi.assistant.agent` package owns ToolSpec, Registry, Executor, five read-only tools, RouterPlanner, and AgentRunner. Runner copies `workspaceId`/`userId` from `WorkspaceContextHolder`, runs the existing rewrite/intent (and ask-path decompose) as fixed steps, then calls tools. `ChatService` / `AnalysisService` keep session persist and SSE token push only. Tool steps write `assistant_run_steps.tool_name` on the v0.8 `run_id`.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Flyway V10, JUnit 5 + Mockito. Python and Vue are unchanged.

**Spec:** `docs/superpowers/specs/2026-10-17-v09-tool-harness-and-agent-runtime.md`

## File map

| Path | Responsibility |
|---|---|
| `backend/src/main/resources/db/migration/V10__assistant_run_steps_tool.sql` | Add `tool_name` |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolNames.java` | Five tool name constants |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolSpec.java` | name, schema, timeoutMs, dangerLevel, permissions |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolContext.java` | workspaceId, userId, question from login |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolResult.java` | ok / data / error |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentTool.java` | spec + execute |
| `backend/src/main/java/com/huiyi/assistant/agent/UnknownToolException.java` | thrown by Registry.require |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolRegistry.java` | in-memory map |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolExecutor.java` | lookup + timeout |
| `backend/src/main/java/com/huiyi/assistant/agent/tool/*.java` | five wrappers |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentToolConfiguration.java` | `@Bean` register the five |
| `backend/src/main/java/com/huiyi/assistant/agent/RouterPlanner.java` | intent → tool name list |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentRequest.java` | entrypoint, question, sessionId |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentResult.java` | intent, answer, sql, references, analysis, ragContext |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java` | fixed steps + tools + unknown-tool gate |
| `ChatService.java` / `AnalysisService.java` | facades |
| `AssistantRunStep.java` / `AssistantRunRecorder.java` / `RequestTraceService.java` | persist `tool_name` |

Do not create: `ReActPlanner`, `POST /agent/runs`, `tool_invocations`, `tool_registry` table, frontend files, Python API changes.

## Global Constraints

- Default mode is `router` only; no ReAct class or `auto` switch.
- Default HTTP stays `/api/v1/workspaces/{id}/chat/stream` and `/api/v1/workspaces/{id}/analysis/query`. No `/agent/runs`.
- `workspaceId` / `userId` only from `WorkspaceContextHolder`. Tool arguments and prompts must not override them; strip `workspaceId`/`userId` from args before execute.
- First-batch tools: `knowledge.search`, `sql.generate`, `sql.validate`, `sql.execute_readonly`, `data.profile`. Do not register `chart.recommend`, `sql.execute_write`, `time.now`.
- Permissions: `workspace_member` (includes VIEWER). Do not raise SQL tools to MEMBER.
- Unknown tool user copy is exactly `工具调用失败，请稍后重试。` — not `RagService.NO_HIT_FALLBACK` or `SEARCH_DEGRADED_FALLBACK`.
- Keep v0.8 `request_id` / `run_id` / `llm_calls` / prometheus. Reuse step_type values `rag`, `sql_generate`, `sql_validate`, `sql_execute`, `llm_summarize`, `query_rewrite`, `intent`, `decompose`; set `tool_name` on tool rows.
- SSE event names stay `session`, `token`, `references`, `error`. No `step` events.
- No MySQL FK. No new run table. Frontend tree must stay unmodified.
- Five-scene e2e (`scripts/e2e-m5.js`) and isolation e2e (`scripts/e2e-v07.js`) must still pass when Compose is up.
- PowerShell: do not chain with `&&`. Maven from `backend/`.

## Review Focus

- Tool `arguments` containing `workspaceId` still call Rag/SQL with the login workspace (Task 4 + Task 6).
- `sql.execute_write` is unregistered; invoke is caught and uses the generic copy, not RAG fallback (Task 2 + Task 6).
- VIEWER `WorkspaceContext` can still run `sql.execute_readonly` (Task 4).
- After facade cutover, `ChatService` has no `analysisService.executeQuery` call (Task 7).
- `chatStream` still does not send an SSE event named `step` (Task 7).

---

### Task 1: Flyway V10 `tool_name`

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__assistant_run_steps_tool.sql`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/entity/AssistantRunStep.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/AssistantRunRecorder.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java`
- Test: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/observability/AssistantRunRecorderTest.java`

**Interfaces:**
- Consumes: existing `AssistantRunRecorder.step(stepType, status, latencyMs, errorMessage)`
- Produces: `AssistantRunStep.toolName`; `AssistantRunRecorder.step(String stepType, String toolName, String status, Integer latencyMs, String errorMessage)` (null `toolName` for fixed steps); `RequestTraceService.stepTool(String toolName, String status, long startNanos, String error)` which records `stepType` from the mapping in Global Constraints

- [ ] **Step 1: Write the failing tests**

```java
// FlywayMigrationFilesTest
assertTrue(sql.contains("tool_name"), "V10 must add assistant_run_steps.tool_name");
assertFalse(sql.contains("tool_invocations"), "no tool_invocations table");
assertFalse(sql.contains("tool_registry"), "no tool_registry table");

// AssistantRunRecorderTest.stepWritesToolName
recorder.start("chat_stream", "q", "s1");
recorder.step("rag", "knowledge.search", "SUCCESS", 12, null);
verify(stepMapper).insert(captor.capture());
assertEquals("knowledge.search", captor.getValue().getToolName());
assertEquals("rag", captor.getValue().getStepType());
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B -Dtest=FlywayMigrationFilesTest,AssistantRunRecorderTest test` (cwd `backend`)
Expected: FAIL — `tool_name` missing / method overload missing

- [ ] **Step 3: Implement V10 + entity field + recorder overload**

V10 SQL (no FK):

```sql
ALTER TABLE assistant_run_steps
  ADD COLUMN tool_name VARCHAR(128) NULL COMMENT '工具名；固定步骤为空' AFTER step_type;
```

Existing `step(...)` without toolName delegates with `toolName=null`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B -Dtest=FlywayMigrationFilesTest,AssistantRunRecorderTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git add backend/src/main/resources/db/migration/V10__assistant_run_steps_tool.sql backend/src/main/java/com/huiyi/assistant/model/entity/AssistantRunStep.java backend/src/main/java/com/huiyi/assistant/observability/AssistantRunRecorder.java backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java backend/src/test/java/com/huiyi/assistant/observability/AssistantRunRecorderTest.java
git commit -m "feat: add assistant_run_steps.tool_name for tool trace"
```

---

### Task 2: ToolRegistry (memory)

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolNames.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolSpec.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolContext.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolResult.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/UnknownToolException.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolRegistry.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/ToolRegistryTest.java`

**Interfaces:**
- Produces:
  - `ToolNames.KNOWLEDGE_SEARCH = "knowledge.search"` (and `SQL_GENERATE`, `SQL_VALIDATE`, `SQL_EXECUTE_READONLY`, `DATA_PROFILE`)
  - `ToolSpec(String name, String description, String inputSchema, long timeoutMs, String dangerLevel, String permissions)` with `DANGER_READ = "read"`, `PERMISSION_WORKSPACE_MEMBER = "workspace_member"`
  - `ToolContext(long workspaceId, long userId, String question)`
  - `ToolResult.ok(Map<String,Object> data)` / `ToolResult.fail(String error)` / `boolean ok()`, `Map<String,Object> data()`, `String error()`
  - `AgentTool.spec()` / `AgentTool.execute(ToolContext, Map<String,Object>)`
  - `UnknownToolException.toolName()`
  - `void ToolRegistry.register(AgentTool tool)`
  - `AgentTool ToolRegistry.require(String name)` throws `UnknownToolException`
  - `boolean ToolRegistry.registered(String name)`
  - `Set<String> ToolRegistry.names()`

- [ ] **Step 1: Write the failing test**

```java
@Test
void requireUnknownThrows() {
    ToolRegistry registry = new ToolRegistry();
    UnknownToolException ex = assertThrows(UnknownToolException.class,
            () -> registry.require("sql.execute_write"));
    assertEquals("sql.execute_write", ex.toolName());
}

@Test
void registerThenRequireReturnsSameTool() { /* fake AgentTool with spec.name knowledge.search */ }

@Test
void writeToolsAreNotRegisteredByDefault() {
    assertFalse(new ToolRegistry().registered("sql.execute_write"));
    assertFalse(new ToolRegistry().registered("time.now"));
    assertFalse(new ToolRegistry().registered("chart.recommend"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=ToolRegistryTest test`
Expected: FAIL — `ToolRegistry` not found

- [ ] **Step 3: Implement types + in-memory `ConcurrentHashMap` registry**

Duplicate names on `register` throw `IllegalStateException`. `require` does not return null.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=ToolRegistryTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add in-memory ToolRegistry and unknown-tool exception"
```

---

### Task 3: ToolExecutor

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolExecutor.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/ToolExecutorTest.java`

**Interfaces:**
- Consumes: `ToolRegistry.require`
- Produces: `ToolExecutor(ToolRegistry registry)` ; `ToolResult execute(String name, ToolContext context, Map<String, Object> arguments)` — calls `require`, then `tool.execute` with a copy of arguments that **omits** keys `workspaceId` and `userId`. If `spec.timeoutMs() > 0`, bound with `Future.get(timeoutMs, MILLISECONDS)` and return `ToolResult.fail("timeout")` on timeout. Does **not** catch `UnknownToolException` (Runner does).

- [ ] **Step 1: Write the failing tests**

```java
@Test
void stripsWorkspaceIdFromArguments() { /* tool records received args; execute with workspaceId=999; assert args have no workspaceId */ }

@Test
void requireUnknownPropagates() { /* empty registry; execute("nope") throws UnknownToolException */ }

@Test
void timeoutReturnsFail() { /* tool sleeps > spec.timeoutMs; assert !result.ok() && "timeout".equals(result.error()) */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=ToolExecutorTest test`
Expected: FAIL — class missing

- [ ] **Step 3: Implement `ToolExecutor.execute(...)`**

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=ToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add ToolExecutor with tenant-arg strip and timeout"
```

---

### Task 4: Five read-only tools

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/tool/KnowledgeSearchTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlGenerateTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlValidateTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlExecuteReadonlyTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/tool/DataProfileTool.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentToolConfiguration.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/tool/KnowledgeSearchToolTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/tool/SqlToolsTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/tool/DataProfileToolTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/AgentToolConfigurationTest.java`

**Interfaces:**
- Consumes: `RagService.prepare(question, workspaceId)`, `TextToSqlService.generateSql(question)`, `PythonServiceClient.validateSql(sql)`, `SqlExecuteService.execute(sql, question)`, `PythonServiceClient.profileData(rows)`
- Produces: five `@Component` `AgentTool`s. Timeouts: search 15000, generate 30000, validate 10000, execute 5000, profile 10000. All `dangerLevel=read`, `permissions=workspace_member`.
  - `knowledge.search` data: `fallbackAnswer`, `prompt`, `references` (from `RagContext`)
  - `sql.generate` data: `sql` (nullable)
  - `sql.validate` data: `valid`, `sql`, `reason`
  - `sql.execute_readonly` data: `rows`
  - `data.profile` data: profile fields including `chartType`; **one** `profileData` call
- `AgentToolConfiguration`: `@Bean ToolRegistry toolRegistry(List<AgentTool> tools)` registers every injected tool

- [ ] **Step 1: Write the failing tests**

```java
// KnowledgeSearchToolTest
@Test
void usesContextWorkspaceNotArgument() {
    when(ragService.prepare(eq("q"), eq(2L))).thenReturn(new RagService.RagContext("fb", null, List.of()));
    tool.execute(new ToolContext(2L, 9L, "q"), Map.of("workspaceId", 99L, "question", "q"));
    verify(ragService).prepare("q", 2L);
    verify(ragService, never()).prepare(eq("q"), eq(99L));
}

// SqlToolsTest
@Test
void viewerContextCanExecuteReadonly() {
    WorkspaceContext viewer = new WorkspaceContext(9L, 2L, 1L, "VIEWER", "ACTIVE", "ACTIVE");
    // execute SqlExecuteReadonlyTool with ToolContext from viewer; verify sqlExecuteService.execute called
}

// DataProfileToolTest
@Test
void singlePythonCallIncludesChartType() {
    DataProfileResponseDTO dto = new DataProfileResponseDTO();
    dto.setChartType("bar");
    when(python.profileData(any())).thenReturn(dto);
    ToolResult result = tool.execute(ctx, Map.of("rows", List.of()));
    assertEquals("bar", result.data().get("chartType"));
    verify(python, times(1)).profileData(any());
}

// AgentToolConfigurationTest (source or bean list)
@Test
void registeredNamesAreExactlyTheFiveReadTools() {
    assertEquals(Set.of("knowledge.search", "sql.generate", "sql.validate",
            "sql.execute_readonly", "data.profile"), registry.names());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B -Dtest=KnowledgeSearchToolTest,SqlToolsTest,DataProfileToolTest,AgentToolConfigurationTest test`
Expected: FAIL — classes missing

- [ ] **Step 3: Implement the five wrappers + configuration bean**

Question for SQL generate/search comes from `ToolContext.question()`, not args. SQL text for validate/execute comes from `arguments.get("sql")`. Rows for profile from `arguments.get("rows")`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B -Dtest=KnowledgeSearchToolTest,SqlToolsTest,DataProfileToolTest,AgentToolConfigurationTest,ToolRegistryTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: register five read-only agent tools"
```

---

### Task 5: RouterPlanner

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/RouterPlanner.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/RouterPlannerTest.java`

**Interfaces:**
- Produces: `List<String> RouterPlanner.toolsFor(IntentTypeEnum intent)`
  - `KNOWLEDGE_QA` → `[knowledge.search]`
  - `DATA_QUERY` → `[sql.generate, sql.validate, sql.execute_readonly, data.profile]`
  - never returns `chart.recommend` or write names

- [ ] **Step 1: Write the failing test**

```java
assertEquals(List.of("knowledge.search"), planner.toolsFor(IntentTypeEnum.KNOWLEDGE_QA));
assertEquals(List.of("sql.generate", "sql.validate", "sql.execute_readonly", "data.profile"),
        planner.toolsFor(IntentTypeEnum.DATA_QUERY));
assertFalse(planner.toolsFor(IntentTypeEnum.DATA_QUERY).contains("chart.recommend"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=RouterPlannerTest test`
Expected: FAIL

- [ ] **Step 3: Implement `RouterPlanner.toolsFor`**

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=RouterPlannerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add RouterPlanner intent-to-tool mapping"
```

---

### Task 6: AgentRunner

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentRequest.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentResult.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/AgentRunnerTest.java`

**Interfaces:**
- Consumes: `QueryRewriteService`, `IntentService`, `QuestionDecomposeService` (ask path only), `RouterPlanner`, `ToolExecutor`, `RequestTraceService`, `LlmService`, `PromptTemplateLoader` (`prompts/sql-result-summary.md`), `WorkspaceContextHolder`
- Produces:
  - `AgentRequest(String entrypoint, String question, String sessionId)`
  - `AgentResult` fields: `IntentService.IntentDecision decision()`, `String answer()`, `String sql()`, `List<ReferenceVO> references()`, `AnalysisResultVO analysis()`, `RagService.RagContext ragContext()`
  - `AgentRunner.UNKNOWN_TOOL_USER_MESSAGE = "工具调用失败，请稍后重试。"`
  - `AgentResult run(AgentRequest request)` — single question (`chat`, `chat_stream`, `analysis_query`)
  - `List<AgentResult> runAsk(AgentRequest request)` — rewrite + decompose + one `run` per sub-question (reuse existing `chatExecutor` or sequential; preserve current parallel `CompletableFuture` behavior via injected `ExecutorService`)
  - `ToolResult invoke(String toolName, Map<String,Object> arguments)` — builds `ToolContext` from **holder only**; catch `UnknownToolException`, `requestTraceService.stepTool(name, "FAILED", ...)`, return `ToolResult.fail(UNKNOWN_TOOL_USER_MESSAGE)` **without rethrowing**

Fixed-step order for `run`:
1. Require holder context (if null, do not invent ids; still run tools with `workspaceId=0` only if current code already allows null workspace for RAG — prefer `context.workspaceId()` when holder present).
2. `timed("query_rewrite", rewrite)`
3. `timed("intent", detectWithDetail)`
4. If DATA_QUERY and modification keywords (copy the list from current `AnalysisService`): record `sql_validate` `REJECTED` and return current `SAFE_REFUSAL` analysis result. Do not call tools.
5. For each name in `routerPlanner.toolsFor(intent)`: `invoke` with the right args (`sql` / `rows` threaded through `AgentState` locals). Stop the chain on generate-null / validate-fail / execute throw using the **existing** user strings from `AnalysisService` (`CANNOT_UNDERSTAND`, whitelist refusal, empty rows).
6. After successful profile, `timed("llm_summarize", summarize)` unless rows empty (keep `EMPTY_RESULT`).
7. Knowledge: after `knowledge.search`, if `ragContext.needLlm()` then for `entrypoint=chat_stream` leave `answer` null and set `ragContext`; otherwise `llmService.chat(prompt)` like `RagService.answer`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void unknownToolIsCaughtAndDoesNotUseRagFallback() {
    when(holderWorkspace()).then... // set WorkspaceContextHolder
    ToolResult result = runner.invoke("sql.execute_write", Map.of());
    assertFalse(result.ok());
    assertEquals(AgentRunner.UNKNOWN_TOOL_USER_MESSAGE, result.error());
    assertNotEquals(RagService.NO_HIT_FALLBACK, result.error());
    verify(trace).stepTool(eq("sql.execute_write"), eq("FAILED"), anyLong(), any());
}

@Test
void dataQueryInvokesFourToolsInOrder() { /* mock executor to record names; intent DATA_QUERY */ }

@Test
void knowledgeStreamLeavesRagContext() { /* entrypoint chat_stream; needLlm true; answer null */ }

@Test
void invokeIgnoresArgumentWorkspaceId() {
    WorkspaceContextHolder.set(new WorkspaceContext(9L, 2L, 1L, "VIEWER", "ACTIVE", "ACTIVE"));
    runner.invoke("knowledge.search", Map.of("workspaceId", 99L));
    verify(executor).execute(eq("knowledge.search"),
            argThat(ctx -> ctx.workspaceId() == 2L), any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=AgentRunnerTest test`
Expected: FAIL

- [ ] **Step 3: Implement `AgentRunner`**

Move modification-keyword list and summary prompt rendering out of `AnalysisService` into Runner (or a package-private helper in `agent`). Do not add `ReActPlanner`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=AgentRunnerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add Router AgentRunner with unknown-tool gate"
```

---

### Task 7: Chat / Analysis facades

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/service/ChatService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/AnalysisService.java`
- Test: `backend/src/test/java/com/huiyi/assistant/service/ChatServiceFacadeTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/service/AnalysisServiceFacadeTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/security/NoAgentRunsMappingTest.java`

**Interfaces:**
- Consumes: `AgentRunner.run` / `runAsk`
- Produces: same public methods on Chat/Analysis as today (`ask`, `chat`, `chatStream`, `query`, `precheck`, `detectIntent`). Delete `AnalysisService.executeQuery` **or** make it `private` unused — ChatService must not call it. `query` starts/finishes requestTrace as today when it owns the run, then `agentRunner.run(new AgentRequest("analysis_query", question, sessionId))`. `chatStream` uses `run` then existing `sendEvent` + `ragService.stream` when `result.ragContext() != null`.

- [ ] **Step 1: Write the failing tests**

```java
// ChatServiceFacadeTest
@Test
void askDelegatesToRunnerNotExecuteQuery() {
    when(agentRunner.runAsk(any())).thenReturn(List.of(sampleKnowledgeResult()));
    chatService.ask(request);
    verify(agentRunner).runAsk(any());
}

@Test
void chatStreamEventNamesStayTokenOnly() throws Exception {
    String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/ChatService.java"));
    assertFalse(src.contains("sendEvent(emitter, \"step\""));
    assertTrue(src.contains("sendEvent(emitter, \"token\""));
}

@Test
void chatServiceSourceDoesNotCallExecuteQuery() throws Exception {
    String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/ChatService.java"));
    assertFalse(src.contains("executeQuery("));
}

// NoAgentRunsMappingTest
@Test
void noAgentRunsController() throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
        String joined = paths.filter(p -> p.toString().endsWith(".java"))
                .map(p -> Files.readString(p)).reduce("", String::concat);
        assertFalse(joined.contains("/agent/runs"));
        assertFalse(joined.contains("ReActPlanner"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B -Dtest=ChatServiceFacadeTest,AnalysisServiceFacadeTest,NoAgentRunsMappingTest test`
Expected: FAIL — ChatService still calls `executeQuery` / `/agent/runs` test may already pass (keep it)

- [ ] **Step 3: Implement facade cutover**

Keep session resolve + `chatSessionService.save*` in the facades. Keep metrics `chatRequest`/`chatSuccess`/`sql*` as currently emitted; Runner should still call `ObservabilityMetrics` where AnalysisService did, or facades do — pick **Runner** for tool-level metrics so they fire once.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B -Dtest=ChatServiceFacadeTest,AnalysisServiceFacadeTest,NoAgentRunsMappingTest,AgentRunnerTest,IntentServiceTest,SqlExecuteServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: delegate chat and analysis to AgentRunner"
```

---

### Task 8: Docs (no frontend)

**Files:**
- Modify: `docs/roadmap.md` (v0.9 section: 口径已冻结 + plan path; drop “SSE Agent 事件” from 本版要做)
- Modify: `docs/development.md` (pointer 规格草稿 → 冻结规格与本计划)
- Modify: `docs/superpowers/specs/2026-10-17-v09-tool-harness-and-agent-runtime.md` header if needed: 实施计划已写
- Test: none. Verify: `git diff -- frontend` empty.

- [ ] **Step 1: Confirm frontend untouched**

Run: `git diff -- frontend`
Expected: empty

- [ ] **Step 2: Update roadmap + development pointers to the frozen spec and this plan**

- [ ] **Step 3: Full backend unit tests**

Run: `mvn -B test` (cwd `backend`)
Expected: PASS

- [ ] **Step 4: Commit**

```text
git commit -m "docs: point v0.9 roadmap at frozen spec and implementation plan"
```

If Compose is already up, optionally run `node scripts/e2e-m5.js` and `node scripts/e2e-v07.js` from repo root; not required to finish this task if services are down — note in the PR later.

---

## Self-review (spec coverage)

| Spec item | Task |
|---|---|
| Chat/Analysis facades, same Runner | 7 |
| Default router, no ReAct | 5, 6, 7 source scan |
| Extend `assistant_run_steps`, no `agent_runs` / FK | 1 |
| Token SSE only | 7 |
| Unknown tool Runner catch + generic copy | 6 |
| Tenant from login only | 3, 4, 6 |
| Fixed rewrite/intent/decompose/summarize | 6 |
| No dual orchestration | 7 |
| Keep v0.8 IDs | 1, 6 |
| One `data.profile` | 4 |
| VIEWER can query | 4 |
| Dangerous tools unregistered | 2, 4 configuration |
| No `/agent/runs` | 7 |
| No ReAct / time.now / tool_invocations / tool_registry table / frontend | 2, 1, 8 |
| Five tools + registry discover | 2–4 |
| Tool step Trace with tool_name | 1, 6 |
