# v1.5.0 Token Metering & User Tiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 按人、按上海自然日限制每日 Token；第一级 50 万、第二级 150 万、第三级 500 万；开跑前不够则这一问不开始；聊天页显示今日额度；当天可加 50 万到晚上 12 点；流式用量、运行合计和提问时检索向量化计入本人。

**Architecture:** Flyway V14 给 `users` 加 `user_tier`，并新建 `token_boost_requests`。额度不另建总账，从 `llm_calls` 按人和上海日加总；当天加额是该日已通过的提额申请。五个会花钱的入口先过每秒 2 次的准入，再做本地预估。预估不够就抛业务异常，不写运行、不写改写或意图步骤。记账仍在每步结束时发生，已经开始的一问做完。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis-Plus、Flyway、JUnit 5、Mockito；Python FastAPI 只给检索响应补用量字段；Vue 3 + Element Plus。无新 Maven 依赖。

**Spec:** `docs/superpowers/specs/2026-11-09-v15-token-metering-and-user-tiers.md`

**状态（2026-11-10）：** Task 1–12 已落在 `main`，自 `0d3a644` 至 `333939d`。下面的步骤框按已落地勾选。规格第 9 节的运行环境点验不在这些步骤里。

## Global Constraints

- 不改 V1–V13，不把 V14 算进已打本地标签的 `v1.4.0`（`e6bb834`）。
- 不改 v1.4.0 的备份、压测、告警、发布验收。
- 等级不是工作区角色。补记 `blank` / `limit` 不改他们的 `VIEWER`。
- 无 MySQL `FOREIGN KEY`。
- `SecurityConfig` 对注册、登录、刷新、退出保持 `permitAll`。来源限制留在已写好的 `frontend/nginx.conf`，本计划不改允许网段。
- staging / prod 不映射后端 `8080`。Java 不读浏览器带来的 `X-Forwarded-For`。
- `REGISTER_ENABLED` 仍是全局开关，不是内网开关。
- 三个等级的每秒次数都是 2。这和 Nginx 的 `/api/` 10 r/s、`/api/v1/auth/` 1 r/s 是两回事。
- 冻结文案：`今日 Token 额度不足，请提额后再问。`；`请求过于频繁，请稍后再试`；第三级额度用完是 `今日 Token 额度不足。`；密码仍是 `密码至少 10 位` / `密码过于简单`。
- 不计入个人额度：被每秒次数拦住的一次、开跑前余额不够的一次、上传解析和向量化、启动时写入意图示例。
- 第一级不能升级，也不能把已有第一级改成第二级或第三级。本版不充值、不支付、不多第二级。
- PowerShell 不要用 `&&`。Maven 的工作目录是 `backend/`。多个测试写成 `mvn -q "-Dtest=A,B" test`。
- 执行时的提交只包含本计划的文件。当前 `main` 工作区里和计量无关的 Java 改动留在原检出，不带进 `feature/v1.5-token-metering`。

## 开工时已经在工作区、要带进功能分支的文件

这些是本版已经写过、还没提交的部分。执行时从 `fb4f4ab` 建分支后，只把下列文件上的改动放进分支：

- `docs/superpowers/specs/2026-11-09-v15-token-metering-and-user-tiers.md`
- `docs/superpowers/specs/2026-11-04-v141-token-metering-design.md`
- `docs/roadmap.md`、`docs/development.md`、`README.md`、`docs/v14-release-issues.md`、`docs/superpowers/specs/2026-10-31-v14-production-ready.md` 里与 v1.5 版本说明有关的段落
- `frontend/nginx.conf` 与 `backend/src/test/java/com/huiyi/assistant/ops/NginxLimitTest.java`
- `frontend/src/styles/theme.css`、`frontend/src/main.ts`、`frontend/src/App.vue`、`frontend/src/views/LoginView.vue`、`frontend/src/views/ChatView.vue`、`frontend/src/views/KnowledgeView.vue`、`frontend/src/components/MessageList.vue`

不要带：`backend/src/main/java` 里本对话开始前已脏的业务改动、`frontend/src/stores/chat.ts` 里原有未提交差异（本计划会按任务改这个文件）、`.superpowers/`。

## 实施口径（规格第 10 节留给实施的决定）

