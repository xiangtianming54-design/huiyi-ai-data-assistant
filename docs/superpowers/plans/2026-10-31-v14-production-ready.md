# v1.4 Production Ready Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Status: 已实施。本地标签 `v1.4.0` 指向 `e6bb834`。GitHub Release 未做。P0 only。K8s / Vault / Grafana / HTTPS / MinIO / 异地容灾不在本计划。发布问题见 [v14-release-issues.md](../../v14-release-issues.md)。
>
> Execution workspace: isolated git worktree via superpowers:using-git-worktrees. Branch: `feature/v1.4-production-ready`.

**Goal:** 把单份 Compose 演示编排改成 dev / staging / prod 三套可运行配置，并带上备份恢复、Nginx 限流、k6 脚本、最小钉钉告警和可回滚的发布脚本。

**Architecture:** 公共 `docker-compose.yml` 不再发布宿主机端口，端口和口令约束放进 override。Java 仍以 `docker` profile 为底座，staging/prod 只追加日志、Actuator 和 `info`。失败任务数与 SQL 校验耗时走 Micrometer，告警脚本读 Actuator，不引入 Prometheus 告警。备份、告警、发布共用同一组 compose 参数。这些脚本互相调用同一套文件名，拆成多份计划会把接口拆断，因此一份计划、按任务提交。

**Tech Stack:** 现有 Docker Compose、Java 17、Spring Boot 3、Micrometer、JUnit 5、Mockito；Nginx `limit_req`；bash；k6。无新 Flyway、无新 Maven 依赖、无前端页面改动。

**Spec:** `docs/superpowers/specs/2026-10-31-v14-production-ready.md`

## File map

| Path | Responsibility |
|---|---|
| `docker-compose.yml` | 公共服务、网络、命名卷；无宿主机端口；知识库卷 |
| `compose.dev.yml` | 现网端口；不要求 `RO_DB_PASSWORD` |
| `compose.staging.yml` / `compose.prod.yml` | 项目名、80 端口、必填口令、Redis 口令；仅 staging 开慢查询 |
| `scripts/init_ro_password.sh` | 空卷首次启动时按需 `ALTER USER` |
| `scripts/run_local.sh` | 带上 dev override |
| `backend/.../application-staging.yml` / `application-prod.yml` | 日志文件、Actuator 暴露、`info` |
| `backend/.../security/SecurityConfig.java` | 放行 `/actuator/metrics` |
| `backend/.../observability/AsyncFailedMetrics.java` | Gauge `huiyi.async.failed` |
| `backend/.../observability/ObservabilityMetrics.java` | Timer `sql_validate_latency_ms` |
| `frontend/nginx.conf` | 限流与 gzip；443 只写注释 |
| `scripts/ops/backup.sh` / `restore.sh` | 三件套备份与恢复 |
| `scripts/ops/lib/alert.sh` / `healthcheck.sh` | 去重与钉钉正文 |
| `scripts/ops/lib/deploy.sh` / `deploy.sh` | 只改两个 env 键；回滚目标 |
| `scripts/ops/k6/*.js` | 三场景压测 |
| `docs/ops/runbook.md` 及配套 | 操作说明、cron 示例、演练与压测记录模板 |

Do not create: V14、Kubernetes 清单、Grafana 仪表盘、证书文件、前端 Vue 改动、新的登录过滤器、MinIO。

## Global Constraints

