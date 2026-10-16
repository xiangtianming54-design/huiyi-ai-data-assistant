## 摘要

- 本 PR 解决什么问题？
- 对应版本 / Issue：

## 变更类型

- [ ] 缺陷修复
- [ ] 文档 / 模板 / CI
- [ ] 测试
- [ ] 功能（请注明目标版本，如 v0.7）
- [ ] 重构（行为不变）

## 测试

- [ ] `backend`：`mvn -B verify`
- [ ] `python-service`：`pytest`
- [ ] `frontend`：`npm run build`（若动到前端）
- [ ] 无需跑测试（仅文档，说明原因）

## 行为影响

- 是否改变 RAG / SQL / 意图 / 改写行为？
- 是否需要更新 `docs/demo.md` 或 `docs/baseline.md`？

## 风险评估

- 对召回 / SQL 安全 / 会话 / 兼容性有何影响？
- 失败时如何降级或回滚？

## 检查

- [ ] 未提交 `.env` 或密钥
- [ ] 文档与代码一致（若有行为变化）
- [ ] 范围未滑入路线图下一版本（见 `docs/roadmap.md`）
