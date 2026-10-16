# 开发踩坑手册

解决一个**非显而易见**的问题后，在本文档**顶部**追加一节（日期 + 标题）。不要写密钥、`.env` 内容或 token。

每条固定四段：

- **现象**：当时看到什么（报错原文、超时、页面表现）
- **原因**：根因，不是猜测列表
- **处理**：改了哪份配置 / 哪段代码
- **以后**：下次怎么避免或怎么验证

---

## 2026-10-16 Docker Chroma 卷缺 workspace_id

- **现象**：命名卷 `huiyi-ai-data-assistant_chroma-data` 里 `embedding_metadata` 没有 `workspace_id`（旧 v0.6 向量）。工作区隔离会拒掉或漏检。
- **原因**：v0.6 写入时没有该字段。`intent_examples` 集合本来就不该带 workspace_id。
- **处理**：把卷拷到临时目录，跑 `python -m app.tools.rebuild_knowledge_index --mapping mapping.json --persist-dir ...`（按 filename/doc_id 映射到 default 工作区 1），再拷回卷。PowerShell `Set-Content -Encoding utf8` 会写 BOM，mapping.json 要用无 BOM 写入。
- **以后**：对着旧 Chroma 卷升级，先查 sqlite 是否有 `workspace_id`；intent 示例不要当知识 chunk 重建失败。

## 2026-10-16 Docker Desktop 构建后端：腾讯 Maven DNS 间歇失败

- **现象**：`docker compose build backend` 在 `mvn dependency:go-offline` 报 `mirrors.cloud.tencent.com: Temporary failure in name resolution`。前几分钟往往已经成功下载大量 POM，随后在某个依赖（如 `flyway-core` → `ojdbc11`）上失败。本机 `mvn` 正常。
- **原因**：构建跑在 Docker Desktop 的 Linux VM 里，DNS 走 `192.168.65.7` 代理，不走 Windows 解析器。`mirrors.cloud.tencent.com` 是 CDN CNAME（A + AAAA）。Java 双栈解析时 AAAA 会卡住；即使强制 IPv4，代理在高频解析下仍会 `EAI_AGAIN`。Dockerfile 注释曾写「阿里云镜像」，实际 `.mvn/settings.xml` 是腾讯云 `mirrorOf *`。
- **处理**：`JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true` 不够。Compose `backend.build.extra_hosts` 把该域名指到**宿主机当时解析到的 IPv4**（`TENCENT_MAVEN_IP`，默认见 `docker-compose.yml`）。编过的镜像：`huiyi-ai-data-assistant-backend:latest`。
- **以后**：容器里 Maven 失败先对比本机 `Resolve-DnsName -Type A` 与 `docker run ... getent ahostsv4/ahostsv6`。CDN IP 会变，构建前可 `$env:TENCENT_MAVEN_IP = (Resolve-DnsName mirrors.cloud.tencent.com -Type A | Where-Object Type -eq A | Select-Object -First 1).IPAddress`。不要只靠改镜像源「试试」。

## 2026-10-16 SSE 结束后光标一直闪：Spring Security 异步派发 403

- **现象**：`e2e-m5.js` 场景 1 已出现 `.reference-tag`，但 60s 内 `.cursor-blink` 不消失。Java 日志：`AuthorizationDeniedException: Access Denied`，且 `Unable to handle the Spring Security Exception because the response is already committed`。助手消息其实已经落库。
- **原因**：`SseEmitter.complete()` 触发 Tomcat ASYNC 派发。JWT 过滤器是 `OncePerRequestFilter`，异步派发上不重跑；`STATELESS` 又清掉 SecurityContext。Spring Security 6 默认校验所有 dispatcher，ASYNC/ERROR 变成匿名 → 401/403，流无法干净关闭，前端 `onDone` 不触发，`message.streaming` 一直为 true。
- **处理**：`SecurityConfig` 对 `DispatcherType.ASYNC`、`DispatcherType.ERROR` `permitAll()`。`SecurityConfigTest` 锁住这两处。
- **以后**：给任意接口加 JWT 后，必须用浏览器/e2e 走一遍 SSE。看到「response already committed」+ Access Denied，先查 dispatcher 类型，不要先改前端超时。

## 2026-10-16 e2e 登录后进不了聊天：refresh 被 Node 侧转走

- **现象**：`page.locator('textarea').fill` 30s 超时。注册登录看起来成功。
- **原因**：access token 只在内存；refresh 在 `localStorage` 的 `huiyi_refresh`。e2e 用 Node `fetch` 调 `/api/v1/auth/refresh` 拿 access token 去建工作区/上传，refresh **旋转**后浏览器里仍是旧 refresh。随后 `page.goto('/chat')` 前端用旧 refresh 换票失败，页面不挂聊天框。
- **处理**：把 `refreshed.data.refreshToken` 写回 `localStorage`，再 `goto /chat` 并等待 `textarea`。
- **以后**：凡是测试里单独 `refresh`，必须把新 refresh 写回浏览器存储。不要假设 access token 在 localStorage。

## 2026-10-16 新用户无法上传知识：default 工作区是 VIEWER

- **现象**：登录后直接上传失败，或 RAG 无 `workspace_id` 隔离命中。
- **原因**：绑定文档规定新用户加入 `default` 为 VIEWER；VIEWER 不能上传。任何人可自建工作区并成为 OWNER。
- **处理**：`e2e-m5.js` 的 `bootstrapOwnerWorkspace`：自建工作区 → 上传 `business-glossary.md` → 写入 `huiyi_workspace_id`。
- **以后**：e2e / 手工验收不要默认「登录就能上传」。需要写知识时先建自己的工作区。

## 2026-10-16 已有 v0.6 MySQL 卷：Flyway 拒绝迁移

- **现象**：`Found non-empty schema(s) huiyi_demo but no schema history table`。`init_mysql.sql` 仍会建 `sales_orders` 等业务表，库从来不是空的。
- **原因**：Flyway 在非空且无 `flyway_schema_history` 的库上默认拒绝。需要 baseline 且仍要跑 V1（V1 用 `IF NOT EXISTS`）。
- **处理**：`application-local.yml` / `application-docker.yml`：`baseline-on-migrate: true`，`baseline-version: 0`。`FlywayMigrationFilesTest` 断言这两项。
- **以后**：对着旧 Docker 卷起 v0.7，不要指望「空库路径」。空卷与旧卷是两条验收。

## 2026-10-16 本机 Maven 单测被 LLM 环境变量污染

- **现象**：合入 `main` 后 `pytest` 中 `TestConfig.test_missing_config_raises` 失败：`DID NOT RAISE EmbeddingError`。
- **原因**：同一 PowerShell 会话加载过 worktree `.env`，`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_EMBEDDING_MODEL` 仍在。该测试假定未配置 embedding。
- **处理**：跑该测试前 `Remove-Item Env:LLM_*`。代码无需改。
- **以后**：加载 `.env` 的 shell 不要接着跑依赖「未配置」的单测。

## 更早条目（原 development.md「常见坑」）

- Docker 构建容器内部分国内 CDN 不可达：Maven 用腾讯云（见上条 DNS 坑），npm 用 npmmirror。
- 本机 MySQL 占用 3306：容器映射 3307（`.env` 的 `MYSQL_HOST_PORT`）。
- SpringAI `OpenAiApi` 默认补全路径含 `/v1`：base-url 已含 `/v1` 时需显式 `completionsPath("/chat/completions")`。
- 多数据源必须 `@Primary`，否则 MyBatis-Plus 可能绑到只读库。
- Pinia SSE 追加必须走响应式代理（数组下标），直接改裸对象引用页面不更新。