- 决策表 D01–D13 不得推翻。规格 §13 的十条实施裁定不得推翻。
- 无新 Flyway。迁移目录结束时仍只有 V1–V13。
- 不新增 MySQL 账号。应用连接仍用 root / `MYSQL_ROOT_PASSWORD`。`huiyi_ro` 权限仍是 `GRANT SELECT ON huiyi_demo.*`。
- 演示占位符保留：`huiyi123`、`huiyi_ro123`、`local-dev-only-change-me-32chars-min`、`please-change-me-to-a-32-byte-minimum-secret`。不得提交填好的 `.env`、`.env.staging`、`.env.prod`，不得写入真实 LLM Key 或钉钉 webhook。
- dev 不要求 `RO_DB_PASSWORD`，不加 Redis 口令。staging/prod 缺 `RO_DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET`、`REDIS_PASSWORD` 时 compose 必须失败。
- 现有 dev Compose 项目不要设置 `name:`。staging 项目名 `huiyi-staging`，prod 项目名 `huiyi-prod`。
- Nginx：`/api/` 为 `10r/s`、`burst=20`；`/api/v1/auth/` 为 `1r/s`、`burst=5`；超限 429。不监听 443，不反代 `/actuator/`。
- 告警名只有 `backend-down`、`python-down`、`mysql-down`、`async-failed-increased`。钉钉正文第一行是 `HuiYi告警`。去重默认 30 分钟。不检查 Redis、磁盘、LLM 错误率、P95。
- Gauge 名 `huiyi.async.failed`。Timer 名 `sql_validate_latency_ms`。`METRICS_ENABLED=false` 时不注册。
- 登录锁定仍用现有 `LOGIN_FAIL_MAX_ATTEMPTS` / `LOGIN_FAIL_WINDOW_SECONDS` / `LOGIN_LOCK_SECONDS`。不改 `AuthService`。
- `git diff -- frontend` 只能出现 `frontend/nginx.conf`。
- 不加评测金标。不改 Python 检索契约，不改 chat / analysis 的请求体。
- PowerShell 下不要用 `&&`。Maven 的工作目录是 `backend/`。提交用 `git commit -F`，不要 HEREDOC，不要 `Co-authored-by`。

## Review Focus

- Gauge 若在注册时把行数抄进内存，新建一条 `FAILED` 之后指标不会变。读指标时必须再查库（Task 4）。
- 同一告警名在 30 分钟内再次失败不得发第二条钉钉（Task 8）。
- staging/prod 未设置 `RO_DB_PASSWORD` 时不得静默使用 `huiyi_ro123` 把栈拉起来（Task 2）。
- 目标栈仍然健康时，`restore.sh` 不带 `--yes` 不得覆盖数据库（Task 7）。
- `deploy.sh` 写 `GIT_COMMIT` / `APP_VERSION` 时不得改写同文件里的口令行；失败中的 HEAD 不得覆盖上一成功版本（Task 9）。

---

### Task 1: 公共 Compose 与 dev 端口

**Files:**
- Modify: `docker-compose.yml`
- Create: `compose.dev.yml`
- Modify: `scripts/run_local.sh`
- Modify: `README.md`（Quick Start 里的 `docker compose up -d`）
- Modify: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/ops/ComposeLayoutTest.java`

**Interfaces:**
- Consumes: 现有服务名 `mysql`、`redis`、`python-service`、`backend`、`frontend`，网络 `huiyi-net`，卷 `mysql-data`、`redis-data`、`chroma-data`
- Produces:
  - 公共文件没有 `ports:`。backend 增加卷 `knowledge-data:/var/huiyi/knowledge` 与环境变量 `HUIYI_KNOWLEDGE_STORAGE_DIR=/var/huiyi/knowledge`。声明卷 `knowledge-data`。公共文件不写 `name:`。
  - `compose.dev.yml` 只补端口：mysql `${MYSQL_HOST_PORT:-3307}:3306`，redis `6379:6379`，python `127.0.0.1:8001:8001`，backend `8080:8080`，frontend `5173:80`。不包含 `:?必须配置`，不设置 `name:`。
  - `run_local.sh` 的基础设施命令改为 `docker compose -f docker-compose.yml -f compose.dev.yml up -d mysql redis`。
  - README 方式一的启动命令改为同一组 `-f`，并写明访问 `http://localhost:5173`。

- [ ] **Step 1: Write the failing test**

`ComposeLayoutTest` 从 `backend/` 读取 `../docker-compose.yml`、`../compose.dev.yml`、`../scripts/run_local.sh`：

- 公共文件包含 `knowledge-data:/var/huiyi/knowledge` 与 `HUIYI_KNOWLEDGE_STORAGE_DIR`，且不包含 `8080:8080`、`5173:80`、`6379:6379`、`name:`。
- dev 文件包含上述四个端口映射（MySQL 用 `${MYSQL_HOST_PORT:-3307}:3306`）以及 `127.0.0.1:8001:8001`，且不包含 `:?必须配置`。
- `run_local.sh` 包含 `-f docker-compose.yml -f compose.dev.yml up -d mysql redis`。

`FlywayMigrationFilesTest` 增加：`Files.list` 迁移目录，没有任何文件名以 `V14` 开头。

- [ ] **Step 2: Run test to verify it fails**

Run from `backend/`: `mvn -B "-Dtest=ComposeLayoutTest,FlywayMigrationFilesTest" test`