- 本地计数：`LocalTokenCounter.count`。不发网络请求。ASCII 连续段每 4 个字符 1 个 Token，不足 4 个进 1；每个非 ASCII 码位 1 个 Token；空白断开 ASCII 段。预估和向量化缺用量时用同一个方法。
- 流式用量：读 Spring AI 最后一个带 `Usage` 的 `ChatResponse` 的 `promptTokens` / `completionTokens`。`LlmConfig` 打开 `streamUsage(true)`。整段都没有用量时记 0，并打日志 `流式用量缺失，本段记 0 purpose={purpose}`。
- 检索向量化只计 `/api/vector/search` 送出的查询文本。服务商 `usage.total_tokens` 有值就用它，没有就用 `LocalTokenCounter.count(query)`。上传 upsert、启动意图示例、相似度接口不记账。
- 表与列：`users.user_tier`；`token_boost_requests`。当天 +50 万就是该上海日 `APPROVED` 的那一行，不另建总账表。
- 路径见各任务的 Interfaces。审评页是 `/boost/review`，申请页是 `/boost`。第三级建号表单在 `/users/new`。
- 进度条按已确认的象牙与铜稿：在输入框上方、靠左、宽 240px、高 2px。底 `#e4d7c3`，填充 `#9a7844`。标题「今日额度」，右侧剩余百分比。这和规格 §6「输入框与发送按钮之间」的旧句不一致，以已确认稿和规格 §11 为准。
- 一次发问含预检和随后那一次问答。预检领一张 1 秒内有效的票，随后的 ask / stream / analysis 交同一张票不再占名额。不带票直接打这三个地址，各算一次发问。

## Review Focus

- 预检因额度或每秒次数失败时，前端不得再转去流式或分析（Task 11）。
- 上传 upsert 和启动意图示例不得增加任何人的已用（Task 8）。
- 一问已经开始后，中间记账使剩余不足也要把正文说完；下一句才拦住，且回答区没有新正文（Task 6、Task 7）。
- 不存在把已有用户从第一级改成第二级或第三级的接口；只有第三级能新建 2 或 3（Task 3、Task 10）。
- 每秒次数用尽的响应没有提额入口；第三级额度用完的响应也没有（Task 5、Task 6、Task 11）。

---

### Task 1: LocalTokenCounter

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/LocalTokenCounter.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/LocalTokenCounterTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `public final class LocalTokenCounter`，`public static int count(String text)`。`null` 和空串为 0。同一字符串每次结果相同。

- [x] **Step 1: Write the failing test**

```java
@Test
void blankIsZero() {
    assertEquals(0, LocalTokenCounter.count(null));
    assertEquals(0, LocalTokenCounter.count(""));
}

@Test
void asciiGroupsByFourAndCjkIsOneEach() {
    assertEquals(1, LocalTokenCounter.count("ab"));
    assertEquals(2, LocalTokenCounter.count("abcdefg"));
    assertEquals(2, LocalTokenCounter.count("订单"));
    assertEquals(LocalTokenCounter.count("订单 abc"), LocalTokenCounter.count("订单 abc"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=LocalTokenCounterTest test`  
Expected: 编译失败，找不到 `LocalTokenCounter`。

- [x] **Step 3: Implement `LocalTokenCounter.count`**

按「实施口径」的 ASCII / 非 ASCII 规则实现。不要调用 HTTP 或模型。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=LocalTokenCounterTest test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: count tokens locally without calling a model`

---

### Task 2: Flyway V14

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__user_tier_and_token_boost.sql`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/entity/User.java`
- Create: `backend/src/main/java/com/huiyi/assistant/model/entity/TokenBoostRequest.java`
- Create: `backend/src/main/java/com/huiyi/assistant/mapper/TokenBoostRequestMapper.java`
- Modify: `backend/src/test/java/com/huiyi/assistant/config/FlywayMigrationFilesTest.java`

**Interfaces:**
- Consumes: 无
- Produces: 列 `users.user_tier`。表 `token_boost_requests`，列：`id`，`applicant_user_id`，`reviewer_user_id`（可空），`status`，`reason`，`step_name`，`step_tokens`，`quota_date`，`created_at`，`reviewed_at`。实体字段与列一一对应，驼峰。`TokenBoostRequestMapper extends BaseMapper<TokenBoostRequest>`。

- [x] **Step 1: Write the failing test**

在 `FlywayMigrationFilesTest` 增加断言：全部迁移文本包含 `user_tier`、`token_boost_requests`、`applicant_user_id`、`quota_date`；V14 文件里有 `username = 'blank'` 且 `id = 16` 时 `user_tier = 3`，`username = 'limit'` 且 `id = 17` 时 `user_tier = 2`；仍断言没有 `FOREIGN KEY`。不要改「没有 file_hash」等旧断言。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=FlywayMigrationFilesTest test`  
Expected: 失败，缺少 `user_tier` 或 `token_boost_requests`。

