# HuiYi v0.7 · Enterprise Foundation 开发文档

> 版本：v0.7.0  
> 状态：已发布（标签 `v0.7.0` → `627da84`；main 含发版后文档/Docker 门禁 `97f53d2`）  
> 基线：`huiyi-ai-data-assistant v0.6.1`  
> 修订说明：本版取消成员 / 用户物理删除，改为账号与成员生命周期状态治理；补充 Chroma 迁移、会话归属、Flyway 和 API 设计。  

---

## 1. 版本目标

v0.7 只做企业基础层，不做 Agent Harness、不重构 RAG、不更换向量库。

本版本完成后，系统应满足：

```text
1. 未登录不能访问核心接口；
2. 用户通过工作区访问资源；
3. 知识库、会话按工作区和用户隔离；
4. 成员和用户使用生命周期状态治理，不做物理删除；
5. RAG 检索按 workspace_id 隔离；
6. Flyway 管理应用表结构；
7. 关键操作有审计；
8. 登录失败有限次保护；
9. 现有五个核心 Demo 场景在登录后仍可用。
```

---

## 2. 范围

## 2.1 本版本包含

```text
1. 注册 / 登录 / Refresh Token / Logout；
2. Workspace 创建、切换、成员管理；
3. RBAC：OWNER / ADMIN / EDITOR / VIEWER；
4. 用户与成员生命周期状态；
5. knowledge_docs、chat_sessions、chat_messages、Chroma metadata 工作区归属；
6. API 升级到 /api/v1；
7. Spring Security + JWT；
8. Flyway；
9. 最小审计；
10. 登录失败限次；
11. 前端登录页和工作区切换。
```

## 2.2 本版本不包含

```text
1. Agent Harness；
2. Tool Registry；
3. Agent Loop；
4. 多租户计费；
5. 全局限流；
6. 对象存储；
7. 异步文档解析；
8. Rerank；
9. 模型管理后台；
10. Kubernetes；
11. 修改混合检索 / RRF / 会话摘要算法；
12. 按工作区拆分演示业务表。
```

---

## 3. 核心原则

## 3.1 不做物理删除

用户、成员、会话、知识文档均不提供物理删除能力。

| 对象 | 生命周期方式 |
|---|---|
| 用户账号 | `ACTIVE / SUSPENDED / DISABLED / DEPRECATED / ARCHIVED` |
| 工作区成员 | `ACTIVE / SUSPENDED / REVOKED / LEFT / ARCHIVED` |
| 会话 | `ACTIVE / ARCHIVED` |
| 知识文档 | `ACTIVE / ARCHIVED` |
| 审计日志 | 保留，不删除 |

“删除成员”统一改为：

```text
将 workspace_members.status 设置为 REVOKED / SUSPENDED / ARCHIVED
```

“删除用户 / 弃用账号”统一改为：

```text
将 users.status 设置为 DISABLED / DEPRECATED / ARCHIVED
```

## 3.2 历史数据保留

成员被撤销或账号被弃用后：

```text
1. workspace_members 记录保留；
2. users 记录保留；
3. chat_messages.user_id 保留；
4. knowledge_docs.created_by 保留；
5. audit_events 保留；
6. 文档、会话、分析记录的历史归属不变。
```

## 3.3 访问校验

访问工作区必须同时满足：

```text
users.status = ACTIVE
AND workspace_members.status = ACTIVE
AND workspaces.status = ACTIVE
```

账号被弃用后，即使工作区成员记录仍存在，也不能访问。

---

## 4. 角色与权限

## 4.1 角色

| 角色 | 说明 |
|---|---|
| `OWNER` | 工作区所有者 |
| `ADMIN` | 工作区管理员 |
| `EDITOR` | 可上传知识库、发起问答和分析 |
| `VIEWER` | 只读使用问答和分析 |

## 4.2 能力矩阵

| 能力 | OWNER | ADMIN | EDITOR | VIEWER |
|---|---|---|---|---|
| 发起问答 / 分析 | ✅ | ✅ | ✅ | ✅ |
| 查看自己的会话 | ✅ | ✅ | ✅ | ✅ |
| 查看他人会话 | ❌ | ❌ | ❌ | ❌ |
| 上传知识文档 | ✅ | ✅ | ✅ | ❌ |
| 归档知识文档 | ✅ | ✅ | ✅ | ❌ |
| 查看知识文档列表 | ✅ | ✅ | ✅ | ✅ |
| 添加成员 | ✅ | ✅ | ❌ | ❌ |
| 修改成员角色 | ✅ | ✅ | ❌ | ❌ |
| 停用 / 恢复成员 | ✅ | ✅ | ❌ | ❌ |
| 修改 / 停用 OWNER | ✅ | ❌ | ❌ | ❌ |
| 创建新工作区 | ✅ | ✅ | ✅ | ✅ |

说明：

```text
1. 任何登录用户都可以创建新工作区，创建者成为 OWNER；
2. ADMIN 不能创建、修改或停用 OWNER；
3. 只有 OWNER 可以指定新的 OWNER；
4. 不能让工作区失去最后一个 ACTIVE OWNER；
5. 成员停用不是物理删除。
```

## 4.3 权限点

```text
workspace:read
workspace:create
workspace:update
workspace:manage-member
knowledge:read
knowledge:upload
knowledge:archive
chat:ask
chat:stream
analysis:query
session:read
session:archive
audit:read
```