Expected: FAIL，`compose.dev.yml` 或知识库卷不存在。

- [ ] **Step 3: Implement the compose split**

按 Interfaces 改四个文件。不要在公共文件里留一份端口再在 override 里“覆盖”；Compose 会把端口列表追加在一起。

- [ ] **Step 4: Run test to verify it passes**

Same Maven command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml compose.dev.yml scripts/run_local.sh README.md backend/src/test/java/com/huiyi/assistant/ops/ComposeLayoutTest.java backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java
git commit -F commit-msg.txt
```

Message: `feat: split compose host ports into the dev override`

---

### Task 2: staging/prod 口令与只读账号

**Files:**
- Create: `compose.staging.yml`
- Create: `compose.prod.yml`
- Create: `scripts/init_ro_password.sh`
- Create: `scripts/ops/ro_password_test.sh`
- Modify: `docker-compose.yml`（mysql 增加 init 脚本挂载）
- Modify: `.env.example`
- Modify: `.gitignore`
- Modify: `backend/src/test/java/com/huiyi/assistant/ops/ComposeLayoutTest.java`

**Interfaces:**
- Consumes: Task 1 的无端口公共文件
- Produces:
  - `ro_alter_sql`：`RO_DB_PASSWORD` 为空时无输出且退出码 0；非空时输出一行 `ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY '<value>'`，单引号写成 `''`。脚本在被直接执行且口令非空时，用 `mysql --user=root --password="$MYSQL_ROOT_PASSWORD"` 执行该 SQL。口令为空则直接退出 0。
  - 公共 mysql 增加只读挂载 `./scripts/init_ro_password.sh:/docker-entrypoint-initdb.d/03_ro_password.sh:ro`。`init_mysql.sql` 的 `CREATE USER` 与 `GRANT SELECT` 不改。
  - staging 文件：`name: huiyi-staging`。frontend 只有 `80:80`。backend 环境 `SPRING_PROFILES_ACTIVE: docker,staging`，并传入四个必填变量，每个都写成 `${VAR:?必须配置}`：`RO_DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET`、`REDIS_PASSWORD`。mysql 环境同样传入 `RO_DB_PASSWORD` 与 `MYSQL_ROOT_PASSWORD` 的 `:?必须配置` 形式。redis `command` 为 `redis-server --requirepass` 加必填口令，healthcheck 改为 `redis-cli -a` 该口令 `ping`。mysql `command` 只追加 mysqld 参数 `--slow_query_log=1`、`--long_query_time=1`、`--slow_query_log_file=/var/lib/mysql/slow.log`，不改 `entrypoint`。backend 再挂 `backend-logs:/var/log/huiyi`。不出现 `8080:8080`。
  - prod 文件：项目名 `huiyi-prod`，profile `docker,prod`，端口、Redis、四个必填变量、日志卷与 staging 相同。mysql 没有 `slow_query_log`。
  - `.env.example` 增加空占位：`RO_DB_PASSWORD`、`REDIS_PASSWORD`、`DINGTALK_WEBHOOK_URL`、`BACKUP_DIR`、`BACKUP_RETAIN_DAYS`、`GIT_COMMIT`、`APP_VERSION`、`ALERT_DEDUP_MINUTES`、`SPRING_PROFILES_ACTIVE`。不写真实 webhook。已有 `huiyi123` 保留。
  - `.gitignore` 增加 `.env.dev`、`.env.staging`、`.env.prod`、`data/backups/`。

- [ ] **Step 1: Write the failing test**

`scripts/ops/ro_password_test.sh`：

- 不设置 `RO_DB_PASSWORD` 时 `ro_alter_sql` 无输出。
- `RO_DB_PASSWORD=abc` 时期望输出 `ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY 'abc';`
- `RO_DB_PASSWORD=a'b` 时期望 `IDENTIFIED BY 'a''b'`。

