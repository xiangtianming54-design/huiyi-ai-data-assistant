# v0.7 Enterprise Foundation Implementation Plan

> Spec: [HuiYi-v0.7-Enterprise-Foundation-Development-Document-v2.md](../specs/HuiYi-v0.7-Enterprise-Foundation-Development-Document-v2.md)  
> Branch: `feature/v0.7-enterprise-foundation`（已合并删除；worktree 已移除）  
> Baseline: v0.6.1 (`ce231b2`)  
> Status: 已完成。合并点 `627da84`，标签 `v0.7.0`。未纳入的 P1（用户状态 API、前端工作区切换）不滚进 v0.8。

## Global Constraints

- Binding spec is the v2 development document, not `docs/v0.7-design.md`.
- No physical delete of users, members, sessions, or knowledge docs; use status.
- Knowledge retrieval MUST filter by `workspace_id` in Chroma where / BM25 before RRF; never full-corpus then filter.
- `intent_examples` stays global; workspace_id not required for that collection.
- Anyone who is logged in may create a workspace and becomes OWNER.
- New users joining `default` get `DEFAULT_MEMBER_ROLE` (default VIEWER).
- Default workspace owner is the `migrated` placeholder user (ARCHIVED, cannot login).
- Old APIs (`/api/chat`, `/api/knowledge`, `/api/analysis`) are removed (404), no compatibility window.
- Password: BCrypt, min length 10, reject common weak passwords.
- Access token TTL 1800s; refresh 7 days, hashed in DB, rotated on use.
- Login fail: 5 failures / 15 min → lock 15 min; Redis keys `login:fail:{username}` / `login:lock:{username}`.
- Uniform login error: `用户名或密码错误，或账号暂时不可用。`
- Python is not public; compose bind `127.0.0.1:8001`.
- Java 17, existing ChatService orchestration stays (no Agent Loop).
- TDD: failing test first for each behavior.

---

## Task 1: Python vector workspace isolation

Files: `python-service/app/core/schema.py`, `vector.py`, `vector_store.py`, `keyword_index.py`, tests.

### Steps

1. Write failing tests in `python-service/app/tests/test_workspace_isolation.py`:
   - knowledge search without workspace_id → 4xx
   - upsert knowledge without workspace_id → 4xx
   - two workspaces same filename: search A does not return B
   - delete by filename only → 4xx
   - delete by workspace_id + doc_id only removes that workspace's chunks
   - keyword search filters by workspace_id before scoring
2. Run `pytest python-service/app/tests/test_workspace_isolation.py` — Expected: FAIL (missing fields / no isolation)
3. Implement schema + VectorStore where filter + KeywordIndex workspace fields + API validation
4. Update existing vector/hybrid tests to pass workspace_id for knowledge collection
5. Run `pytest python-service/app/tests` — Expected: PASS

---

## Task 2: Flyway V1–V6 and init_mysql trim

Files: `backend/src/main/resources/db/migration/V1__*.sql` … `V6__*.sql`, `scripts/init_mysql.sql`, remove `SessionSummarySchemaInitializer`, pom Flyway.

### Steps

1. Write a test or SQL assertion helper that expected table names exist in migration files (users, workspaces, workspace_members, refresh_tokens, chat_sessions, audit_events)
2. Add Flyway dependency; V1 baseline IF NOT EXISTS for knowledge_docs / chat_messages / chat_session_summaries
3. V2 identity tables; V3 nullable resource columns + chat_sessions; V4 migrated user + default workspace + backfill; V5 audit; V6 indexes
4. Strip application tables from `init_mysql.sql`
5. Delete `SessionSummarySchemaInitializer`
6. `mvn -q -f backend/pom.xml test` still compiles (Flyway off in unit tests)

---

## Task 3: Auth JWT register / login / refresh / logout

Packages: `com.huiyi.assistant.auth`, entities/mappers for users and refresh_tokens.

### Steps

1. Write `AuthServiceTest` covering: register success, duplicate username, REGISTER_ENABLED=false, login success, unknown user, bad password, DISABLED, refresh, revoked refresh, logout, lock after 5 fails. Expected RED.
2. Implement PasswordService, JwtTokenService, RefreshTokenService, LoginAttemptService, AuthService, AuthController
3. GREEN the auth tests
4. Commit

---

## Task 4: Spring Security + WorkspaceContext

### Steps

1. Tests: anonymous health 200; anonymous chat 401; valid token reaches `/api/v1/me`
2. SecurityConfig, JwtAuthenticationFilter (check status ACTIVE + token_version), CurrentUser
3. WorkspaceContextResolver from path `{workspaceId}` + membership ACTIVE
4. GREEN

---

## Task 5: Workspace RBAC and member lifecycle

### Steps

1. Tests: create workspace → creator OWNER; list only ACTIVE memberships; non-member 403; VIEWER cannot upload (later wired); EDITOR cannot add member; ADMIN can add member; ADMIN cannot change OWNER; cannot suspend last ACTIVE OWNER; re-add restores ACTIVE
2. Implement WorkspaceController/Service/MemberService — no DELETE member, only PATCH status/role
3. GREEN

---

## Task 6: Chat sessions and v1 chat APIs

### Steps

1. Tests: server generates sessionId; A cannot read B's session; list only own; archive via PATCH status
2. Move ChatController to `/api/v1/workspaces/{workspaceId}/chat`; ChatSession entity; validate ownership
3. Remove old `/api/chat`
4. GREEN

---

## Task 7: Knowledge archive + RAG workspace

### Steps

1. Tests: upload stores workspace_id; list scoped; archive sets ARCHIVED and deletes Chroma by doc_id; search passes workspace_id
2. KnowledgeDocService archive (no physical delete); DELETE maps to archive
3. RagService.prepare(question, workspaceId)
4. GREEN

---

## Task 8: Analysis v1 + audit

### Steps

1. Move analysis to v1 workspace path; audit events for login/register/members/knowledge/chat/analysis
2. Tests: ACCESS denied audited; login failure audited without password
3. GREEN

---

## Task 9: Frontend login, token, workspace switch, API v1

### Steps

1. Login/register pages; auth store (access in memory, refresh in localStorage); 401 → refresh → login
2. Workspace switcher; clear session on switch
3. All API paths use `/v1/workspaces/{id}/...`
4. Frontend build passes

---

## Task 10: Compose, env, e2e, docs

### Steps

1. `.env.example` new vars; python port `127.0.0.1:8001`; JWT in compose
2. e2e login then five scenarios; README Quick Start
3. `mvn test`, `pytest`, frontend build
4. Update roadmap/README for v0.7 in progress

---

## Review Focus

- Cross-workspace vector leak
- Session ownership including OWNER/ADMIN
- Last OWNER cannot be suspended
- Login error enumeration
- Flyway empty vs existing volume
- No physical DELETEs