---

## 5. 生命周期状态

## 5.1 用户状态

| 状态 | 是否可登录 | 说明 |
|---|---|---|
| `ACTIVE` | ✅ | 正常用户 |
| `SUSPENDED` | ❌ | 暂停使用 |
| `DISABLED` | ❌ | 已禁用，例如离职 |
| `DEPRECATED` | ❌ | 账号弃用，历史保留 |
| `ARCHIVED` | ❌ | 历史归档账号 |
| `LOCKED` | ❌ | 风控或管理员锁定 |

## 5.2 工作区成员状态

| 状态 | 是否可访问 | 说明 |
|---|---|---|
| `ACTIVE` | ✅ | 当前成员 |
| `SUSPENDED` | ❌ | 暂停访问 |
| `REVOKED` | ❌ | 已撤销访问 |
| `LEFT` | ❌ | 已离开 |
| `ARCHIVED` | ❌ | 历史成员 |

## 5.3 工作区状态

| 状态 | 说明 |
|---|---|
| `ACTIVE` | 正常 |
| `SUSPENDED` | 暂停 |
| `ARCHIVED` | 归档 |

## 5.4 会话和知识文档状态

会话：

```text
ACTIVE
ARCHIVED
```

知识文档：

```text
UPLOADED
PARSING
READY
FAILED
ARCHIVED
```

当前 v0.6 同步解析逻辑可以保留，但如果解析失败应记录 `FAILED`。

---

## 6. API 设计

## 6.1 Auth API

### 注册

```http
POST /api/v1/auth/register
```

请求：

```json
{
  "username": "alice",
  "password": "strong-password",
  "displayName": "Alice"
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "userId": 1,
    "username": "alice",
    "displayName": "Alice",
    "accessToken": "...",
    "refreshToken": "...",
    "defaultWorkspaceId": 1
  }
}
```

### 登录

```http
POST /api/v1/auth/login
```

请求：

```json
{
  "username": "alice",
  "password": "strong-password"
}
```

无论用户不存在、密码错误还是账号被停用，对外错误信息保持一致：

```text
用户名或密码错误，或账号暂时不可用。
```

### Refresh Token

```http
POST /api/v1/auth/refresh
```

请求：

```json
{
  "refreshToken": "..."
}
```

### Logout

```http
POST /api/v1/auth/logout
```

请求：

```json
{
  "refreshToken": "..."
}
```

效果：

```text
撤销 refresh token；
access token 等待自然过期或通过 token_version 提前失效。
```

### Current User

```http
GET /api/v1/me
```

响应：

```json
{
  "userId": 100,
  "username": "alice",
  "displayName": "Alice",
  "status": "ACTIVE",
  "workspaces": [
    {
      "workspaceId": 1,
      "code": "default",
      "name": "Default Workspace",
      "role": "OWNER",
      "memberStatus": "ACTIVE"
    }
  ]
}
```

## 6.2 Workspace API

### 创建工作区

```http
POST /api/v1/workspaces
```

任何登录用户都可以创建，创建者自动成为 `OWNER`。

请求：

```json
{
  "name": "Finance Team",
  "code": "finance-team"
}
```

### 查询当前用户工作区

```http
GET /api/v1/workspaces
```

只返回当前用户有 `ACTIVE` 成员关系的 `ACTIVE` 工作区。

### 工作区详情

```http
GET /api/v1/workspaces/{workspaceId}
```

### 成员列表

```http
GET /api/v1/workspaces/{workspaceId}/members
```

查询参数：

```text
page=1
pageSize=20
keyword=
status=ACTIVE
role=EDITOR
```

响应示例：