- [x] **Step 3: Add V14 and the two types**

`user_tier TINYINT NOT NULL DEFAULT 1`。两条 UPDATE 必须同时匹配 id 和 username。`token_boost_requests.status` 用 `VARCHAR(16)`。索引 `(applicant_user_id, quota_date)` 和 `llm_calls` 的 `(user_id, created_at)`。不写外键。`User` 增加 `Integer userTier`。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=FlywayMigrationFilesTest test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: add user tier and token boost tables`

---

### Task 3: 注册默认第一级，且没有改级接口

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/auth/AuthService.java`（`register` 插入前）
- Test: `backend/src/test/java/com/huiyi/assistant/quota/UserTierAssignmentTest.java`

**Interfaces:**
- Consumes: `User.userTier`
- Produces: 自助注册插入的用户 `userTier == 1`。仓库里不新增修改已有用户等级的方法。

- [x] **Step 1: Write the failing test**

用 Mockito 构造 `AuthService` 的依赖。`register` 一个新用户名后，`ArgumentCaptor<User>` 捕获 `userMapper.insert` 的参数，断言 `getUserTier()` 为 1。再断言 `AuthService` 的公开方法名里没有 `updateTier`、`changeTier`、`promote`。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=UserTierAssignmentTest test`  
Expected: 失败，`getUserTier()` 为 null。

- [x] **Step 3: Set tier 1 in `register`**

只在插入前 `user.setUserTier(1)`。不改工作区角色，不改登录。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=UserTierAssignmentTest test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: register new users at tier 1`

---

### Task 4: 今日额度

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/UserTierLimits.java`
- Create: `backend/src/main/java/com/huiyi/assistant/quota/QuotaSnapshot.java`
- Create: `backend/src/main/java/com/huiyi/assistant/quota/TokenQuotaService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/mapper/LlmCallMapper.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/TokenQuotaServiceTest.java`

**Interfaces:**
- Consumes: `LocalTokenCounter.count`，`User.userTier`，`TokenBoostRequest`
- Produces:
  - `UserTierLimits.dailyLimit(int tier)`：1 → 500_000，2 → 1_500_000，3 → 5_000_000。其他值抛 `IllegalArgumentException`。
  - `UserTierLimits.BOOST_TOKENS = 500_000`
  - `record QuotaSnapshot(int tier, int usedTokens, int limitTokens, int remainingTokens, int remainingPercent, boolean boostAllowed)`
  - `TokenQuotaService(UserMapper users, LlmCallMapper calls, TokenBoostRequestMapper boosts, Clock clock)`
  - `QuotaSnapshot snapshot(long userId)`
  - `int estimate(String question, List<String> recentTexts)`
  - `void assertCanStart(long userId, String question, List<String> recentTexts)`
  - `LlmCallMapper.sumSuccessTokens(long userId, LocalDateTime startInclusive, LocalDateTime endExclusive)`

`snapshot`：上海日的起止是 `[当日 00:00, 次日 00:00)`，时区 `Asia/Shanghai`。已用是该窗口内 `llm_calls.status = SUCCESS` 的 `total_tokens` 之和，只按 `user_id`，不按工作区。上限 = 等级原值；若该日有 `status = APPROVED` 的提额，再加 500_000。剩余 = 上限 − 已用，可以为负。剩余百分比：剩余 &lt; 0 时为 0，否则 `Math.round(remaining * 100.0 / limit)`。`boostAllowed` 仅在等级不是 3 时为 true。`estimate` 把问题和近期原文各 `count` 后相加，不调用模型。`assertCanStart` 在剩余 &lt; 预估时抛 `BizException`：等级 1 和 2 的码 `4292`、文案 `今日 Token 额度不足，请提额后再问。`；等级 3 的码 `4293`、文案 `今日 Token 额度不足。`

- [x] **Step 1: Write the failing test**

固定 `Clock` 在 `2026-11-09T00:30+08:00`。用户等级 1，已用 400_000，无通过记录：剩余 100_000，百分比 20，`boostAllowed` true。同一用户已用 600_000：剩余 −100_000，百分比 0。等级 3 的 `boostAllowed` 为 false，上限 5_000_000。该日有一条 APPROVED：等级 1 上限 1_000_000。`estimate("订单", List.of("abcd"))` 等于两次 `count` 之和。剩余 1、预估大于 1 时 `assertCanStart` 抛码 4292；等级 3 抛码 4293。另一个 userId 的已用不改变这个人的剩余。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TokenQuotaServiceTest test`  
Expected: 编译失败，找不到 `TokenQuotaService`。

