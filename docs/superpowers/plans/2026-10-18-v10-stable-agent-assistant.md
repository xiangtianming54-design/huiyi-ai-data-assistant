# v1.0 Stable Agent Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: 未开工。P0 only. P1 ReAct 不在本计划。
>
> Execution workspace: create an isolated git worktree via superpowers:using-git-worktrees before Task 1. Branch name: `feature/v1.0-agent-stable`.

**Goal:** On the existing Chat/Analysis facades and Router `AgentRunner`, add JSON Schema rejection, live `tool.*` SSE on `/chat/stream`, Context Budget, output redaction, 90s run cap, and `assistant_runs.error_code`.

**Architecture:** Extend `com.huiyi.assistant.agent` (no new HTTP controller). `ToolExecutor` validates args before execute. `AgentRunner` gains a `ToolEventListener` + `AgentDeadline`. `ChatService.chatStream` creates the `SseEmitter` and sends `session` **before** `run`. `ContextBudget` trims assembled prompts; `OutputGuard` strips secrets/stacks. Flyway V11 only adds `error_code`.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Flyway V11, JUnit 5 + Mockito. No new Maven JSON-Schema library (subset validator in-repo). Python and Vue trees stay unmodified.

**Spec:** `docs/superpowers/specs/2026-10-18-v10-stable-agent-assistant.md`  
**Technical design:** `docs/superpowers/specs/2026-10-18-v10-stable-agent-assistant-technical.md`

## File map

