# HuiYi v1.1 · Eval 评测集与回归（仓库规格）

> 版本：`v1.1.0`  
> 状态：**已发布**（标签 `v1.1.0`；P0 D01–D13 已拍板；P1 Prompt 版本/灰度只写入规格，本版不含）  
> 技术设计：[2026-10-18-v11-eval-and-regression-technical.md](2026-10-18-v11-eval-and-regression-technical.md)  
> 实施计划：[../plans/2026-10-18-v11-eval-and-regression.md](../plans/2026-10-18-v11-eval-and-regression.md)  
> 基线：`huiyi-ai-data-assistant v1.0.0`（标签 `v1.0.0`）  
> 路线图名称：Eval & Prompt Governance。本版 **P0 只做评测集与回归门闩**，不做 Prompt 治理。  
> 对照：桌面企业迭代文档 v1.1 章节；[roadmap.md](../../roadmap.md)；v1.0 规格 D09（无评测页）。  
> 拍板：D01–D13，见 §8。改写或实施时 **不得推翻** 该表。

**改写原则（已拍板）：** 方向跟路线图「评测集、回归报告」走；Prompt/模型版本与灰度、评测页、评测平台 **不按路线图全文一次做完**。

---

## 1. 版本定位

v1.1 P0 把助手行为变成 **可版本化、可重复、失败可对照** 的评测集，并让 GitHub Actions 用确定性金标挡住回归。

它不是：

- 新的聊天/Agent HTTP；
- 评测后台或报告页面；
- Prompt 版本库、灰度、多模型路由；
- 把 `e2e-m5.js` / `e2e-v07.js` 变成必过 CI。

现网入口仍是 `POST /api/v1/workspaces/{id}/chat/stream` 与 `.../analysis/query`。默认 Planner 仍是 Router。五工具名不变。

---

## 2. P0 必须完成

1. **评测夹具** 放仓库根 `testdata/eval/*.json`（一条用例一个文件或按套分组，实施计划钉文件名）。
2. **独立 Java runner** 读取该目录，注入固定测试工作区上下文，按 §5 对照，写出报告。
3. **报告**：`eval-report.json` 与 `eval-report.md`（路径由实施计划钉；CI 作为 Artifact 上传）。
4. **GitHub Actions** 新增独立 job（名称建议 `eval-goldens`）：失败挡 `main` / `dev` 的 push 与 PR；上传两份报告。
5. **现有 JUnit** 保留；本版评测集不替代 `AgentRunnerTest` 等单测。
6. **覆盖面（D03）**：
   - 五场景的 **结构化契约**（意图、工具链/冻结句、`error_code` 分流），用打桩 LLM，不打真模型；
   - v1.0 Agent 金标：未知工具用户句、Schema `REJECTED` 用户句、`tool.*` 事件名、预算/超时用户句、`error_code=AGENT_LIMIT_EXCEEDED` 分流、输出无堆栈；
   - 工作区隔离的 **确定性** 用例（两条工作区上下文互不读到对方数据）。Compose 脚本 `e2e-v07.js` 仍只本地跑。

## 3. P0 明确不做

- 评测页、简单评测报告页面、评测平台；
- 新公开 API（含 `/eval`、`/agent/runs`）；
- 新 Flyway 表（含 `eval_runs` / `eval_cases`），不写 `assistant_runs` 当评测流水；
- 前端树、Python sidecar 行为改动；
- ReAct 金标、`mode` 列、前端 `toolCalls`；
- Prompt 文件版本、灰度、embedding/LLM-as-judge；
- 把 `e2e-m5.js` / `e2e-v07.js` 挂进 GitHub Actions（必过或 nightly 都不做）；
- 夹具或报告里出现完整 Prompt、密钥、堆栈。

P1（只写不本周开工）：Prompt/模型版本与灰度；可选再议评测页、夜间 Compose e2e。

---

## 4. 夹具与租户

- 目录：仓库根 `testdata/eval/`。
- 夹具 **禁止** 出现 `workspaceId` / `userId` 键（与 Tool Schema Strip、现网只从 `WorkspaceContextHolder` 取租户一致）。
- Runner **必须** 在跑每条用例前 `WorkspaceContextHolder.set(...)` 固定测试工作区（成员角色 `VIEWER` 即可覆盖五只读工具）；隔离用例切换两个上下文。
- 用例跑完 `clear()`。
- 打桩：不访问真 LLM、不访问现网 MySQL/Redis/Python；外部依赖一律 stub。
- 本地与 CI **同一入口**：从 `backend/` 调 Maven/Java 跑该 runner（命令字符串由实施计划钉；PowerShell 禁止 `&&`，多测试用 `"-Dtest=A,B"`）。

---

## 5. 对照规则（D05）

判过只看结构化字段，不把自由生成长答案当文底金标。