- [x] **Step 3: Implement the quota service**

`sumSuccessTokens` 用 `@Select`，`COALESCE(SUM(total_tokens),0)`，条件是 `user_id`、`status = 'SUCCESS'`、`created_at >=` 与 `<`。不要按工作区过滤。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TokenQuotaServiceTest test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: compute a person's Shanghai-day token quota`

---

### Task 5: 每秒两次发问

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/AskAdmission.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/AskAdmissionTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `AskAdmission(Clock clock)`。`String begin(long userId)`：过去 1000ms 内该用户已有 2 次 `begin` 时抛 `BizException(4291, "请求过于频繁，请稍后再试")`，否则记一次并返回票。`void follow(long userId, String ticket)`：票属于该用户、未使用、签发不超过 1000ms，则标记已用并返回；否则当作 `begin`。票不能给另一个用户用。

- [x] **Step 1: Write the failing test**

同一时钟：第 1、2 次 `begin` 成功，第 3 次抛 4291，且异常文案就是那句冻结文案。时钟前进 1001ms 后可以再 `begin`。`follow` 使用刚签发的票不增加次数，因此两次 `begin` 加一次合法 `follow` 后仍能再 `begin` 一次。用过的票、别人的票、空票都走 `begin`。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AskAdmissionTest test`  
Expected: 编译失败，找不到 `AskAdmission`。

- [x] **Step 3: Implement `AskAdmission`**

内存滑动窗口即可。三个等级共用这一套，不要按等级分支。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AskAdmissionTest test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: admit two questions per second per user`

---

### Task 6: 开跑前拦住五个入口

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/AskGate.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/QueryRewriteService.java`（`HISTORY_LIMIT` 改为 `public static final`）
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/ChatRequestDTO.java`（可空字段 `admissionTicket`）
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/AnalysisRequestDTO.java`（同样的可空字段 `admissionTicket`）
- Modify: `backend/src/main/java/com/huiyi/assistant/model/vo/IntentResponseVO.java`（可空字段 `admissionTicket`）
- Modify: `backend/src/main/java/com/huiyi/assistant/api/v1/ChatV1Controller.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/api/v1/AnalysisV1Controller.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/ChatService.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/AskGateTest.java`

**Interfaces:**
- Consumes: `AskAdmission.begin` / `follow`，`TokenQuotaService.estimate` / `assertCanStart`，`QueryRewriteService.HISTORY_LIMIT`，`ChatSessionService.getRecentMessages`
- Produces: `AskGate.enter(long userId, String question, String sessionId, String ticket, boolean issuing)` 返回票。`issuing` 为 true 时 `begin` 并把新票返回；为 false 时 `follow`。然后取该会话最近 `HISTORY_LIMIT` 条消息的原文做 `assertCanStart`。会话 id 为空则近期为空。准入失败时不得调用额度检查。额度失败时异常原样抛出。

`ChatService.precheck(String question, String admissionTicket)` 把这张票放进两个 `new IntentResponseVO(...)`。现有两参构造改为三参，第三参就是这张票。

控制器先 `enter`，再审计，再调用服务。`/chat/intent` 的 `issuing` 为 true，其余四个为 false：`POST .../chat`、`POST .../chat/ask`、`POST .../chat/stream`、`POST .../analysis/query`。闸门抛错时不调用 `auditAsked`、`requestTraceService.start`、`saveUserMessage`。`AgentRunner` 和各服务方法内部不再调用 `AskGate` 或 `assertCanStart`。

- [x] **Step 1: Write the failing test**

`AskGate` 用假的 admission、quota、session。`begin` 抛 4291 时，quota 的 `assertCanStart` 调用次数为 0。quota 抛 4292 时异常码仍是 4292。通过时 `issuing=true` 返回新票。近期条数使用 `QueryRewriteService.HISTORY_LIMIT`，断言该常量是 20。再用 Mockito 构造 `ChatV1Controller`：`enter` 抛 4292 时，`chatService.precheck`、`chat`、`ask`、`chatStream` 的调用次数都是 0。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AskGateTest test`  
Expected: 编译失败，找不到 `AskGate`。

