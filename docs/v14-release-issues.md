# v1.4 发布问题

本地标签 `v1.4.0` 指向 `e6bb834`（`fix: finish v1.4 staging acceptance`）。本版验收在 2026-11-08 完成。本文只记这次发布里已经核对过原因的问题。格式与 [pitfalls.md](pitfalls.md) 相同：现象 / 原因 / 处理 / 以后。不写密钥、`.env` 内容或 token。

标签还没有推送，GitHub Release 没有做。`run_local.sh` 这次没有重跑，因为 staging 栈还在，dev 和 staging 不能同时起。

---

## 1. 壳脚本和 env 文件带了 Windows 换行

- **现象**：空数据卷第一次初始化报 `env: 'bash\r': No such file or directory`。恢复时宿主机 `mysql` 报 Access denied。`.env.staging` 里当时有 21 个 CR。
- **原因**：仓库 `core.autocrlf=true`。Git Bash 往往能跑带 CR 的脚本，容器里的 Linux bash 不能。带 CR 的口令和环境里的口令不是同一个字符串。
- **处理**：`.gitattributes` 增加 `*.sh text eol=lf`。给容器用的 env 去掉 CR。MySQL 入口在执行 `init_ro_password.sh` 前再剥一次 CR。
- **以后**：进 Linux 容器的脚本必须是 LF。从 Windows 复制 `.env.staging` 之后先确认没有 CR，再跑 `backup.sh` / `restore.sh` / `deploy.sh`。

## 2. 对话和向量被指到同一个模型

