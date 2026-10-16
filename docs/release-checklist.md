# 发版核对清单

每个版本打 tag、出 GitHub Release 前勾完下列项。基线版本：`v0.6.0`。

## 代码与测试

- [ ] 目标分支已合并，工作区无未提交的发版必须项
- [ ] `backend`：`mvn -B verify` 通过
- [ ] `python-service`：`pytest --cov=app --cov-report=term-missing` 通过
- [ ] `frontend`：`npm run build` 通过
- [ ] GitHub Actions 对应 commit 三条 job 均为成功
- [ ] 若改了检索 / SQL / 意图 / 改写：相关单测已更新

## 安全与仓库卫生

- [ ] 未提交 `.env`、密钥、本地数据库文件
- [ ] `.gitignore` 仍包含 `.env` / `.env.local`
- [ ] README / 文档中的示例只有 `.env.example` 占位符
- [ ] 今后提交作者名为 `xianglonghai`（本仓库 local `user.name`；不改写已推送历史）

## 文档

- [ ] [baseline.md](baseline.md) 仍能描述「上一已发布版本」；若本版改变基线能力，同步改基线或在 changelog 写清增量
- [ ] [roadmap.md](roadmap.md) 当前版本状态已更新
- [ ] README 版本说明、Quick Start、Demo 与本版一致
- [ ] 若有行为变化：`docs/development.md` 或独立 `docs/vX.Y-design.md` 已写

## 运行与演示

- [ ] `docker compose up -d` 后健康检查通过：
  - `GET http://localhost:8080/api/health`
  - `GET http://localhost:8001/health`
- [ ] [demo.md](demo.md) 五场景仍可走通（或已注明本版故意变更的期望）

## 发布动作

- [ ] 语义化标签：`v主.次.补`（例：`v0.6.1`）
- [ ] `git push origin <tag>`
- [ ] GitHub Release 写清：新增 / 修复 / 不包含
- [ ] 若本版是公开里程碑：确认仓库可见性仍符合预期

## v0.6.1 特别项（仅本版）

- [ ] 不引入用户体系或 Agent
- [ ] PR / Issue 模板已入库
- [ ] 本清单与 `docs/baseline.md`、`docs/roadmap.md` 已存在
