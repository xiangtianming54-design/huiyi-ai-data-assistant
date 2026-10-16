# 发版核对清单

每个版本打 tag、出 GitHub Release 前勾完下列项。基线版本：`v0.6.0`。

## 代码与测试

- [x] 目标分支已合并并推送（`main` @ `97f53d2`；标签 `v0.7.0` → `627da84`）
- [x] `backend`：`mvn -B verify` 通过（本地）
- [x] `python-service`：`pytest --cov=app --cov-report=term-missing` 通过（本地 111+）
- [x] `frontend`：`npm run build` 通过（本地）
- [x] GitHub Actions 对应 commit 三条 job 均为成功（`97f53d2` CI#15 成功）
- [x] 若改了检索 / SQL / 意图 / 改写：相关单测已更新（本版未改检索/SQL 算法）

## 安全与仓库卫生

- [x] 未提交 `.env`、密钥、本地数据库文件
- [x] `.gitignore` 仍包含 `.env` / `.env.local`
- [x] README / 文档中的示例只有 `.env.example` 占位符
- [x] 今后提交作者名为 `xianglonghai`（本仓库 local `user.name`；不改写已推送历史）

## 文档

- [x] [baseline.md](baseline.md) 仍能描述「上一已发布版本」；若本版改变基线能力，同步改基线或在 changelog 写清增量
- [x] [roadmap.md](roadmap.md) 当前版本状态已更新
- [x] README 版本说明、Quick Start、Demo 与本版一致
- [x] 若有行为变化：`docs/development.md` 或独立 `docs/vX.Y-design.md` 已写

## 运行与演示

- [x] `docker compose up -d` 后健康检查通过（v0.7 镜像，2026-10-16）：
  - `GET http://localhost:8080/api/health`
  - `GET http://localhost:8001/health`
- [x] [demo.md](demo.md) 五场景仍可走通（`e2e-m5.js` 在 Compose 前端上通过）

## 发布动作

- [x] 语义化标签：`v0.7.0`
- [x] `git push origin v0.7.0`
- [ ] GitHub Release 写清：新增 / 修复 / 不包含（本机无 `gh`，需在 GitHub 网页创建）
- [ ] 若本版是公开里程碑：确认仓库可见性仍符合预期

## v0.7.0 特别项（仅本版）

- [x] 本地标签 `v0.7.0`（`main`）
- [x] GitHub Actions 对应 commit 三条 job 均为成功（`97f53d2` CI#15）
- [x] Flyway：旧 v0.6 卷已 migrate；空卷用独立 MySQL 容器再验一次
- [x] 五场景 `e2e-m5.js` + 企业场景 `e2e-v07.js`
- [x] 回滚路径：`v0.6.1` + MySQL / Chroma 备份（见 development.md）

## v0.6.1 特别项（仅本版）

- [ ] 不引入用户体系或 Agent
- [ ] PR / Issue 模板已入库
- [ ] 本清单与 `docs/baseline.md`、`docs/roadmap.md` 已存在
