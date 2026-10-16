# v0.6 设计：混合检索 + 会话摘要压缩

日期：2026-10-16  
状态：待确认

## 意图

现有知识问答只靠向量 Top-5，问法与文档用词不一致时容易漏检。多轮改写只取最近 6 条且整段塞进 Prompt，会话一长就又贵又不稳。

这一轮补两块短板，不改意图路由、不引入 Tool Calling：

1. **知识库检索**改为向量 + 关键词混合，再融合出给 RAG 的 Top-K。
2. **会话改写**保留近 10 轮原文；更早的轮次用 LLM 写成摘要并持久化，和近 10 轮一起参与改写。

## 目标

- 专有名词、短问、和文档用词不一致的问题，比纯向量更容易命中正确分块。
- 超过 10 轮的追问仍能改写指代，且不再把全部历史原文送进改写 Prompt。
- 无命中时的确定性兜底、引用按文件去重、知识/数据分流，行为与现在一致。
- 混合检索对 Java 仍是一次 search 调用；意图示例句 collection 不受影响。

## 非目标

- Agent / Tool Calling、知识库权限隔离。
- Cross-encoder 重排序模型（本轮融合用 RRF，不加第二套模型）。
- 会话摘要展示在前端。
- 改分块参数（仍是 500 / 50）。
- 按 token 精确裁剪（用「轮」和摘要，不用 tokenizer）。

## 反目标

- 为了混合检索把无关片段塞进 Prompt，导致模型编口径。
- 摘要失败就打断主问答。
- 意图分类（`intent_examples`）被关键词检索带偏。

## 约束

- 继续 Java 主编排 + Python Sidecar。
- 知识库向量仍在 Chroma；关键词索引必须与 upsert / delete 同步。
- 会话仍落 MySQL `chat_messages`。
- 摘要是额外一次 LLM 调用，失败必须降级，不能拖垮改写和主链路。

---

## 一、混合检索

### 范围

仅默认知识库 collection `huiyi_knowledge`（及调用方显式当作知识库检索的同一路径）走混合检索。  
`intent_examples` 以及其它非知识库 collection **仍只做向量检索**（builder 按 collection 名分支，不要做成全局开关误伤意图层）。

### 关键词检索（builder 选型，须满足）

- 对已入库分块的 `document` 正文建倒排 / BM25，中文按字符 bigram 或同等可用的分词即可（本仓库知识库以中文制度/口径为主）。
- 与 Chroma 同一生命周期：`upsert` 写入或覆盖，`delete_by_filename` 删干净。
- 进程重启后索引仍在（与 `chroma_store` 一样落盘，目录可配置）。
- 不引入独立搜索集群（Elasticsearch 等不在范围）。

推荐默认：进程内 BM25（如 `rank-bm25`）+ 与 collection 对应的落盘文档表。若有更小依赖且能测的等价实现，builder 可换，但测试契约不变。

### 融合（我的决定：RRF）

两条支路各取 `top_k * 2`（默认 top_k=5 → 各 10 条）：

1. **向量支路**：现有 cosine，`score >= 0.5` 的才进入融合。
2. **关键词支路**：BM25 分大于 0 且至少命中一个查询词的才进入融合。

用 Reciprocal Rank Fusion 合并：

```text
RRF(d) = Σ 1 / (60 + rank_i(d))
```

`k=60`。按 RRF 分取最终 `top_k` 条。返回给 Java 的 `score` 仍用该 hit 的 **向量 cosine**（若仅关键词命中、向量未入选，则 `score` 用 `null` 或 0，见下条）。

仅关键词命中、向量分低于 0.5 的片段：可以进融合，但最终列表里若它排进 Top-K，`score` 标为关键词命中（Java 侧：`score == null` 或 `< 0.5` 时 **不要用 0.5 阈值再滤掉**，否则关键词成果会被现有 `RagService` 阈值打掉）。

因此 Java `RagService` 必须改过滤逻辑：

- 混合检索返回的 hits：**不再用 0.5 一刀切**。
- 空列表才走「未找到足够信息」。
- 引用分数：有 cosine 用 cosine，只有关键词命中则不展示误导性的 0.3，可只展示来源文件名或标 `score` 为关键词支路的归一化值。Builder 选一种并在 API 文档写清；前端能接受 `score` 为空。

向量检索服务不可用：与现在一样，整次 search 失败 → Java 降级话术。关键词索引缺失/为空：退回纯向量（行为与 v0.5 一致），打 warn 日志。