`ComposeLayoutTest` 追加：staging/prod 含各自 `name:`、`80:80`、四个 `:?必须配置`、redis `--requirepass`；staging 含 `slow_query_log=1`；prod 文件不含 `slow_query_log`；两个文件都不含 `8080:8080`。公共文件含 `03_ro_password.sh`。`.gitignore` 含 `.env.staging` 与 `data/backups/`。

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/ops/ro_password_test.sh`

Expected: FAIL，函数或文件不存在。

然后 from `backend/`: `mvn -B "-Dtest=ComposeLayoutTest" test`

Expected: FAIL。

- [ ] **Step 3: Implement overrides, the alter script, and ignore rules**

dev 的 compose 文件不要加入这些 `:?必须配置`。不要把 `huiyi_ro123` 从 `init_mysql.sql` 或 yml 默认值里删掉。

- [ ] **Step 4: Run test to verify it passes**

两个命令都 PASS。

- [ ] **Step 5: Commit**

Message: `feat: require staging and prod secrets for the read-only account`

---

### Task 3: staging/prod profile 与 metrics 放行

**Files:**
- Create: `backend/src/main/resources/application-staging.yml`
- Create: `backend/src/main/resources/application-prod.yml`
- Modify: `backend/src/main/java/com/huiyi/assistant/security/SecurityConfig.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/security/SecurityConfigTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/ops/ProfileLayoutTest.java`

**Interfaces:**
- Consumes: Task 2 写入的 `SPRING_PROFILES_ACTIVE`
- Produces: 两个 yml 都不含 `jdbc:`、不复制数据源。两者都含：
  - `logging.file.name: /var/log/huiyi/app.log`
  - `include: health,info,metrics,prometheus`
  - `management.info.env.enabled: true`
  - `info.git.commit: ${GIT_COMMIT:unknown}`
  - `info.app.version: ${APP_VERSION:unknown}`
- `SecurityConfig` 的 permitAll 匹配器增加 `"/actuator/metrics"` 与 `"/actuator/metrics/**"`。已有 health、prometheus、info 保留。

- [ ] **Step 1: Write the failing test**

`ProfileLayoutTest` 读取两个 yml，断言上面五个字符串存在，且 `jdbc:` 不存在。

`SecurityConfigTest` 断言源码包含 `"/actuator/metrics"`。

- [ ] **Step 2: Run test to verify it fails**

From `backend/`: `mvn -B "-Dtest=ProfileLayoutTest,SecurityConfigTest" test`

Expected: FAIL。

- [ ] **Step 3: Add the two profiles and the matcher**

不要改 `application-docker.yml` 的数据源。dev 继续只激活 `docker`。

- [ ] **Step 4: Run test to verify it passes**

Same command. Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: expose actuator info and metrics on staging and prod`

---

### Task 4: 异步失败 Gauge

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/observability/AsyncFailedMetrics.java`
- Test: `backend/src/test/java/com/huiyi/assistant/observability/AsyncFailedMetricsTest.java`

**Interfaces:**
- Consumes: `ObservabilityProperties.isMetricsEnabled()`，`AsyncTaskMapper.selectCount(Wrapper)`，常量 `AsyncTaskService.STATUS_FAILED`（值 `FAILED`）
- Produces: `public AsyncFailedMetrics(ObservabilityProperties properties, MeterRegistry registry, AsyncTaskMapper mapper)`
  - `metricsEnabled == false` 时不注册。
  - 否则注册 Gauge，名称为 `huiyi.async.failed`。Gauge 的取值函数每次调用 `selectCount`，条件是列 `status` 等于 `FAILED`。不得把第一次的计数存进字段。

- [ ] **Step 1: Write the failing test**

用 Mockito 桩 `selectCount`。第一次返回 `2L`，构造对象后 `registry.get("huiyi.async.failed").gauge().value()` 为 `2.0`。再把桩改成 `5L`，立刻再读 Gauge，值为 `5.0`。

另一个用例：`setMetricsEnabled(false)` 后 `registry.find("huiyi.async.failed").gauge()` 为 null。

- [ ] **Step 2: Run test to verify it fails**

From `backend/`: `mvn -B "-Dtest=AsyncFailedMetricsTest" test`

Expected: FAIL，类不存在。

- [ ] **Step 3: Implement `AsyncFailedMetrics`**

使用 `Gauge.builder(...).register(registry)`。查询放在 lambda 里。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: gauge the current failed async task count`

---