```json
{
  "items": [
    {
      "memberId": 10,
      "userId": 100,
      "username": "alice",
      "displayName": "Alice",
      "userStatus": "ACTIVE",
      "memberStatus": "ACTIVE",
      "role": "OWNER",
      "joinedAt": "2026-10-16T10:00:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

### 添加成员

```http
POST /api/v1/workspaces/{workspaceId}/members
```

请求：

```json
{
  "username": "bob",
  "role": "EDITOR"
}
```

如果 Bob 已存在成员记录：

```text
不插入新行；
将原记录 status 恢复为 ACTIVE；
更新 role；
记录审计。
```

### 修改成员角色

```http
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/role
```

请求：

```json
{
  "role": "VIEWER",
  "reason": "调整为只读"
}
```

### 修改成员状态

```http
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/status
```

请求：

```json
{
  "status": "SUSPENDED",
  "reason": "项目暂停"
}
```

不允许：

```http
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
```

## 6.3 Chat API

### 创建会话

```http
POST /api/v1/workspaces/{workspaceId}/chat/sessions
```

响应：

```json
{
  "sessionId": "s_7f3a...",
  "workspaceId": 1,
  "userId": 100,
  "status": "ACTIVE"
}
```

服务端生成 `sessionId`，不接受客户端自定义新会话 ID。

### 会话列表

```http
GET /api/v1/workspaces/{workspaceId}/chat/sessions
```

只返回当前用户自己的会话。

### 会话详情

```http
GET /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}
```

### 会话消息

```http
GET /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}/messages
```

### 归档会话

```http
PATCH /api/v1/workspaces/{workspaceId}/chat/sessions/{sessionId}/status
```

请求：

```json
{
  "status": "ARCHIVED"
}
```

### 提问

```http
POST /api/v1/workspaces/{workspaceId}/chat
```

### 流式提问

```http
POST /api/v1/workspaces/{workspaceId}/chat/stream
```

### 意图预检

```http
POST /api/v1/workspaces/{workspaceId}/chat/intent
```

### 统一问答

```http
POST /api/v1/workspaces/{workspaceId}/chat/ask
```

## 6.4 Knowledge API

### 上传文档

```http
POST /api/v1/workspaces/{workspaceId}/knowledge/upload
```

### 文档列表

```http
GET /api/v1/workspaces/{workspaceId}/knowledge/docs
```

### 文档详情

```http
GET /api/v1/workspaces/{workspaceId}/knowledge/docs/{docId}
```

### 归档文档

```http
PATCH /api/v1/workspaces/{workspaceId}/knowledge/docs/{docId}/status
```

请求：

```json
{
  "status": "ARCHIVED"
}
```

效果：

```text
1. knowledge_docs.status = ARCHIVED；
2. 删除或禁用该文档对应 Chroma chunks；
3. 不物理删除数据库记录；
4. 不删除 audit 历史。
```

当前前端的 `DELETE /knowledge/docs/{docId}` 可以兼容为 archive，但新前端应逐步改为状态接口。

## 6.5 Analysis API

```http
POST /api/v1/workspaces/{workspaceId}/analysis/query
```

---

## 7. 数据库设计

## 7.1 迁移原则

1. 应用表统一由 Flyway 管理；
2. `scripts/init_mysql.sql` 不再创建应用表；
3. 不提供用户、成员、会话、文档的物理删除接口；
4. 旧数据必须迁移到 default workspace；
5. 迁移必须兼容空库和 v0.6 已有数据卷。

`scripts/init_mysql.sql` 只保留：

```text
建库；
huiyi_ro 只读账号；
products；
regions；
channels；
sales_orders；
示例订单数据可单独由 init_sample_data.sql 处理。
```

移除：

```text
knowledge_docs；
chat_messages；
chat_session_summaries；
SessionSummarySchemaInitializer；
```

这些应用表改由 Flyway 创建。

## 7.2 Flyway 迁移

目录：

```text
backend/src/main/resources/db/migration/
├── V1__baseline_huiyi_v0_6.sql
├── V2__enterprise_identity.sql
├── V3__enterprise_resource_scope.sql
├── V4__enterprise_backfill.sql
├── V5__enterprise_audit.sql
└── V6__enterprise_indexes.sql
```

### V1：应用表基线

使用 `CREATE TABLE IF NOT EXISTS` 兼容已有 v0.6 数据卷：

```text
knowledge_docs
chat_messages
chat_session_summaries
```

### V2：企业身份表

创建：

```text
users
workspaces
workspace_members
refresh_tokens
```

### users

```sql
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(128) NULL,
  display_name VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  token_version INT NOT NULL DEFAULT 0,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  status_changed_at DATETIME NULL,
  status_changed_by BIGINT NULL,
  status_reason_code VARCHAR(64) NULL,
  status_remark VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### workspaces

```sql
CREATE TABLE IF NOT EXISTS workspaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  owner_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### workspace_members

```sql
CREATE TABLE IF NOT EXISTS workspace_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  changed_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  changed_by BIGINT NULL,
  reason_code VARCHAR(64) NULL,
  remark VARCHAR(255) NULL,
  UNIQUE KEY uk_workspace_user (workspace_id, user_id),
  INDEX idx_user_status (user_id, status),
  INDEX idx_workspace_status (workspace_id, status)
);
```

### refresh_tokens

```sql
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  replaced_by_token_hash VARCHAR(255) NULL,
  user_agent VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_status_expire (user_id, status, expires_at)
);
```

refresh token 保存哈希值，不保存明文。

## 7.3 资源归属字段

### knowledge_docs

新增：

```sql
ALTER TABLE knowledge_docs
  ADD COLUMN workspace_id BIGINT NOT NULL,
  ADD COLUMN knowledge_space_id BIGINT NULL,
  ADD COLUMN created_by BIGINT NOT NULL,
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
```

如果 MySQL 版本不支持直接在回填前加 `NOT NULL`，迁移顺序应为：

```text
1. 添加 nullable 字段；
2. 回填 default workspace；
3. 再收紧 NOT NULL。
```

### chat_messages

新增：

```sql
ALTER TABLE chat_messages
  ADD COLUMN workspace_id BIGINT NOT NULL,
  ADD COLUMN user_id BIGINT NOT NULL;