| Path | Responsibility |
|---|---|
| `backend/src/main/resources/db/migration/V11__assistant_runs_error_code.sql` | `error_code` column |
| `backend/src/main/java/com/huiyi/assistant/model/entity/AssistantRun.java` | `errorCode` |
| `backend/src/main/java/com/huiyi/assistant/observability/AssistantRunRecorder.java` | persist `errorCode` on finish |
| `backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java` | `finish` overload with errorCode |
| `backend/src/main/java/com/huiyi/assistant/config/AgentProperties.java` | `runTimeoutMs=90000`, `promptMaxChars=24000` |
| `backend/src/main/java/com/huiyi/assistant/HuiYiApplication.java` | register `AgentProperties` |
| `backend/src/main/resources/application-local.yml` and `application-docker.yml` | `huiyi.agent` |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolSchemas.java` | five JSON Schema strings |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolSchemaValidator.java` | subset object/required/additionalProperties/minLength |
| `backend/src/main/java/com/huiyi/assistant/agent/tool/*.java` | `spec().inputSchema` from `ToolSchemas` |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolExecutor.java` | validate after `require`, before pool |
| `backend/src/main/java/com/huiyi/assistant/agent/ToolEventListener.java` | started/completed/failed defaults |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentDeadline.java` | 90s wall clock |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentResult.java` | last component `errorCode` |
| `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java` | listener, deadline, schema→user copy, limit copy |
| `backend/src/main/java/com/huiyi/assistant/service/ChatService.java` | emitter before run; overlay `tool.*` |
| `backend/src/main/java/com/huiyi/assistant/agent/ContextBudget.java` | char cap + trim order |
| `backend/src/main/java/com/huiyi/assistant/agent/PromptBudgetExceededException.java` | budget signal |
| `backend/src/main/java/com/huiyi/assistant/service/QueryRewriteService.java` | trim history |
| `backend/src/main/java/com/huiyi/assistant/service/RagService.java` | trim knowledge context |
| `backend/src/main/java/com/huiyi/assistant/service/TextToSqlService.java` | trim schema |
| `backend/src/main/java/com/huiyi/assistant/agent/OutputGuard.java` | strip stack/secrets |
| `docs/api.md` / `docs/development.md` / `docs/roadmap.md` | P0 pointers |

Do not create: `ReActPlanner`, `/agent/runs`, `tool_invocations`, `tool_registry` table, `chart.recommend`, frontend files, Python API changes, steps JSON columns, `mode` column.

## Global Constraints

- Default HTTP stays `POST /api/v1/workspaces/{id}/chat/stream` and `.../analysis/query`. No `/agent/runs`.
- Default planner is `router` only. No `ReActPlanner`, no `mode=auto`.
- Tools stay `knowledge.search`, `sql.generate`, `sql.validate`, `sql.execute_readonly`, `data.profile`. Do not register `sales.query`, `chart.recommend`, `time.now`, `sql.execute_write`.
- Permissions remain `workspace_member` (VIEWER included). No `sales.read`.
- Unknown tool and Schema `REJECTED` user copy is exactly `工具调用失败，请稍后重试。` — not RAG no-hit, not `AGENT_LIMIT_EXCEEDED`.
- Run/budget wall user copy is exactly `当前问题较复杂，请缩小范围后重试。` with `error_code=AGENT_LIMIT_EXCEEDED`.
- `sql.validate` `valid=false` stays existing refusal copy (modification / whitelist), step `REJECTED`, SSE `tool.failed`.
- SSE names: keep `session` / `token` / `references` / `error`; add `tool.started` / `tool.completed` / `tool.failed`. Never `answer.token` or `step`.
- `analysis/query` stays JSON without `toolCalls`.
- `workspaceId` / `userId` only from `WorkspaceContextHolder`; strip those keys; Schema must not declare them.
- Per-tool timeouts unchanged (15s / 30s / 10s / 15s / 10s). Run cap 90s. No 20s desktop cap.
- No MySQL FK. No new run table. No frontend tree edits.
- PowerShell: no `&&`. Maven from `backend/`. Quote `"-Dtest=A,B"`.
- Five-scene e2e (`scripts/e2e-m5.js`) and isolation (`scripts/e2e-v07.js`) must still pass when Compose is up.

## Review Focus

- Schema invalid must not write `AGENT_LIMIT_EXCEEDED` (Task 4).
- `/chat/stream` still sends `token`, never `answer.token` (Task 5).
- `analysis/query` JSON has no `toolCalls` field (Task 5).
- Budget drops knowledge before history (Task 6).
- OutputGuard strips stack frames; unknown-tool copy stays unchanged (Task 7).

---

### Task 1: Flyway V11 `error_code` + AgentProperties

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__assistant_runs_error_code.sql`
- Create: `backend/src/main/java/com/huiyi/assistant/config/AgentProperties.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/entity/AssistantRun.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/AssistantRunRecorder.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/HuiYiApplication.java`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-docker.yml`
- Test: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/observability/AssistantRunRecorderTest.java`

**Interfaces:**
- Consumes: existing `AssistantRunRecorder.finish(status, intent, errorMessage, promptTokens, completionTokens, totalLatencyMs)`
- Produces: `AssistantRun.errorCode`; `finish(..., String errorCode)` (null-safe); `RequestTraceService.finish(String status, String intent, String error, long startNanos, String errorCode)` plus old 4-arg finish delegating `errorCode=null`; `AgentProperties.getRunTimeoutMs()` default `90000L`, `getPromptMaxChars()` default `24000`

- [ ] **Step 1: Write the failing tests**

```java
// FlywayMigrationFilesTest.migrationsCreateEnterpriseTables
assertTrue(sql.contains("error_code"), "V11 must add assistant_runs.error_code");
assertFalse(sql.contains("FOREIGN KEY"), "no MySQL FK");
assertFalse(sql.contains("arguments_json"), "no steps JSON columns");
assertFalse(sql.contains("agent_runs"), "no agent_runs table");

// AssistantRunRecorderTest.finishWritesErrorCode
recorder.start("chat_stream", "q", "s1");
recorder.finish("FAILED", "data_query", "run_timeout", 0, 0, 12, "AGENT_LIMIT_EXCEEDED");
verify(runMapper).update(captor.capture(), any());
assertEquals("AGENT_LIMIT_EXCEEDED", captor.getValue().getErrorCode());
assertEquals("FAILED", captor.getValue().getStatus());
```

