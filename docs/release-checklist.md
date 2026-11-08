# 发版核对清单

每个版本打 tag、出 GitHub Release 前勾完下列项。基线版本：`v0.6.0`。

## 代码与测试

- [x] 目标分支已合并（`main` 含 v1.4 验收 `e6bb834`）
- [x] `backend`：`mvn -B test` 通过（干净检出 `e6bb834`，2026-11-08，212 tests）
- [x] `python-service`：`pytest -q` 通过（同一检出，项目虚拟环境，2026-11-08，120 passed）
- [x] `frontend`：相对 `v1.3.0` 只有 `frontend/nginx.conf`
- [ ] GitHub Actions 对应 commit 四条 job 均为成功（含 `eval-goldens`；push `main` 后在网页确认。本机当时连不上 `github.com:443`）
- [x] 本版评测金标：不加新金标；现有 `EvalGoldenRunner` 仍绿（1 个测试，16 条金标）

## 安全与仓库卫生

- [x] 未提交 `.env`、密钥、本地数据库文件
- [x] `.gitignore` 仍包含 `.env` / `.env.local`；`.env.staging` 与 `.env.prod` 不入库
- [x] README / 文档中的示例只有 `.env.example` 占位符
- [x] 今后提交作者名为 `xianglonghai`（本仓库 local `user.name`；不改写已推送历史）

## 文档

- [x] [baseline.md](baseline.md) 已标注 v1.4 单机 staging/prod
- [x] [roadmap.md](roadmap.md) 当前发布为本地标签 `v1.4.0`；GitHub Release 未做
- [x] README 版本说明与本版一致
- [x] [development.md](development.md) 已写 v1.4 变更与回滚
- [x] [v14-release-issues.md](v14-release-issues.md) 记下这次发布里会再犯的问题

## 运行与演示

- [x] staging 备份、恢复、登录、RAG、Text-to-SQL、429、告警和发布回滚已写入 `docs/ops/`
- [ ] `run_local.sh` 与五场景 `e2e-m5.js` 这次没有重跑（staging 栈还在，不与 dev 同时起）

## 发布动作

- [x] 语义化标签：`v1.4.0` → `e6bb834`（附注标签，说明 `v1.4.0 Production Ready`）
- [ ] `git push origin main` 与 `git push origin v1.4.0`（本机当时连不上 GitHub）
- [ ] GitHub Release 写清：新增 / 修复 / 不包含
- [ ] 若本版是公开里程碑：确认仓库可见性仍符合预期

## v1.4.0 特别项（仅本版）

- [x] 无新 Flyway。迁移目录仍是 V1–V13
- [x] 不做 Kubernetes、Swarm、Vault、Grafana、HTTPS、MinIO、异地容灾、用户等级
- [x] `git diff v1.3.0 e6bb834 -- frontend` 只有 `frontend/nginx.conf`
- [x] 回滚路径：`deploy.sh <env> <上一 tag>`，不必还原 schema（见 development.md）
- [x] LLM 延迟只记降级声明；错误率、断流、危险 SQL、校验耗时有实测
- [ ] `run_local.sh` 拉起 dev MySQL 3307 与 Redis 6379（这次未跑）

## v1.3.0 特别项（仅本版）

- [x] Flyway V13：`async_tasks` + `knowledge_docs.storage_key` / `error_message`（无 FK、无 `file_hash` / `parse_status` / `doc_version`）
- [x] 不做 MinIO、hash 去重、任务列表接口、重启补偿、前端轮询、ReAct、`/agent/runs`
- [x] `git diff -- frontend` 为空
- [x] 回滚路径：`v1.2.0` + MySQL 备份（见 development.md）

## v1.2.0 特别项（仅本版）

- [x] Flyway V12：`knowledge_spaces` + 回填 `knowledge_docs.knowledge_space_id` NOT NULL（无 FK）
- [x] 不做异步解析、OSS、细权限码、前端空间 UI、空间恢复、ReAct、`/agent/runs`
- [x] `git diff -- frontend` 为空
- [x] 回滚路径：`v1.1.0` + MySQL 备份（见 development.md）
- [x] 语义化标签：`v1.2.0` → `1cb0b5d`；已 `git push origin v1.2.0`

## v1.1.0 特别项（仅本版）

- [x] 无新 Flyway；无 `/eval`、无 `/agent/runs`、无评测页
- [x] 不做 Prompt 版本/灰度、ReAct、Compose e2e 进 Actions
- [x] CI job `eval-goldens`：`if: always()` 上传 `eval-report.json/.md`
- [x] 回滚路径：`v1.0.0`（见 development.md）；本版 P0 不改 schema

## v1.0.0 特别项（仅本版）

- [x] Flyway V11：仅 `assistant_runs.error_code`（无新表、无 FK、无 steps JSON）
- [x] 不做 ReAct、`POST /agent/runs`、前端 `toolCalls`、评测页
- [x] 回滚路径：`v0.9.0` + MySQL 备份（见 development.md）
- [x] 语义化标签：`v1.0.0` → `2debba2`；已 `git push origin v1.0.0`

## v0.9.0 特别项（仅本版）

- [ ] Flyway V10：`assistant_run_steps.tool_name`（现网 MySQL 账号未在收尾脚本中核对；代码迁移已入库）
- [x] 五场景 `e2e-m5.js` + 企业场景 `e2e-v07.js`（2026-10-18，现网 Compose 栈）
- [x] 不做 ReAct、`POST /agent/runs`、步骤 SSE、前端 `toolCalls`
- [x] 回滚路径：`v0.8.0` + MySQL 备份（见 development.md）
- [x] 语义化标签：`v0.9.0` → `7aca6ac`；已 `git push origin v0.9.0`

## v0.8.0 特别项（仅本版）

- [x] Flyway V7–V9：`assistant_runs` / `assistant_run_steps` / `llm_calls`
- [x] 五场景 `e2e-m5.js` + 企业场景 `e2e-v07.js`
- [x] 不做 Grafana、Agent Loop、P2 调试面板
- [x] 回滚路径：`v0.7.0` + MySQL 备份（见 development.md）

## v0.7.0 特别项（仅本版）

- [x] 本地标签 `v0.7.0`（`main`）
- [x] GitHub Actions 对应 commit 三条 job 均为成功（`97f53d2` CI#15）
- [x] Flyway：旧 v0.6 卷已 migrate；空卷用独立 MySQL 容器再验一次
- [x] 五场景 `e2e-m5.js` + 企业场景 `e2e-v07.js`
- [x] 回滚路径：`v0.6.1` + MySQL / Chroma 备份（见 development.md）
