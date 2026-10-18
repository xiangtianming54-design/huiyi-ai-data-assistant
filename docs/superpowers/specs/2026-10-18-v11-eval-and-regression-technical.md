# HuiYi v1.1 · Eval 评测集与回归 技术设计

> 版本：`v1.1.0`  
> 状态：**规格已冻结，实施计划已写，P0 实现已落地**  
> 产品规格：[2026-10-18-v11-eval-and-regression.md](2026-10-18-v11-eval-and-regression.md)  
> 基线代码：`v1.0.0` 包 `com.huiyi.assistant.agent`  
> 口径：D01–D13。本文只写 **怎么改代码**，不重新开范围。

P1 Prompt 版本/灰度只在 §8 留接口，本周不开工。

---

## 1. 设计目标与差量

在 **不换 HTTP 入口、不加表、不加 Controller** 的前提下：

1. 用仓库根 `testdata/eval/*.json` 描述确定性金标；
2. Java 在打桩依赖上跑现网 `AgentRunner`（或同等产品路径）；
3. 按结构化字段对照，写出 `eval-report.json` / `.md`；
4. GitHub Actions 独立 job `eval-goldens` 失败挡合并。

| 路线图说法 | 本版技术落点 |
|---|---|
| 评测集 | `testdata/eval/*.json` |
| 回归报告 | `backend/target/eval-report.json` 与 `.md` + Actions Artifact |
| 评测页 / 评测 API | 不做 |
| Prompt 治理 | P1，本周无类 |

禁止平行再造一套 Agent。金标必须走 `AgentRunner.run(AgentRequest, ToolEventListener, AgentDeadline)`。

---

## 2. 现网基线（代码事实）

| 类 | 本版角色 |
|---|---|
| `AgentRunner` | 被测编排；勿为评测改用户句 |
| `AgentRequest` | `(entrypoint, question, sessionId)` |
| `AgentResult` | `decision` / `answer` / `sql` / `errorCode` / `analysis` |
| `ToolEventListener` | 采集 `tool.*` |
| `WorkspaceContextHolder` | 租户唯一来源 |
| `OutputGuard` | 对 `answer` 再 `sanitize` 后对照 Guard 金标 |
| `.github/workflows/ci.yml` | 现有 backend / python / frontend 三 job |

冻结句（不得改字）：

- `AgentRunner.UNKNOWN_TOOL_USER_MESSAGE` = `工具调用失败，请稍后重试。`
- `AgentRunner.LIMIT_USER_MESSAGE` = `当前问题较复杂，请缩小范围后重试。`
- `AgentRunner.ERROR_AGENT_LIMIT` = `AGENT_LIMIT_EXCEEDED`
- 修改拦截：`该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。`
- 知识未命中：`RagService.NO_HIT_FALLBACK`

---

## 3. 包与调用链

新包：`com.huiyi.assistant.eval`（`backend/src/main/java`）。测试在 `backend/src/test/java/com/huiyi/assistant/eval`。

```text
EvalGoldenRunner（JUnit，CI 入口）
  → EvalCaseLoader.load(dir)          拒租户键
  → 对每条 EvalCase：
        WorkspaceContextHolder.set(fixed VIEWER context 或隔离用的 A/B)
        EvalHarness.run(case, AgentRunner)
        EvalComparer.compare(case.expect, actual)
        WorkspaceContextHolder.clear()
  → EvalReportWriter.write(results, target/eval-report.json, .md)
  → 任一条 fail → 测试失败（非 0）
```

`AgentRunner` 的协作件全部 Mockito 打桩：`QueryRewriteService`、`IntentService`、`RouterPlanner`、`ToolExecutor`、`RequestTraceService.timed` 透传、`LlmService`、`PromptTemplateLoader`。不连 MySQL / Redis / Python / 真 LLM。

---

## 4. 夹具 JSON（字节级）

每个文件一个用例。文件名 = `id` + `.json`。UTF-8。

```json
{
  "id": "agent-schema-rejected",
  "entrypoint": "analysis_query",
  "question": "上个月销售额最高的 10 个商品是什么？",
  "sessionId": "eval-s1",
  "workspace": "default",
  "stubs": {
    "rewrite": "上个月销售额最高的 10 个商品是什么？",
    "intent": "data_query",
    "tools": [
      { "name": "sql.generate", "ok": true, "data": { "sql": "select 1" } },
      { "name": "sql.validate", "ok": false, "error": "schema_invalid" }
    ]
  },
  "expect": {
    "intent": "data_query",
    "answerEquals": "工具调用失败，请稍后重试。",
    "errorCode": null,
    "analysisHasToolCalls": false,
    "toolEvents": [
      { "name": "sql.generate", "phase": "started" },
      { "name": "sql.generate", "phase": "completed", "status": "SUCCESS" },
      { "name": "sql.validate", "phase": "started" },
      { "name": "sql.validate", "phase": "failed", "status": "REJECTED" }
    ],
    "forbiddenSubstrings": []
  }
}
```

字段约定：

| 字段 | 规则 |
|---|---|
| `workspace` | 仅 `default` / `A` / `B`。Runner 映射到固定 id。禁止数字租户键 |
| `stubs.intent` | `knowledge_qa` / `data_query` |
| `stubs.tools` | 按调用顺序；`ToolExecutor.execute(name,…)` 依次返回 |
| `expect.errorCode` | JSON `null` 表示必须为空；字符串则全等 |
| `expect.answerEquals` | 缺省则不对照全文 |
| `expect.answerContains` | 可选字符串数组，皆须出现 |
| `expect.forbiddenSubstrings` | 皆不得出现 |
| `expect.toolEvents` | 若出现则实际事件序列必须 **前缀或全等**（允许后面还有 profile） |
| 任意 `workspaceId` / `userId` | **加载即失败** |

