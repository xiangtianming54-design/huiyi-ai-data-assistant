"""embedding 封装单元测试：配置校验、响应解析、维度校验（不依赖真实 API）。"""

import pytest

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