| 种类 | 对照 |
|---|---|
| 冻结用户句 | 全文相等：`工具调用失败，请稍后重试。`；`当前问题较复杂，请缩小范围后重试。` |
| Schema / 未知工具 | 用户句同上；该步 `REJECTED` 或 `FAILED`；**不得** 写 `error_code=AGENT_LIMIT_EXCEEDED` |
| 预算 / run 90s 墙 | 用户句为超限句；`error_code=AGENT_LIMIT_EXCEEDED` |
| SSE 事件名 | 允许 `session` / `token` / `references` / `error` / `tool.started` / `tool.completed` / `tool.failed`；禁止 `answer.token`、`step` |
| analysis JSON | 无 `toolCalls` 字段 |
| 输出 Guard | 实际用户可见字符串不得含 `Exception in thread`、`at com.`、`Traceback (most recent call last)`、`sk-`+8 位以上、`API_KEY=`，以及普通 `java.lang.*Exception` / `org.springframework` 堆栈帧 |
| 隔离 | 工作区 A 的夹具数据不得出现在工作区 B 的结构化结果里 |
| 五场景契约 | 只断言 intent、是否走知识/SQL 链、冻结失败句、`error_code` 有无；不断言模型临场长答案 |

任一条失败 → runner 非 0 退出 → CI job 红。

---

## 6. 报告与 CI（D08、D10）

报告 **只含**：

- case id；
- 期望结构化字段 / 实际结构化字段；
- 是否通过；
- 可选：短错误码（如 `mismatch.error_code`），不含堆栈正文。

报告 **禁止**：密钥、堆栈原文、完整 Prompt、模型完整原文、`.env`。

CI：

- 现有 `backend` / `python` / `frontend` job 保留；
- 新增独立 `eval-goldens`（或同等名称），`runs-on: ubuntu-latest`，Java 17，与现网一致；
- 失败挡合并；
- `actions/upload-artifact` 上传 `eval-report.json` 与 `eval-report.md`（失败也尽量上传，便于对照）。

不把 Compose、Docker 服务、模型 API Key 写入该 job。

---

## 7. 验收

- [ ] `testdata/eval/` 含 D03 所列类别（五场景契约、Agent 金标、隔离），且无租户键。
- [ ] 本地从 `backend/` 跑 runner，与 CI 同命令，产出两份报告。
- [ ] GitHub Actions `eval-goldens` 失败则 PR/push 红；报告可从 Artifact 下载。
- [ ] `git diff -- frontend` 空；Python 行为无强制改动。
- [ ] 无新 Flyway 文件；无 `/eval` 或 `/agent/runs` 映射。
- [ ] 现有 `mvn -B test` 仍绿。
- [ ] `scripts/e2e-m5.js` / `e2e-v07.js` 仍可在有 Compose 时本地跑，但不是本版 CI 门闩。

---

## 8. 需求评审决策日志（已冻结）

拍板方式：表格逐项确认（仓库规则 `decision-table-confirm`）。下表是已选项，不是待选建议。

| 编号 | 决策项 | 已拍板 |
|---|---|---|
| D01 | v1.1 范围 | **B** P0 评测集与回归；Prompt 版本/灰度 P1 |
| D02 | 评测放哪 | **B** 仓库夹具 + 脚本；CI Artifact；无页面、无新公开 API |
| D03 | 覆盖哪些 | **B** 五场景 + v1.0 Agent 金标 + 隔离 |
| D04 | CI 怎么跑 | **C** Actions 必过确定性金标；Compose e2e 不挡合并 |
| D05 | 怎么判过 | **B** 结构化对照；长答案不作文底相等 |
| D06 | 数据落点 | **B** 无新表；夹具进仓库；报告仅 Artifact |
| D07 | 谁执行 | **B** `testdata/eval/*.json` + 独立 Java runner；JUnit 保留 |
| D08 | CI job | **B** 独立 job，失败挡合并，上传 json/md |
| D09 | 租户 | **B** Runner 注入上下文；夹具禁止 `workspaceId`/`userId` |
| D10 | 报告内容 | **B** 仅 case id 与结构化字段；禁 Prompt/密钥/堆栈 |
| D11 | 非目标 | **B** 无评测页/新 API/新表/前端 Python/ReAct 金标/Prompt 灰度；e2e 不进 Actions |
| D12 | 目录与入口 | **A** 根目录 `testdata/eval/`；本地与 CI 同一 Java/Maven 入口 |
| D13 | 规格形态 | **B** 本文（短篇）；不套桌面 17 章 |

依赖：

- D01=B → Prompt 灰度不得写入 P0 验收。
- D02=B + D11=B → 不得新增 Controller 或前端评测页。
- D04=C + D11=B → 不得把 `e2e-m5.js` 写成必过 CI。
- D03=B + D04=C → 五场景与隔离在 P0 以 **打桩金标** 交付；Compose 脚本仍本地。
- D09=B → 夹具 schema 不得声明租户键。
- D10=B → 报告生成器必须过滤 Prompt/密钥/堆栈。
