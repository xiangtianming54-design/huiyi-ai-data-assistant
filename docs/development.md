# 开发指南

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | backend 编译运行 |
| Maven | 3.8+ | 依赖管理（项目级 `.mvn/settings.xml` 使用腾讯云镜像） |
| Python | 3.11+ | python-service（Docker 内为 3.11） |
| Node | 18+ | frontend 构建 |
| Docker Desktop | 最新 | MySQL / Redis / 服务编排 |

## 本地开发

```bash
# 基础设施（MySQL 3307 / Redis 6379）
docker compose up -d mysql redis

# Python Sidecar（8001）
cd python-service
python -m venv .venv && .venv\Scripts\activate
pip install -r requirements-dev.txt
uvicorn app.main:app --reload --port 8001

# Java 主服务（8080）
cd backend
mvn spring-boot:run

# 前端（5173，代理 /api → 8080）
cd frontend
npm install
npm run dev
```

`.env` 从 `.env.example` 复制并填入 LLM 四项配置（`.env` 已被 gitignore，密钥不入库）。

## 测试

```bash
# Python（pytest + 覆盖率）
cd python-service
pytest --cov=app --cov-report=term-missing

# Java（JUnit + Mockito + MockWebServer + H2）
cd backend
mvn test

# 评测金标（与 CI job eval-goldens 相同，cwd backend）
mvn -B -Dtest=EvalGoldenRunner test

# 端到端五场景（需全栈启动）
npm i playwright-core
node scripts/e2e-m5.js
```

## Git 工作流

```text
main            —— 稳定发布，里程碑打 tag
dev             —— 当前 GitHub 日常集成（CI 监听 main / dev）
feature/*       —— 功能分支；下一版：feature/v1.2-*
提交规范        —— feat:/fix:/test:/docs:/chore: + 中文摘要
```

企业迭代文档的目标日常分支是 `develop`。v0.6.1 **不重命名** 远程 `dev`，以免打断现有 PR；完整模型见 [roadmap.md](roadmap.md)。

## 目录速览

```text
backend/          Java 主服务（Spring Boot 3 + SpringAI + MyBatis-Plus）
  └── src/main/resources/prompts/    Prompt 模板（rag-system / text-to-sql-system / sql-result-summary / business-glossary）
python-service/   Python Sidecar（FastAPI）
  └── app/{api,core,services,tests}/ 接口层 / 配置与模型 / 业务实现 / 单测
frontend/         Vue3 + TS + Element Plus + ECharts
scripts/          init_mysql.sql（建表+只读账号）、init_sample_data.sql（确定性造数）、e2e-m5.js
docs/             架构 / API / 演示 / 开发文档 / 踩坑手册 + Postman 集合 + 截图
```

## 关键技术决策

1. **Java + Python 协同**：Java 主链路承载业务与工程能力，Python 承担文档/向量/数据处理（生态合适），WebClient 统一封装调用、超时与异常转换（4xx 透传 detail、5xx/断连降级）
2. **SQL 安全四道防线**：意图层拦截 → sqlglot 静态校验 → 只读账号执行 → 超时/行数/审计
3. **检索质量**：知识库向量（cosine≥0.5 进融合）+ BM25，RRF 合成 Top-K；Java 不再二次 0.5 过滤，列表为空才走未命中兜底
4. **Prompt 工程**：模板文件化（classpath）+ `{{var}}` 占位替换工具；Text-to-SQL 输出约定 JSON，三级提取兜底（JSON → 代码块 → 裸 SELECT），不抛栈
5. **响应式流式**：SSE 事件协议（session/token/references/error），前端 ReadableStream 解析，Pinia 中必须经响应式代理写入（裸对象引用不触发更新）

## v0.6 变更（2026-10-16）

**混合检索 + 近 10 轮摘要压缩：**