```

### chat_sessions

新建：

```sql
CREATE TABLE IF NOT EXISTS chat_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_message_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_workspace_user_time (
    workspace_id, user_id, last_message_at DESC
  )
);
```

不要使用 `id` 表示字符串会话 ID，统一使用 `session_id`。

## 7.4 审计表

```sql
CREATE TABLE IF NOT EXISTS audit_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NULL,
  workspace_id BIGINT NULL,
  user_id BIGINT NULL,
  username VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NULL,
  resource_id VARCHAR(64) NULL,
  result_status VARCHAR(32) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  detail_json JSON NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_workspace_time (workspace_id, created_at),
  INDEX idx_user_time (user_id, created_at),
  INDEX idx_action_time (action, created_at)
);
```

至少记录：

```text
AUTH_REGISTER
AUTH_LOGIN_SUCCESS
AUTH_LOGIN_FAILED
AUTH_REFRESH
AUTH_LOGOUT
USER_STATUS_CHANGED
WORKSPACE_CREATED
MEMBER_ADDED
MEMBER_ROLE_CHANGED
MEMBER_STATUS_CHANGED
KNOWLEDGE_UPLOADED
KNOWLEDGE_ARCHIVED
CHAT_ASKED
ANALYSIS_QUERIED
ACCESS_DENIED
```

登录失败时：

```text
user_id 可为空；
username 必须记录；
不要记录密码；
不要记录完整 Token。
```

## 7.5 回填策略

### 7.5.1 创建 default workspace

```text
code = default
name = Default Workspace
owner_id = migrated
status = ACTIVE
```

### 7.5.2 创建 migrated 占位用户

```text
username = migrated
status = ARCHIVED
password_hash = 不可登录的随机哈希
display_name = Migrated Data
```

该用户：

```text
1. 不能登录；
2. 不展示在成员列表；
3. 仅用于承接 v0.6 之前的历史数据。
```

### 7.5.3 回填规则

```text
knowledge_docs.workspace_id      = default workspace_id
knowledge_docs.created_by        = migrated user_id
knowledge_docs.status            = ACTIVE

chat_messages.workspace_id       = default workspace_id
chat_messages.user_id            = migrated user_id

chat_sessions.workspace_id       = default workspace_id
chat_sessions.user_id            = migrated user_id
chat_sessions.status             = ACTIVE
```

旧会话归 migrated 用户，不自动交给第一个真实 OWNER。

## 7.6 Chroma 迁移策略

### 问题

v0.6 的 Chroma chunk metadata 中没有 `workspace_id`。

如果只修改数据库，不更新 Chroma metadata，会出现越权检索。

### 推荐方案：重建知识索引

当前是内部 Demo，数据量不大，推荐一次性重建。

步骤：

```text
1. 停止写入；
2. 备份 chroma_store；
3. 读取 knowledge_docs 中状态为 ACTIVE / READY 的文档；
4. 重新解析或读取原始内容；
5. 重新分块；
6. 生成 chunk_id；
7. metadata 写入 workspace_id / doc_id / filename / chunk_index；
8. 清空或重建 huiyi_knowledge collection；
9. 重新 upsert；
10. 抽样校验所有 chunk metadata.workspace_id 非空；
11. 开启写入。
```

迁移后 chunk metadata 示例：

```json
{
  "workspace_id": "1",
  "doc_id": "12",
  "filename": "business-glossary.md",
  "chunk_index": 0,
  "source_type": "knowledge"
}
```

不允许存在：

```text
metadata.workspace_id 缺失；
metadata.workspace_id 为空；
metadata.doc_id 缺失；
metadata 指向已归档文档但未过滤。
```

## 7.7 Chroma 接口调整

### SearchRequest

新增必填：

```json
{
  "workspaceId": 1
}
```

### UpsertRequest

所有 chunk metadata 必须包含 `workspace_id`。

Python 校验：

```text
1. workspaceId 必填；
2. 每个 chunk.metadata.workspace_id 必填；
3. 所有 chunk.metadata.workspace_id 必须一致；
4. 不允许多个 chunk 混合多个 workspace。
```

### DeleteRequest

不再只按 filename 删除。

推荐请求：

```json
{
  "workspaceId": 1,
  "docId": 12
}
```

或：

```json
{
  "workspaceId": 1,
  "chunkIds": ["12-chunk-0000", "12-chunk-0001"]
}
```

禁止：

```json
{
  "filename": "business-glossary.md"
}
```

因为不同工作区可能存在同名文件。

## 7.8 混合检索过滤

必须在检索阶段过滤，不允许先取全库 Top-K 再过滤。

### Vector Search

```text
Chroma where:
workspace_id = {workspaceId}
```

### BM25 Keyword Index

每条 keyword 文档必须保存：

```text
workspace_id
doc_id
chunk_id
```

BM25 检索前过滤：

```text
workspace_id = {workspaceId}
```

### RRF 融合

只融合当前 workspace 的结果。

禁止：

```text
先全库检索 Top-K；
再过滤 workspace；
```

因为可能 Top-K 全部来自其他工作区，过滤后结果为空。

---

## 8. 认证与安全

## 8.1 密码

```text
使用 BCrypt；
最小长度 10；
禁止常见弱密码；
不记录明文密码；
修改密码后撤销 refresh token；
token_version += 1。
```

## 8.2 JWT

### Access Token

有效期：

```text
建议 30 分钟，最大不超过 2 小时
```

Claims：

```json
{
  "sub": "100",
  "username": "alice",
  "typ": "access",
  "tv": 3,
  "iss": "huiyi",
  "aud": "huiyi-api",
  "iat": 1760000000,
  "exp": 1760001800
}
```

说明：

```text
tv = users.token_version；
不把 workspaceId 写死进 access token；
workspace 归属每次请求时校验。
```

### Refresh Token

有效期：

```text
7 天
```

生成方式：

```text
生成高熵随机字符串；
数据库保存 SHA-256 哈希；
不保存明文。
```

Refresh 使用后轮换：

```text
旧 refresh token 状态 = REVOKED；
新 refresh token 入库；
新 access token 签发。
```

## 8.3 Token Version

用户状态变更或修改密码时：

```sql
UPDATE users
SET token_version = token_version + 1
WHERE id = ?;
```

同时撤销 refresh token。

JWT 校验时：

```text
如果 token.tv != users.token_version，拒绝访问。
```

这样可以近似立即禁用用户访问。

## 8.4 登录失败保护

策略：

```text
同一 username：
15 分钟内失败 5 次，锁定 15 分钟。
```

计数优先使用 Redis：

```text
login:fail:{username}
login:lock:{username}
```

如果没有 Redis，可使用数据库表，但不建议使用单机内存，因为多实例会失效。

错误信息统一：

```text
用户名或密码错误，或账号暂时不可用。
```

不区分：

```text
用户不存在；
密码错误；
账号停用。
```

避免用户名枚举。

## 8.5 Spring Security

公开接口：

```text
GET  /api/health
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