`stubs.tools` 未覆盖的 `execute`：返回 `ToolResult.fail("schema_invalid")` 不得误用；未 stub 则 fail `eval_stub_missing`（对照时记失败，不抛给用户句）。

---

## 5. 对照实现

`EvalActual`：

- `intent` ← `result.decision().intent().getCode()`
- `answer` ← `OutputGuard.sanitize(result.answer())`（null 当 `""`）
- `errorCode` ← `result.errorCode()`
- `analysisHasToolCalls` ← analysis JSON 是否含键 `toolCalls`（用 Jackson 树；analysis 为 null 则 false）
- `toolEvents` ← 监听器记录的 `(name, phase, status)`

`EvalComparer`：只比 `expect` 里出现的键。`errorCode` 键存在且值为 JSON null → 实际必须为 null。

报告行：`{ "id", "passed", "expected", "actual", "mismatch" }`。`mismatch` 为短码（`intent` / `answerEquals` / `errorCode` / `toolEvents` / `forbiddenSubstrings` / `analysisHasToolCalls`）。`expected`/`actual` 只回显对照过的结构化字段，不含 Prompt。

`EvalReportWriter` 写 JSON 数组 + Markdown 表。扫描将写入的字符串：若含 `sk-`+8、`API_KEY=`、`Exception in thread`、`Traceback (most recent call last)`，整段换成 `[redacted]`。

---

## 6. 必收录夹具（id 固定）

| 文件 | 覆盖 |
|---|---|
| `scene-s1-knowledge.json` | 五场景 1：intent `knowledge_qa`；工具 `knowledge.search` started+completed |
| `scene-s2-top10.json` | 五场景 2：`data_query`；generate/validate/execute/profile 顺序 |
| `scene-s3-channel.json` | 五场景 3：同 SQL 链 |
| `scene-s4-blocked.json` | 五场景 4：`answerEquals` 修改拒绝句；不调用 `sql.generate` |
| `scene-s5-nohit.json` | 五场景 5：`answerContains` 未命中兜底 |
| `agent-unknown-tool.json` | `invoke` 未注册名 → 未知工具句；`errorCode` null |
| `agent-schema-rejected.json` | validate `schema_invalid` → 未知工具句；`errorCode` null；`REJECTED` |
| `agent-tool-event-names.json` | 成功 SQL 链事件名仅为允许集合 |
| `agent-limit-deadline.json` | `AgentDeadline.alreadyExpired()` → 超限句 + `AGENT_LIMIT_EXCEEDED` |
| `agent-limit-budget.json` | rewrite 抛 `PromptBudgetExceededException` → 同上 |
| `agent-output-guard.json` | stub 答案含 `Exception in thread`；sanitize 后 forbidden 不含该串 |
| `iso-workspace-b-isolated.json` | `workspace: "B"`；stub 数据带标记 `WS_A_SECRET`；结果 forbidden 含该标记 |

`agent-unknown-tool` / `agent-output-guard` 可以只测 `invoke` 或 harness 内对 answer 走 Guard；计划钉：unknown 走 `run`+未注册工具名由 `ToolExecutor` throw `UnknownToolException`；Guard 走 harness 对脏 answer 的 sanitize（若 `run` 不易注入脏答案，允许 harness 在对照前对 `answerEquals` 缺省的用例把 stub `dirtyAnswer` 经 Guard 写入 actual）。

更干净：**Guard 夹具** `stubs.dirtyAnswer` 存在则 **不调用** `AgentRunner`，只 `OutputGuard.sanitize(dirtyAnswer)` 对照 `forbiddenSubstrings`。其它夹具走 `AgentRunner`。

隔离：Harness 为 `A` 设 `workspaceId=1`，`B` 设 `workspaceId=2`，`default` 设 `workspaceId=9`；`userId=1`；角色 `VIEWER`。夹具不得自带这些数字。

---

## 7. CI

`.github/workflows/ci.yml` 增加 job `eval-goldens`：

- `runs-on: ubuntu-latest`；Java 17 Temurin；`cache: maven`
- `working-directory: backend`
- `mvn -B -Dtest=EvalGoldenRunner test`
- `if: always()` 上传 `backend/target/eval-report.json` 与 `backend/target/eval-report.md`

cwd 为 `backend/` 时夹具目录 = `Paths.get("..", "testdata", "eval")`。若该目录不存在，再试 `Paths.get("testdata", "eval")`。

PowerShell 本地：`mvn -B -Dtest=EvalGoldenRunner test`（cwd `backend`）。不要 `&&`。

---

## 8. P1 接口（本周不实现）

- Prompt 版本目录 / 灰度 header；
- `/eval` HTTP；
- `eval_runs` 表；
- nightly Compose e2e job；
- LLM-as-judge。

---

## 9. 测试矩阵

| 测试 | 断言 |
|---|---|
| `EvalCaseLoaderTest.rejectsTenantKeys` | 含 `workspaceId` 的 JSON 抛异常 |
| `EvalComparerTest.schemaInvalidNotLimitCode` | 未知工具句 + `errorCode` null 通过；若 actual 为 `AGENT_LIMIT_EXCEEDED` 失败 |
| `EvalReportWriterTest.redactsSecrets` | 写入后文件不含 `sk-abcdefghijk` |
| `EvalHarnessTest.deadlineUsesLimitCopy` | alreadyExpired → 超限句 + 超限码 |
| `EvalGoldenRunner` | 加载全部夹具；全过才绿；写出两份报告 |

`git diff -- frontend` 必须空。无新 Flyway。
