# 发版核对清单

每个版本打 tag、出 GitHub Release 前勾完下列项。基线版本：`v0.6.0`。

## 代码与测试

- [x] 目标分支已合并（`main` 含 v1.1 实现 `2013015`）
- [x] `backend`：`mvn -B test` 通过（本地，2026-10-18，156 tests）
- [ ] `python-service`：本版未改 Python；沿用 v0.9 的 pytest 记录
- [ ] `frontend`：本版未改前端；沿用 v0.9 的 `npm run build` 记录
- [ ] GitHub Actions 对应 commit 四条 job 均为成功（含 `eval-goldens`；push `main` 后在网页确认；本机无 `gh`）
- [x] 本版评测金标：`EvalGoldenRunner` + `testdata/eval/` 12 条已绿

## 安全与仓库卫生

- [x] 未提交 `.env`、密钥、本地数据库文件
- [x] `.gitignore` 仍包含 `.env` / `.env.local`
- [x] README / 文档中的示例只有 `.env.example` 占位符
- [x] 今后提交作者名为 `xianglonghai`（本仓库 local `user.name`；不改写已推送历史）

## 文档

- [x] [baseline.md](baseline.md) 已标注 v1.1 评测金标 / CI `eval-goldens`
- [x] [roadmap.md](roadmap.md) 当前发布为 v1.1.0；下一版 v1.2 未开工
- [x] README 版本说明与本版一致
- [x] [development.md](development.md) 已写 v1.1 变更与回滚

## 运行与演示

- [ ] Compose 健康检查（本版收尾未重跑现网栈；v0.9 发版时已通过）
- [ ] 五场景 `e2e-m5.js`（本版收尾未重跑；代码侧单测 156/156；金标走 stub 不连 Compose）

## 发布动作

- [x] 语义化标签：`v1.1.0` → `8dca47a`
- [x] `git push origin main` 与 `git push origin v1.1.0`（2026-10-18）
- [ ] GitHub Release 写清：新增 / 修复 / 不包含（本机无 `gh`，需在 GitHub 网页创建）
- [ ] 若本版是公开里程碑：确认仓库可见性仍符合预期

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
