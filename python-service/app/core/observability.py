"""Request/run ID helpers for Python Sidecar access logs."""

from __future__ import annotations

import logging
import time
import uuid

from starlette.types import ASGIApp, Receive, Scope, Send

REQUEST_ID_HEADER = b"x-request-id"
RUN_ID_HEADER = b"x-run-id"
logger = logging.getLogger("huiyi.python.access")


def new_request_id() -> str:
    return "req_" + uuid.uuid4().hex


class ObservabilityMiddleware:
    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = {k.lower(): v for k, v in scope.get("headers", [])}
        request_id = headers.get(REQUEST_ID_HEADER, b"").decode("latin-1").strip() or new_request_id()
        run_id = headers.get(RUN_ID_HEADER, b"").decode("latin-1").strip()
        started = time.perf_counter()
        status_code = 500

        async def send_wrapper(message: dict) -> None:
            nonlocal status_code
            if message["type"] == "http.response.start":
                status_code = int(message.get("status", 500))
                raw_headers = list(message.get("headers") or [])
                raw_headers.append((REQUEST_ID_HEADER, request_id.encode("latin-1")))
                if run_id:
                    raw_headers.append((RUN_ID_HEADER, run_id.encode("latin-1")))
                message = {**message, "headers": raw_headers}
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            latency_ms = int((time.perf_counter() - started) * 1000)
            path = scope.get("path", "")
            method = scope.get("method", "")
            logger.info(
                "request_id=%s run_id=%s path=%s method=%s status=%s latency_ms=%s",
                request_id,
                run_id or "-",
                path,
                method,
                status_code,
                latency_ms,
            )
