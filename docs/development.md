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

# 端到端五场景（需全栈启动）
npm i playwright-core
node scripts/e2e-m5.js
```

## Git 工作流

```text
main            —— 始终保持可运行，里程碑打 tag（m1-skeleton … m6-release）
dev             —— 日常集成分支
feature/pN-xxx  —— 每个阶段一个 feature 分支，完成后合并 dev
提交规范        —— feat:/fix:/test:/docs:/chore: + 中文摘要
```

## 目录速览

```text
backend/          Java 主服务（Spring Boot 3 + SpringAI + MyBatis-Plus）
  └── src/main/resources/prompts/    Prompt 模板（rag-system / text-to-sql-system / sql-result-summary / business-glossary）
python-service/   Python Sidecar（FastAPI）
  └── app/{api,core,services,tests}/ 接口层 / 配置与模型 / 业务实现 / 单测
frontend/         Vue3 + TS + Element Plus + ECharts
scripts/          init_mysql.sql（建表+只读账号）、init_sample_data.sql（确定性造数）、e2e-m5.js
docs/             架构 / API / 演示 / 开发文档 + Postman 集合 + 截图
```

## 关键技术决策

1. **Java + Python 协同**：Java 主链路承载业务与工程能力，Python 承担文档/向量/数据处理（生态合适），WebClient 统一封装调用、超时与异常转换（4xx 透传 detail、5xx/断连降级）
2. **SQL 安全四道防线**：意图层拦截 → sqlglot 静态校验 → 只读账号执行 → 超时/行数/审计
3. **检索质量**：cosine 相似度 + 0.5 阈值过滤低分命中，保证「未命中」场景的确定性兜底
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

## 常见坑（本仓库已踩过）

- Docker 构建容器内部分国内 CDN 不可达：依赖镜像统一用腾讯云（Maven）/ npmmirror（npm）
- 本机 MySQL 占用 3306：容器映射 3307（`.env` 的 `MYSQL_HOST_PORT` 可调）
- SpringAI `OpenAiApi` 默认补全路径含 `/v1`：base-url 含 `/v1` 时需显式 `completionsPath("/chat/completions")`
- 多数据源需显式 `@Primary`，否则 MyBatis-Plus 可能绑错只读库