- **现象**：文档解析失败，智谱返回模型不存在。`BAAI/bge-m3` 发给了智谱。接着 `embedding-3` 返回 429。有一次把 `LLM_*` 整组改到硅基流动的 `Qwen/Qwen2.5-7B-Instruct`，对话和向量都走了硅基流动。
- **原因**：聊天和向量原来共用 `LLM_BASE_URL` / `LLM_API_KEY`。查询时的向量同样走 Python 的 embedding 客户端，不只是文档入库。
- **处理**：聊天保持智谱 `glm-5.2`（`LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`）。向量单独用硅基流动 `BAAI/bge-m3`（`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`LLM_EMBEDDING_MODEL`）。现有 Chroma 向量已按 bge-m3 写入。
- **以后**：不要为了修向量去改 `LLM_*`。验收前核对两个 host：聊天是 `open.bigmodel.cn`，向量是 `api.siliconflow.cn`。[backup-drill.md](ops/backup-drill.md) 里「对话也指到硅基流动」是这次走错之后的记录，不是现行分工。

## 3. SSE 压测空转，把限流打成 99% 错误

- **现象**：第一轮 SSE 约 20 万次请求，`http_req_failed` 99.21%，k6 退出码 99。
- **原因**：每个虚拟用户流一结束就立刻再发。`/api/` 是 10 r/s、burst 20，空转会先被 429 打死。
- **处理**：`scripts/ops/k6/sse.js` 每次只 `http.post` 一条，然后 `sleep(300)`。按这个形状重跑：51 次请求，失败率 0，退出码 0。`k6_shape_test.sh` 现在锁定 `sleep(300)`。
- **以后**：SSE 场景是一个虚拟用户占住一条流。收完就重发的数字不能写进报告。工作区里曾经把这两行删掉，标签 `e6bb834` 里仍在；改这个文件先跑 `k6_shape_test.sh`。

## 4. 存活检查把 `docker compose exec` 的耗时当成服务挂了

- **现象**：`probe_backend` 大约 10.5 秒就失败。随后把超时放到 30 秒，健康检查先是 HTTP 503，服务起来之后是 UP。Spring 进程启动大约 9–13 秒，一次 `docker compose exec` 本身大约 9 秒。
- **原因**：`timeout 10` 先杀掉了 exec。连接拒绝会立刻返回，启动中的进程则需要再等。
- **处理**：单次探测超时 30 秒。`probe_backend` 在 60 秒内每 2 秒重试到 UP。`healthcheck.sh` 的超时同样改为 30 秒。
- **以后**：一次 exec 超时不等于应用 DOWN。先看是连接拒绝还是命令被杀掉。

## 5. 版本号没有进容器，Actuator 一直是 unknown

- **现象**：`/actuator/info` 的 `git.commit` 和 `app.version` 都是 `unknown`。`docker compose config` 里看不到这两个键。
- **原因**：`--env-file` 只替换 compose 文件里写到的变量，不会把文件里其余的键注入容器。
- **处理**：staging 和 prod 的 backend 显式传入 `GIT_COMMIT`、`APP_VERSION`。`application-staging.yml` / `application-prod.yml` 读这两个变量。
- **以后**：部署后读 `/actuator/info`。自动回滚时 `git.commit` 是回滚到的提交，`app.version` 仍是这次请求的标签。规格要的对照是再执行一次 `deploy.sh <env> <上一 tag>`，那时两个字段都要等于那个标签。Git Bash 访问 `/actuator/...` 前设置 `MSYS_NO_PATHCONV=1`，否则路径会被改成 Windows 路径并返回 HTTP 400。`docker cp /tmp/...` 也会落到 `C:\tmp`。

## 6. 重建 MySQL 后只读账号被拒绝

- **现象**：回滚重建 MySQL 后 `/actuator/health` 为 DOWN。Hikari 报 `huiyi_ro` Access denied (using password: YES)。两个容器里的 `RO_DB_PASSWORD` 长度和哈希一致，库里的账号口令已经和 env 不一致。日志里没有 Initializing database。
- **原因**：`docker-entrypoint-initdb.d` 只在空数据卷第一次启动时执行。重建容器不会重跑初始化，已有卷上的 `huiyi_ro` 不会自己跟上 env。
- **处理**：`scripts/mysql_entrypoint.sh` 等最终 `mysqld` 就绪后，再执行 `init_ro_password.sh`，最多 5 次。临时服务器阶段的 cmdline 还不是 `mysqld`，不能在那时候改口令。口令里的反斜杠要先加倍，再加倍单引号；含换行的口令直接拒绝。
- **以后**：看到 Access denied 时，先区分「env 没传进容器」和「卷里的账号是旧口令」。不要指望重建容器会重跑 init。不重启 MySQL 又要立刻改口令时，仍用 runbook 里的 `ALTER USER`。

## 7. 本机连不上 GitHub，发布脚本的 fetch 会先失败

- **现象**：`git fetch --tags` 对 `github.com:443` 连接被重置或超时，退出码 128。演练在这一步就停过。
- **原因**：这台机器当时到 GitHub 的 443 不通。脚本按设计必须先 fetch。
- **处理**：演练用一次性的 PATH 包装只跳过这一次 `git fetch --tags`，没有改 `deploy.sh`。本地标签当时已经在仓库里。
- **以后**：网络不通就记在演练记录里，不要把跳过 fetch 写进 `deploy.sh`。能连上之后再推 `main` 和 `v1.4.0`。

## 8. 校验耗时不能报成 p95

- **现象**：`sql_validate_latency_ms` 的 MAX 读出来是 0，分位没有配置。
- **原因**：这个 Timer 的基础单位是秒，MAX 会衰减。没有打开百分位，就不能报 p95。
- **处理**：报告写 COUNT 146、TOTAL_TIME 2.069 秒、平均 14.2 毫秒。平均用来对照 300 毫秒，不写 p95。
- **以后**：这个指标只报 COUNT、TOTAL_TIME 和平均。MAX 衰减之后不要补一个 p95。

## 9. 压测前的失败任务数没有读

- **现象**：报告里 `huiyi.async.failed` 只有压测后的 4.0（2026-11-08T10:58:04Z）。更早一次别的运行读到过 5.0。
- **原因**：这一轮 GLM 压测开始前没有读 Gauge。Gauge 是读取当时 `async_tasks.status=FAILED` 的数量，不是增量。
- **处理**：报告写明「这轮压测开始前没有读」，不用 5.0 充当这一轮的前值。
- **以后**：k6 开始前和结束后各读一次 `/actuator/metrics/huiyi.async.failed`。没有前值就留空，不借用另一次运行的数。

## 10. 告警去重没有打到钉钉

- **现象**：停 backend、失败任务增加、30 分钟内第二次不发，都有记录。接收端是 `127.0.0.1:18080`，不是钉钉。
- **原因**：当时没有配置钉钉 webhook。webhook 为空时日志是 `webhook skipped`，并且不去重。
- **处理**：正文要求 `HuiYi告警` 和 `async-failed-increased` 只在本机接收端上核对。
- **以后**：没有钉钉地址就写成「本机 webhook」。不要把这次记录说成钉钉已经收到。

## 11. 延迟未达标，其余四项不能跟着降级

- **现象**：`glm-5.2` 的 RAG waiting p95 15592 ms、duration p95 16096 ms，Text-to-SQL duration p95 19615 ms。k6 退出码 99。错误率仍是 0。
- **原因**：退出码 99 只表示延迟阈值没过。摘要里的 `thresholds: true` 在退出码 99 时表示这条阈值失败。
- **处理**：只给 LLM 延迟写降级声明。错误率、断流、危险 SQL、校验 Timer 有数保持实测。
- **以后**：延迟可以声明降级。那四项没有退路，不能改成「已通过」来消掉退出码 99。

## 12. 脏工作区和发版提交叠在一起

- **现象**：`main` 上还有未提交的 Java 改动、v1.4.1 计量说明，以及上面第 3 条里被删掉的 `sleep(300)`。
- **原因**：验收和后续功能改在同一棵工作区。测试如果对着这棵树跑，结果不属于标签。
- **处理**：`mvn -B test`、`pytest -q`、`EvalGoldenRunner` 在干净工作区 `.worktrees/v14-tag` 的 `e6bb834` 上跑。结果是 212、120、1，都通过。标签打在这个提交上。归档时把工作区里丢失的 `sleep(300)` 加回去。
- **以后**：打标签前用单独工作区检出即将被打标签的提交。未提交的下一版改动不进这个标签。`RunbookTest` 曾经锁着「尚未发布」；归档时要连测试一起改，否则文档改完测试会红。
