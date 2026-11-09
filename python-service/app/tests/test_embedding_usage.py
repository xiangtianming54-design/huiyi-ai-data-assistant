"""查询向量化用量：从 embedding 响应体解析 usage.total_tokens。"""

from app.services.embedding import usage_tokens


def test_usage_total_tokens_is_returned():
    assert usage_tokens({"usage": {"total_tokens": 12}}) == 12


def test_missing_usage_is_none():
    assert usage_tokens({"data": [{"index": 0, "embedding": [0.1]}]}) is None