- [x] **Step 3: Implement the gate and call it first**

五个入口都走闸门。`chatStream` 在创建 `SseEmitter` 之前就可能抛 `BizException`，这样响应仍是 JSON `Result`。不要在闸门里调用意图、改写、检索或查数。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=AskGateTest,QueryRewriteServiceTest" test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: stop a question before it starts when quota or rate is exceeded`

---

### Task 7: 流式用量和运行合计

**Files:**
- Modify: `backend/src/main/java/com/huiyi/assistant/config/LlmConfig.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/LlmService.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/mapper/LlmCallMapper.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/observability/RequestTraceService.java`
- Create: `backend/src/main/java/com/huiyi/assistant/quota/StreamUsage.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/StreamUsageTest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/observability/RequestTraceServiceTest.java`（若已有则追加）

**Interfaces:**
- Consumes: `LlmCallRecorder.record`，`AssistantRunRecorder.finish`
- Produces:
  - `StreamUsage.from(Usage usage)` 返回 `int[]{prompt, completion}`；`usage` 或其中一侧为 null 时返回 `null`。
  - `record RunTokenSum(int promptTokens, int completionTokens)`，放在 `com.huiyi.assistant.quota`。
  - `LlmCallMapper.sumByRun(String runId)` 返回该 `run_id` 下 `SUCCESS` 行的 prompt 与 completion 之和。没有行时两个都是 0。
  - `RequestTraceService.finish` 用这个和调用 `runRecorder.finish`，不再写死 0, 0。

`LlmService.chatStream` 改为消费 `chatResponse()` 流，向外仍只发出文本。完成时若 `StreamUsage.from` 至少成功过一次，用最后一次的数记账；一次都没有则记 0, 0，并 `log.warn("流式用量缺失，本段记 0 purpose={}", purpose)`。非流式路径保持现在的 `tokens(response, prompt)`。

- [x] **Step 1: Write the failing test**

`StreamUsage.from(null)` 为 null。两侧都有数时返回该数组。`RequestTraceService.finish` 在 mapper 返回 prompt 3、completion 4 时，传给 `runRecorder.finish` 的就是 3 和 4，不是 0 和 0。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=StreamUsageTest,RequestTraceServiceTest" test`  
Expected: 失败。`finish` 仍把 0, 0 传给 recorder，或 `StreamUsage` 不存在。

- [x] **Step 3: Implement usage capture and the run sum**

`OpenAiChatOptions.builder()` 增加 `.streamUsage(true)`。合计只加 `SUCCESS` 行，这样缺用量而记 0 的步骤不构成「最多的一步」，但运行合计仍包含真正记上的检索和补全。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=StreamUsageTest,RequestTraceServiceTest" test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: record stream usage and roll it onto the run`

---

### Task 8: 提问时的检索向量化

**Files:**
- Modify: `python-service/app/services/embedding.py`
- Modify: `python-service/app/services/vector_store.py`（search 把查询用量带出来）
- Modify: `python-service/app/core/schema.py`（`SearchResponse.embedding_tokens: int | None = None`）
- Modify: `python-service/app/api/vector.py`
- Test: `python-service/app/tests/test_embedding_usage.py`
- Modify: `backend/src/main/java/com/huiyi/assistant/model/dto/VectorSearchResponseDTO.java`（`Integer embeddingTokens`）
- Create: `backend/src/main/java/com/huiyi/assistant/quota/QueryEmbeddingMeter.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/service/PythonServiceClient.java`（只在 `searchVectors` 成功返回后调用 meter）
- Test: `backend/src/test/java/com/huiyi/assistant/quota/QueryEmbeddingMeterTest.java`

**Interfaces:**
- Consumes: `LocalTokenCounter.count`，`LlmCallRecorder.record`，`WorkspaceContextHolder`
- Produces: `usage_tokens(body: dict) -> int | None`，放在 `embedding.py`。有 `usage.total_tokens` 时返回该整数，否则返回 `None`。search 响应用这个值填 `embedding_tokens`。`QueryEmbeddingMeter.record(String query, Integer providerTokens)`：当前没有工作区用户时直接返回。`providerTokens != null` 时用该数，否则用 `LocalTokenCounter.count(query)`。记一条 `purpose = query_embedding`、`status = SUCCESS`、completion 为 0 的 `llm_calls`。