- [ ] **Step 2: Run tests to verify they fail**

Run (cwd `backend`): `mvn -B "-Dtest=FlywayMigrationFilesTest,AssistantRunRecorderTest" test`  
Expected: FAIL — `error_code` missing / finish overload missing

- [ ] **Step 3: Implement V11 + entity + recorder + properties**

V11 SQL (no FK):

```sql
ALTER TABLE assistant_runs
  ADD COLUMN error_code VARCHAR(64) NULL COMMENT '如 AGENT_LIMIT_EXCEEDED；无则空' AFTER status;
```

`AgentProperties` `@ConfigurationProperties(prefix = "huiyi.agent")`. Register next to `ObservabilityProperties`. YAML:

```yaml
huiyi:
  agent:
    run-timeout-ms: ${HUIYI_AGENT_RUN_TIMEOUT_MS:90000}
    prompt-max-chars: ${HUIYI_AGENT_PROMPT_MAX_CHARS:24000}
```

Old `finish` without errorCode sets column null.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=FlywayMigrationFilesTest,AssistantRunRecorderTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git add backend/src/main/resources/db/migration/V11__assistant_runs_error_code.sql backend/src/main/java/com/huiyi/assistant/model/entity/AssistantRun.java backend/src/main/java/com/huiyi/assistant/observability/AssistantRunRecorder.java backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java backend/src/main/java/com/huiyi/assistant/config/AgentProperties.java backend/src/main/java/com/huiyi/assistant/HuiYiApplication.java backend/src/main/resources/application-local.yml backend/src/main/resources/application-docker.yml backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java backend/src/test/java/com/huiyi/assistant/observability/AssistantRunRecorderTest.java
git commit -m "feat: add assistant_runs.error_code and agent timeout properties"
```

---

### Task 2: ToolSchemas + ToolSchemaValidator

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolSchemas.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolSchemaValidator.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/ToolSchemaValidatorTest.java`

**Interfaces:**
- Consumes: none
- Produces: `ToolSchemas.KNOWLEDGE_SEARCH`, `SQL_GENERATE`, `SQL_VALIDATE`, `SQL_EXECUTE_READONLY`, `DATA_PROFILE` (JSON strings matching technical design §6); `ToolSchemaValidator.SCHEMA_INVALID = "schema_invalid"`; `boolean isValid(String inputSchema, Map<String, Object> arguments)` — null args = empty object; `additionalProperties:false`; `required`; `minLength` on strings. No new Maven dependency.

- [ ] **Step 1: Write the failing test**

```java
ToolSchemaValidator v = new ToolSchemaValidator();
assertTrue(v.isValid(ToolSchemas.SQL_GENERATE, Map.of()));
assertTrue(v.isValid(ToolSchemas.KNOWLEDGE_SEARCH, Map.of()));
assertTrue(v.isValid(ToolSchemas.DATA_PROFILE, Map.of()));
assertTrue(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "select 1")));
assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of()));
assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "")));
assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "select 1", "workspaceId", 1)));
assertFalse(v.isValid(ToolSchemas.SQL_GENERATE, Map.of("foo", 1)));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=ToolSchemaValidatorTest test`  
Expected: FAIL — class missing

- [ ] **Step 3: Implement validator + five schema constants**

