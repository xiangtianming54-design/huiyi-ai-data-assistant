# HuiYi AI Data Assistant

> 会意 · 企业经营数据智能分析助手

当前发布：**v1.3.0 Async Ingestion**（标签 `v1.3.0`）。规格 [v1.3 开发文档](docs/superpowers/specs/2026-10-20-v13-async-ingestion.md)。基线对照 [docs/baseline.md](docs/baseline.md) · 踩坑 [docs/pitfalls.md](docs/pitfalls.md) · 路线图 [docs/roadmap.md](docs/roadmap.md)

基于 **Java + Python 协同架构** 的企业经营数据智能分析助手，支持 **RAG 知识库问答** 与 **Text-to-SQL 数据分析** 两类核心能力。

![demo](docs/images/demo.gif)

## 项目亮点

- **Java 主服务 + Python Sidecar 分层协同**：Java 承载业务接口、Prompt 编排、LLM 调用、SQL 执行与结果组装；Python 承载文档解析、分块、向量检索、SQL 静态校验与数据画像
- **RAG 知识库问答**：Markdown / PDF / Word 解析 → 段落优先分块 → Chroma 向量化 → **向量 + BM25 RRF 混合检索** → 带引用来源的回答；无命中确定性兜底，不编造业务口径
- **Text-to-SQL**：LLM 生成 SQL → **四道安全防线**（意图层拦截 → sqlglot 静态校验 → 只读账号执行 → 超时/行数/审计）→ pandas 数据画像与图表推荐
- **SSE 流式问答**：自洽事件协议（session/token/references/error），前端 ReadableStream 逐 token 渲染
- **工程化**：Docker Compose 一键启动、JWT / 工作区 RBAC / Flyway / 审计、请求级 Request/Run ID 与工具步骤 Trace、`/actuator/prometheus`；RAG / SQL / 画像收成只读工具，由 Router `AgentRunner` 编排；敏感配置全环境变量化；GitHub Actions CI（`mvn verify` / `pytest --cov` / `npm run build` / `eval-goldens`）

## Architecture

![architecture](docs/images/architecture.png)

详见 [docs/architecture.md](docs/architecture.md)。

## Quick Start

### 前置要求

| 工具 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.8+ |
| Python | 3.11+ |
| Node | 18+ |
| Docker Desktop | 最新稳定版 |

### 方式一：Docker Compose 一键启动

```bash
cp .env.example .env   # 填入 LLM_* 与 JWT_SECRET（至少 32 字节）
docker compose up -d
```

启动后访问 **http://localhost:5173**。首次使用请注册账号（默认加入 `default` 工作区，角色由 `DEFAULT_MEMBER_ROLE` 决定，默认 VIEWER；任何登录用户可创建自己的工作区成为 OWNER）。

> 说明：容器 MySQL 默认映射到宿主机 **3307** 端口（规避本机 MySQL 占用的 3306），
> 可通过 `.env` 中 `MYSQL_HOST_PORT` 修改。LLM 推荐硅基流动（有免费模型额度，
> 配置示例见 `.env.example` 注释与 docs/development.md）。

### 方式二：本地开发模式

```bash
# 1. 启动基础设施（MySQL + Redis）
docker compose up -d mysql redis

# 2. 启动 Python Sidecar（端口 8001）
cd python-service
python -m venv .venv && .venv\Scripts\activate   # Windows
pip install -r requirements-dev.txt
uvicorn app.main:app --reload --port 8001

# 3. 启动 Java 主服务（端口 8080；若与容器后端冲突可指定 SERVER_PORT=8081）
cd backend
mvn spring-boot:run

# 4. 启动前端开发服务器（端口 5173，代理 /api 到 8080）
cd frontend
npm install
npm run dev
```

### 健康检查

```bash
curl http://localhost:8080/api/health   # Java 主服务 → {"code":0,"message":"ok"}
curl http://localhost:8001/health       # Python Sidecar → {"status":"ok"}
```

## Demo Questions

上传 `sample-data/knowledge/business-glossary.md` 后，依次体验五个场景（详见 [docs/demo.md](docs/demo.md)）：

| 场景 | 提问 | 预期 |
|---|---|---|
| 口径问答 | 什么是有效订单？ | 引用知识库回答并附来源 |
| 销售排名 | 上个月销售额最高的 10 个商品是什么？ | SQL + 表格 + 柱状图 + 总结 |
| 渠道分析 | 华东地区各渠道的有效订单量是多少？ | 分渠道表格与图表 |
| 安全拦截 | 删除所有销售订单。 | 安全策略拒绝，仅支持只读查询 |
| 未命中兜底 | 公司年假有多少天？ | 明确提示知识库未找到，不编造 |

## Tech Stack

| 层 | 技术 |
|---|---|
| 后端主服务 | Java 17 / Spring Boot 3 / SpringAI / MyBatis-Plus / MySQL / Redis |
| Python Sidecar | Python 3.11 / FastAPI / Chroma / pandas / sqlglot / PyMuPDF |
| 前端 | Vue3 / TypeScript / Vite / Element Plus / ECharts / markdown-it |
| LLM | OpenAI 兼容 API（chat + embedding，示例使用硅基流动免费模型） |
| 部署 | Docker / Docker Compose / GitHub Actions |

## API Overview