Python：解析 embedding 响应里的 `usage.total_tokens`；没有这个字段则为 `None`。只放到 search 响应。upsert 和 similarity 的响应不加这个字段，Java 也不在这两处调用 meter。

- [x] **Step 1: Write the failing tests**

Python：给定带 `usage.total_tokens = 12` 的响应体，解析结果为 12；没有 `usage` 时为 `None`。Java：有用户且 provider 为 null 时，recorder 收到的 prompt 等于 `LocalTokenCounter.count("订单")`。provider 为 12 时记下 12。`WorkspaceContextHolder` 为空时 recorder 调用次数为 0。

- [x] **Step 2: Run tests to verify they fail**

Run: 工作目录 `python-service`，`python -m pytest app/tests/test_embedding_usage.py`。  
Run: `mvn -q -Dtest=QueryEmbeddingMeterTest test`。  
Expected: 失败，`usage_tokens` 或 `QueryEmbeddingMeter` 不存在。

- [x] **Step 3: Implement the meter and the search-only wiring**

`upsertChunks`、文档上传用的 upsert、`backfillSpaceIds`、similarity 方法体里不出现 `QueryEmbeddingMeter`。

- [x] **Step 4: Run tests to verify they pass**

两条命令都退出码 0。再跑与 vector search DTO 相关的现有 Java 测试，确认多出来的可空字段不破坏旧断言。

- [x] **Step 5: Commit**

`feat: bill query embedding tokens to the asker`

---

### Task 9: 提额申请和审评

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/TokenBoostService.java`
- Create: `backend/src/main/java/com/huiyi/assistant/quota/SentenceUsage.java`
- Create: `backend/src/main/java/com/huiyi/assistant/api/v1/QuotaController.java`
- Create: `backend/src/main/java/com/huiyi/assistant/model/dto/BoostSubmitRequest.java`
- Create: `backend/src/main/java/com/huiyi/assistant/model/dto/BoostDecisionRequest.java`
- Test: `backend/src/test/java/com/huiyi/assistant/quota/TokenBoostServiceTest.java`

**Interfaces:**
- Consumes: `UserTierLimits.BOOST_TOKENS`，`TokenBoostRequest`，`Clock`，运行与 `llm_calls`
- Produces:
  - `GET /api/v1/me/quota` → `QuotaSnapshot`
  - `GET /api/v1/me/quota/sentences` → 申请人可见的句子列表：`runId`、`question`、`totalTokens`、`heaviest`。不含 `stepName` / `stepTokens`。
  - `POST /api/v1/me/quota/boosts`，体 `{ "runId": "..." }`
  - `GET /api/v1/quota/boosts` → 待审列表，含申请人、理由、步骤名、步骤 Token
  - `POST /api/v1/quota/boosts/{id}/decision`，体 `{ "decision": "APPROVED" | "REJECTED" }`
  - `SentenceUsage` 如上。`TokenBoostService.submit(long applicantId, String runId)` 按当时的账重算，不采用客户端传来的 Token 数。`decide(long reviewerId, long requestId, String decision)`。

句子来源：该用户上海日当天 `assistant_runs.total_tokens > 0` 的运行。最多的一句 `heaviest=true`。步骤取该 run 下 `llm_calls` 里 `total_tokens > 0` 的最大一条，`purpose` 写入 `step_name`。还是 0 的调用不参与。没跑起来的句子没有运行行，自然不在列表里。

规则：等级 3 提交抛 `BizException(400, "第三级不能提额")`。该日已有 APPROVED 再提交抛 `BizException(400, "今日提额已通过")`。REJECTED 之后可以再提交。通过时写入 `reviewer_user_id`、`reviewed_at`、`status=APPROVED`。第一级的申请只有用户名 `limit` 的第二级能审。第二级的申请任一第三级都能审。审评人等级不对抛 `BizException(403, "没有权限执行该操作")`。通过不触发问答。

- [x] **Step 1: Write the failing test**

等级 3 不能提交。第一级被拒绝后可以再交；通过后再交得到 `今日提额已通过`。通过后 `TokenQuotaService` 在同一上海日的上限是等级原值 + 500_000。时钟拨到次日 00:00 上海时间，上限回到等级原值。列表里 Token 最多的一句被标出。申请人的句子 DTO 没有步骤字段；审评 DTO 有。第二级不能审另一名第一级以外的伪造单，第三级不能审第一级的单。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TokenBoostServiceTest test`  
Expected: 编译失败，找不到 `TokenBoostService`。