Copy schema JSON from technical design §6.1–§6.5 verbatim. Implement only the subset those schemas need (object, properties, required, additionalProperties, string minLength, array). Invalid schema text → `isValid` false (do not treat as `"{}"`).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=ToolSchemaValidatorTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add in-repo JSON Schema validator for five tools"
```

---

### Task 3: Wire schemas into tools and ToolExecutor

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/KnowledgeSearchTool.java` (`spec()` schema)
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlGenerateTool.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlValidateTool.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/SqlExecuteReadonlyTool.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/tool/DataProfileTool.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/ToolExecutor.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/ToolExecutorTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/tool/SqlToolsTest.java` (schema not `"{}"`)

**Interfaces:**
- Consumes: `ToolSchemaValidator.isValid`, `ToolSchemas.*`, existing `registry.require` + timeout pool
- Produces: `ToolExecutor.execute` order = `require` → `stripTenant` → `isValid` else `ToolResult.fail("schema_invalid")` **without** calling `AgentTool.execute` → existing timeout execute. Timeouts unchanged.

- [ ] **Step 1: Write the failing tests**

```java
// ToolExecutorTest.invalidSchemaDoesNotExecute
AtomicBoolean ran = new AtomicBoolean(false);
registry.register(recordingToolWithSchema("sql.validate", ToolSchemas.SQL_VALIDATE, ran));
ToolResult result = executor.execute("sql.validate", ctx, Map.of());
assertFalse(result.ok());
assertEquals("schema_invalid", result.error());
assertFalse(ran.get());

// SqlToolsTest.generateSpecIsNotEmptyObjectSchema
assertNotEquals("{}", new SqlGenerateTool(textToSqlService).spec().inputSchema());
```

Keep existing `stripsWorkspaceIdFromArguments` / timeout tests green.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B "-Dtest=ToolExecutorTest,SqlToolsTest" test`  
Expected: FAIL — still `"{}"` / execute still invoked

- [ ] **Step 3: Point each `spec()` at `ToolSchemas` and validate in `ToolExecutor`**

`knowledge.search` execute still uses `context.question()` and ignores argument `query` if present.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=ToolExecutorTest,SqlToolsTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: validate tool arguments against JSON Schema before execute"
```

---

### Task 4: AgentRunner listener, deadline, schema copy, errorCode

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ToolEventListener.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/AgentDeadline.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentResult.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/ObservabilityMetrics.java` (`agentLimitExceeded`, `toolSchemaRejected`)
- Test: `backend/src/test/java/com/huiyi/assistant/agent/AgentRunnerTest.java`
- Test: existing `new AgentResult(...)` call sites — add trailing `null` errorCode

**Interfaces:**
- Consumes: Task 1 `AgentDeadline` timeout ms; Task 3 `schema_invalid`; `ToolEventListener.NOOP`
- Produces:

```java
public interface ToolEventListener {
    ToolEventListener NOOP = new ToolEventListener() {};
    default void onStarted(String toolName) {}
    default void onCompleted(String toolName, String status) {}
    default void onFailed(String toolName, String status) {}
}

public final class AgentDeadline {
    public static AgentDeadline fromNow(long timeoutMs);
    public static AgentDeadline expired(); // tests
    public boolean expired();
}

public AgentResult run(AgentRequest request); // delegates NOOP + fromNow(properties or 90_000)
public AgentResult run(AgentRequest request, ToolEventListener listener, AgentDeadline deadline);

public record AgentResult(..., String errorCode) // last field
```

Constants on `AgentRunner`: `LIMIT_USER_MESSAGE = "当前问题较复杂，请缩小范围后重试。"`; `ERROR_AGENT_LIMIT = "AGENT_LIMIT_EXCEEDED"`. Keep `UNKNOWN_TOOL_USER_MESSAGE`.

`invoke` behavior:
1. If `deadline.expired()`: `onFailed(name, "FAILED")`, do not call executor, run returns answer=`LIMIT_USER_MESSAGE`, `errorCode=ERROR_AGENT_LIMIT` (stop remaining tools).
2. `onStarted` then `toolExecutor.execute`.
3. `schema_invalid` → step `REJECTED`, `onFailed(name, "REJECTED")`, user `UNKNOWN_TOOL_USER_MESSAGE`, `errorCode` stays null.
4. Unknown tool: unchanged catch; `onFailed(name, "FAILED")`.
5. Success: `onCompleted(name, stepStatus(...))`. `valid=false` → `onFailed` + `REJECTED` (not completed).
6. Listener exceptions must not fail the run (catch + log).