其余接口：

```text
必须携带有效 access token；
必须校验 users.status = ACTIVE；
必须校验 token_version；
必须校验 workspace membership。
```

## 8.6 Python Sidecar 安全边界

本版本 Python 不做用户登录。

约束：

```text
1. 所有产品流量只经过 Java；
2. Python 不暴露公网；
3. Compose 中 8001 端口只绑定 127.0.0.1 或不映射；
4. Java 调 Python 时必须携带 workspaceId；
5. Python 缺少 workspaceId 时返回 4xx；
6. request_id 列入 v0.8；服务间认证未列入桌面 §8 的 v0.8 范围。
```

Compose 示例：

```yaml
python-service:
  ports:
    - "127.0.0.1:8001:8000"
```

生产建议不映射 host port。

---

## 9. 后端模块设计

## 9.1 新增包

```text
com.huiyi.assistant.api.v1
com.huiyi.assistant.auth
com.huiyi.assistant.workspace
com.huiyi.assistant.security
com.huiyi.assistant.audit
com.huiyi.assistant.context
```

## 9.2 Auth 模块

```text
AuthController
AuthService
JwtTokenService
RefreshTokenService
PasswordService
LoginAttemptService
UserStatusService
```

## 9.3 Workspace 模块

```text
WorkspaceController
WorkspaceService
WorkspaceMemberService
WorkspaceAccessValidator
WorkspaceContext
WorkspaceContextResolver
```

## 9.4 Security 模块

```text
SecurityConfig
JwtAuthenticationFilter
JwtAuthenticationProvider
CurrentUserResolver
AccessDeniedHandler
AuthenticationEntryPoint
```

## 9.5 Context 模块

```text
WorkspaceContext
WorkspaceContextResolver
```

`WorkspaceContextResolver` 从路径和 Token 解析：

```text
userId；
workspaceId；
membershipId；
roleCode；
memberStatus；
userStatus。
```

后续 Controller / Service 不直接从 HTTP Header 中猜测身份。

---

## 10. 现有服务改造

## 10.1 ChatService

保留业务编排逻辑，但入口迁移。

改造点：

```text
1. Controller 从 /api/chat 迁到 /api/v1/workspaces/{workspaceId}/chat；
2. 所有方法接收 WorkspaceContext；
3. sessionId 必须校验归属；
4. 新会话由服务端生成；
5. 消息保存 workspace_id / user_id；
6. RAG 检索传入 workspace_id；
7. SSE session 事件保持兼容。
```

本版本不把 `ChatService` 重写成 Agent Loop。

## 10.2 RagService

改造点：

```text
1. prepare(question, workspaceId)；
2. Python search 请求携带 workspace_id；
3. 检索结果必须已经按 workspace 隔离；
4. 引用来源保留 workspace 过滤后的结果；
5. 无命中兜底逻辑保持不变。
```

## 10.3 KnowledgeDocService

改造点：

```text
1. upload(file, workspaceId, userId)；
2. listDocs(workspaceId)；
3. archiveDoc(docId, workspaceId, userId)；
4. 上传时 metadata 写入 workspace_id / doc_id；
5. 删除逻辑改为归档；
6. Chroma 删除按 doc_id 或 chunk_ids；
7. 不物理删除 knowledge_docs。
```

## 10.4 IntentService / IntentEmbeddingClassifier

`intent_examples` 仍然全局，不按工作区隔离。

原因是：

```text
它是系统级分类训练示例，不是用户知识资产。
```

## 10.5 AnalysisService

改造点：

```text
1. 接口迁移到 workspace 路径；
2. 校验用户是否为 workspace ACTIVE 成员；
3. 审计记录 workspace_id / user_id；
4. 演示业务表仍为全局数据；
5. 本版本不做业务表行级隔离。
```

---

## 11. Python Sidecar 改造

## 11.1 Schema 改造

### SearchRequest

```json
{
  "query": "什么是有效订单？",
  "top_k": 5,
  "collection_name": "huiyi_knowledge",
  "workspace_id": 1
}
```

`workspace_id` 必填。

### UpsertRequest

```json
{
  "collection_name": "huiyi_knowledge",
  "workspace_id": 1,
  "chunks": [
    {
      "id": "12-chunk-0000",
      "text": "...",
      "metadata": {
        "workspace_id": "1",
        "doc_id": "12",
        "filename": "business-glossary.md",
        "chunk_index": 0
      }
    }
  ]
}
```

### DeleteRequest

```json
{
  "workspace_id": 1,
  "doc_id": 12
}
```

或：

```json
{
  "workspace_id": 1,
  "chunk_ids": ["12-chunk-0000"]
}
```

