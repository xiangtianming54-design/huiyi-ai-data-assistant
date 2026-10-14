"""Embedding 封装：httpx 调 OpenAI 兼容 embedding API。

特性：
- 批量：单次请求可携带多条文本，内部按 batch_size 拆分
- 重试：网络错误 / 429 / 5xx 指数退避重试，4xx 立即失败
- 维度校验：返回向量必须非空且维度一致
"""

import time

import httpx

from app.core.config import settings


class EmbeddingError(Exception):
    """Embedding 调用失败（未配置、网络异常、响应非法等）。"""


# 单批最大文本数
BATCH_SIZE = 32

# 最大重试次数（不含首次）
MAX_RETRIES = 2


class EmbeddingService:
    """OpenAI 兼容 embedding API 客户端。"""

    def __init__(self) -> None:
        self._base_url = settings.llm_base_url.rstrip("/")
        self._api_key = settings.llm_api_key
        self._model = settings.llm_embedding_model

    def embed(self, texts: list[str]) -> list[list[float]]:
        """批量取向量，返回顺序与输入一致。"""
        if not texts:
            return []
        self._check_config()
        vectors: list[list[float]] = []
        for i in range(0, len(texts), BATCH_SIZE):
            vectors.extend(self._embed_batch(texts[i : i + BATCH_SIZE]))
        self._check_dimensions(vectors)
        return vectors

    def embed_single(self, text: str) -> list[float]:
        """单条取向量。"""
        return self.embed([text])[0]

    def _check_config(self) -> None:
        if not self._base_url or not self._api_key or not self._model:
            raise EmbeddingError("Embedding 未配置：请设置 LLM_BASE_URL / LLM_API_KEY / LLM_EMBEDDING_MODEL")

    def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        payload = {"model": self._model, "input": texts}
        headers = {"Authorization": f"Bearer {self._api_key}"}
        last_error: Exception | None = None

        for attempt in range(MAX_RETRIES + 1):
            try:
                with httpx.Client(timeout=30) as client:
                    response = client.post(f"{self._base_url}/embeddings", json=payload, headers=headers)
                if response.status_code == 200:
                    return self._parse_response(response.json(), expected=len(texts))
                # 4xx（除 429）属于配置/参数问题，重试无意义
                if 400 <= response.status_code < 500 and response.status_code != 429:
                    raise EmbeddingError(f"Embedding API 返回 {response.status_code}: {response.text[:200]}")
                last_error = EmbeddingError(f"Embedding API 返回 {response.status_code}")
            except httpx.HTTPError as e:
                last_error = e
            if attempt < MAX_RETRIES:
                time.sleep(2**attempt)

        raise EmbeddingError(f"Embedding API 调用失败（重试 {MAX_RETRIES} 次后仍失败）: {last_error}")

    @staticmethod
    def _parse_response(body: dict, expected: int) -> list[list[float]]:
        """解析响应：按 index 排序，数量必须与请求一致。"""
        try:
            data = sorted(body["data"], key=lambda item: item["index"])
            vectors = [item["embedding"] for item in data]
        except (KeyError, TypeError) as e:
            raise EmbeddingError(f"Embedding 响应格式非法: {e}") from e
        if len(vectors) != expected:
            raise EmbeddingError(f"Embedding 数量不匹配: 期望 {expected}，实际 {len(vectors)}")
        return vectors

    @staticmethod
    def _check_dimensions(vectors: list[list[float]]) -> None:
        """维度校验：非空且全部一致。"""
        if any(len(v) == 0 for v in vectors):
            raise EmbeddingError("Embedding 返回了空向量")
        dimensions = {len(v) for v in vectors}
        if len(dimensions) != 1:
            raise EmbeddingError(f"Embedding 维度不一致: {dimensions}")