### Task 5: SQL 校验耗时

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/ObservabilityMetrics.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/agent/AgentRunner.java`（两处 `sqlValidate` 调用）
- Test: `backend/src/test/java/com/huiyi/assistant/observability/ObservabilityMetricsTest.java`

**Interfaces:**
- Consumes: 现有计数器名 `sql_validate`、`sql_validate_success`、`sql_validate_rejected`
- Produces: `public void sqlValidate(boolean passed, Duration latency)`
  - 仍按 `passed` 增加原来的计数器。
  - `latency != null` 时写入 Timer `sql_validate_latency_ms`。
  - `metricsEnabled == false` 时方法直接返回。
- `AgentRunner` 在修改意图拦截处，以及 `SQL_VALIDATE` 工具返回后，把这段耗时传进去。不改拒绝文案 `该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。`

- [ ] **Step 1: Write the failing test**

`metricsEnabled` 为 true 时，`sqlValidate(true, Duration.ofMillis(12))` 后：`sql_validate` 计数为 1，`sql_validate_success` 计数为 1，`sql_validate_latency_ms` 的 `count()` 为 1。

`sqlValidate(false, Duration.ofMillis(4))` 后 `sql_validate_rejected` 计数为 1。

`metricsEnabled` 为 false 时，调用后 `registry.getMeters()` 为空。

- [ ] **Step 2: Run test to verify it fails**

From `backend/`: `mvn -B "-Dtest=ObservabilityMetricsTest" test`

Expected: FAIL，方法签名不匹配或 Timer 不存在。

- [ ] **Step 3: Change the signature and both call sites**

删掉单参数 `sqlValidate(boolean)`，避免调用点漏改后仍能编译。

- [ ] **Step 4: Run test to verify it passes**

`mvn -B "-Dtest=ObservabilityMetricsTest,AgentRunnerTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: record sql validation latency`

---

### Task 6: Nginx 限流

**Files:**
- Modify: `frontend/nginx.conf`
- Modify: `backend/src/test/java/com/huiyi/assistant/ops/ComposeLayoutTest.java`（或同包新测试 `NginxLimitTest`，读取 `../frontend/nginx.conf`）

**Interfaces:**
- Consumes: 现有 `location /api/` 的 `proxy_pass http://backend:8080`、`proxy_buffering off`、`proxy_cache off`、`proxy_read_timeout 300s`
- Produces: 文件顶部
  - `limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;`
  - `limit_req_zone $binary_remote_addr zone=auth:10m rate=1r/s;`
  - `limit_req_status 429;`
  - `location /api/v1/auth/` 使用 `limit_req zone=auth burst=5 nodelay;`，代理头与 `/api/` 相同。
  - `location /api/` 使用 `limit_req zone=api burst=20 nodelay;`。
  - `gzip on;`，`gzip_types` 含 `text/css`、`application/javascript`、`application/json`、`image/svg+xml`。
  - 文件末尾注释含 `listen 443 ssl`。整个文件只有一个 `server {`。没有 `location /actuator`。

- [ ] **Step 1: Write the failing test**

断言上列字符串都在，且 `"server {"` 只出现一次。

- [ ] **Step 2: Run test to verify it fails**

From `backend/`: `mvn -B "-Dtest=NginxLimitTest" test`

Expected: FAIL。

- [ ] **Step 3: Update nginx.conf only**