## 11.2 VectorStore 改造

新增方法：

```python
def search_by_workspace(query, workspace_id, top_k, collection_name)
def upsert_workspace_chunks(workspace_id, chunks, collection_name)
def delete_by_doc(workspace_id, doc_id, collection_name)
def delete_by_chunk_ids(workspace_id, chunk_ids, collection_name)
```

Chroma where 条件：

```python
where = {"workspace_id": {"$eq": str(workspace_id)}}
```

## 11.3 HybridSearch 改造

```text
1. vector search 按 workspace_id 过滤；
2. keyword index 按 workspace_id 过滤；
3. RRF 融合后不再跨工作区；
4. 输出引用 metadata 包含 workspace_id。
```

## 11.4 Keyword Index 改造

每条文档保存：

```json
{
  "workspace_id": 1,
  "doc_id": 12,
  "chunk_id": "12-chunk-0000",
  "text": "..."
}
```

删除文档时同步删除 keyword index。

---

## 12. 前端改造

## 12.1 登录页

新增：

```text
登录页；
注册页；
Token 管理；
401 跳转登录；
当前用户展示；
工作区切换。
```

## 12.2 Token 管理

建议：

```text
access token 保存在内存；
refresh token 可暂存 localStorage，后续升级为 HttpOnly Cookie；
所有请求带 Authorization: Bearer；
401 时尝试 refresh；
refresh 失败后跳转登录。
```

## 12.3 Workspace 管理

顶栏展示：

```text
当前工作区；
当前用户；
工作区切换入口。
```

切换工作区时：

```text
1. 更新 Pinia workspaceId；
2. 清空当前聊天界面；
3. 清空当前 session；
4. 重新加载知识库列表；
5. 重新加载会话列表。
```

不要继续使用上一个工作区的 sessionId。

## 12.4 API 路径升级

旧：

```text
POST /api/chat
POST /api/chat/stream
POST /api/analysis/query
POST /api/knowledge/upload
```

新：

```text
POST /api/v1/workspaces/{workspaceId}/chat
POST /api/v1/workspaces/{workspaceId}/chat/stream
POST /api/v1/workspaces/{workspaceId}/analysis/query
POST /api/v1/workspaces/{workspaceId}/knowledge/upload
```

旧路径不留兼容期。

---

## 13. 迁移方案

## 13.1 部署前检查

```text
[ ] 备份 MySQL；
[ ] 备份 chroma_store；
[ ] 备份当前镜像 tag；
[ ] 确认当前数据量；
[ ] 确认是否存在同名知识文档；
[ ] 确认 Chroma metadata 当前结构；
[ ] 记录当前测试结果。
```

## 13.2 数据库迁移

### 空库

```text
init_mysql.sql 只初始化业务演示表；
Flyway 创建应用表；
Flyway 写入 default workspace；
Flyway 不创建真实用户。
```

### v0.6 已有数据卷

```text
V1 基线表已存在则跳过；
V2 创建企业身份表；
V3 给现有资源加字段；
V4 回填 default workspace / migrated user；
V5 创建审计表；
V6 补索引。
```

## 13.3 Chroma 迁移

推荐一次性重建：

```text
1. 备份 chroma_store；
2. 创建新 collection；
3. 按 knowledge_docs 重建；
4. metadata 写入 workspace_id；
5. 校验；
6. 切换 collection；
7. 删除旧 collection 或保留备份。
```

## 13.4 回滚

如果失败：

```text
1. 回滚应用镜像到 v0.6.1；
2. 恢复 MySQL 备份；
3. 恢复 chroma_store 备份；
4. 清理 Flyway 失败记录；
5. 重新验证。
```

注意：

```text
Flyway 回滚不是自动删表；
应通过数据库备份恢复。
```

---

## 14. 测试计划

## 14.1 单元测试

### Auth

```text
注册成功；
重复注册失败；
REGISTER_ENABLED=false 注册失败；
登录成功；
密码错误；
用户不存在；
账号 DISABLED；
token_version 不匹配；
refresh 成功；
refresh token 已撤销；
logout 后 refresh 失效；
登录失败 5 次锁定。
```

### Workspace

```text
创建 workspace；
创建者为 OWNER；
成员列表只返回当前 workspace；
非成员访问返回 403；
VIEWER 上传失败；
EDITOR 添加成员失败；
ADMIN 添加成员成功；
ADMIN 不能修改 OWNER；
不能停用最后一个 ACTIVE OWNER；
重复添加成员转为恢复 ACTIVE。
```

## 14.2 集成测试

### RAG

```text
上传文档；
检索；
回答；
引用来源；
无命中兜底；
其他工作区不可见；
归档后不可检索。
```

### Chat

```text
创建会话；
发送消息；
会话归属校验；
历史消息查询；
他人会话不可访问；
会话归档；
workspace 切换后 session 不串。
```

### Analysis

```text
正常查询；
SQL 校验失败；
危险 SQL 拦截；
只读执行；
审计记录；
跨工作区访问失败。
```

## 14.3 迁移测试

| 场景 | 预期 |
|---|---|
| 空 MySQL volume | Flyway 创建应用表 |
| v0.6 schema | Flyway ALTER 成功 |
| 有知识文档 | 文档回填 default workspace |
| 有聊天记录 | 消息回填 default workspace / migrated user |
| 有 session summary | 迁移后可关联 chat_sessions |
| Chroma 旧数据 | 重建后 metadata 有 workspace_id |
| 重复启动 | Flyway 不重复执行 |

