# API 参考

统一约定：

- Java 主服务（8080）所有响应均为统一返回体 `{"code":0,"message":"ok","data":...}`；`code != 0` 时 `message` 为可读错误信息
- Python Sidecar（8001）为内部服务，直接返回业务 JSON；错误返回 `{"detail": "..."}` + 相应 HTTP 状态码
- Postman 集合：[`docs/postman/HuiYi-AI-Data-Assistant.postman_collection.json`](postman/HuiYi-AI-Data-Assistant.postman_collection.json)
- OpenAPI：[`docs/openapi-v1.yaml`](openapi-v1.yaml)
- 除健康检查与注册/登录/refresh/logout 外，Java 接口需 `Authorization: Bearer <accessToken>`
- 旧路径 `/api/chat`、`/api/knowledge`、`/api/analysis` 已移除，返回 404

---

## Java 主服务

### 健康检查

```http
GET /api/health
```

响应：`{"code":0,"message":"ok"}`

### 注册 / 登录 / Refresh / 登出 / 改密

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/password
GET  /api/v1/me
```

登录失败统一返回「用户名或密码错误，或账号暂时不可用。」，不区分用户不存在、密码错误或停用。修改密码后撤销全部 refresh token，并将 `token_version` 加一。

### 工作区与成员

```http
POST /api/v1/workspaces
GET  /api/v1/workspaces
GET  /api/v1/workspaces/{workspaceId}
GET  /api/v1/workspaces/{workspaceId}/members?page=1&pageSize=20&keyword=&status=ACTIVE&role=EDITOR
POST /api/v1/workspaces/{workspaceId}/members
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/role
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/status
```

成员列表响应：`{ items, page, pageSize, total }`。成员不停物理删除，只改状态。

### 上传知识文档

```http
POST /api/v1/workspaces/{workspaceId}/knowledge/upload
Content-Type: multipart/form-data
```

表单字段：`file`（md/txt/pdf/docx，≤ 10MB）

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "docId": 1,
    "docName": "business-glossary.md",
    "fileType": "md",
    "fileSize": 1083,
    "chunkCount": 1,
    "status": "READY",
    "createdAt": "2026-10-15T08:20:07"
  }
}
```

流程：解析分块 → 向量写入（metadata 含 `workspace_id`）→ 元数据落库（`status=READY`）。VIEWER 不能上传。

### 文档列表

```http
GET /api/v1/workspaces/{workspaceId}/knowledge/docs?page=1&pageSize=20
```

响应：`data` 为 `{ items, page, pageSize, total }`。

### 归档文档

```http
DELETE /api/v1/workspaces/{workspaceId}/knowledge/docs/{docId}
PATCH /api/v1/workspaces/{workspaceId}/knowledge/docs/{docId}/status
```

文档状态改为 `ARCHIVED`，并按 `workspace_id + doc_id` 删除向量。不物理删库记录。

### RAG 问答（非流式）

```http
POST /api/v1/workspaces/{workspaceId}/chat
Content-Type: application/json
```

请求：

```json
{
  "sessionId": "s-001",
  "question": "什么是有效订单？"
}
```

`sessionId` 可空，服务端自动生成并返回。

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessionId": "s-c3c607ad",
    "answer": "有效订单是指支付成功且未退款的订单……来源文件：business-glossary.md",
    "intent": "knowledge_qa",
    "references": [{ "source": "business-glossary.md", "score": 0.7407 }]
  }
}
```

`intent` 取值：`knowledge_qa`（知识问答）/ `data_query`（数据查询，走 Text-to-SQL 链路）。

### 会话

```http
POST /api/v1/workspaces/{workspaceId}/chat/sessions
GET  /api/v1/workspaces/{workspaceId}/chat/sessions?page=1&pageSize=20
GET  /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}
GET  /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}/messages
PATCH /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}/status
POST /api/v1/workspaces/{workspaceId}/chat/intent
POST /api/v1/workspaces/{workspaceId}/chat/ask
```

会话必须属于当前用户与当前工作区。

### RAG 问答（SSE 流式）

```http
POST /api/v1/workspaces/{workspaceId}/chat/stream
Accept: text/event-stream
```

请求体同上。事件名保持 `session` / `token` / `references` / `error`，并叠加 `tool.started` / `tool.completed` / `tool.failed`。无 `/agent/runs`，无 `answer.token` / `step`。`tool.*` 的 `data` 为单行 JSON，不含 SQL 全文。

```text
event:session
data:s-c6172277

event:tool.started
data:{"tool":"knowledge.search"}

