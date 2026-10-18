# v1.1 Eval 评测集与回归 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: 未开工。P0 only. P1 Prompt 灰度不在本计划。
>
> Execution workspace: isolated git worktree via superpowers:using-git-worktrees. Branch: `feature/v1.1-eval-regression`.

**Goal:** Deterministic JSON goldens under `testdata/eval/` run through `AgentRunner` (stubs, no live LLM), write `eval-report.json/.md`, and fail CI job `eval-goldens` on mismatch.

**Architecture:** New package `com.huiyi.assistant.eval`. Loader rejects tenant keys. Harness injects `WorkspaceContext`, stubs `ToolExecutor` / intent / rewrite, collects `ToolEventListener`. Comparer checks only structured expect fields. `EvalGoldenRunner` JUnit is the CI entry; existing unit tests stay.

**Tech Stack:** Java 17, JUnit 5, Mockito, Jackson, Maven Surefire, GitHub Actions. No new Maven JSON-Schema library. No Flyway. No frontend/Python edits.

**Spec:** `docs/superpowers/specs/2026-10-18-v11-eval-and-regression.md`  
**Technical design:** `docs/superpowers/specs/2026-10-18-v11-eval-and-regression-technical.md`

## File map

| Path | Responsibility |
|---|---|
| `backend/src/main/java/com/huiyi/assistant/eval/EvalCase.java` | Jackson model |
| `backend/src/main/java/com/huiyi/assistant/eval/EvalCaseLoader.java` | load dir; reject `workspaceId`/`userId` |
| `backend/src/main/java/com/huiyi/assistant/eval/EvalComparer.java` | structured match |
| `backend/src/main/java/com/huiyi/assistant/eval/EvalReportWriter.java` | json/md + redact |
| `backend/src/main/java/com/huiyi/assistant/eval/EvalHarness.java` | context + stubs + run |
| `backend/src/test/java/com/huiyi/assistant/eval/EvalGoldenRunner.java` | load all + write `target/eval-report.*` + assert all pass |
| `testdata/eval/*.json` | frozen case ids from technical §6 |
| `.github/workflows/ci.yml` | job `eval-goldens` |
| `docs/development.md` / `docs/roadmap.md` | command + job pointer |

Do not create: eval Controller, Flyway, frontend files, Python eval, `ReActPlanner`.

## Global Constraints

- No `/eval`, no `/agent/runs`, no new tables, no frontend/Python behavior change.
- Fixtures must not contain `workspaceId` or `userId`.
- Frozen copies exact: `工具调用失败，请稍后重试。` and `当前问题较复杂，请缩小范围后重试。`
- Schema/unknown-tool must not write `AGENT_LIMIT_EXCEEDED`.
- Report must not contain secrets, stacks, or full prompts.
- PowerShell: no `&&`. Maven from `backend/`. Quote `"-Dtest=A,B"`.
- `e2e-m5.js` / `e2e-v07.js` stay local-only.

## Review Focus

- Fixture with `workspaceId` must fail load, not silently strip (Task 1).
- Schema reject actual `errorCode=AGENT_LIMIT_EXCEEDED` must fail the case (Task 2).
- Report writer given `sk-abcdefghijk` must not persist it (Task 3).
- `EvalGoldenRunner` must fail the JUnit test if any golden fails, not only write a report (Task 5).
- CI job must upload artifacts on failure (`if: always()`) (Task 6).

---

### Task 1: EvalCaseLoader rejects tenant keys

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalCase.java`
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalCaseLoader.java`
- Test: `backend/src/test/java/com/huiyi/assistant/eval/EvalCaseLoaderTest.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper` already on classpath
- Produces: `EvalCase` with `id`, `entrypoint`, `question`, `sessionId`, `workspace`, `stubs`, `expect`; `EvalCaseLoader.load(Path dir) -> List<EvalCase>` sorted by id; `IllegalArgumentException` if raw JSON contains `"workspaceId"` or `"userId"` (substring check on file text is enough)

- [ ] **Step 1: Write the failing test**

```java
Path dir = Files.createTempDirectory("eval");
Files.writeString(dir.resolve("bad.json"), "{\"id\":\"bad\",\"workspaceId\":1,\"question\":\"q\"}");
EvalCaseLoader loader = new EvalCaseLoader();
assertThrows(IllegalArgumentException.class, () -> loader.load(dir));
Files.writeString(dir.resolve("ok.json"), "{\"id\":\"ok\",\"entrypoint\":\"chat_stream\",\"question\":\"q\",\"workspace\":\"default\"}");
Files.delete(dir.resolve("bad.json"));
List<EvalCase> cases = loader.load(dir);
assertEquals("ok", cases.get(0).id());
```

- [ ] **Step 2: Run test to verify it fails**

Run (cwd `backend`): `mvn -B -Dtest=EvalCaseLoaderTest test`  
Expected: FAIL — class missing

