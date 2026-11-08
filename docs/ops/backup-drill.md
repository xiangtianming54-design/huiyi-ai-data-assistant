# 备份恢复演练

| 日期 | 环境 | 备份目录 | 登录 | RAG | Text-to-SQL | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-11-08 | 本机 Docker 项目 `huiyi-staging`，代码 `90f4257` | `data/backups/staging/20261108T091459Z` | 通过。清卷恢复后，原账号仍能登录并拿到 access token | 通过。恢复后问「什么是有效订单？」，命中 `有效订单.md`，回答里有「支付」「已完成」「没有」「取消」 | 通过。恢复后问「上个月销售额最高的 10 个商品是什么？」返回 10 行。危险问句的拒绝是在恢复前核对的 | 三件套：`mysql.sql.gz` 15603 字节、`chroma.tar.gz` 160232 字节、`knowledge.tar.gz` 364 字节。第一次导入因 `.env.staging` 带 Windows 换行失败；去掉换行后 `restore.sh` 退出 0，随后登录、RAG、Text-to-SQL 如上 |

模型是 `Qwen/Qwen2.5-7B-Instruct`，地址是硅基流动。当时智谱的 `embedding-3` 返回 429，`BAAI/bge-m3` 也不在智谱上，这次演练把对话和向量都指到了硅基流动。这是走错之后的记录。现行分工是对话用智谱 `glm-5.2`，向量用硅基流动 `BAAI/bge-m3`。口令没有写入本文件。见 [v1.4 发布问题](../v14-release-issues.md)。
