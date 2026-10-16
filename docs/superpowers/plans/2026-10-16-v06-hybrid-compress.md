# v0.6 Hybrid Retrieval + Session Compress Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. User asked to finish v0.6 and publish on GitHub.

**Goal:** Knowledge search fuses BM25 + vectors via RRF; rewrite keeps 10 turns and LLM-summarizes older ones.

**Architecture:** Python KeywordIndex + RRF inside VectorStore.search for `huiyi_knowledge` only. Java QueryRewriteService loads last 20 messages, incrementally summarizes overflow into `chat_session_summaries`. RagService stops applying 0.5 after hybrid returns.

**Tech Stack:** Existing FastAPI/Chroma/Spring; homemade BM25 (char bigrams); MySQL new table.

**Spec:** `docs/v0.6-design.md`

## Global Constraints

- Hybrid only for `huiyi_knowledge`; `intent_examples` stays vector-only.
- Summary LLM failure must not fail the chat request.
- Incremental SQL for existing Docker volumes.

## Review Focus

- Keyword-only hit must not be dropped by Java 0.5 filter.
- Intent collection must not use BM25.
- Delete-by-filename clears keyword docs too.
- Rewrite with null message ids (unit tests) skips summary.
- Summary failure uses last 20 raw messages only.
