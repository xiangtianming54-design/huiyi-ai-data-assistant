from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from app.core.observability import ObservabilityMiddleware


def _app() -> Starlette:
    async def health(_request):
        return JSONResponse({"status": "ok"})

    application = Starlette(routes=[Route("/health", health)])
    application.add_middleware(ObservabilityMiddleware)
    return application


def test_health_echoes_generated_request_id():
    response = TestClient(_app()).get("/health")
    assert response.status_code == 200
    request_id = response.headers.get("x-request-id")
    assert request_id
    assert request_id.startswith("req_")


def test_health_honors_incoming_ids():
    response = TestClient(_app()).get(
        "/health",
        headers={
            "X-Request-Id": "req_abc123abc123abc123abc123abc123ab",
            "X-Run-Id": "run_abc123abc123abc123abc123abc123ab",
        },
    )
    assert response.status_code == 200
    assert response.headers.get("x-request-id") == "req_abc123abc123abc123abc123abc123ab"
    assert response.headers.get("x-run-id") == "run_abc123abc123abc123abc123abc123ab"
