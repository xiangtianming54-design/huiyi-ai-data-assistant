# v0.8 Observability & Cost Implementation Plan

> Status: 已完成。标签 `v0.8.0`。P2（调试面板、反馈带 ID、Python embedding/向量指标）不滚进 v0.9。
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship request-level observability: Request ID, Chat/Analysis Run ID, step Trace, LLM token rows, Micrometer metrics, Python access logs, and dependency health — without Grafana or Agent Loop.

**Architecture:** A servlet filter stamps every HTTP call with `X-Request-Id` and MDC. Chat/Analysis entrypoints mint `run_id` (`X-Run-Id` to Python). Recorders write `assistant_runs` / `assistant_run_steps` / `llm_calls`. Micrometer counters/timers expose `/actuator/prometheus`. Python middleware echoes IDs and logs path/status/latency.

**Tech Stack:** Spring Boot 3.5 Filter + Actuator + Micrometer Prometheus, MyBatis-Plus, Flyway V7–V9, FastAPI middleware, Vue axios/fetch.

**Spec:** `docs/superpowers/specs/2026-10-16-v08-observability-and-cost.md`

## Global Constraints

- Request-level Trace only; no Tool Registry / Agent Loop / Grafana.
- `audit_events` is not rebuilt; only fill `request_id`.
- `cost_amount` may be 0; no real model price table.
- Do not log passwords, tokens, or full prompts.
- Five-scene e2e and workspace isolation must still pass.
- Header names: `X-Request-Id`, `X-Run-Id`. IDs: `req_` / `run_` + 32 hex.
- Run status: `SUCCEEDED` | `FAILED`. Step status: `SUCCESS` | `FAILED` | `PASSED` | `REJECTED`.
- `/actuator/prometheus` and `/actuator/health` are permitAll; `/api/health` keeps `code=0` and adds `data.dependencies`.
- P2 debug panel / Python embedding-vector metrics are out of this implementation pass.

## Review Focus

- Missing inbound `X-Request-Id` still gets a generated id and response header.
- Parallel compound-question threads inherit `request_id` / `run_id` (InheritableThreadLocal).
- Recorder DB failures must not fail the user-facing Chat/Analysis call.
- Health payload must still parse as `code=0` when Java is up, even if a dependency is down.
- Prometheus endpoint must not require JWT.

## Task 1: Request ID filter and context

**Files:**
- Create: `backend/src/main/java/com/huiyi/assistant/observability/ObservabilityIds.java`
- Create: `backend/src/main/java/com/huiyi/assistant/observability/ObservabilityContextHolder.java`
- Create: `backend/src/main/java/com/huiyi/assistant/observability/RequestIdFilter.java`
- Create: `backend/src/main/java/com/huiyi/assistant/observability/ObservabilityProperties.java`
- Test: `backend/src/test/java/com/huiyi/assistant/observability/RequestIdFilterTest.java`
- Modify: `SecurityConfig.java` (filter order), `application-local.yml`, `application-docker.yml`

**Interfaces:**
- Produces: `ObservabilityContextHolder.requestId()`, `runId()`, `setRunId()`, `clear()`; `ObservabilityIds.requestId()` / `runId()`; filter writes `X-Request-Id`.

- [ ] Write failing `RequestIdFilterTest` (generate vs honor header, always echo response header, MDC/clear after request)
- [ ] Implement IDs, holder (InheritableThreadLocal), filter, properties
- [ ] `mvn -B -Dtest=RequestIdFilterTest test`
- [ ] Commit `feat: add request_id filter and observability context`

## Task 2: Flyway V7–V9

**Files:**
- Create: `V7__assistant_runs.sql`, `V8__assistant_run_steps.sql`, `V9__llm_calls.sql`
- Modify: `FlywayMigrationFilesTest.java`

- [ ] Extend test to require the three new table names
- [ ] Add SQL matching spec §6
- [ ] `mvn -B -Dtest=FlywayMigrationFilesTest test`
- [ ] Commit `feat: add Flyway V7-V9 observability tables`

## Task 3: Recorders and audit request_id

**Files:**
- Create entities/mappers for the three tables
- Create `AssistantRunRecorder`, `LlmCallRecorder`
- Modify `AuditService` to copy `ObservabilityContextHolder.requestId()`
- Test: recorder unit tests + `AuditServiceTest` request_id assertion

- [ ] Failing tests for insert payloads and audit request_id
- [ ] Implement recorders (swallow DB errors, cap steps at `traceMaxSteps`)
- [ ] Verify tests
- [ ] Commit `feat: record assistant runs, steps, llm calls, and audit request_id`

## Task 4: LLM usage + Python headers/metrics

**Files:**
- Modify `LlmService`, `PythonServiceClient`, `WebClientConfig`
- Create `ObservabilityMetrics`
- Tests: `LlmService` can stay thin with mocked ChatClient if costly; prefer `LlmCallRecorder` unit + `PythonServiceClientTest` header assertion

- [ ] Test Python client sends `X-Request-Id` / `X-Run-Id`
- [ ] Implement WebClient filter, LLM record on chat/chatStream complete, python counters
- [ ] Commit `feat: record LLM usage and propagate IDs to Python`

## Task 5: Chat/Analysis Request Trace + SQL metrics

**Files:**
- Create `RequestTraceService`
- Modify `ChatService`, `AnalysisService`, `SqlExecuteService`, controllers as needed
- Tests: `RequestTraceServiceTest`; extend `SqlExecuteServiceTest` for metrics hooks if cheap

- [ ] Test run start/step/finish and SQL reject/timeout/row-limit counters
- [ ] Wire Chat ask/stream/analysis executeQuery
- [ ] Commit `feat: write request trace for chat and analysis`

## Task 6: Actuator, health, security

**Files:**
- Modify `pom.xml`, `SecurityConfig`, `HealthController`, yml management endpoints
- Tests: `SecurityConfigTest` permits actuator; `HealthController` dependency map (mock)

- [ ] Failing tests for prometheus permitAll and health data.dependencies
- [ ] Implement
- [ ] Commit `feat: expose prometheus metrics and dependency health`

## Task 7: Python middleware

**Files:**
- Create `python-service/app/core/observability.py`
- Modify `main.py`
- Test: `python-service/app/tests/test_observability.py`

- [ ] Failing TestClient test for header echo + access log fields
- [ ] Implement middleware
- [ ] `pytest app/tests/test_observability.py -v`
- [ ] Commit `feat: add Python request_id and run_id access logs`

## Task 8: Frontend and env

**Files:**
- Modify `frontend/src/api/http.ts`, `chatStream.ts`, `.env.example`, `docker-compose.yml`
- Optional: `frontend/src/auth/requestId.ts`

- [ ] Save last `X-Request-Id` / `X-Run-Id` on responses; include in error toast when present
- [ ] Add observability env keys
- [ ] Commit `feat: persist request ids on the client and add observability config`