- [x] **Step 3: Implement submit, decide, and the five routes**

控制器只取登录用户，再调用服务。不要在通过时调用 `ChatService`。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=TokenBoostServiceTest,TokenQuotaServiceTest" test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: let a tier ask for today's token boost`

---

### Task 10: 第三级新建账号

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/quota/CreateTierAccountService.java`
- Create: `backend/src/main/java/com/huiyi/assistant/model/dto/CreateTierAccountRequest.java`
- Create: `backend/src/main/java/com/huiyi/assistant/model/vo/CreatedUserVO.java`
- Modify: `backend/src/main/java/com/huiyi/assistant/api/v1/QuotaController.java` 或新建方法放在现有 workspace 控制器。放在 `WorkspaceController` 会和成员接口混在一起，因此加在 `QuotaController` 不可行（它没有 workspace）。新建 `backend/src/main/java/com/huiyi/assistant/api/v1/TierAccountController.java`。
- Test: `backend/src/test/java/com/huiyi/assistant/quota/CreateTierAccountServiceTest.java`

**Interfaces:**
- Consumes: `PasswordService.validate` / `hash`，`WorkspaceMemberMapper`，`UserMapper`，`WorkspaceService.requireMember`
- Produces: `POST /api/v1/workspaces/{workspaceId}/users`。体：`username`、`password`、可空 `displayName`、`tier`。`CreatedUserVO(Long id, String username, int tier)`。不返回 Token，不登录新用户。

`create(AuthUser creator, long workspaceId, CreateTierAccountRequest request)`：创建者等级不是 3 时抛 `BizException(403, "只有第三级可以新建账号")`。`tier` 不是 2 或 3 时抛 `BizException(400, "等级只能是第二级或第三级")`。口令走现有 `PasswordService`。用户名规则与 `register` 相同：至少 3 位、不可重复。显示名为空时用用户名。新用户 `userTier` 为请求的等级，`status` 为 ACTIVE。成员行的 `roleCode` 为 `VIEWER`，工作区是路径上的 `workspaceId`，且创建者必须已是该工作区成员。不修改创建者或 `blank` / `limit` 的角色。

- [x] **Step 1: Write the failing test**

等级 2 调用得到 403。等级 3 请求等级 1 得到 400。等级 3 请求等级 2：插入用户的 `userTier` 为 2，成员角色为 `VIEWER`，创建者自己的角色更新次数为 0。短密码得到 `密码至少 10 位`。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=CreateTierAccountServiceTest test`  
Expected: 编译失败，找不到该类。

- [x] **Step 3: Implement create-account**

不要提供修改已有 `user_tier` 的方法。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=CreateTierAccountServiceTest,UserTierAssignmentTest" test`  
Expected: 退出码 0。

- [x] **Step 5: Commit**

`feat: let tier 3 create a tier 2 or tier 3 account`

---

### Task 11: 聊天页今日额度

**Files:**
- Create: `frontend/src/quota/quotaView.ts`
- Create: `frontend/src/quota/quotaView.test.ts`
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/api/chatStream.ts`（`streamChat` 增加参数 `admissionTicket: string | undefined`，放进请求体）
- Modify: `frontend/src/stores/chat.ts`
- Modify: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/api/http.ts`（仅当 4291 / 4292 / 4293 需要把 `code` 留在错误对象上）

**Interfaces:**
- Consumes: `GET /api/v1/me/quota` 的 JSON 字段 `tier`、`usedTokens`、`limitTokens`、`remainingTokens`、`remainingPercent`、`boostAllowed`
- Produces: `quotaPercent(remaining: number, limit: number): number`。`remaining < 0` 或 `limit <= 0` 返回 0，否则 `Math.round(remaining * 100 / limit)`。`quotaErrorKind(code: number): 'rate' | 'quota' | 'tier3' | null`：4291 → rate，4292 → quota，4293 → tier3。

条放在输入框上方，靠左，宽 240px，高 2px，颜色用现有 `--huiyi-line` 与 `--huiyi-brass`。左标题「今日额度」，右百分比。进入聊天页拉一次；一问结束（流式 `onDone`、分析或复合问题的 `finally`）再拉一次。不要按吐出的字更新。