event:tool.completed
data:{"tool":"knowledge.search","status":"SUCCESS"}

event:token
data:有效
...
event:references
data:[{"source":"business-glossary.md","score":0.74}]
```

数据查询成功顺序示例：`session` → `sql.generate` / `sql.validate` / `sql.execute_readonly` / `data.profile` 的 started/completed → `token*` → `references`。Schema 拒绝或未知工具走 `tool.failed`（`status=REJECTED` 或 `FAILED`），用户句仍是「工具调用失败，请稍后重试。」。run 90s 或 Prompt 超限走 `event:error`，文案「当前问题较复杂，请缩小范围后重试。」。异常中断内容仍会落库。

### 数据分析（Text-to-SQL）

```http
POST /api/v1/workspaces/{workspaceId}/analysis/query
Content-Type: application/json
```

请求：

```json
{
  "sessionId": "s-001",
  "question": "上个月销售额最高的 10 个商品是什么？"
}
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessionId": "s-0e7c3ebe",
    "question": "上个月销售额最高的 10 个商品是什么？",
    "sql": "SELECT p.product_name, SUM(so.total_amount) AS sales_amount FROM ...",
    "rows": [{ "product_name": "4K 便携显示器", "sales_amount": 85734.0 }],
    "summary": { "rowCount": 10, "chartType": "bar" },
    "answer": "上个月销售额最高的10个商品中……"
  }
}
```

安全拦截时 `sql` 为 null、`answer` 为拒绝话术；空结果时 `answer` 明确提示无数据。`chartType` 取值：`bar` / `line` / `pie` / `table`。响应 JSON **没有** `toolCalls` 字段。

---

## Python Sidecar

### 健康检查

```http
GET /health
```

响应：`{"status":"ok"}`

### 文档解析

```http
POST /api/documents/parse
Content-Type: multipart/form-data
```

表单字段：`file`；可选 `chunk_size`（默认 500）、`chunk_overlap`（默认 50）

响应：

```json
{
  "filename": "business-glossary.md",
  "chunk_count": 8,
  "chunks": [
    {
      "id": "business-glossary-md-chunk-0000",
      "text": "销售额默认只统计 status = PAID 的订单。",
      "metadata": { "filename": "business-glossary.md", "chunk_index": 0, "source_type": "md" }
    }
  ]
}
```

### 向量写入

```http
POST /api/vector/upsert
```

请求：`{"collection_name":"huiyi_knowledge","workspace_id":1,"chunks":[{id,text,metadata}]}`，同 id 覆盖（幂等）。知识库 collection 必须带 `workspace_id`。

响应：`{"success":true,"upserted_count":2,"collection_name":"huiyi_knowledge"}`

### 向量检索

```http
POST /api/vector/search
```

请求：`{"query":"什么是有效订单？","top_k":5,"collection_name":"huiyi_knowledge","workspace_id":1}`

响应：`{"success":true,"hits":[{"id":"...","text":"...","metadata":{...},"score":0.74}]}`  
`huiyi_knowledge` 为向量 + BM25 的 RRF 混合结果，`score` 为该命中的向量 cosine（仅关键词命中时为 0）。其它 collection 仍为纯向量。

### 向量删除

```http
POST /api/vector/delete
```

请求：`{"workspace_id":1,"doc_id":12,"collection_name":"huiyi_knowledge"}`

响应：`{"success":true,"deleted_count":2}`。知识库禁止只按文件名删除。

重建旧索引（一次性）：`python -m app.tools.rebuild_knowledge_index --mapping mapping.json`

### 文本相似度

```http
POST /api/vector/similarity
```

请求：`{"text_a":"什么是有效订单？","text_b":"有效订单的定义是什么？"}`

响应：`{"success":true,"score":0.91}`（score 为 embedding cosine，范围 [0, 1]）

### SQL 校验

```http
POST /api/sql/validate
```

请求：`{"sql":"SELECT * FROM sales_orders"}`

通过时：

```json
{ "valid": true, "sql": "SELECT * FROM sales_orders LIMIT 200", "tables": ["sales_orders"] }
```

拒绝时：`{"valid":false,"reason":"仅允许 SELECT 查询"}`

### 数据画像

```http
POST /api/data/profile
```

请求：`{"rows":[{"product_name":"A","sales_amount":100}]}`

响应：

```json
{
  "row_count": 1,
  "columns": ["product_name", "sales_amount"],
  "summary": { "sales_amount": { "sum": 100.0, "max": 100.0, "min": 100.0 } },
  "chart_type": "table",
  "empty": false
}
```