| 端点 | 说明 |
|---|---|
| `GET /api/health` | Java 健康检查 |
| `POST /api/v1/auth/register` | 注册并签发 Token |
| `POST /api/v1/auth/login` | 登录 |
| `POST /api/v1/auth/refresh` | 刷新 Token |
| `POST /api/v1/auth/password` | 修改密码（撤销 refresh，token_version+1） |
| `GET /api/v1/me` | 当前用户与工作区 |
| `POST /api/v1/workspaces` | 创建工作区（创建者成为 OWNER） |
| `GET /api/v1/workspaces/{id}/knowledge/docs` | 知识库文档列表（分页） |
| `POST /api/v1/workspaces/{id}/knowledge/upload` | 上传知识文档 |
| `POST /api/v1/workspaces/{id}/chat/stream` | RAG 问答（SSE） |
| `POST /api/v1/workspaces/{id}/analysis/query` | Text-to-SQL 数据分析 |
| `GET /health`（8001） | Python Sidecar 健康检查 |
| `POST /api/vector/search`（8001） | 向量检索（知识库必须带 `workspace_id`） |

完整契约见 [docs/api.md](docs/api.md)、[docs/openapi-v1.yaml](docs/openapi-v1.yaml)，Postman 集合见 [docs/postman](docs/postman)。

E2E：`node scripts/e2e-m5.js`（五场景，先注册登录）；`node scripts/e2e-v07.js`（认证与隔离）。Chroma 旧数据重建：`python -m app.tools.rebuild_knowledge_index --mapping mapping.json`。

## 目录结构

```text
huiyi-ai-data-assistant/
├── backend/            # Java 主服务（Spring Boot 3）
├── python-service/     # Python Sidecar（FastAPI）
├── frontend/           # Vue3 前端
├── docs/               # 架构 / API / 演示 / 开发文档 + 截图
├── sample-data/        # 示例知识库文档
├── scripts/            # 数据库初始化 / 造数 / e2e 验证 / 架构图渲染 / GIF 生成
├── docker-compose.yml  # 一键编排
└── .env.example        # 环境变量模板
```

## Roadmap

- [x] **M1 骨架可运行**：双端健康检查 + Compose 一键启动
- [x] **M2 知识库链路贯通**：上传 → 解析 → 分块 → 向量检索
- [x] **M3 RAG 问答可用**：引用来源 / SSE 流式 / 会话落库
- [x] **M4 数据查询可用**：Text-to-SQL + 四道安全防线
- [x] **M5 端到端可演示**：五场景前端演示（截图见 [docs/images](docs/images)）
- [x] **M6 开源就绪**：测试 / 文档 / Demo GIF / CI 齐备

- [x] **v0.2 统一对话入口**：单一聊天入口按意图路由 —— 知识问答走 SSE 流式，数据查询在气泡内嵌 SQL/图表/明细富结果卡片（见 docs/development.md「v0.2 变更」）

- [x] **v0.3 多轮对话上下文**：查询改写将追问转为独立问题，RAG 与 Text-to-SQL 两链路共用（见 docs/development.md「v0.3 变更」）

- [x] **v0.4 意图识别混合分层**：规则快路径 → embedding 示例句投票 → LLM 兜底，判定轨迹可解释（见 docs/development.md「v0.4 变更」）

- [x] **v0.5 复合问题分解**：一句话多问题 → 分解 → 并行多路回答，知识气泡与数据卡片并存（见 docs/development.md「v0.5 变更」）

- [x] **v0.5.1 误拆去重**：同义复述 / 指代回退单问题（词面主题 + embedding cosine），真复合仍并行（见 docs/development.md「v0.5.1 变更」）

- [x] **v0.6 混合检索与会话压缩**：知识库向量 + BM25 RRF 融合；改写保留近 10 轮，更早对话 LLM 摘要（见 [docs/v0.6-design.md](docs/v0.6-design.md)）
- [x] **v0.6.1 Baseline Hardening**：稳定基线、补齐发布流程（见 [docs/baseline.md](docs/baseline.md)、[docs/roadmap.md](docs/roadmap.md)）

- [x] **v0.7.0 Enterprise Foundation**：认证、工作区、RBAC、资源隔离、审计、Flyway、`/api/v1`（见 [docs/development.md](docs/development.md)）
- [x] **v0.8.0 Observability & Cost**：Request/Run ID、请求级 Trace、Metrics、Token/`cost_amount`、Python 访问日志、健康检查（[规格](docs/superpowers/specs/2026-10-16-v08-observability-and-cost.md)；不做 Grafana / Agent Loop）
- [x] **v0.9.0 Tool Harness & Agent Runtime**：内存 ToolRegistry、Router AgentRunner、五个只读工具、Flyway V10 `tool_name`（[规格](docs/superpowers/specs/2026-10-17-v09-tool-harness-and-agent-runtime.md)）
- [x] **v1.0.0 Stable Agent Assistant**：`tool.*` SSE、五工具 JSON Schema、Context Budget、OutputGuard、90s run 帽、Flyway V11 `error_code`（[规格](docs/superpowers/specs/2026-10-18-v10-stable-agent-assistant.md)；P1 ReAct 不在本版）
- [x] **v1.1.0 Eval & Prompt Governance**：`testdata/eval/` 金标、`EvalGoldenRunner`、CI `eval-goldens` 报告 Artifact（[规格](docs/superpowers/specs/2026-10-18-v11-eval-and-regression.md)；P1 Prompt 灰度不在本版）
- [x] **v1.2.0 Enterprise Knowledge Plane**：工作区内知识空间、文档归属、RAG 按空间过滤、Flyway V12（[规格](docs/superpowers/specs/2026-10-19-v12-enterprise-knowledge-plane.md)；异步解析 / OSS / 细权限 / 前端空间 UI 不在本版）
- [x] **v1.3.0 Async Ingestion**：上传立刻返回、本地落盘、`DOC_PARSE` 异步、失败重试、Flyway V13（[规格](docs/superpowers/specs/2026-10-20-v13-async-ingestion.md)；MinIO / hash / 前端轮询不在本版）

按企业迭代文档演进：下一迭代 **v1.4.0** Production Ready。不把未做能力写成当前功能。

## License

[MIT](LICENSE)