- 知识库 `huiyi_knowledge`：向量（cosine≥0.5）与 BM25 关键词（字符 bigram）各取 top_k×2，RRF 合成 Top-K；`intent_examples` 仍纯向量
- 关键词索引与 upsert/delete 同步并落盘；索引缺失时退回纯向量
- Java `RagService` 不再二次 0.5 过滤，避免打掉关键词-only 命中
- 改写窗口改为近 20 条原文（10 轮）；更早消息由 LLM 压成摘要写入 `chat_session_summaries`，增量更新；失败只用近 10 轮
- 已有库执行 `scripts/alter_v06_session_summaries.sql`

## v0.5.1 变更（2026-10-16）

**单一问题误拆修复**：LLM 把同义复述拆成两问时，回退为原问题，避免第二问丢指代、答案重复。

- Prompt 强约束：`question-decompose.md` 增加「复述 / 指代 / 同题」禁止拆分的反例
- `QuestionDeduper`：词面主题归并（抽出问句套话后主题相同或后句纯指代 → 合并）+ embedding cosine ≥ 0.85 合并；embedding 失败仅跳过该层，不影响词面判定
- Python `POST /api/vector/similarity`：两条文本 embedding cosine，供 Java 去重调用
- 实测：「什么是有效订单？它的定义是什么？」不再拆成两问；真复合「什么是有效订单？上个月销售额前三？」仍并行两路

## v0.5 变更（2026-10-16 已发布）

**复合问题分解与多路并行回答**：一句话包含多个问题时，每个子问题都得到正确形态的回答。

- `QuestionDecomposeService`：LLM 将复合问题拆为最多 2 个独立子问题（`prompts/question-decompose.md`，JSON 解析 + 限 2 + 异常回退单问题）
- `POST /api/chat/ask`：改写 → 分解 → 子问题**并行执行**（独立线程池）→ parts 响应；`/api/chat/intent` 预检以问号启发式识别 compound（避免单问题的额外 LLM 调用）
- 前端多气泡渲染：知识部分 Markdown + 引用，数据部分复用 `AnalysisResultCard` 富结果卡片
- 实测：「什么是有效订单？上个月销售额最高的 3 个商品？」→ 定义气泡（引用 0.74）+ 排名数据卡片并存

## v0.4 变更（2026-10-16 已发布）

**意图识别混合分层**（规则 → embedding → LLM 三层）：

- **L1 规则快路径**：强/弱关键词判数据查询，新增知识关键词（什么是/定义/口径等）判知识问答，命中即返回（~70% 请求 0ms）
- **L2 embedding 投票**：两类意图示例句（各 8 条，配置化）启动时写入独立 collection `intent_examples`；用户问题取 top3 最近邻按分数投票，阈值 0.55（实测无关键词问法「卖得最好的是哪些？」以 0.70 分正确判为 data_query）
- **L3 LLM 兜底**：低置信时调 LLM 分类（`prompts/intent-classify.md`），异常保守返回 knowledge_qa
- **可解释性**：每次判定的来源层与分数写入 `chat_messages.metadata_json`（intentLayer/intentScore）
- **灰度开关**：`app.intent.mode: rule | hybrid`，rule 模式完全回退 v1 行为
- Python 侧 `ChunkMetadata` 开放 extra 字段以在向量 metadata 中携带 intent 标签

后续评估：示例句在线调优、判定轨迹前端可视化。

## v0.3 变更（2026-10-15 已发布）

**多轮对话上下文**：支持「那第二名呢？」「那取消的订单算什么？」这类依赖上下文的追问。实现方案（Conversational RAG 经典模式）：

- 新增 `QueryRewriteService`：保存用户消息**之前**取最近 6 条历史，有历史时调用 LLM 将追问改写为独立问题（`prompts/condense-question.md`），无历史透传（首问零开销）、改写失败回退原问题
- 改写后的独立问题同时驱动意图识别、RAG 检索与 Text-to-SQL 生成，两链路共用一次改写
- 实测：「上个月销售额最高的 10 个商品」→「那第二名呢？」正确回答第二名；「什么是有效订单」→「那取消的订单算什么？」命中口径手册（0.62 分）

