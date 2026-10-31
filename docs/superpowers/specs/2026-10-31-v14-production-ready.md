# HuiYi v1.4 · Production Ready（仓库规格）

> 版本：`v1.4.0`  
> 状态：**规格已冻结；实施以本计划为准，尚未发布**（P0 D01–D13 已拍板，2026-10-31；无待确认项）  
> 实施计划：[2026-10-31-v14-production-ready.md](../plans/2026-10-31-v14-production-ready.md)  
> 来源：[HuiYi-v1.4-Production-Ready-Requirement-Freeze.md](file:///C:/Users/33568/Documents/Codex/2026-10-12/new-chat-2/outputs/HuiYi-v1.4-Production-Ready-Requirement-Freeze.md)  
> 基线：`huiyi-ai-data-assistant v1.3.0`（标签 `v1.3.0`）  
> 路线图名称：桌面 §14 Production Ready。本版 **P0 只做单机 Compose 多环境、配置治理、备份恢复、Nginx 限流、k6 压测、最小告警、发布回滚**。  
> 对照：桌面《HuiYi-Enterprise-Agent-Version-Iteration-Development-Plan》§14；[roadmap.md](../../roadmap.md)。  
> 拍板：D01–D13，见 §12。改写或实施时 **不得推翻** 该表。  
> 剩下来的：K8s / Swarm、Vault、Grafana、HTTPS、MinIO、异地容灾，进 **v1.5+** 或有域名之后的小步骤。

**改写原则（已拍板）：** 方向跟桌面 §14 走。单机 Docker Compose 承载全部目标。K8s、Vault、Grafana 大盘、多机高可用、model/prompt 热更新 **不按桌面 §14 全文一次做完**。

---

## 1. 版本定位

把现网「一份 `docker-compose.yml` 同时当演示和部署」改成 **同一套服务、三套 override**：`dev` 仍给本机开发，`staging` / `prod` 是单机 VPS 上的长期运行。敏感值只走环境变量。MySQL、Chroma、知识库原文件每天备份，并且做过一次恢复演练。入口用 Nginx 限流。压测和告警够用即可，不引入新的监控平台。

它不是：

- Kubernetes、Docker Swarm、多机、双活；
- Vault、配置中心、model / prompt 热更新；
- Prometheus 抓取架构或 Grafana 大盘（`/actuator/prometheus` 保持 v0.8 现状，本版不围绕它做告警）；
- MinIO、对象存储上云；
- 证书与 HTTPS（无域名；走 `http://IP`）；
- 新 Flyway、新业务表、新业务 API、前端页面改动；
- 分布式限流、消息队列。

现网聊天、分析、知识库、异步解析行为不变。默认 Planner 仍是 Router。五工具名不变。

---

## 2. P0 必须完成

1. **Compose 拆分**：保留 `docker-compose.yml` 为公共服务定义。新增 `compose.dev.yml`、`compose.staging.yml`、`compose.prod.yml`。`scripts/run_local.sh` 仍是一条命令起本地开发，内部改为带上 dev override。
2. **配置治理**：只提交 `.env.example`。`.env`、`.env.dev`、`.env.staging`、`.env.prod` 不进 Git。JWT、数据库口令、Redis 口令、LLM API Key、钉钉 webhook 全部环境变量。`docs/ops/runbook.md` 列出键位、谁在用、空值时的行为。
3. **Profile**：Compose 里的 Java 继续以 `docker` 为底座。`staging` 激活 `docker,staging`，`prod` 激活 `docker,prod`。新增的 yml 只覆盖日志文件、Actuator 暴露面、`info` 版本。不复制第二份数据源配置。
4. **失败任务指标**：Micrometer 指标名 **`huiyi.async.failed`**。值是 `async_tasks.status='FAILED'` 的当前行数。无新表。
5. **备份与恢复**：`scripts/ops/backup.sh`、`scripts/ops/restore.sh`。每日 cron 示例。保留 7 天。演练记录写进 `docs/ops/backup-drill.md`。
6. **Nginx 限流**：`/api/` 按 IP `10r/s`、`burst=20`；`/api/v1/auth/` 按 IP `1r/s`、`burst=5`。超限 **429**。gzip 打开。443 只写注释，不监听。
7. **压测**：k6 三个场景打 staging，报告 `docs/ops/load-test-report.md`。指标见 §7。
8. **告警**：`scripts/ops/healthcheck.sh`，cron 每 5 分钟。检查 backend health、python health、MySQL ping、`huiyi.async.failed` 是否比上次升高。异常发钉钉机器人，正文含关键词 **`HuiYi告警`**，并追加 `logs/ops-alert.log`。同一告警名 30 分钟内不重复发送。
9. **发布与回滚**：`scripts/ops/deploy.sh <staging|prod> <git-tag>`。在目标机的 git 工作区切 tag、compose build、up、健康检查。失败则切回上一成功 tag 再 build/up。同一 tag 重跑必须成功。`/actuator/info` 露出 `git.commit` 与 `app.version`。
10. **Runbook**：`docs/ops/runbook.md` 覆盖部署、回滚、备份、恢复、告警响应、常见故障，以及「以后有域名时如何补 certbot」的附录（本版不执行该附录）。

---

## 3. P0 明确不做

- Kubernetes / Swarm / 多机 / 异地容灾 / 双活
- Vault、配置中心、model / prompt 热更新
- Prometheus 告警规则、Grafana、Alertmanager
- MinIO；知识库原文件仍在本地目录，备份覆盖该目录
- 分布式限流、API 网关集群、消息队列
- 购买域名、证书、监听 443
- 前端 Vue / 路由 / 页面改动（只允许改 `frontend/nginx.conf`）
- 新 Flyway（**不创建 V14**）、新业务 API、登录锁定逻辑重写
- 磁盘、Redis、LLM 错误率、P95 的独立告警（桌面 §14.8 里有，本版告警清单以 §8 为准）

---

## 4. 环境与编排

| 环境 | 命令 | 数据 | 宿主机端口 |
|---|---|---|---|
| dev | `docker compose -f docker-compose.yml -f compose.dev.yml up -d` | 现有命名卷 + 样例初始化 SQL | 与现网一致：MySQL `${MYSQL_HOST_PORT:-3307}`、Redis `6379`、Python `127.0.0.1:8001`、backend `8080`、frontend `5173:80` |
| staging | `docker compose -f docker-compose.yml -f compose.staging.yml --env-file .env.staging up -d` | 独立 Compose 项目名 `huiyi-staging`，每日备份 | **只** 发布 frontend `80:80` |
| prod | `docker compose -f docker-compose.yml -f compose.prod.yml --env-file .env.prod up -d` | 独立 Compose 项目名 `huiyi-prod`，每日备份 | **只** 发布 frontend `80:80` |

`run_local.sh` 仍只拉起 **dev 的 mysql + redis**（再加上本机 Python / Java / Vite），不改成整栈 staging。它必须改成带 `-f compose.dev.yml`，否则基础文件不再把 3307/6379 映到宿主机（见 §13）。

公共文件继续定义 mysql、redis、python-service、backend、frontend、网络 `huiyi-net`、卷 `mysql-data`、`redis-data`、`chroma-data`。本版在公共文件 **增加** 卷 `knowledge-data`，挂到 backend 的 `/var/huiyi/knowledge`，并设置 `HUIYI_KNOWLEDGE_STORAGE_DIR=/var/huiyi/knowledge`。v1.3 的上传文件现在写在容器可写层里，容器一换就没了；不挂卷则备份没有第三件套。`run_local.sh` 的本机 Java 仍用默认 `./data/knowledge`，不受这个卷影响。

**不要** 给现有 dev 项目另起 `name:`。现有卷前缀来自目录名 `huiyi-ai-data-assistant`。staging/prod 用 `name: huiyi-staging` 与 `name: huiyi-prod`，避免和本机 dev 卷混在一起。

staging / prod 的 mysql、redis、python、backend **不映射宿主机端口**。健康检查和备份都走 `docker compose exec`，不经过公网，也不经过 frontend Nginx。

Redis：dev 保持无口令（现网 healthcheck `redis-cli ping` 继续可用）。staging / prod 的 override 设置 `redis-server --requirepass ${REDIS_PASSWORD}`，并改掉该环境的 healthcheck，使 `redis-cli -a` 能 ping。Java 侧 `application-docker.yml` 已有 `REDIS_PASSWORD`，空字符串表示无口令。

MySQL 应用账号 **仍是 root**，`DB_PASSWORD` 继续取 `MYSQL_ROOT_PASSWORD`。本版不新增业务账号。`huiyi_ro` 仍是只读账号，权限仍是 `GRANT SELECT ON huiyi_demo.*`。口令按 §5 分开：dev 沿用现有演示口令，staging/prod 首次建库时改成环境变量。

日志：`application-staging.yml` / `application-prod.yml` 把 Java 日志写到 `/var/log/huiyi/app.log`。backend 把命名卷 `backend-logs` 挂到 `/var/log/huiyi`。dev 保持控制台日志。

---

## 5. 配置

`.env.example` 增补下列键，值只用占位符，不写真实口令或 webhook：

`SPRING_PROFILES_ACTIVE`、`MYSQL_ROOT_PASSWORD`、`RO_DB_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET`、`LLM_API_KEY`、`LLM_BASE_URL`、`DINGTALK_WEBHOOK_URL`、`BACKUP_DIR`、`BACKUP_RETAIN_DAYS`、`GIT_COMMIT`、`APP_VERSION`、`ALERT_DEDUP_MINUTES`。

已有演示占位符留在仓库里，不为本版删除：`.env.example` 的 `MYSQL_ROOT_PASSWORD=huiyi123`，`application-local.yml` 的 `huiyi123` 与 `local-dev-only-change-me-32chars-min`，`scripts/init_mysql.sql` 与两个 yml 里的 `huiyi_ro123`。这些值只在 **dev / profile `local`** 生效。`application-local.yml` 不会被 staging/prod 加载。删掉它们也不能从 git 历史里抹掉，却会逼 `run_local.sh` 把 `.env` 注入宿主机上的 Java 进程，现版本机进程只读 yml 默认值。

会进 staging/prod 数据库的是另一条。`init_mysql.sql` 在**空数据卷首次启动**时创建 `huiyi_ro` / `huiyi_ro123`，而 `application-docker.yml` 的默认口令也是 `huiyi_ro123`。staging/prod 使用新的 Compose 项目名，数据卷是空的，所以这条口令会变成线上只读账号的口令。安排如下：

- 账号与授权仍写在 `init_mysql.sql`，不改权限模型。
- 新增 `scripts/init_ro_password.sh`，挂到 MySQL 的 `docker-entrypoint-initdb.d/`，排在 `01_init.sql`、`02_sample_data.sql` 之后。仅当 `RO_DB_PASSWORD` 非空时执行 `ALTER USER 'huiyi_ro'@'%' IDENTIFIED BY` 该值。为空则保持 `huiyi_ro123`。官方镜像不会替换 `.sql` 里的变量，所以用 shell。
- `compose.staging.yml` 与 `compose.prod.yml` 把 `RO_DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET`、`REDIS_PASSWORD` 写成 `${VAR:?必须配置}`。缺任一键时 `docker compose up` 直接失败。
- dev override **不** 要求 `RO_DB_PASSWORD`。已有 dev 数据卷不会重跑 init，只读口令保持 `huiyi_ro123`，与 yml 默认值一致。
- 之后要改已有卷上的只读口令，走 runbook 里的 `ALTER USER`，不要指望再次执行 init 脚本。

`.gitignore` 增加 `.env.dev`、`.env.staging`、`.env.prod`。已有 `.env` 规则保留。填好的环境文件不得提交。

Actuator（`application-docker.yml` 已暴露 `health,prometheus,info`）：

- `application-staging.yml` 与 `application-prod.yml` 的 web exposure 改为 `health,info,metrics,prometheus`。
- `SecurityConfig` 对 `/actuator/metrics` 与 `/actuator/metrics/**` 放行，与现有 health/info/prometheus 相同。staging/prod 不把 8080 映射到宿主机，这个放行只在 Compose 网络内可达。
- Nginx **不** 反代 `/actuator/`。
- `info.git.commit` = `${GIT_COMMIT:unknown}`，`info.app.version` = `${APP_VERSION:unknown}`。`deploy.sh` 在启动前写入这两个变量（commit 用 `git rev-parse HEAD`，version 用 tag）。

`docs/ops/runbook.md` 写明：webhook 为空时 healthcheck 只写本地日志、退出码非 0，不要求钉钉可达。

---

## 6. 备份与恢复

`backup.sh` 接受环境参数 `dev|staging|prod`，读取该环境的 compose 文件与 env 文件。产出目录：

```text
${BACKUP_DIR:-./data/backups}/<env>/<UTC时间戳>/
  mysql.sql.gz
  chroma.tar.gz
  knowledge.tar.gz
```

- MySQL：`docker compose exec -T mysql mysqldump`，用 `MYSQL_ROOT_PASSWORD`，库名 `huiyi_demo`，gzip。
- Chroma：卷 `chroma-data` 打 tar（容器内路径 `/app/chroma_store`）。
- 知识库原文件：卷 `knowledge-data` 打 tar（容器内路径 `/var/huiyi/knowledge`）。
- 保留天数默认 `BACKUP_RETAIN_DAYS=7`，按目录名时间戳删除更老的环境子目录。
- 不备份 Redis（缓存可丢）。不做 binlog 增量，不做异地 rsync。

`restore.sh <env> <备份时间戳目录>`：

1. 拒绝在目标栈健康且调用者未传 `--yes` 时覆盖。
2. 停 backend、python-service（避免写库和写向量）。
3. 导入 SQL、解包 chroma 与 knowledge 卷。
4. 再 up，并等待 mysql / backend / python 健康。

演练（写入 `docs/ops/backup-drill.md`，记日期、环境、备份目录、结果）：在一套干净 compose 上恢复后，人工完成 **登录、一次 RAG 问答、一次 Text-to-SQL**。本版不把这次演练放进 GitHub Actions。

cron 示例放 `docs/ops/cron.example`，不由脚本写入系统 crontab：

```text
15 3 * * *  <repo>/scripts/ops/backup.sh prod
*/5 * * * * <repo>/scripts/ops/healthcheck.sh prod
```

---

## 7. 限流

只改 `frontend/nginx.conf`（它被复制到镜像的 `/etc/nginx/conf.d/default.conf`，位于 `http` 内，`limit_req_zone` 可以写在文件顶部）。

```text
limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=auth:10m rate=1r/s;
limit_req_status 429;
```

- `location /api/v1/auth/`：`limit_req zone=auth burst=5 nodelay;`，其余 proxy 头与现有 `/api/` 相同。
- `location /api/`：`limit_req zone=api burst=20 nodelay;`。保留现有 SSE 设置：`proxy_buffering off`、`proxy_cache off`、`proxy_read_timeout 300s`。
- `gzip on`，覆盖 `text/css`、`application/javascript`、`application/json`、`image/svg+xml`。
- 文件末尾用注释写明日后可加的 `listen 443 ssl` 与证书路径，**不** 增加第二个 `server`，**不** `EXPOSE 443`。

后端登录失败锁定保持 v0.7：`LOGIN_FAIL_MAX_ATTEMPTS` / `LOGIN_FAIL_WINDOW_SECONDS` / `LOGIN_LOCK_SECONDS`。本版不新写计数器、不新写过滤器。Nginx 是新增的那一层。

---

## 8. 压测

脚本目录：`scripts/ops/k6/`。

| 文件 | 场景 | 负载 | 目标 |
|---|---|---|---|
| `rag.js` | `POST /api/v1/workspaces/{id}/chat/stream` | 20 VU，持续 5 分钟，10 秒内爬坡 | 首字（time to first byte）P95 < 3s；连接总时长 P95 < 6s |
| `text2sql.js` | `POST /api/v1/workspaces/{id}/analysis/query` | 10 VU，持续 5 分钟，10 秒内爬坡 | HTTP 总耗时 P95 < 10s |
| `sse.js` | 同上 chat stream | 50 VU，持续 3 分钟，**30 秒内爬坡** | 无中途断流；HTTP 非 2xx 比例 < 1% |

账号从环境变量读：`K6_BASE_URL`、`K6_USERNAME`、`K6_PASSWORD`、`K6_WORKSPACE_ID`。脚本先 `POST /api/v1/auth/login` 拿 token。仓库里不写真实口令。

爬坡是为了不和 Nginx `10r/s burst=20` 对着打：50 个连接若在同一秒建立，限流会先把压测打成 429。连接建立之后的 SSE 长连接不再计成新请求。

SQL 校验耗时：现有 `ObservabilityMetrics.sqlValidate` 只有计数。本版补一个 Timer，指标名 **`sql_validate_latency_ms`**，在校验返回时记录耗时。压测报告从 `/actuator/metrics/sql_validate_latency_ms` 读取 P95（Micrometer 的 percentile 若未开，就记录 MAX 与 COUNT，并在报告里说明），目标 < 300ms。

报告同时记下：压测前后 `huiyi.async.failed` 的值、staging MySQL 慢查询条数、backend 与 python 容器内存峰值。staging 的 mysql override 打开慢查询日志，`long_query_time=1`，日志在 mysql 数据卷内。prod 不打开慢查询日志。

LLM 绑定的延迟（首字 3s、RAG 总时长 6s、Text-to-SQL 10s）若未达标：**不挡住打 tag**，报告必须写明模型名、实测 P95、以及一句降级声明（调参或接受该模型的延迟）。错误率 < 1%、SSE 无断流、危险 SQL 仍被拒绝、SQL 校验 Timer 有数，这四项没有降级声明这条退路。

向量检索 < 800ms 不单列场景（桌面总表有，冻结后的三场景没有）。

---

## 9. 告警

`healthcheck.sh <env>` 在目标机执行，使用对应 compose 文件：

| 检查 | 失败条件 |
|---|---|
| backend | `docker compose exec -T backend` 访问 `http://127.0.0.1:8080/actuator/health`，非 200 或 status 不是 UP |
| python | 同样方式访问 `http://127.0.0.1:8001/health`，非 200 |
| MySQL | `docker compose exec -T mysql mysqladmin ping` 失败 |
| 异步失败 | `GET http://127.0.0.1:8080/actuator/metrics/huiyi.async.failed` 的值 **大于** 上次记录 |

上次记录与去重状态放在 `logs/ops-alert-state`（gitignore）。告警名：`backend-down`、`python-down`、`mysql-down`、`async-failed-increased`。同一名字在 `ALERT_DEDUP_MINUTES`（默认 30）内不发第二条钉钉。本地日志每次失败都写。

钉钉正文第一行固定为 `HuiYi告警`，随后是环境、告警名、时间、一行原因。`DINGTALK_WEBHOOK_URL` 为空则不发 HTTP，日志写 `webhook skipped`。不实现加签。

Gauge 实现：新建一个小组件（名称由实施计划钉死），用 `AsyncTaskMapper.selectCount` 统计 `status=FAILED`，注册 `Gauge` 名 `huiyi.async.failed`。每次 scrape / healthcheck 读取时查库，不在失败路径上另维护一份会漂移的内存计数。`METRICS_ENABLED=false` 时不注册，与现有 `ObservabilityMetrics` 一致。单测用 `SimpleMeterRegistry` 与 mapper 桩：2 行 FAILED 时期望 Gauge 为 2。

本版 healthcheck **不** 检查 Redis、磁盘、LLM 错误率、P95。

---

## 10. 发布与回滚

`scripts/ops/deploy.sh`：

```text
deploy.sh <staging|prod> <git-tag>
```

在已经 clone 好的仓库根目录执行。不做镜像仓库，不 `docker pull` 远程业务镜像。

1. `git fetch --tags`
2. 记录当前 HEAD 到 `logs/ops-deploy-previous-<env>`（若该文件已有值且本次是失败回滚，不要把失败中的 HEAD 写成“上一成功版本”）
3. 成功路径开始前，把**当前已健康**的 HEAD 记为回滚目标
4. `git checkout <git-tag>`
5. `GIT_COMMIT=$(git rev-parse HEAD)`、`APP_VERSION=<git-tag>` 写入该环境 env 文件中的这两行（只改这两键）
6. `docker compose -f docker-compose.yml -f compose.<env>.yml --env-file .env.<env> build`
7. `up -d`
8. 调用与 healthcheck 相同的三项存活检查（backend、python、mysql）。异步失败计数升高 **不是** 部署失败
9. 任一步失败：`git checkout` 回滚目标，重复 5–8。回滚后再失败则退出非 0，并不要继续切换
10. 同一 tag 再执行一遍必须成功（幂等）

不在脚本里打 tag，不推远程。v1.4.0 的 git tag 仍按 [release-checklist.md](../../release-checklist.md) 在验收之后单独做。

---

## 11. 数据

无新迁移。`backend/src/main/resources/db/migration/` 在本版结束时仍只有 V1–V13。`FlywayMigrationFilesTest` 增加一条：目录中不存在 `V14` 开头的文件。

不改 `knowledge_docs`、`async_tasks` 的列。Gauge 只读。

---

## 12. 需求评审决策日志（已冻结）

拍板来源：2026-10-31 需求冻结文档。下表是已选项。

| 编号 | 决策项 | 已拍板 |
|---|---|---|
| D01 | 版本范围 | P0 = 环境分离 + 配置治理 + 备份恢复 + 限流 + 压测 + 最小告警 + 发布回滚脚本 |
| D02 | 部署形态 | 单机 Docker Compose × dev / staging / prod。不做 K8s / Swarm |
| D03 | 环境矩阵 | dev=本地 compose；staging=单机 VPS（HTTP）；prod=单机 VPS（HTTP）。HTTPS 顺延 |
| D04 | 配置治理 | `.env.*` 不进 Git，只提交 `.env.example`。密钥全环境变量。配套 runbook |
| D05 | 数据库迁移 | 无新迁移。V14 留空号。阈值与限流参数走配置 |
| D06 | 备份 | 每日 `mysqldump` + Chroma 目录 tar + uploads 目录。保留 7 天。提供 `restore.sh` |
| D07 | 恢复演练 | 干净环境上登录 + RAG + Text-to-SQL，记入 `docs/ops/backup-drill.md` |
| D08 | 限流 | Nginx `limit_req`（`/api` 与 `/api/v1/auth`）+ 现有登录失败锁定。不引新组件 |
| D09 | HTTPS | 无域名。staging/prod 走 `http://IP`。nginx 只注释预留 443，不启用 |
| D10 | 压测 | k6 三场景打 staging，报告落 `docs/ops/`。指标对齐桌面 §14.7，未达标可写降级声明 |
| D11 | 告警 | `healthcheck.sh` 每 5 分钟。钉钉机器人 webhook + `logs/ops-alert.log`。30 分钟去重。关键词 `HuiYi告警`。FAILED 计数用 Gauge `huiyi.async.failed` |
| D12 | 发布与回滚 | `deploy.sh` 切 tag、build、健康检查、失败回滚上一 tag。版本写入 `/actuator/info` |
| D13 | 前端 | 零功能改动。只改 nginx.conf 的限流与 gzip |

---

## 13. 实施裁定

这些不是新的产品决策。冻结文档没有写死，但写错会导致返工，因此在本规格里定死：

1. **端口不能靠 override 删掉。** Compose 合并端口列表时是追加。公共 `docker-compose.yml` 去掉 mysql/redis/python/backend/frontend 的 `ports`。dev override 加回现网端口。staging/prod override 只给 frontend 加 `80:80`。`run_local.sh` 与 README 的 Compose 启动命令改为显式 `-f compose.dev.yml`。
2. **知识库卷是本版新增的运行时挂载**，不是新的存储产品。路径 `/var/huiyi/knowledge`。本机 `mvn spring-boot:run` 仍用 `./data/knowledge`。
3. **不新增 MySQL 账号。** 冻结文档里的 `MYSQL_PASSWORD` 不落地为第二个账号；连接口令就是 `MYSQL_ROOT_PASSWORD`。演示口令按 §5：本地默认值保留；staging/prod 的 `huiyi_ro` 在空卷首次启动时改为 `RO_DB_PASSWORD`，并在缺键时拒绝启动。
4. **staging/prod 不把 8080、8001、3306、6379 暴露到宿主机。** 告警和部署检查走 `docker compose exec`。
5. **SQL 校验耗时补 Timer `sql_validate_latency_ms`**，否则 §8 的 300ms 目标无法测量。不改校验规则。
6. **k6 必须爬坡**，避免 50 并发建连被 `10r/s` 限流先打死。
7. **LLM 延迟允许降级声明；错误率、断流、危险 SQL 拦截、校验 Timer 有数不允许。**
8. **deploy 不依赖镜像仓库**，在 VPS 上的 git checkout 里 build。
9. **dev 的 Redis 不加口令**，避免 `run_local.sh` 和现有 healthcheck 失效。staging/prod 加口令。
10. **慢查询日志只在 staging 打开。**

---

## 14. 验收

- [ ] `run_local.sh` 仍能拉起 dev 的 MySQL（宿主机 3307）和 Redis（6379）。
- [ ] `docker compose -f docker-compose.yml -f compose.staging.yml --env-file .env.staging up -d` 之后，宿主机只监听 frontend 的 80；3306/6379/8001/8080 不在宿主机监听。
- [ ] 已提交内容里仍可以有演示占位符 `huiyi123`、`huiyi_ro123`、`local-dev-only-change-me-32chars-min`、`please-change-me-to-a-32-byte-minimum-secret`。不得提交填好的 `.env`、`.env.staging`、`.env.prod`，也不得出现真实 LLM Key 或钉钉 webhook。
- [ ] 未设置 `RO_DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET`、`REDIS_PASSWORD` 时，staging/prod 的 `docker compose up` 失败。dev 在不设置 `RO_DB_PASSWORD` 时仍能用 `huiyi_ro123` 做 Text-to-SQL。
- [ ] `backup.sh` 产出 `mysql.sql.gz`、`chroma.tar.gz`、`knowledge.tar.gz`。`docs/ops/backup-drill.md` 有一次登录 + RAG + Text-to-SQL 的演练记录。
- [ ] 超过 Nginx 限额的请求返回 429。`/api/v1/auth/` 的限额严于 `/api/`。
- [ ] `docs/ops/load-test-report.md` 有三场景结果。未达标的 LLM 延迟带降级声明。错误率、断流、危险 SQL、校验耗时四项有实测。
- [ ] 停掉 backend 容器后，下一次 healthcheck（或手动跑脚本）写出告警日志；webhook 配了则钉钉正文含 `HuiYi告警`。
- [ ] 制造一条 `async_tasks.status=FAILED` 后，`/actuator/metrics/huiyi.async.failed` 变大，再次 healthcheck 发出 `async-failed-increased`。30 分钟内第二次不发钉钉。
- [ ] `deploy.sh` 中途失败后重跑同一 tag 能成功。回滚到上一个 tag 后 `/actuator/info` 的 `git.commit` 与该 tag 一致。
- [ ] `docs/ops/runbook.md` 覆盖部署、回滚、备份、恢复、告警响应。
- [ ] `flyway_schema_history` 与迁移目录仍是 V1–V13，无 V14 文件。
- [ ] `git diff -- frontend` 只有 `frontend/nginx.conf`。
- [ ] `mvn -B test`、`pytest -q`、现有 `eval-goldens` 仍绿。不加新金标。