- [ ] **Step 1: Write the failing tests**

```java
// schemaInvalidUsesUnknownToolCopyNotLimitCode
when(toolExecutor.execute(eq("sql.validate"), any(), any()))
        .thenReturn(ToolResult.fail("schema_invalid"));
ToolResult result = runner.invoke("sql.validate", Map.of("sql", "select 1"));
assertEquals(AgentRunner.UNKNOWN_TOOL_USER_MESSAGE, result.error());
verify(requestTraceService).stepTool(eq("sql.validate"), eq("REJECTED"), anyLong(), any());

// deadlineStopsExecutor
when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY))
        .thenReturn(List.of("sql.generate"));
AgentResult limited = runner.run(new AgentRequest("analysis_query", "q", "s1"),
        ToolEventListener.NOOP, AgentDeadline.expired());
assertEquals(AgentRunner.LIMIT_USER_MESSAGE, limited.answer());
assertEquals("AGENT_LIMIT_EXCEEDED", limited.errorCode());
verify(toolExecutor, never()).execute(any(), any(), any());

// listenerSeesStartedThenCompleted — mock generate/search success; captor listener events
```

Update every existing `new AgentResult(` in tests with `null` as last arg.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B -Dtest=AgentRunnerTest test`  
Expected: FAIL — overload / errorCode / copy mismatch

- [ ] **Step 3: Implement listener + deadline + AgentResult field + invoke mapping**

Inject `AgentProperties` if easy; otherwise hardcode `90_000L` in `run(AgentRequest)` deadline. Metrics: increment `huiyi_agent_limit_exceeded` and `huiyi_tool_schema_rejected` **without** `_total` suffix (match `ObservabilityMetrics.increment` style).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=AgentRunnerTest,ChatServiceFacadeTest,AnalysisServiceFacadeTest" test`  
Expected: PASS (fix every AgentResult arity break)

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add tool listener, run deadline, and schema failure copy"
```

---

### Task 5: Overlay `tool.*` on `/chat/stream`

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/service/ChatService.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/service/ChatServiceFacadeTest.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/service/AnalysisServiceFacadeTest.java`

**Interfaces:**
- Consumes: `AgentRunner.run(request, listener, deadline)`, Task 1 `AgentProperties.getRunTimeoutMs()`
- Produces: `chatStream` order = `new SseEmitter(90s)` → `event:session` → `run(..., listener, deadline)` → existing token/references/error. Listener sends JSON `{"tool":"..."}` / `{"tool":"...","status":"..."}` via existing `sendEvent`. `analysis.query` still `run(AgentRequest)` one-arg (NOOP). Finish uses `result.errorCode()`.

- [ ] **Step 1: Write the failing tests**

```java
// ChatServiceFacadeTest.chatStreamSourceOverlaysToolEvents
String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/ChatService.java"), UTF_8);
assertTrue(src.contains("sendEvent(emitter, \"session\""));
assertTrue(src.indexOf("new SseEmitter") < src.indexOf("agentRunner.run"));
assertTrue(src.contains("tool.started"));
assertTrue(src.contains("tool.completed"));
assertTrue(src.contains("tool.failed"));
assertTrue(src.contains("sendEvent(emitter, \"token\""));
assertFalse(src.contains("answer.token"));
assertFalse(src.contains("sendEvent(emitter, \"step\""));
assertFalse(src.contains("/agent/runs"));

// ChatServiceFacadeTest.chatStreamDelegatesThreeArgRun
when(... session ...).thenReturn("s1");
when(agentRunner.run(any(), any(), any())).thenReturn(sampleKnowledgeResult());
chatService.chatStream(requestWithQuestion());
verify(agentRunner).run(any(AgentRequest.class), any(ToolEventListener.class), any(AgentDeadline.class));

// AnalysisServiceFacadeTest.queryJsonHasNoToolCalls
String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/AnalysisService.java"), UTF_8);
assertFalse(src.contains("toolCalls"));
assertFalse(src.contains("tool.started"));
```