## 14.4 E2E 测试

保留五个场景：

```text
1. 口径问答；
2. 销售排名；
3. 渠道分析；
4. 危险 SQL 拦截；
5. 知识库未命中兜底。
```

新增：

```text
6. 注册 / 登录 / 登出；
7. 未登录访问返回 401；
8. Workspace A 文档对 Workspace B 不可见；
9. 用户 A 的会话对用户 B 不可见；
10. 成员停用后访问失败。
```

---

## 15. 审计

## 15.1 事件清单

| Action | 说明 |
|---|---|
| `AUTH_REGISTER` | 注册 |
| `AUTH_LOGIN_SUCCESS` | 登录成功 |
| `AUTH_LOGIN_FAILED` | 登录失败 |
| `AUTH_REFRESH` | 刷新 Token |
| `AUTH_LOGOUT` | 登出 |
| `USER_STATUS_CHANGED` | 用户状态变更 |
| `WORKSPACE_CREATED` | 创建工作区 |
| `MEMBER_ADDED` | 添加成员 |
| `MEMBER_ROLE_CHANGED` | 成员角色变更 |
| `MEMBER_STATUS_CHANGED` | 成员状态变更 |
| `KNOWLEDGE_UPLOADED` | 上传知识文档 |
| `KNOWLEDGE_ARCHIVED` | 归档知识文档 |
| `CHAT_ASKED` | 发起问答 |
| `ANALYSIS_QUERIED` | 发起分析 |
| `ACCESS_DENIED` | 权限拒绝 |
| `RATE_LIMITED` | 登录限次触发 |

## 15.2 审计规则

```text
1. 不记录密码；
2. 不记录 access token；
3. 不记录 refresh token 明文；
4. 登录失败记录 attempted username；
5. 审计失败需要记录错误，不应完全静默；
6. 安全类审计写入失败时优先暴露告警。
```

---

## 16. 任务拆分

## 16.1 后端

| 任务 | 优先级 |
|---|---|
| 引入 Flyway | P0 |
| 创建 users / workspaces / members 表 | P0 |
| 创建 refresh_tokens 表 | P0 |
| 实现 JWT | P0 |
| 实现 Auth API | P0 |
| 实现 Spring Security | P0 |
| 实现 WorkspaceContextResolver | P0 |
| 实现 RBAC 校验 | P0 |
| 修改资源归属 | P0 |
| 实现 ChatSession 表和 API | P0 |
| Python vector API 增加 workspace_id | P0 |
| Chroma metadata 迁移 | P0 |
| 移除 SessionSummarySchemaInitializer | P0 |
| 审计表和审计记录 | P1 |
| 登录失败保护 | P1 |
| 用户状态管理 | P1 |
| 成员状态管理 | P1 |
| Feedback API | P2 |
| Request ID | P2 |

## 16.2 Python

| 任务 | 优先级 |
|---|---|
| vector schema 增加 workspace_id | P0 |
| vector search workspace 过滤 | P0 |
| vector upsert workspace 校验 | P0 |
| vector delete by doc_id / chunk_ids | P0 |
| hybrid search workspace 过滤 | P0 |
| keyword index workspace 过滤 | P0 |
| 测试补充 | P0 |

## 16.3 前端

| 任务 | 优先级 |
|---|---|
| 登录页 | P0 |
| Token 管理 | P0 |
| 请求拦截器 | P0 |
| API v1 路径迁移 | P0 |
| 工作区切换 | P1 |
| 当前用户展示 | P1 |
| 会话列表 | P1 |
| 401 处理 | P0 |
| 403 提示 | P1 |

## 16.4 DevOps

| 任务 | 优先级 |
|---|---|
| Flyway 环境 | P0 |
| .env.example 更新 | P0 |
| Compose 端口安全 | P1 |
| CI 增加 migration 测试 | P1 |
| 备份脚本 | P2 |

---

## 17. 配置变更

`.env.example` 新增：

```env
REGISTER_ENABLED=true
DEFAULT_MEMBER_ROLE=VIEWER
JWT_SECRET=
JWT_ISSUER=huiyi
JWT_AUDIENCE=huiyi-api
JWT_ACCESS_TOKEN_TTL=1800
JWT_REFRESH_TOKEN_TTL=604800
LOGIN_FAIL_MAX_ATTEMPTS=5
LOGIN_FAIL_WINDOW_SECONDS=900
LOGIN_LOCK_SECONDS=900
MIGRATED_USERNAME=migrated
DEFAULT_WORKSPACE_CODE=default
```

生产建议：

```env
REGISTER_ENABLED=false
DEFAULT_MEMBER_ROLE=VIEWER
```

---

## 18. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| Flyway 与已有表冲突 | 启动失败 | V1 使用 IF NOT EXISTS，迁移前备份 |
| Chroma 旧数据缺 workspace_id | 数据越权 | 一次性重建索引 |
| 用户切换工作区后 session 串数据 | 数据泄露 | 前端清空 session，后端校验归属 |
| access token 短期内仍有效 | 禁用不即时 | 使用 token_version |
| 客户端自带 sessionId | 越权 | 服务端生成并校验归属 |
| 旧 API 直接删除影响前端 | 联调失败 | 前端 / 后端 / e2e 同版本修改 |
| 注册开放导致越权 | 数据风险 | 生产关闭注册，新用户默认 VIEWER |
| 多实例登录限次失效 | 安全风险 | 使用 Redis 而不是本地内存 |

