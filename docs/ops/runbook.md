# 运维手册

v1.4.0 单机 Compose 的操作说明。本版尚未发布。规格见 [v1.4 规格](../superpowers/specs/2026-10-31-v14-production-ready.md)，计划见 [v1.4 计划](../superpowers/plans/2026-10-31-v14-production-ready.md)。

公共 `docker-compose.yml` 不再发布宿主机端口。dev 必须带上 `compose.dev.yml`。staging / prod 只把 frontend 映到宿主机 `80:80`。mysql、redis、python、backend 的健康检查和备份都走 `docker compose exec`，不经过公网，也不经过 frontend Nginx。

`docs/ops/backup-drill.md` 保持空表。`docs/ops/load-test-report.md` 的实测保持「未对 staging 执行」。本手册不把演练或压测写成已通过，也不标记 GitHub Release 已完成。

命令在已经 clone 好的仓库根目录执行。`<env>` 在备份、恢复、告警里是 `dev`、`staging` 或 `prod`。`deploy.sh` 只接受 `staging` 或 `prod`。

## 部署

### 拉起环境

| 环境 | 命令 | 数据与端口 |
|---|---|---|
| dev | `docker compose -f docker-compose.yml -f compose.dev.yml up -d` | 现有命名卷。宿主机端口：MySQL `${MYSQL_HOST_PORT:-3307}`、Redis `6379`、Python `127.0.0.1:8001`、backend `8080`、frontend `5173:80` |
| staging | `docker compose -f docker-compose.yml -f compose.staging.yml --env-file .env.staging up -d` | Compose 项目名 `huiyi-staging`。只发布 frontend `80:80` |
| prod | `docker compose -f docker-compose.yml -f compose.prod.yml --env-file .env.prod up -d` | Compose 项目名 `huiyi-prod`。只发布 frontend `80:80` |

本地只起 MySQL 和 Redis 时：

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up -d mysql redis
```

`scripts/run_local.sh` 使用同一条命令。只执行 `docker compose up` 不会把这些端口映到宿主机。dev 的公共文件和 `compose.dev.yml` 都不要设置 `name:`。staging / prod 的项目名用来和本机 dev 卷分开。

### 用 tag 发布

在目标机的 git 工作区：

```bash
scripts/ops/deploy.sh <staging|prod> <git-tag>
```

脚本依次：`git fetch --tags`；把当前已健康的 HEAD 记为回滚目标（`logs/ops-deploy-previous-<env>`；失败中的 HEAD 不会被写成上一成功版本）；`git checkout` 该 tag；把 `GIT_COMMIT`（`git rev-parse HEAD`）和 `APP_VERSION`（该 tag）写入该环境 env 文件的这两行；`docker compose -f docker-compose.yml -f compose.<env>.yml --env-file .env.<env> build`；`up -d`；检查 backend、python、mysql 存活。`huiyi.async.failed` 升高不是部署失败。任一步失败则 checkout 回滚目标并再做一次写 env、build、up、存活检查。第二次仍失败则退出非 0，不再继续切换。同一 tag 再执行一遍必须成功。

脚本不打 tag，不推远程，不 `docker pull` 业务镜像。v1.4.0 的 git tag 与 GitHub Release 仍按 [release-checklist.md](../release-checklist.md) 在验收之后单独做。

staging / prod 不映射 8080。看版本用：

```bash
docker compose -f docker-compose.yml -f compose.<env>.yml --env-file .env.<env> exec -T backend \
  bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/info HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" >&3 && cat <&3'