- [ ] **Step 3: Implement loader**

`EvalCase` Jackson-friendly record/POJO matching technical §4 (nested stubs/expect may be `Map` or nested records). Ignore unknown properties. Skip non-`.json` files.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -Dtest=EvalCaseLoaderTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: load eval fixtures and reject tenant keys"
```

---

### Task 2: EvalComparer structured match

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalActual.java`
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalComparer.java`
- Test: `backend/src/test/java/com/huiyi/assistant/eval/EvalComparerTest.java`

**Interfaces:**
- Consumes: `EvalCase.expect` fields from Task 1
- Produces: `EvalComparer.Result(boolean passed, String mismatch)` ; `compare(EvalCase.Expect expect, EvalActual actual)`

- [ ] **Step 1: Write the failing test**

```java
EvalComparer c = new EvalComparer();
EvalCase.Expect expect = new EvalCase.Expect("data_query", "工具调用失败，请稍后重试。", null, false, List.of(), List.of());
EvalActual ok = new EvalActual("data_query", "工具调用失败，请稍后重试。", null, false, List.of());
assertTrue(c.compare(expect, ok).passed());
EvalActual limit = new EvalActual("data_query", "工具调用失败，请稍后重试。", "AGENT_LIMIT_EXCEEDED", false, List.of());
assertFalse(c.compare(expect, limit).passed());
assertEquals("errorCode", c.compare(expect, limit).mismatch());
```

If `Expect` uses a sentinel for “errorCode key present and JSON null”, document it: Java `errorCode` field `Optional` or `boolean errorCodeSpecified`. Loader must distinguish omitted vs JSON null. Technical: JSON null means must be null. For this test construct Expect so errorCode is specified as null.

- [ ] **Step 2: Run test**

Run: `mvn -B -Dtest=EvalComparerTest test`  
Expected: FAIL — class missing

- [ ] **Step 3: Implement comparer**

Compare only non-absent expect fields. `toolEvents`: actual starts with expect list (prefix). `forbiddenSubstrings`: none present in actual.answer. `analysisHasToolCalls`: boolean equality when expect field non-null.

- [ ] **Step 4: Run test**

Run: `mvn -B "-Dtest=EvalComparerTest,EvalCaseLoaderTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: compare eval goldens on structured fields"
```

---

### Task 3: EvalReportWriter redacts secrets

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalReportWriter.java`
- Test: `backend/src/test/java/com/huiyi/assistant/eval/EvalReportWriterTest.java`

**Interfaces:**
- Consumes: list of `{id, passed, expected, actual, mismatch}`
- Produces: `write(List<EvalReportRow> rows, Path json, Path md)`

- [ ] **Step 1: Write the failing test**

```java
Path json = Files.createTempFile("eval", ".json");
Path md = Files.createTempFile("eval", ".md");
EvalReportWriter w = new EvalReportWriter();
w.write(List.of(new EvalReportRow("x", false, Map.of("answer", "key sk-abcdefghijk"), Map.of(), "answerEquals")), json, md);
String body = Files.readString(json) + Files.readString(md);
assertFalse(body.contains("sk-abcdefghijk"));
assertTrue(body.contains("x"));
```

- [ ] **Step 2: Run test**

Run: `mvn -B -Dtest=EvalReportWriterTest test`  
Expected: FAIL

- [ ] **Step 3: Implement writer**

Redact `sk-[A-Za-z0-9]{8,}`, `API_KEY=\S*`, `Exception in thread`, `Traceback (most recent call last)` to `[redacted]` after JSON/MD render (or on every string field). Markdown table: id | passed | mismatch.

- [ ] **Step 4: Run test**

Run: `mvn -B -Dtest=EvalReportWriterTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: write eval reports without secrets or stacks"
```

---

### Task 4: EvalHarness drives AgentRunner

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalHarness.java`
- Create: `backend/src/main/java/com/huiyi/assistant/eval/EvalRecordingListener.java`
- Test: `backend/src/test/java/com/huiyi/assistant/eval/EvalHarnessTest.java`

**Interfaces:**
- Consumes: `EvalCase`, `AgentRunner`
- Produces: `EvalActual EvalHarness.run(EvalCase cse, AgentRunner runner)` ; maps workspace `default→9`, `A→1`, `B→2`; userId `1`; role `VIEWER`; always `clear()` in finally

- [ ] **Step 1: Write the failing test**

Reuse `AgentRunner` construction style from `AgentRunnerTest` (Mockito `@InjectMocks`). Case: `intent=data_query`, `toolsFor=sql.generate`, deadline already expired via harness flag `expect` not needed — put `"id":"limit"` and `stubs.deadlineExpired=true` on EvalCase.

```java
when(queryRewriteService.rewrite(any(), any())).thenReturn("q");
when(intentService.detectWithDetail("q")).thenReturn(
    new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0));