---

## 19. 发布清单

发布前：

```text
[ ] main 分支包含 v0.7 全部代码；
[ ] CI 全绿；
[ ] mvn verify 通过；
[ ] pytest 覆盖率不低于当前基线；
[ ] frontend build 通过；
[ ] Docker Compose 启动成功；
[ ] Flyway 空库测试通过；
[ ] Flyway v0.6 数据卷测试通过；
[ ] Chroma 重建通过；
[ ] 五个旧场景通过；
[ ] 十个企业场景通过；
[ ] 无 .env / API Key 提交；
[ ] README 更新；
[ ] 回滚方案确认。
```

发布：

```text
1. 合并 release/v0.7.0 到 main；
2. 打 tag v0.7.0；
3. 构建镜像；
4. staging 验证；
5. 备份 MySQL / Chroma；
6. 执行迁移；
7. 发布新版本；
8. 健康检查；
9. 核心场景回归；
10. 观察日志和指标。
```

回滚：

```text
1. 切回 v0.6.1 镜像；
2. 恢复 MySQL 备份；
3. 恢复 chroma_store 备份；
4. 验证健康检查；
5. 记录失败原因。
```

---

## 20. 验收标准

## 20.1 认证

```text
[ ] 可以注册；
[ ] 可以登录；
[ ] 可以 refresh；
[ ] 可以 logout；
[ ] 未登录访问核心接口返回 401；
[ ] 密码错误返回统一错误；
[ ] 登录失败 5 次后锁定；
[ ] refresh token 可撤销；
[ ] 修改密码后旧 refresh token 失效。
```

## 20.2 工作区

```text
[ ] 用户可以创建工作区；
[ ] 创建者成为 OWNER；
[ ] 用户只能查看自己加入的工作区；
[ ] ADMIN 可以添加成员；
[ ] ADMIN 不能修改 OWNER；
[ ] 成员不能物理删除；
[ ] 成员可以停用 / 恢复；
[ ] 不能停用最后一个 ACTIVE OWNER。
```

## 20.3 资源隔离

```text
[ ] Workspace A 不能检索 Workspace B 的知识；
[ ] Workspace A 不能归档 Workspace B 的文档；
[ ] 用户 A 不能访问用户 B 的会话；
[ ] 用户 A 不能向用户 B 的会话发消息；
[ ] Workspace A 删除文档不影响 Workspace B；
[ ] Chroma metadata 全部有 workspace_id。
```

## 20.4 API

```text
[ ] 核心接口迁移到 /api/v1；
[ ] 旧接口返回 404；
[ ] 错误响应结构统一；
[ ] 列表接口支持分页；
[ ] OpenAPI / 文档更新。
```

## 20.5 工程化

```text
[ ] Flyway 管理 schema；
[ ] init_mysql.sql 不创建应用表；
[ ] SessionSummarySchemaInitializer 移除；
[ ] CI 全绿；
[ ] Python 覆盖率不低于 v0.6；
[ ] Java 关键链路测试补充；
[ ] E2E 通过。
```

---

## 21. 后续版本预告

## v0.8

```text
Request ID；
Run ID / assistant_run_id；
Request Trace（非 Agent Trace）；
Metrics（Actuator / Prometheus 端点）；
LLM Token / 成本字段；
Python 访问日志；
健康检查增强。
不做 Grafana / Agent Loop。
```

## v0.9

```text
Tool Registry；
Tool Executor；
Agent Runner；
Agent Trace；
Router / ReAct。
```

## v1.0

```text
Guardrails；
上下文预算；
反馈；
Plan-and-Execute；
稳定发布。
```

---

## 22. 一页纸总结

### 版本目标

```text
企业基础层：认证、工作区、RBAC、资源隔离、审计、Flyway。
```

### 核心原则

```text
1. 不做物理删除；
2. 用户和成员用状态治理；
3. 资源按 workspace / user 隔离；
4. Chroma 检索必须按 workspace_id 过滤；
5. 会话必须服务端生成并校验归属；
6. Flyway 兼容空库和 v0.6 数据。
```

### 主要 API

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me
POST /api/v1/workspaces
GET  /api/v1/workspaces/{id}/members
PATCH /api/v1/workspaces/{id}/members/{memberId}/status
POST /api/v1/workspaces/{id}/chat/sessions
POST /api/v1/workspaces/{id}/chat
POST /api/v1/workspaces/{id}/chat/stream
POST /api/v1/workspaces/{id}/knowledge/upload
POST /api/v1/workspaces/{id}/analysis/query
```

### 关键规则

```text
1. 不提供 DELETE 成员；
2. 不提供物理删除用户；
3. 只能修改状态；
4. 不能停用最后一个 ACTIVE OWNER；
5. ADMIN 不能修改 OWNER；
6. 会话只能属于当前用户和当前工作区；
7. 知识检索必须按 workspace_id 过滤；
8. Chroma 缺少 workspace_id 的数据不允许返回。
```

### 完成定义

```text
CI 全绿；
Flyway 空库和旧库均通过；
Chroma 迁移完成；
权限隔离测试通过；
五个旧场景通过；
新增企业场景通过；
可发布 v0.7.0。
```