```

`info.git.commit` 与 `info.app.version` 来自 `GIT_COMMIT`、`APP_VERSION`。两者都空时 Actuator 显示 `unknown`。

### 配置键

只提交 `.env.example`。`.env`、`.env.dev`、`.env.staging`、`.env.prod` 不进 Git。下表不写真实口令或 webhook。

| 键 | 谁在用 | 空值时 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Compose 覆盖。staging 写死 `docker,staging`，prod 写死 `docker,prod` | dev 不靠这个键选 staging/prod。example 里留空 |
| `MYSQL_ROOT_PASSWORD` | MySQL root，也是应用连接口令 `DB_PASSWORD`。备份、恢复用它做 `mysqldump` / `mysql` | `.env.example` 保留演示值 `huiyi123`。staging/prod 写成 `${MYSQL_ROOT_PASSWORD:?必须配置}`，缺键时 `docker compose up` 直接失败 |
| `RO_DB_PASSWORD` | 空卷首次初始化，以及之后每次 MySQL 启动，`scripts/init_ro_password.sh` 用它 `ALTER USER` 改 `huiyi_ro` | 空则保持 `huiyi_ro123`。dev 不要求此键。staging/prod 缺键则 compose 失败。已有卷见「恢复」 |
| `REDIS_PASSWORD` | staging/prod 的 `redis-server --requirepass` 与 Java | dev 空口令，healthcheck 仍是 `redis-cli ping`。staging/prod 缺键则 compose 失败 |
| `JWT_SECRET` | 签发 JWT | 本地 example 保留演示占位。staging/prod 缺键则 compose 失败 |
| `LLM_API_KEY` | 聊天与 embedding | 空则无法调用模型。仓库不写真实 Key |
| `LLM_BASE_URL` | OpenAI 兼容端点 | 空则无法调用模型 |
| `DINGTALK_WEBHOOK_URL` | `healthcheck.sh` 发钉钉 | 空则不发 HTTP。见「告警响应」 |
| `BACKUP_DIR` | `backup.sh` 根目录 | 默认 `./data/backups` |
| `BACKUP_RETAIN_DAYS` | 按目录名时间戳删除更老的备份 | 默认 `7` |
| `GIT_COMMIT` | `deploy.sh` 写入；`/actuator/info` 的 `git.commit` | 未部署时为 `unknown` |
| `APP_VERSION` | `deploy.sh` 写入 tag；`/actuator/info` 的 `app.version` | 未部署时为 `unknown` |
| `ALERT_DEDUP_MINUTES` | 同一告警名的钉钉去重窗口 | 默认 `30`。非数字时 `healthcheck.sh` 退出 1 |

staging/prod 另外要求 `REDIS_PASSWORD` 与 `JWT_SECRET` 使用 `${VAR:?必须配置}`。dev override 不要求 `RO_DB_PASSWORD`。

## 回滚

没有新迁移。迁移目录仍是 V1–V13，无 V14。回滚不必还原 schema，也不要为了回到上一版去恢复 MySQL 备份。

在仓库根目录重新执行：

```bash
scripts/ops/deploy.sh <env> <上一 tag>
```

`<env>` 是 `staging` 或 `prod`。`deploy.sh` 拒绝 `dev`。上一 tag 是上一成功版本的 git tag。脚本会 checkout 该 tag，重写 `GIT_COMMIT` 与 `APP_VERSION`，再 build、up，并做 backend / python / mysql 存活检查。回滚后再失败则退出非 0，不要继续切换。完成后 `/actuator/info` 的 `git.commit` 应与上一 tag 的 commit 一致。人工核对留在 staging 验收，本文件不记录已通过。

## 备份

```bash
scripts/ops/backup.sh <dev|staging|prod>
```

产出目录：

```text
${BACKUP_DIR:-./data/backups}/<env>/<UTC时间戳>/
  mysql.sql.gz
  chroma.tar.gz
  knowledge.tar.gz