后续评估：历史感知的回答语气连贯、上下文压缩（长会话）。

## v0.2 变更（2026-10-15 已发布）

**统一对话入口**：原「智能问答」与「数据分析」两个页面合并为单一「智能助手」入口。实现方案（方案 A）：

- 后端新增 `POST /api/chat/intent` 意图预检接口；意图关键词从硬编码外置到 `app.intent.*` 配置
- 前端 chat store 先预检意图：`knowledge_qa` → SSE 流式（不变）；`data_query` → `/api/analysis/query`，气泡内嵌 `AnalysisResultCard` 富结果卡片（总结 + SQL 折叠块 + ECharts 图表 + 分页明细表）
- 侧边栏移除「数据分析」，`/analysis` 路由 302 到 `/chat`；回归脚本 `scripts/e2e-v02.js` 覆盖四场景

后续评估：LLM 意图识别（当前规则版）、多轮对话上下文。

## v0.7 变更（2026-10-16，标签 v0.7.0，已发布）

企业基础层：JWT 注册/登录/刷新/登出、工作区 RBAC、资源挂 `workspace_id`、Flyway V1–V6、审计、登录失败锁定、前端登录页，旧 `/api/chat|knowledge|analysis` 返回 404。

**回滚：** 切回镜像/代码标签 `v0.6.1`，恢复发版前 MySQL 与 `chroma_store` 备份，再做健康检查。v0.7 Flyway 会改 schema，不能只换镜像不还原库。

## v0.8 变更（2026-10-17，标签 v0.8.0，已发布）

请求级可观测：`X-Request-Id` / `X-Run-Id`，Chat/Analysis 写入 `assistant_runs` / `assistant_run_steps` / `llm_calls`（Flyway V7–V9），审计行带 `request_id`，Micrometer 出口 `/actuator/prometheus`，Python 访问日志透传 ID，`/api/health` 探测 mysql/redis/python。`cost_amount` 默认 0。规格见 [v0.8 开发文档](superpowers/specs/2026-10-16-v08-observability-and-cost.md)。

**回滚：** 切回标签 `v0.7.0`，恢复发版前 MySQL 备份后再起服务。V7–V9 已改 schema，不能只换镜像不还原库。

## v0.9 变更（2026-10-18，标签 v0.9.0，已发布）

Tool Harness + Router Runtime：RAG / Text-to-SQL / 数据画像收成五个只读工具，Chat 与 Analysis 门面委托同一 `AgentRunner`。工具步骤写入 `assistant_run_steps.tool_name`（Flyway V10）。未知工具返回「工具调用失败，请稍后重试。」。入口仍是 `/api/v1/workspaces/{id}/chat/stream` 与 `.../analysis/query`。规格见 [v0.9 开发文档](superpowers/specs/2026-10-17-v09-tool-harness-and-agent-runtime.md)。

**回滚：** 切回标签 `v0.8.0`，恢复发版前 MySQL 备份后再起服务。V10 已改 schema，不能只换镜像不还原库。

## v1.0 变更（2026-10-18，标签 v1.0.0，已发布）

Stable Agent Assistant P0：现网 `/chat/stream` 叠加 `tool.*` SSE；五工具 JSON Schema 拒绝；Context Budget（默认 24000 字符）；OutputGuard 剥堆栈/密钥；run 墙钟 90s；Flyway V11 仅加 `assistant_runs.error_code`。入口仍是 `chat/stream` 与 `analysis/query`，无 `/agent/runs`，无 ReAct。规格：[v1.0 开发文档](superpowers/specs/2026-10-18-v10-stable-agent-assistant.md)。技术设计：[v1.0 技术设计](superpowers/specs/2026-10-18-v10-stable-agent-assistant-technical.md)。计划：[v1.0 实施计划](superpowers/plans/2026-10-18-v10-stable-agent-assistant.md)。

