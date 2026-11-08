# 发布演练

2026-11-08 19:58–20:04 在 `.worktrees/v14-fix-prev` 上对 `huiyi-staging` 重跑。演练当时没有打 `v1.4.0`。同一组修复随后进入 `e6bb834`，本地标签 `v1.4.0` 指向这个提交。标签尚未推送。

修复先做成两个本地提交，因为 `deploy.sh` 只能检出标签。它们不在 `main` 上。

| 标签 | 提交 | 父提交 |
| --- | --- | --- |
| `v14-drill-fix-prev` | `0fffee5c9be5e8d4673fc3f955c1f6b42782ec94` | `524a7ad` |
| `v14-drill-fix-next` | `64182bf6c94968ef312a2f25bd2833f2410f23eb` | `90f4257` |

`git fetch --tags` 仍然连不上 `github.com:443`。演练只跳过这一步，本地标签已经在仓库里。第一次构建被故意做成退出码 1。

## 结果

1. 部署 `v14-drill-fix-next` 时第一次构建失败。脚本滚回 `0fffee5`，构建、`up` 和存活检查成功，退出码 0。`logs/ops-deploy-previous-staging` 记下 `0fffee5c9be5e8d4673fc3f955c1f6b42782ec94`。自动回滚后的 `/actuator/info` 是 `git.commit=0fffee5…`，`app.version=v14-drill-fix-next`。版本号仍是这次请求的标签，提交号是回滚目标。
2. 同一标签再部署一次，退出码 0，HEAD 为 `64182bf`。
3. 再部署 `v14-drill-fix-prev`，退出码 0。`/actuator/info` 为 `git.commit=0fffee5c9be5e8d4673fc3f955c1f6b42782ec94`，`app.version=v14-drill-fix-prev`。提交号与该标签一致。

存活检查在 backend 端口拒绝连接时重试，随后通过。当前 staging 停在 `v14-drill-fix-prev`。对话是 `glm-5.2`（`open.bigmodel.cn`），向量是 `BAAI/bge-m3`（`api.siliconflow.cn`）。

## 此前失败的那次

同一天 19:15 的第一次演练没有通过：compose 没有把版本号送进容器，`timeout 10` 等不到服务起来，回滚重建 MySQL 后 `huiyi_ro` 登录被拒绝。上面这次用的是修复之后的两个本地提交。