```

- MySQL：`docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --databases huiyi_demo`，再 gzip。库名是 `huiyi_demo`。
- Chroma：在 `python-service` 里把 `/app/chroma_store` 打成 `chroma.tar.gz`。
- 知识库原文件：在 `backend` 里把 `/var/huiyi/knowledge` 打成 `knowledge.tar.gz`。
- 不备份 Redis。不做 binlog 增量，不做异地 rsync。
- 三件套任一为空则删除未完成目录并退出非 0。
- 保留天数默认 7，删除该环境下更老的 `YYYYMMDDTHHMMSSZ` 目录。

cron 示例在 [cron.example](cron.example)。脚本不写入系统 crontab：

```text
15 3 * * *  <repo>/scripts/ops/backup.sh prod
```

演练记入 [backup-drill.md](backup-drill.md)：日期、环境、备份目录、登录、RAG、Text-to-SQL。该表目前没有结果。不要把空行填成已通过。

## 恢复

```bash
scripts/ops/restore.sh <dev|staging|prod> <备份时间戳目录> [--yes]
```

`<备份时间戳目录>` 必须同时有 `mysql.sql.gz`、`chroma.tar.gz`、`knowledge.tar.gz`。

1. 目标栈里 mysql、backend、python-service 都健康，且调用者没传 `--yes` 时，拒绝覆盖并退出 2。
2. 停 backend、python-service。
3. 导入 SQL，再把 chroma 与 knowledge 卷解包回去。
4. `up -d`，等待 mysql、backend、python-service 健康。

恢复之后的登录、RAG、Text-to-SQL 记入演练表。本版不把这次演练放进 GitHub Actions，也没有已填写的通过记录。

### 已有卷上的只读口令

`scripts/init_ro_password.sh` 挂在 MySQL 的 `docker-entrypoint-initdb.d/`，初始化脚本不会重跑。容器入口在最终 `mysqld` 就绪后会再执行这个脚本，把已有卷上的 `huiyi_ro` 对齐到当前 `RO_DB_PASSWORD`。口令为空则保持 `huiyi_ro123`。下面的 `ALTER USER` 只在不重启 MySQL、又要立刻改口令时使用。

会改 `RO_DB_PASSWORD` 的是 staging 和 prod。`compose_files` 给 dev 读 `.env`，给 staging 读 `.env.staging`，给 prod 读 `.env.prod`。`--env-file` 不会把口令导出到宿主机。下面两条命令用单引号把 `mysql` 交给容器里的 shell，由容器展开 `MYSQL_ROOT_PASSWORD`。

```bash
docker compose -f docker-compose.yml -f compose.staging.yml --env-file .env.staging exec -T mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "ALTER USER '\''huiyi_ro'\''@'\''%'\'' IDENTIFIED BY '\''<新口令>'\'';"'
```

```bash
docker compose -f docker-compose.yml -f compose.prod.yml --env-file .env.prod exec -T mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "ALTER USER '\''huiyi_ro'\''@'\''%'\'' IDENTIFIED BY '\''<新口令>'\'';"'
```

把 `<新口令>` 换成该环境的 `RO_DB_PASSWORD`，不要把口令写进 Git。dev 上已有数据卷不会重跑 init，只读口令保持 `huiyi_ro123`，与 yml 默认值一致。权限仍是 `GRANT SELECT ON huiyi_demo.*`。本版不新增 MySQL 账号。

## 告警响应

```bash
scripts/ops/healthcheck.sh <dev|staging|prod>
```

cron 示例（每 5 分钟，不由脚本写入 crontab）：

```text
*/5 * * * * <repo>/scripts/ops/healthcheck.sh prod
```

检查都在容器内做。失败条件：

| 告警名 | 条件 |
|---|---|
| `backend-down` | backend 上 `http://127.0.0.1:8080/actuator/health` 非 200，或 status 不是 `UP` |
| `python-down` | python-service 上 `http://127.0.0.1:8001/health` 非 200 |
| `mysql-down` | `mysqladmin ping` 失败 |
| `async-failed-increased` | `GET /actuator/metrics/huiyi.async.failed` 的值大于上次记录 |

本脚本不检查 Redis、磁盘、LLM 错误率或 P95。Gauge 升高不是 `deploy.sh` 的失败条件。

生产环境勿关 `huiyi.observability.metrics-enabled`（环境变量 `METRICS_ENABLED`）。关掉后 `huiyi.async.failed` 不注册，`FAILED` 增长不会发 `async-failed-increased`，健康检查只记 `huiyi.async.failed unreadable`。