**回滚：** 切回标签 `v0.9.0`，恢复发版前 MySQL 备份后再起服务。V11 已改 schema，不能只换镜像不还原库。

## v1.1 变更（2026-10-18，标签 v1.1.0，已发布）

Eval 评测集与回归 P0：`testdata/eval/` 金标 + Java `EvalGoldenRunner` + GitHub Actions 独立 job `eval-goldens` 上传 `eval-report.json/.md`。本地与 CI 同命令（cwd `backend`）：`mvn -B -Dtest=EvalGoldenRunner test`。无评测页、无新 API、无新表。Prompt 版本/灰度为 P1。规格：[v1.1 开发文档](superpowers/specs/2026-10-18-v11-eval-and-regression.md)。技术设计：[v1.1 技术设计](superpowers/specs/2026-10-18-v11-eval-and-regression-technical.md)。计划：[v1.1 实施计划](superpowers/plans/2026-10-18-v11-eval-and-regression.md)。

**回滚：** 切回标签 `v1.0.0`。本版 P0 不改 Flyway；若后续误加迁移，不能只换镜像不还原库。

## v1.2 变更（Enterprise Knowledge Plane，进行中）

工作区内知识空间：Flyway `V12__knowledge_spaces.sql` 建表并回填 `knowledge_docs.knowledge_space_id`。空间 HTTP `/api/v1/workspaces/{id}/knowledge-spaces`；文档上传/列表可选 `spaceId`；旧 `/knowledge/upload` 写入默认空间。RAG 把 ACTIVE 空间 id 列表传给 Python，`huiyi_knowledge` 检索用 `workspace_id` + `knowledge_space_id $in`。评测仍走 `EvalGoldenRunner`（含空间隔离金标）。启动时 `huiyi.knowledge.backfill-vector-space-ids`（默认 true）回填向量 metadata，Python 不可用不阻断启动。无前端空间 UI。规格：[v1.2 开发文档](superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane.md)。技术设计：[v1.2 技术设计](superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane-technical.md)。计划：[v1.2 实施计划](superpowers/plans/2026-10-19-v12-enterprise-knowledge-plane.md)。

## 常见坑（本仓库已踩过）

完整记录（现象 / 原因 / 处理 / 以后）见 [pitfalls.md](pitfalls.md)。修好非显而易见的问题后往该文档**顶部**追加，不要只留在对话里。

速查：

- Docker Desktop 里编后端镜像：腾讯 Maven DNS 会间歇失败；Compose `extra_hosts` + `TENCENT_MAVEN_IP`（见 pitfalls）
- 已有 v0.6 MySQL 卷跑 Flyway：必须 `baseline-on-migrate: true` 且 `baseline-version: 0`
- JWT 之后 SSE 结束光标不消失：放行 `ASYNC` / `ERROR` dispatcher
- e2e 里 Node 侧 `refresh` 会作废浏览器里的 `huiyi_refresh`，必须写回新 refresh
- 新用户在 `default` 工作区是 VIEWER，不能上传；要写知识先自建工作区
- 本机 MySQL 占用 3306：容器映射 3307（`.env` 的 `MYSQL_HOST_PORT` 可调）
- SpringAI `OpenAiApi` 默认补全路径含 `/v1`：base-url 含 `/v1` 时需显式 `completionsPath("/chat/completions")`
- 多数据源需显式 `@Primary`，否则 MyBatis-Plus 可能绑错只读库
- Pinia SSE 追加必须走响应式代理（数组下标），裸对象引用不触发视图更新
- 不要 `@Qualifier("jdbcTemplate")`：只有 `readOnlyJdbcTemplate` 时 Boot 不会再造该 Bean
- worktree 里不要 `docker compose up` 整套：项目名会变，容器名与现网栈冲突
