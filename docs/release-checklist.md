# 发版核对清单

每个版本打 tag、出 GitHub Release 前勾完下列项。基线版本：`v0.6.0`。

## 代码与测试

- [x] 目标分支已合并（`main` 含 v0.9 实现 `4d83216`）
- [x] `backend`：`mvn -B verify` 通过（本地，2026-10-18，130 tests）
- [x] `python-service`：`pytest --cov=app --cov-report=term-missing` 通过（本地，115 passed）
- [x] `frontend`：`npm run build` 通过（本地，2026-10-18）
- [ ] GitHub Actions 对应 commit 三条 job 均为成功（push `main` 后在网页确认；本机无 `gh`）
- [x] 本版改了 Chat/Analysis 编排：Agent / Facade / Registry 单测已更新

## 安全与仓库卫生

- [x] 未提交 `.env`、密钥、本地数据库文件
- [x] `.gitignore` 仍包含 `.env` / `.env.local`
- [x] README / 文档中的示例只有 `.env.example` 占位符
- [x] 今后提交作者名为 `xianglonghai`（本仓库 local `user.name`；不改写已推送历史）

## 文档

- [x] [baseline.md](baseline.md) 已标注 v0.9 Registry + Router；ReAct 仍非本版
- [x] [roadmap.md](roadmap.md) 当前发布为 v0.9.0；下一版 v1.0 未开工
- [x] README 版本说明与本版一致
- [x] [development.md](development.md) 已写 v0.9 变更与回滚

## 运行与演示

- [x] Compose 健康检查通过（现网栈，2026-10-18）：
  - `GET http://localhost:8080/api/health` → `code=0`，`data.mysql/redis/python`
  - `GET http://localhost:8080/actuator/prometheus` → 200
  - `GET http://localhost:8001/health` → `{"status":"ok"}`
- [x] [demo.md](demo.md) 五场景仍可走通（`e2e-m5.js` 2026-10-18 在 Compose 前端通过）

## 发布动作

- [x] 语义化标签：`v0.9.0` → `7aca6ac`
- [x] `git push origin main` 与 `git push origin v0.9.0`（2026-10-18）
- [ ] GitHub Release 写清：新增 / 修复 / 不包含（本机无 `gh`，需在 GitHub 网页创建）
- [ ] 若本版是公开里程碑：确认仓库可见性仍符合预期

## v0.9.0 特别项（仅本版）

- [ ] Flyway V10：`assistant_run_steps.tool_name`（现网 MySQL 账号未在收尾脚本中核对；代码迁移已入库）
- [x] 五场景 `e2e-m5.js` + 企业场景 `e2e-v07.js`（2026-10-18，现网 Compose 栈）
- [x] 不做 ReAct、`POST /agent/runs`、步骤 SSE、前端 `toolCalls`
- [x] 回滚路径：`v0.8.0` + MySQL 备份（见 development.md）

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