不要改 `frontend/src`。不要在 Dockerfile 增加 `EXPOSE 443`。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: rate-limit api and auth at nginx`

---

### Task 7: 备份与恢复

**Files:**
- Create: `scripts/ops/lib/backup.sh`
- Create: `scripts/ops/backup.sh`
- Create: `scripts/ops/restore.sh`
- Create: `scripts/ops/backup_prune_test.sh`
- Create: `scripts/ops/restore_guard_test.sh`
- Create: `docs/ops/cron.example`
- Create: `docs/ops/backup-drill.md`

**Interfaces:**
- Consumes: 卷内路径 `/app/chroma_store`、`/var/huiyi/knowledge`，库名 `huiyi_demo`
- Produces:
  - `compose_files <dev|staging|prod>` 打印 `-f docker-compose.yml -f compose.<env>.yml --env-file <file>`。dev 的 env 文件是 `.env`；staging 是 `.env.staging`；prod 是 `.env.prod`。
  - `prune_backups <dir> <retain-days>`：目录名匹配 `YYYYMMDDTHHMMSSZ`。按该 UTC 时间计算，早于保留天数的目录删除，天内的留下。
  - `backup.sh <env>` 在 `${BACKUP_DIR:-./data/backups}/<env>/<UTC时间戳>/` 写出 `mysql.sql.gz`、`chroma.tar.gz`、`knowledge.tar.gz`。MySQL 用 `docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --databases huiyi_demo`。Chroma 与知识库用对应服务里的 `tar`，不另起一个猜测卷名的容器。然后调用 `prune_backups`，默认保留天数 `BACKUP_RETAIN_DAYS`，缺省 `7`。
  - `refuse_overwrite <healthy:true|false> <yes:true|false>`：healthy 且未传 yes 时退出码 2。`restore.sh <env> <备份目录> [--yes]` 在覆盖前调用它。通过后停止 `backend` 与 `python-service`，导入三件套，再 `up -d`。
  - `docs/ops/cron.example` 含规格里的两行 cron（备份 `15 3 * * *`，健康检查 `*/5 * * * *`）。
  - `docs/ops/backup-drill.md` 是空表：日期、环境、备份目录、登录、RAG、Text-to-SQL、结果。不要编造一次已经做过的演练。

- [ ] **Step 1: Write the failing tests**

`backup_prune_test.sh` 在临时目录放 `20200101T000000Z` 与一个此刻的 UTC 目录，`prune_backups dir 7` 后只剩后者。

`restore_guard_test.sh`：`true false` 退出码 2；`true true` 退出码 0；`false false` 退出码 0。

- [ ] **Step 2: Run tests to verify they fail**

`bash scripts/ops/backup_prune_test.sh` 与 `bash scripts/ops/restore_guard_test.sh`

Expected: FAIL。

- [ ] **Step 3: Implement the scripts and the two docs**

不备份 Redis。脚本不得调用 `crontab`。

- [ ] **Step 4: Run tests to verify they pass**

两个 bash 命令 PASS。

- [ ] **Step 5: Commit**

Message: `feat: back up mysql, chroma, and knowledge files`

---

### Task 8: 健康检查与钉钉去重

**Files:**
- Create: `scripts/ops/lib/alert.sh`
- Create: `scripts/ops/healthcheck.sh`
- Create: `scripts/ops/alert_test.sh`

**Interfaces:**
- Consumes: Task 4 的指标名 `huiyi.async.failed`；Task 7 的 `compose_files`
- Produces:
  - `should_send <name> <now-epoch> <state-file> <dedup-minutes>`：状态文件里该名字没有时间戳，或距今不小于去重分钟，则退出码 0，并把 `now` 写回该键。否则退出码 1，且不改文件。
  - `metric_increased <new-value> <state-file>`：大于文件中的 `async-failed-value` 时退出码 0，并把新值写回。相等或更小则退出码 1。文件没有该键时，把当前值记下并退出码 1（第一次只建立基线，不告警）。
  - `format_alert <env> <name> <reason>`：第一行恰好是 `HuiYi告警`，后面三行是环境、告警名、原因。
  - `healthcheck.sh <env>`：用 `docker compose exec` 检查 backend `http://127.0.0.1:8080/actuator/health`、python `http://127.0.0.1:8001/health`、`mysqladmin ping`，以及 Gauge。失败则追加 `logs/ops-alert.log`。`DINGTALK_WEBHOOK_URL` 为空时日志含 `webhook skipped`，不调用 curl。有 webhook 且 `should_send` 为 0 时 POST JSON `{"msgtype":"text","text":{"content":...}}`。任一检查失败则脚本退出码非 0。去重分钟来自 `ALERT_DEDUP_MINUTES`，缺省 `30`。脚本正文不检查 redis、磁盘、llm、p95。

- [ ] **Step 1: Write the failing test**

`alert_test.sh`：

- 第一次 `should_send backend-down` 为 0；10 分钟后、去重 30 分钟，为 1；31 分钟后为 0。
- `metric_increased` 从无到 `2` 为 1；`2` 再到 `2` 为 1；`2` 再到 `3` 为 0。
- `format_alert staging backend-down down` 的第一行是 `HuiYi告警`。
- `healthcheck.sh` 含 `mysqladmin`，不含 `redis-cli`、`df `、`llm_failed`。注释里可以出现“不检查 Redis”。

- [ ] **Step 2: Run test to verify it fails**

`bash scripts/ops/alert_test.sh`

Expected: FAIL。

- [ ] **Step 3: Implement alert.sh and healthcheck.sh**

