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
# Python（pytest + 覆盖率，当前 90 用例 / 97%）
cd python-service
pytest --cov=app --cov-report=term-missing

# Java（JUnit + Mockito + MockWebServer + H2，当前 31 用例）
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

## 常见坑（本仓库已踩过）

- Docker 构建容器内部分国内 CDN 不可达：依赖镜像统一用腾讯云（Maven）/ npmmirror（npm）
- 本机 MySQL 占用 3306：容器映射 3307（`.env` 的 `MYSQL_HOST_PORT` 可调）
- SpringAI `OpenAiApi` 默认补全路径含 `/v1`：base-url 含 `/v1` 时需显式 `completionsPath("/chat/completions")`
- 多数据源需显式 `@Primary`，否则 MyBatis-Plus 可能绑错只读库