when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY)).thenReturn(List.of("sql.generate"));
EvalCase cse = /* deadlineExpired true, entrypoint analysis_query, question q */;
EvalActual actual = new EvalHarness().run(cse, runner);
assertEquals(AgentRunner.LIMIT_USER_MESSAGE, actual.answer());
assertEquals("AGENT_LIMIT_EXCEEDED", actual.errorCode());
```

Also: `stubs.dirtyAnswer` present → do not call runner; `actual.answer = OutputGuard.sanitize(dirtyAnswer)`.

- [ ] **Step 2: Run test**

Run: `mvn -B -Dtest=EvalHarnessTest test`  
Expected: FAIL

- [ ] **Step 3: Implement harness**

`RequestTraceService.timed` must passthrough (same as AgentRunnerTest). Stub `ToolExecutor.execute` in order of `stubs.tools`. `schema_invalid` remains the executor error string so `AgentRunner.invoke` remaps copy. If `deadlineExpired`, call `runner.run(req, listener, AgentDeadline.alreadyExpired())`.

- [ ] **Step 4: Run test**

Run: `mvn -B -Dtest=EvalHarnessTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```text
git commit -m "feat: run eval cases through AgentRunner with stubs"
```

---

### Task 5: Golden fixtures + EvalGoldenRunner

**Files:**
- Create: `testdata/eval/` JSON files listed in technical §6 (all 12 ids)
- Create: `backend/src/test/java/com/huiyi/assistant/eval/EvalGoldenRunner.java`
- Test: same class

**Interfaces:**
- Consumes: Tasks 1–4
- Produces: JUnit test `allGoldensPass` that writes `target/eval-report.json` and `target/eval-report.md` relative to `backend/` (`Path.of("target/eval-report.json")`)

- [ ] **Step 1: Write EvalGoldenRunner that fails if dir missing**

```java
@Test
void allGoldensPass() throws Exception {
    Path dir = EvalCaseLoader.resolveEvalDir();
    List<EvalCase> cases = new EvalCaseLoader().load(dir);
    assertFalse(cases.isEmpty());
    // run harness per case with a shared mocked AgentRunner configured from each case's stubs
    // write report; assertTrue(rows.stream().allMatch(EvalReportRow::passed));
}
```

`resolveEvalDir`: `../testdata/eval` if exists else `testdata/eval`.

Each JSON must match technical §6 table. Schema-rejected expect `answerEquals` unknown-tool copy and `errorCode` null. Limit cases expect limit copy + `AGENT_LIMIT_EXCEEDED`. s4 blocked copy exact. s5 `answerContains` `当前知识库未找到足够信息`. iso forbidden `WS_A_SECRET`.

- [ ] **Step 2: Run**

Run: `mvn -B -Dtest=EvalGoldenRunner test`  
Expected: FAIL until fixtures + wiring exist

- [ ] **Step 3: Add 12 JSON files and wire runner**

Construct one `AgentRunner` per case (or reset stubs) so Mockito order is per-case. `timed` passthrough.

- [ ] **Step 4: Run**

Run: `mvn -B -Dtest=EvalGoldenRunner test`  
Expected: PASS；`backend/target/eval-report.json` 存在

- [ ] **Step 5: Commit**

```text
git commit -m "feat: add eval golden fixtures and CI runner"
```

---

### Task 6: CI job + docs

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/development.md` (v1.1 段补命令)
- Modify: `docs/roadmap.md` (实施计划路径)
- Modify: `docs/superpowers/specs/2026-10-18-v11-eval-and-regression.md` header: 实施计划已写
- Test: none. `git diff -- frontend` empty.

- [ ] **Step 1: Confirm frontend untouched**

Run: `git diff -- frontend`  
Expected: empty

- [ ] **Step 2: Add job `eval-goldens`**

Independent job, Java 17, `working-directory: backend`, `mvn -B -Dtest=EvalGoldenRunner test`, upload artifact `if: always()` paths `backend/target/eval-report.json` and `.md`.

- [ ] **Step 3: Full backend tests**

Run: `mvn -B test` (cwd `backend`)  
Expected: PASS

- [ ] **Step 4: Commit**

```text
git commit -m "ci: add eval-goldens job and document v1.1 runner"
```

---

## Self-review (spec coverage)

| Spec item | Task |
|---|---|
| `testdata/eval/*.json` | 5 |
| Loader reject tenant keys | 1 |
| Structured compare; schema ≠ limit code | 2 |
| Report redact | 3 |
| Harness + WorkspaceContext | 4 |
| Agent goldens + five scenes + isolation | 5 |
| CI job + artifacts | 6 |
| No frontend / no Flyway / no `/eval` | 6 diff + file map |

Gaps frozen out: Prompt 灰度、评测页、nightly e2e、ReAct 金标。