状态文件路径 `logs/ops-alert-state`。`logs/` 已被 gitignore。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: alert on health failures and rising async failures`

---

### Task 9: 发布与回滚

**Files:**
- Create: `scripts/ops/lib/deploy.sh`
- Create: `scripts/ops/deploy.sh`
- Create: `scripts/ops/deploy_test.sh`

**Interfaces:**
- Consumes: Task 7 的 `compose_files`；Task 8 里同样的三项存活检查可以抽成 `scripts/ops/lib/probe.sh` 的 `probe_alive <env>`，供 healthcheck 与 deploy 共用。异步失败升高不算部署失败。
- Produces:
  - `update_env_keys <file> <commit> <version>`：只替换或追加 `GIT_COMMIT=` 与 `APP_VERSION=` 两行。其它行逐字保留。重复调用不产生重复键。
  - `rollback_target <state-file> <current-head> <stack-healthy:true|false>`：栈健康时把 current 写入文件并打印 current。栈不健康且文件里已有 sha 时打印该 sha，不改文件。栈不健康且文件为空时打印 current，仍不把这次写成成功记录。
  - `deploy.sh <staging|prod> <git-tag>`：拒绝 `dev`。顺序按规格 §10：fetch、计算回滚目标、checkout tag、更新 env 两键、`docker compose build`、`up -d`、`probe_alive`。失败则 checkout 回滚目标并再做一次 更新 env / build / up / probe。第二次仍失败则退出非 0，不再 checkout。环境变量 `HUIYI_DEPLOY_COMPOSE` 若为 `echo`，用它代替真正的 compose 与 probe，方便测试，生产不设置。

- [ ] **Step 1: Write the failing test**

`deploy_test.sh`：

- env 文件含 `MYSQL_ROOT_PASSWORD=secret` 与 `JWT_SECRET=abc`。调用 `update_env_keys file deadbeef v1.4.0` 后这两行仍在，且 `GIT_COMMIT=deadbeef`、`APP_VERSION=v1.4.0` 各一行。再调用一次，仍然各一行。
- 状态文件已有 `aaa`，current 为 `bbb`，栈不健康：`rollback_target` 打印 `aaa`，文件内容仍是 `aaa`。
- 栈健康、current 为 `ccc`：打印 `ccc`，文件变为 `ccc`。

- [ ] **Step 2: Run test to verify it fails**

`bash scripts/ops/deploy_test.sh`

Expected: FAIL。

- [ ] **Step 3: Implement deploy helpers and deploy.sh**

不要 `docker pull`。不要在脚本里 `git tag` 或 `git push`。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: deploy a git tag and roll back to the last healthy commit`

---

### Task 10: k6 三场景

**Files:**
- Create: `scripts/ops/k6/rag.js`
- Create: `scripts/ops/k6/text2sql.js`
- Create: `scripts/ops/k6/sse.js`
- Create: `scripts/ops/k6_shape_test.sh`
- Create: `docs/ops/load-test-report.md`

**Interfaces:**
- Consumes: 登录体 `{"username","password"}`，成功时 token 在 `data.accessToken`。请求头 `Authorization: Bearer`。聊天体 `{"question":"什么是有效订单？"}`，路径 `/api/v1/workspaces/${K6_WORKSPACE_ID}/chat/stream`。分析体 `{"question":"上个月销售额最高的 10 个商品是什么？"}`，路径 `.../analysis/query`。危险句 `删除所有销售订单。` 的拒绝文案是 `该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。`
- Produces:
  - 三个脚本都从 `K6_BASE_URL`、`K6_USERNAME`、`K6_PASSWORD`、`K6_WORKSPACE_ID` 读配置。缺任一变量就在 `setup` 里失败。仓库里不写口令。
  - `rag.js`：10 秒爬到 20 VU，再保持 5 分钟。阈值 `http_req_waiting` p95 < 3000，`http_req_duration` p95 < 6000。
  - `text2sql.js`：10 秒爬到 10 VU，再保持 5 分钟。阈值 `http_req_duration` p95 < 10000。`setup` 另发一次危险句，响应正文必须包含上面的拒绝文案。
  - `sse.js`：30 秒爬到 50 VU，再保持 3 分钟。阈值 `http_req_failed` rate < 0.01。检查响应体含 `data:`。
  - `docs/ops/load-test-report.md` 列出三场景的目标、实测 P95、错误率、模型名、`huiyi.async.failed` 前后值、慢查询条数、容器内存峰值，以及降级声明栏。没有实测之前写「未对 staging 执行」，不要编造达标数字。2026-11-08 已填入实测，占位句已删除。LLM 延迟可以事后填降级声明；错误率、断流、危险 SQL、校验 Timer 有数这四栏没有这条退路。