Replace `chatStreamEventNamesStayTokenOnly` (it currently forbids the overlay).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B "-Dtest=ChatServiceFacadeTest,AnalysisServiceFacadeTest" test`  
Expected: FAIL — run still before emitter / no tool events

- [ ] **Step 3: Reorder `chatStream` and implement listener JSON**

`onStarted` → `tool.started` data `{"tool":"<name>"}`.  
`onCompleted` → `tool.completed` data `{"tool":"<name>","status":"SUCCESS"}`.  
`onFailed` → `tool.failed` data `{"tool":"<name>","status":"<FAILED|REJECTED>"}`.  
Do not send SQL, prompts, or stack traces. If `result.errorCode()` is set, `event:error` uses `LIMIT_USER_MESSAGE` and skip token flux.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=ChatServiceFacadeTest,AnalysisServiceFacadeTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: emit tool SSE events on existing chat stream"
```

---

### Task 6: ContextBudget

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/ContextBudget.java`
- Create: `backend/src/main/java/com/huiyi/assistant/agent/PromptBudgetExceededException.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/QueryRewriteService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/RagService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/TextToSqlService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java` (`summarize` + catch budget)
- Test: `backend/src/test/java/com/huiyi/assistant/agent/ContextBudgetTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/agent/AgentRunnerTest.java` (budget → limit copy)

**Interfaces:**
- Consumes: `AgentProperties.getPromptMaxChars()`
- Produces:

```java
public final class ContextBudget {
    public ContextBudget(int maxChars);
    public String trimKnowledge(String knowledge);
    public String trimSchema(String schema);
    public String trimHistory(String history);
    public void assertFits(String system, String knowledge, String schema, String history, String question);
}
```

`assertFits` after trims: if `system+question` alone still `> maxChars` throw `PromptBudgetExceededException`. Trim order inside a convenience `fit(...)` used by tests: knowledge → schema → history; never trim system or question.

`AgentRunner.run` catches `PromptBudgetExceededException` → same as deadline (`LIMIT_USER_MESSAGE`, `AGENT_LIMIT_EXCEEDED`).

Keep v0.6 10-turn + summary path; only cap assembled strings.

- [ ] **Step 1: Write the failing tests**

```java
ContextBudget b = new ContextBudget(50);
String knowledge = "K".repeat(40);
String history = "H".repeat(40);
String fittedK = b.trimKnowledge(knowledge);
assertTrue(fittedK.length() < knowledge.length());
b.assertFits("SYS", fittedK, "", b.trimHistory(history), "Q");
ContextBudget tiny = new ContextBudget(3);
assertThrows(PromptBudgetExceededException.class,
        () -> tiny.assertFits("SYSTEM", "", "", "", "QUESTION"));

// AgentRunnerTest.budgetExceededUsesLimitCopy
when(queryRewriteService.rewrite(any(), any())).thenThrow(new PromptBudgetExceededException());
AgentResult r = runner.run(new AgentRequest("chat_stream", "q", "s1"));
assertEquals(AgentRunner.LIMIT_USER_MESSAGE, r.answer());
assertEquals("AGENT_LIMIT_EXCEEDED", r.errorCode());
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B "-Dtest=ContextBudgetTest,AgentRunnerTest" test`  
Expected: FAIL — class missing

- [ ] **Step 3: Implement trim + hook rewrite/RAG/text-to-sql/summarize**

`RagService.prepare`: trim `context` from `buildContext(hits)` before template render.  
`TextToSqlService.generateSql`: trim `schemaService.getSchemaDescription()`.  
`QueryRewriteService.rewrite`: trim formatted history.  
`AgentRunner.summarize`: if profile+rows JSON too long, shrink rows further then `assertFits`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=ContextBudgetTest,AgentRunnerTest,QueryRewriteServiceTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: cap assembled prompts with context budget"
```

---

