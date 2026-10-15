"""embedding 封装单元测试：配置校验、响应解析、维度校验、重试与批量（不依赖真实 API）。"""

import httpx
import pytest

from app.services import embedding as embedding_module
from app.services.embedding import EmbeddingError, EmbeddingService


class TestConfig:
    def test_missing_config_raises(self):
        # 测试环境默认未配置 LLM_*，应快速失败而非发起网络请求
        service = EmbeddingService()
        with pytest.raises(EmbeddingError, match="未配置"):
            service.embed(["文本"])


class TestParseResponse:
    def test_sorted_by_index(self):
        body = {
            "data": [
                {"index": 1, "embedding": [0.2, 0.3]},
                {"index": 0, "embedding": [0.1, 0.4]},
            ]
        }
        vectors = EmbeddingService._parse_response(body, expected=2)
        assert vectors == [[0.1, 0.4], [0.2, 0.3]]

    def test_count_mismatch_raises(self):
        body = {"data": [{"index": 0, "embedding": [0.1]}]}
        with pytest.raises(EmbeddingError, match="数量不匹配"):
            EmbeddingService._parse_response(body, expected=2)

    def test_invalid_format_raises(self):
        with pytest.raises(EmbeddingError, match="格式非法"):
            EmbeddingService._parse_response({"unexpected": []}, expected=1)


class TestDimensionCheck:
    def test_inconsistent_dimensions_raise(self):
        with pytest.raises(EmbeddingError, match="维度不一致"):
            EmbeddingService._check_dimensions([[0.1, 0.2], [0.1]])

    def test_empty_vector_raises(self):
        with pytest.raises(EmbeddingError, match="空向量"):
            EmbeddingService._check_dimensions([[]])

    def test_valid_vectors_pass(self):
        EmbeddingService._check_dimensions([[0.1, 0.2], [0.3, 0.4]])


class _FakeResponse:
    """模拟 httpx.Response 的极简实现。"""

    def __init__(self, status_code: int, body: dict | None = None):
        self.status_code = status_code
        self._body = body or {}
        self.text = "" if body is None else str(body)

    def json(self):
        return self._body


class _FakeClient:
    """模拟 httpx.Client：按脚本返回响应或抛错，记录调用次数。"""

    def __init__(self, script: list, timeout: int = 30):
        self._script = list(script)
        self.calls: list[dict] = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def post(self, url: str, json: dict, headers: dict):
        self.calls.append({"url": url, "json": json, "headers": headers})
        action = self._script.pop(0)
        if isinstance(action, Exception):
            raise action
        return action


def _ok_body(count: int, dim: int = 3) -> dict:
    return {"data": [{"index": i, "embedding": [0.1 * (i + 1)] * dim} for i in range(count)]}


@pytest.fixture
def configured_service(monkeypatch):
    """配置完整的 EmbeddingService。"""
    monkeypatch.setattr(embedding_module.settings, "llm_base_url", "https://llm.example.com/v1")
    monkeypatch.setattr(embedding_module.settings, "llm_api_key", "sk-test")
    monkeypatch.setattr(embedding_module.settings, "llm_embedding_model", "test-embedding")
    return EmbeddingService()


class TestEmbedCalls:
    def test_single_embed_success(self, configured_service, monkeypatch):
        client = _FakeClient([_FakeResponse(200, _ok_body(1))])
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        vectors = configured_service.embed(["文本"])
        assert vectors == [[0.1, 0.1, 0.1]]
        assert client.calls[0]["url"] == "https://llm.example.com/v1/embeddings"
        assert client.calls[0]["headers"]["Authorization"] == "Bearer sk-test"
        assert client.calls[0]["json"]["model"] == "test-embedding"

    def test_batch_splitting(self, configured_service, monkeypatch):
        client = _FakeClient([_FakeResponse(200, _ok_body(32)), _FakeResponse(200, _ok_body(2))])
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        vectors = configured_service.embed([f"文本{i}" for i in range(34)])
        assert len(vectors) == 34
        assert len(client.calls) == 2, "34 条应按 32/批拆为两次请求"

    def test_retry_on_network_error(self, configured_service, monkeypatch):
        monkeypatch.setattr(embedding_module.time, "sleep", lambda _: None)
        client = _FakeClient([
            httpx.ConnectError("boom"),
            _FakeResponse(200, _ok_body(1)),
        ])
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        assert configured_service.embed_single("文本") == [0.1, 0.1, 0.1]
        assert len(client.calls) == 2

    def test_retry_exhausted_raises(self, configured_service, monkeypatch):
        monkeypatch.setattr(embedding_module.time, "sleep", lambda _: None)
        client = _FakeClient([httpx.ConnectError("boom")] * 3)
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        with pytest.raises(EmbeddingError, match="重试"):
            configured_service.embed(["文本"])

    def test_4xx_fails_fast_without_retry(self, configured_service, monkeypatch):
        client = _FakeClient([_FakeResponse(401, {"error": "unauthorized"})])
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        with pytest.raises(EmbeddingError, match="401"):
            configured_service.embed(["文本"])
        assert len(client.calls) == 1, "4xx 不应重试"

    def test_5xx_retries_then_raises(self, configured_service, monkeypatch):
        monkeypatch.setattr(embedding_module.time, "sleep", lambda _: None)
        client = _FakeClient([_FakeResponse(500)] * 3)
        monkeypatch.setattr(embedding_module.httpx, "Client", lambda **kw: client)
        with pytest.raises(EmbeddingError):
            configured_service.embed(["文本"])
        assert len(client.calls) == 3

    def test_empty_input_returns_empty(self, configured_service):
        assert configured_service.embed([]) == []