每次失败都追加 `logs/ops-alert.log`。去重状态在 `logs/ops-alert-state`（gitignore）。同一告警名在 `ALERT_DEDUP_MINUTES`（默认 30）分钟内不发第二条钉钉；本地日志仍然写。钉钉正文第一行是 `HuiYi告警`，随后是环境、告警名、时间、一行原因。不实现加签。

`DINGTALK_WEBHOOK_URL` 为空时不发 HTTP，不要求钉钉可达。检查失败时日志只记 `webhook skipped`，且 `healthcheck.sh` 退出码非 0。

处理：

- `backend-down`、`python-down`、`mysql-down`：先看 `docker compose ps`。服务起不来时，用「回滚」里的上一 tag。
- `async-failed-increased`：查 `async_tasks.status='FAILED'` 的当前行数。处理失败任务，不要把它当成必须回滚 schema 的信号。

停掉 backend 后的实机告警、以及 30 分钟内第二次不发钉钉，依赖一台真正的 staging，不在本文写成已验收。

## 常见故障

- **宿主机上看不到 3307、6379、8080 或 5173。** 公共 Compose 不再发布这些端口。dev 命令必须是 `docker compose -f docker-compose.yml -f compose.dev.yml`。
- **staging 或 prod 的 `docker compose up` 立刻失败。** 缺 `RO_DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET` 或 `REDIS_PASSWORD`。四个键都要出现在对应的 env 文件里。
- **改了 `RO_DB_PASSWORD`，Text-to-SQL 仍被只读账号拒绝。** 已有卷不会重跑 init。按「恢复」里的 `ALTER USER` 改 `huiyi_ro`。
- **worktree 里起了整套 Compose，容器名和现网栈冲突。** dev 不要设置 `name:`。不要在 worktree 里对现网项目再起一套同名栈。
- **在宿主机上 curl `127.0.0.1:8080` 连不上 staging。** staging/prod 不映射 8080、8001、3306、6379。用 `docker compose exec`。
- **接口返回 429。** Nginx：`/api/` 为 `10r/s`、`burst=20`；`/api/v1/auth/` 为 `1r/s`、`burst=5`。k6 必须爬坡，否则建连阶段会被限流打成 429。压测数字仍以 `load-test-report.md` 的「未对 staging 执行」为准。
- **`backup.sh` 没有留下目录。** 三件套有一个为空时，未完成目录会被删掉，脚本退出非 0。先看 mysql、python-service、backend 是否在跑，以及 `MYSQL_ROOT_PASSWORD` 是否为空。
- **`restore.sh` 退出 2。** 目标栈健康。确认要覆盖时才加 `--yes`。
- **`deploy.sh` 中途失败。** 它会回滚到上一健康 HEAD 并再试一次。第二次失败就停，退出非 0。同一 tag 再跑是幂等的；实机结果不在本文填写。
- **`/actuator/info` 仍是 `unknown`。** `deploy.sh` 才会写入 `GIT_COMMIT` 和 `APP_VERSION`。手写 `up` 不会改这两个键。

## 附录：有域名之后的 certbot

本版不执行本附录。现在没有域名，staging 和 prod 走 `http://IP`。`frontend/nginx.conf` 只在文件末尾用注释预留 `listen 443 ssl` 以及证书路径 `/etc/nginx/certs/fullchain.pem`、`/etc/nginx/certs/privkey.pem`。本版不监听 443，不增加第二个 `server`，不 `EXPOSE 443`，也不运行 certbot。

有域名之后再考虑下面的事，不要在本版做：

1. 用 certbot 为该域名签发证书，得到 fullchain 与 privkey。
2. 把证书挂进 frontend 容器，路径与 nginx 注释里的两行一致。
3. 启用那三行注释。仍然不要新增第二个 `server`。
4. 确认 80 上的现有 `/api/` 与 `/api/v1/auth/` 限流还在，再单独验收 HTTPS。

在此之前，忽略本附录。