`chat.ts` 的 `ask`：预检失败且种类是 rate、quota、tier3 时，不推用户气泡，不调用 stream / analysis / ask。rate 只保留拦截器已弹出的那句「请求过于频繁，请稍后再试」，页面不放提额按钮。quota 在输入区下方放一个文字按钮「提额」，链到 `/boost`。tier3 不放这个按钮。预检成功时把返回的 `admissionTicket` 放进随后的 ask、stream 或 analysis 请求。

- [x] **Step 1: Write the failing test**

```js
test('negative remaining shows 0 percent', () => {
  assert.equal(quotaPercent(-1, 500000), 0)
})
test('rate limit is not a boost', () => {
  assert.equal(quotaErrorKind(4291), 'rate')
  assert.equal(quotaErrorKind(4292), 'quota')
  assert.equal(quotaErrorKind(4293), 'tier3')
})
```

- [x] **Step 2: Run test to verify it fails**

Run: `node --experimental-strip-types --test src/quota/quotaView.test.ts`，工作目录 `frontend`。  
Expected: 找不到模块。

- [x] **Step 3: Implement the bar and the send abort**

预检以外的错误仍按现在的方式落到知识问答。不要改登录、上传、发送按钮文案。

- [x] **Step 4: Run test to verify it passes**

同一条 `node` 命令退出码 0。再跑 `node --experimental-strip-types --test src/components/assistantWaitHint.test.ts`，确认等待文案测试仍过。

- [x] **Step 5: Commit**

`feat: show today's quota above the chat input`

---

### Task 12: 申请页、审评页、建号页和文档状态

**Files:**
- Create: `frontend/src/views/BoostView.vue`
- Create: `frontend/src/views/BoostReviewView.vue`
- Create: `frontend/src/views/CreateTierUserView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/App.vue`（仅当需要菜单入口：第三级看见「新建账号」，第二级和第三级看见「审评」）
- Modify: `docs/development.md` 的「待完善：用户等级」
- Modify: `docs/superpowers/specs/2026-11-09-v15-token-metering-and-user-tiers.md` 开头状态行

**Interfaces:**
- Consumes: Task 9 和 Task 10 的路径与字段
- Produces: 路由 `/boost`、`/boost/review`、`/users/new`，都不是 `public`。申请页列出句子，最多的一句标明「最多」。确认后把该 `runId` 提交，不让用户填步骤。审评页能看见申请人、理由、步骤名、步骤 Token，以及通过、拒绝。建号页字段只有用户名、口令、等级（只能选 2 或 3）、可空显示名。

- [x] **Step 1: Write the failing test**

在 `quotaView.test.ts` 追加：等级选择只接受 2 和 3，`tierChoiceAllowed(1)` 为 false，`tierChoiceAllowed(2)` 与 `tierChoiceAllowed(3)` 为 true。

- [x] **Step 2: Run test to verify it fails**

Run: `node --experimental-strip-types --test src/quota/quotaView.test.ts`  
Expected: `tierChoiceAllowed` 未定义。

- [x] **Step 3: Implement the three pages**

沿用现有象牙底和铜按钮，不新增第二种强调色。菜单按 `GET /api/v1/me/quota` 的 `tier` 显示。文档把「未做」里已经落地的等级列、补记、默认第一级、第三级建号、每日扣减改成指向这些类；规格状态改为「实施中」，不要写成已经验收。

- [x] **Step 4: Run tests**

`node --experimental-strip-types --test src/quota/quotaView.test.ts` 退出码 0。  
`mvn -q -Dtest=NginxLimitTest test` 退出码 0，确认来源限制测试仍在。  
然后跑 `mvn -q test`。失败的测试按名字记下来，属于本计划改坏的就修，属于分支带入前就失败的不要混进本版修复。

- [x] **Step 5: Commit**

`feat: add boost review and tier account pages`

---

## Spec coverage

| 规格 | 任务 |
| --- | --- |
| 1 目标、6 提问时发生什么 | 4、5、6、11 |
| 2 核心能力 1–6 | 2、3、4、7、8、9、11 |
| 4 等级与不能做的事 | 2、3、10 |
| 5 额度怎么计、现网缺口 | 1、4、7、8 |
| 7 提额 | 9、12 |
| 8 数据与入口 | 2、9、10 |
| 9 验收里内网与 8080 | 已在 `nginx.conf` 与 compose；本计划用 `NginxLimitTest` 守住，不改网段 |
| 11 页面外观 | 开工时带入已改页面；11 只加额度条 |
| 12 内网 | 不改 `SecurityConfig`，不改允许网段 |

明确不做的项没有对应实现任务。
