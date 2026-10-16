"""vector_store 单元测试：upsert / search / delete / 幂等 / 持久化（临时目录隔离 Chroma）。"""

import pytest
from fastapi.testclient import TestClient

from app.core.schema import Chunk, ChunkMetadata
from app.main import app
from app.services.vector_store import VectorStore, get_vector_store

GLOSSARY_CHUNK = Chunk(
    id="business-glossary-md-chunk-0000",
    text="有效订单是指支付成功且未退款的订单，即状态为 PAID、SHIPPED 或 COMPLETED 的订单。",
    metadata=ChunkMetadata(filename="business-glossary.md", chunk_index=0, source_type="md"),
)
CHANNEL_CHUNK = Chunk(
    id="business-glossary-md-chunk-0001",
    text="销售渠道分为线上商城、直播带货、线下门店、分销代理四类。",
    metadata=ChunkMetadata(filename="business-glossary.md", chunk_index=1, source_type="md"),
)


class FakeEmbeddingService:
    """字符袋假 embedding：字符重叠越多的文本 cosine 越近，可预测检索排序。"""

    DIM = 128

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._vec(t) for t in texts]

    def embed_single(self, text: str) -> list[float]:
        return self._vec(text)

    def _vec(self, text: str) -> list[float]:
        vec = [0.0] * self.DIM
        for ch in text:
            vec[ord(ch) % self.DIM] += 1.0
        return vec


@pytest.fixture
def store(tmp_path):
    """每个测试用独立临时目录，互不污染。"""
    return VectorStore(str(tmp_path), FakeEmbeddingService())


@pytest.fixture
def client(store):
    app.dependency_overrides[get_vector_store] = lambda: store
    yield TestClient(app)
    app.dependency_overrides.clear()


class TestUpsertAndSearch:
    def test_search_returns_most_relevant_chunk(self, store):
        store.upsert([GLOSSARY_CHUNK, CHANNEL_CHUNK], "huiyi_knowledge")
        hits = store.search("什么是有效订单？", top_k=2, collection_name="huiyi_knowledge")
        assert hits
        assert hits[0].id == GLOSSARY_CHUNK.id
        assert "有效订单" in hits[0].text
        assert hits[0].metadata["filename"] == "business-glossary.md"
        assert 0 < hits[0].score <= 1

    def test_upsert_is_idempotent(self, store):
        store.upsert([GLOSSARY_CHUNK, CHANNEL_CHUNK], "huiyi_knowledge")
        store.upsert([GLOSSARY_CHUNK, CHANNEL_CHUNK], "huiyi_knowledge")
        hits = store.search("订单", top_k=10, collection_name="huiyi_knowledge")
        assert len(hits) == 2, "同 id 重复 upsert 不应产生重复向量"

    def test_search_empty_collection_returns_no_hits(self, store):
        assert store.search("任意问题", top_k=5, collection_name="huiyi_knowledge") == []

    def test_upsert_empty_chunks_returns_zero(self, store):
        assert store.upsert([], "huiyi_knowledge") == 0


class TestDelete:
    def test_delete_by_filename(self, store):
        store.upsert([GLOSSARY_CHUNK, CHANNEL_CHUNK], "huiyi_knowledge")
        deleted = store.delete_by_filename("business-glossary.md", "huiyi_knowledge")
        assert deleted == 2
        assert store.search("什么是有效订单？", top_k=5, collection_name="huiyi_knowledge") == []

    def test_delete_nonexistent_returns_zero(self, store):
        assert store.delete_by_filename("not-exist.md", "huiyi_knowledge") == 0


class TestPersistence:
    def test_data_survives_client_recreate(self, tmp_path):
        persist_dir = str(tmp_path)
        VectorStore(persist_dir, FakeEmbeddingService()).upsert([GLOSSARY_CHUNK], "huiyi_knowledge")
        # 模拟服务重启：同一目录重建 client，数据仍在
        new_store = VectorStore(persist_dir, FakeEmbeddingService())
        hits = new_store.search("什么是有效订单？", top_k=1, collection_name="huiyi_knowledge")
        assert hits and hits[0].id == GLOSSARY_CHUNK.id


class TestSimilarity:
    def test_paraphrase_is_closer_than_unrelated(self, store):
        paraphrase = store.similarity("什么是有效订单？", "有效订单的定义是什么？")
        unrelated = store.similarity("什么是有效订单？", "上个月销售额前三的商品？")
        assert 0 <= unrelated < paraphrase <= 1

    def test_identical_texts_are_one(self, store):
        assert store.similarity("有效订单", "有效订单") == pytest.approx(1.0)

    def test_similarity_api(self, client):
        resp = client.post(
            "/api/vector/similarity",
            json={"text_a": "什么是有效订单？", "text_b": "有效订单的定义是什么？"},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["success"] is True
        assert 0 < body["score"] <= 1


class TestVectorApi:
    def test_upsert_search_delete_flow(self, client):
        chunks = [c.model_dump() for c in [GLOSSARY_CHUNK, CHANNEL_CHUNK]]

        upsert_resp = client.post(
            "/api/vector/upsert",
            json={"collection_name": "huiyi_knowledge", "chunks": chunks},
        )
        assert upsert_resp.status_code == 200
        assert upsert_resp.json() == {
            "success": True,
            "upserted_count": 2,
            "collection_name": "huiyi_knowledge",
        }

        search_resp = client.post(
            "/api/vector/search",
            json={"query": "什么是有效订单？", "top_k": 5, "collection_name": "huiyi_knowledge"},
        )
        assert search_resp.status_code == 200
        body = search_resp.json()
        assert body["success"] is True
        assert body["hits"][0]["id"] == GLOSSARY_CHUNK.id
        assert body["hits"][0]["metadata"]["filename"] == "business-glossary.md"
        assert body["hits"][0]["score"] > 0

        delete_resp = client.post(
            "/api/vector/delete",
            json={"filename": "business-glossary.md", "collection_name": "huiyi_knowledge"},
        )
        assert delete_resp.status_code == 200
        assert delete_resp.json()["deleted_count"] == 2

        search_again = client.post(
            "/api/vector/search",
            json={"query": "什么是有效订单？", "top_k": 5, "collection_name": "huiyi_knowledge"},
        )
        assert search_again.json()["hits"] == []