### 接口

保持 `POST /api/vector/search`。内部按 collection 决定 hybrid / vector。  
不新增 Java 客户端方法，除非过滤逻辑需要 hits 上带 `source: vector|keyword|hybrid`（可选 metadata，非必须）。

### 可测契约（Python）

- 同一批文档：问句与正文用词高度重叠但语义向量可能一般时，混合结果应包含该块（用假 embedding 可构造）。
- 纯无关查询：两路皆空 → `hits: []`。
- upsert 幂等、按文件名删除后关键词也检索不到。
- `intent_examples` 路径单测仍只走向量。

---

## 二、近 10 轮 + LLM 摘要

### 一轮的定义

**一轮 = 一条 user + 一条 assistant（按时间相邻）。**  
近 10 轮 = 最近约 20 条 `role in (user, assistant)` 的原文。  
当前正在改写的这一问还不在库里（先改写再落库），不计入这 20 条。

### 何时压缩

会话里「已完成的轮数」> 10 时，比近 10 轮更早的消息不得以原文进入改写 Prompt。  
这些旧消息由 LLM 压成 **一段中文摘要**，持久化，之后改写使用：

```text
{{history}} =
  【更早对话摘要】
  {summary}
  【最近对话】
  用户: …
  助手: …
```

`condense-question.md` 增加一条规则：摘要只作指代背景，近 10 轮原文优先。

### 摘要 Prompt（新文件）

`prompts/history-summarize.md`：

- 角色：对话归档助手。
- 输入：已有摘要（可空）+ 本批要归档的旧消息。
- 输出：一段不超过约 400 字的中文摘要，保留实体、口径、结论、未决指代（商品名、排名、地区等）。
- 不回答用户、不编造数字。

### 持久化（我的决定）

新增表 `chat_session_summaries`（不要用一条假的 `role=summary` 消息混进 `getRecentMessages`）：

| 列 | 含义 |
|---|---|
| session_id | 主键 |
| summary | 摘要正文 |
| covered_until_id | 已归档的最后一条 `chat_messages.id`（该 id 及更早的原文不再进改写） |
| updated_at | 更新时间 |

增量更新：已有摘要 + 「`id > covered_until_id` 且不在近 10 轮窗口内的新溢出消息」→ 再调一次摘要 LLM → 覆盖写入。  
不要每次请求把全部旧消息重压一遍。

触发点：`QueryRewriteService.rewrite` 开头。保证改写看到的是最新摘要。不要在前端触发。

### 失败降级

摘要 LLM 失败或空白：打 warn，**只用近 10 轮原文改写**，旧摘要保持上次成功值（若有）。主问答继续。  
无历史：与现在一样不调改写 LLM。  
不足 10 轮：不调摘要 LLM。

### 前端

无新 UI。`reset()` 仍清 `sessionId`；新会话自然没有摘要行。

### 可测契约（Java）

- 20 条以内：不调摘要，改写 Prompt 的 history 不含「更早对话摘要」。
- 超过 20 条：先调摘要（mock），改写 Prompt 含摘要 + 仅最近 20 条原文。
- 摘要抛错：改写仍发生，history 只有近 20 条。
- `getRecentMessages` 行为不变（仍只读 `chat_messages`）。

---

## 三、留给 builder 的部分

- BM25 库与中文切词的具体实现，只要满足落盘、同步、可测。
- 关键词-only hit 的 `score` 在 JSON 里用 `null` 还是单独字段。
- 摘要表的 MyBatis 实体命名、是否要一份 `ALTER` 兼容已有库（本地 Docker 卷已有表，**必须提供可重复执行的增量 SQL**，不能只改 `init_mysql.sql`）。
- RRF 的 `k=60`、摘要约 400 字：可配常量，不必做成环境变量，除非现有配置风格已经这么做。

## 成功怎么验收

1. 上传现有 `business-glossary.md`：用文档里的专有词换一种说法提问，混合检索仍能引用该文件（比纯向量更稳，可用构造数据单测证明融合逻辑）。
2. 同一 session 连续超过 10 轮后，改写日志里 history 含摘要、原文条数不超过约 20；第 11 轮及以后的追问仍能补全「那第二名呢？」这类指代。
3. 停掉摘要所用的 LLM（mock 失败）：问答接口仍 200，不 500。
4. 删除知识库文件后，向量和关键词都检索不到该文件分块。
