"""混合检索：BM25 关键词、RRF 融合、与向量支路协同。"""

from app.core.schema import Chunk, ChunkMetadata
from app.services.hybrid_search import rrf_merge
from app.services.keyword_index import KeywordIndex
from app.services.vector_store import VectorStore, get_vector_store
from app.tests.test_vector_store import FakeEmbeddingService, GLOSSARY_CHUNK, CHANNEL_CHUNK
from fastapi.testclient import TestClient

from app.main import app


def test_rrf_prefers_docs_on_both_lists():
    vector = [("a", 0.9), ("b", 0.8), ("c", 0.7)]
    keyword = [("c", 1.0), ("a", 0.5)]
    merged = rrf_merge(vector, keyword, top_k=2)
    assert [item[0] for item in merged][0] == "a"


def test_keyword_index_finds_overlapping_terms(tmp_path):
    index = KeywordIndex(str(tmp_path))
    index.upsert("huiyi_knowledge", [
        Chunk(id="g1", text="有效订单是指支付成功且未退款的订单。",
              metadata=ChunkMetadata(filename="glossary.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="1")),
        Chunk(id="c1", text="销售渠道分为线上商城与线下门店。",
              metadata=ChunkMetadata(filename="channel.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="2")),
    ])
    hits = index.search("huiyi_knowledge", "有效订单未退款", top_k=2)
    assert hits
    assert hits[0]["id"] == "g1"
    assert hits[0]["score"] > 0


def test_keyword_delete_by_filename(tmp_path):
    index = KeywordIndex(str(tmp_path))
    index.upsert("huiyi_knowledge", [
        Chunk(id="g1", text="有效订单定义",
              metadata=ChunkMetadata(filename="glossary.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="1")),
    ])
    index.delete_by_filename("huiyi_knowledge", "glossary.md")
    assert index.search("huiyi_knowledge", "有效订单", top_k=5) == []


def test_keyword_survives_reload(tmp_path):
    KeywordIndex(str(tmp_path)).upsert("huiyi_knowledge", [
        Chunk(id="g1", text="有效订单定义",
              metadata=ChunkMetadata(filename="glossary.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="1")),
    ])
    hits = KeywordIndex(str(tmp_path)).search("huiyi_knowledge", "有效订单", top_k=3)
    assert hits and hits[0]["id"] == "g1"


def test_hybrid_includes_lexical_match_when_vector_is_weak(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([
        Chunk(id="weak-vec", text="有效订单是支付成功且未退款的订单",
              metadata=ChunkMetadata(filename="glossary.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="1")),
        Chunk(id="strong-vec", text="渠道渠道渠道渠道渠道渠道渠道渠道",
              metadata=ChunkMetadata(filename="channel.md", chunk_index=0, source_type="md", workspace_id="1", doc_id="2")),
    ], "huiyi_knowledge")
    hits = store.search("什么是有效订单未退款", top_k=2, collection_name="huiyi_knowledge", workspace_id=1)
    ids = [h.id for h in hits]
    assert "weak-vec" in ids


def test_unrelated_query_can_be_empty(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([GLOSSARY_CHUNK], "huiyi_knowledge")
    hits = store.search("zzzzqqqxxx", top_k=5, collection_name="huiyi_knowledge", workspace_id=1)
    assert hits == [] or all(h.score < 0.5 and "zzzz" not in h.text for h in hits)


def test_intent_collection_stays_vector_only(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([
        Chunk(id="intent-1", text="上个月销售额最高的商品排名",
              metadata=ChunkMetadata(filename="intent-examples", chunk_index=0, source_type="intent")),
    ], "intent_examples")
    hits = store.search("销售额", top_k=3, collection_name="intent_examples")
    assert hits
    assert all(h.score is not None for h in hits)


def test_hybrid_api_search(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([GLOSSARY_CHUNK, CHANNEL_CHUNK], "huiyi_knowledge")
    app.dependency_overrides[get_vector_store] = lambda: store
    try:
        client = TestClient(app)
        resp = client.post(
            "/api/vector/search",
            json={
                "query": "什么是有效订单？",
                "top_k": 5,
                "collection_name": "huiyi_knowledge",
                "workspace_id": 1,
                "knowledge_space_ids": [100],
            },
        )
        assert resp.status_code == 200
        assert resp.json()["success"] is True
        assert resp.json()["hits"]
        assert resp.json()["hits"][0]["id"] == GLOSSARY_CHUNK.id
    finally:
        app.dependency_overrides.clear()