- [ ] **Step 1: Write the failing test**

`k6_shape_test.sh` 用 `grep` 断言三个文件含各自的 `target:`、`duration`、阈值数字，以及危险句拒绝文案。报告填入实测之后，测试改为锁定 `sleep(300)` 和 `glm-5.2`，并拒绝报告再出现「未对 staging 执行」。

- [ ] **Step 2: Run test to verify it fails**

`bash scripts/ops/k6_shape_test.sh`

Expected: FAIL。

- [ ] **Step 3: Add the scripts and the report skeleton**

不在本任务执行 k6，也不把 k6 放进 GitHub Actions。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `feat: add k6 scenarios for rag, text-to-sql, and sse`

---

### Task 11: Runbook 与版本说明

**Files:**
- Create: `docs/ops/runbook.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/development.md`
- Modify: `docs/superpowers/specs/2026-10-31-v14-production-ready.md`（文首状态改为指向本计划）
- Modify: `README.md`（路线图当前迭代一句）

**Interfaces:**
- Consumes: Task 1–10 的命令与文件名
- Produces: `docs/ops/runbook.md` 含这些小节：部署、回滚、备份、恢复、告警响应、常见故障、有域名之后的 certbot 附录。附录写明本版不执行。恢复小节写明已有卷改 `huiyi_ro` 口令用 `ALTER USER`，初始化脚本不会重跑。告警小节写明 webhook 为空时只记 `webhook skipped` 且退出码非 0。
- `roadmap.md` 的「当前开发」改为 v1.4.0 Production Ready，并链接本计划与规格。不要把 v1.4 写成已发布。
- `development.md` 增加「v1.4 变更」草稿：做了什么、没做什么、回滚方式是重新执行 `deploy.sh <env> <上一 tag>`。因为没有新迁移，回滚不必还原 schema。

- [ ] **Step 1: Write the failing test**

在 `ComposeLayoutTest` 或新建 `RunbookTest`：`docs/ops/runbook.md` 含标题「部署」「回滚」「备份」「恢复」「告警响应」「常见故障」「certbot」。`roadmap.md` 含 `v1.4.0` 与本计划的相对链接。

- [ ] **Step 2: Run test to verify it fails**

From `backend/`: `mvn -B "-Dtest=RunbookTest" test`

Expected: FAIL。

- [ ] **Step 3: Write the runbook and version notes**

不要把压测骨架或演练空表填成已通过。不要标记 GitHub Release 已完成。

- [ ] **Step 4: Run the full backend tests**

From `backend/`: `mvn -B test`

Expected: PASS，且迁移目录仍无 `V14`。

From `python-service/`: `pytest -q`

Expected: 与改动前相同的通过数。本任务不改 Python。

- [ ] **Step 5: Commit**

Message: `docs: describe v1.4 deploy, backup, and alert operations`

---

## 手动验收（不进上述提交）

这些依赖一台真正的 staging，计划里的单测替代不了：

- 停 backend 后跑 `healthcheck.sh staging`，日志出现告警；配了 webhook 时正文含 `HuiYi告警`。
- 插入一条 `async_tasks.status='FAILED'` 后，Gauge 变大，30 分钟内第二次不发钉钉。
- `backup.sh` 得到三件套后，在干净项目名上 `restore.sh ... --yes`，再人工做登录、RAG、Text-to-SQL，把结果写入 `backup-drill.md`。
- 对 staging 跑三个 k6 脚本，把实测填进 `load-test-report.md`。LLM 延迟未达标就写降级声明。
- `deploy.sh` 中途失败后重跑同一 tag 成功；回滚后 `/actuator/info` 的 `git.commit` 与上一 tag 一致。

## Self-review

- 规格 §2 的十项 P0 分别落在 Task 1–2（编排与配置）、Task 3（profile）、Task 4（Gauge）、Task 7（备份）、Task 6（限流）、Task 10（压测）、Task 8（告警）、Task 9（发布）、Task 11（runbook）。
- 规格 §3 的非目标没有对应实现任务。
- Review Focus 的五条都有测试步骤：Task 4、Task 8、Task 2、Task 7、Task 9。
- 演练记录与压测实测明确留空，避免实施者编造验收通过。