### Task 7: OutputGuard

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/agent/OutputGuard.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/ChatService.java` (token/error/persist)
- Modify: `backend/src/main/java/com/huiyi/assistant/service/AnalysisService.java` (answer)
- Test: `backend/src/test/java/com/huiyi/assistant/agent/OutputGuardTest.java`

**Interfaces:**
- Consumes: none
- Produces: `OutputGuard.sanitize(String text)` — if contains `Exception in thread`, `at com.`, `Traceback (most recent call last)`, `sk-` + 8+ alphanumerics, or `API_KEY=` → return `AI 服务暂不可用，请稍后重试。` when the whole string is tainted; otherwise remove the matching span. Must **not** rewrite `工具调用失败，请稍后重试。` or `当前问题较复杂，请缩小范围后重试。`.

- [ ] **Step 1: Write the failing test**

```java
OutputGuard g = new OutputGuard();
assertEquals("工具调用失败，请稍后重试。", g.sanitize("工具调用失败，请稍后重试。"));
assertEquals("当前问题较复杂，请缩小范围后重试。", g.sanitize("当前问题较复杂，请缩小范围后重试。"));
assertFalse(g.sanitize("boom\nException in thread \"main\" java.lang.RuntimeException").contains("Exception in thread"));
assertFalse(g.sanitize("key sk-abcdefghijk").contains("sk-"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -Dtest=OutputGuardTest test`  
Expected: FAIL — class missing

- [ ] **Step 3: Implement sanitize and apply at Chat/Analysis user-visible strings**

Chat: each `token` and `error` data; assistant persist. Analysis: `answer` field. Do not sanitize SQL in analysis `sql` field (already shown to authorized members); still sanitize `answer`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B "-Dtest=OutputGuardTest,ChatServiceFacadeTest,AnalysisServiceFacadeTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: redact stacks and secrets from agent output"
```

---

### Task 8: Docs + full verify (no frontend)

**Files:**
- Modify: `docs/api.md` (`/chat/stream` event list + `tool.*` examples from technical design §7)
- Modify: `docs/development.md` (v1.0 未发布段落：规格+计划路径；回滚仍 `v0.9.0`)
- Modify: `docs/roadmap.md` (plan path)
- Modify: `docs/superpowers/specs/2026-10-18-v10-stable-agent-assistant.md` header: 实施计划已写
- Test: none. Verify frontend diff empty.

- [ ] **Step 1: Confirm frontend untouched**

Run: `git diff -- frontend`  
Expected: empty

- [ ] **Step 2: Update api/development/roadmap/spec pointers**

State P0 overlay events; no `/agent/runs`; Flyway V11; run 90s.

- [ ] **Step 3: Full backend unit tests**

Run: `mvn -B test` (cwd `backend`)  
Expected: PASS

- [ ] **Step 4: Commit**

```text
git commit -m "docs: document v1.0 tool SSE, budget, and error_code"
```

If Compose is already up, optionally `node scripts/e2e-m5.js` and `node scripts/e2e-v07.js` from repo root; not required if services are down.

---

## Self-review (spec coverage)

| Spec / tech-design item | Task |
|---|---|
| V11 `error_code` only; no JSON steps; no `agent_runs` | 1 |
| Five JSON Schemas + runtime reject | 2, 3 |
| Schema copy = unknown tool; not `AGENT_LIMIT_EXCEEDED` | 4 |
| 90s run cap + limit copy | 4 |
| Listener + `/chat/stream` overlay; keep `token` | 5 |
| analysis JSON, no `toolCalls` | 5 |
| Context Budget trim order + v0.6 history | 6 |
| Output Guard; no hallucination detector | 7 |
| Docs; no frontend; no `/agent/runs`; no ReAct | 8 |
| `workspace_member` / five tools / no chart.recommend | 3 (unchanged names) |

Gaps not in this plan (frozen out): ReAct, `/agent/runs`, eval page, `mode` column, frontend timeline.
