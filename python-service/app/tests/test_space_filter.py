"""知识库按 knowledge_space_id 过滤：检索 $in、空列表无命中。"""

from app.core.schema import Chunk, ChunkMetadata
from app.main import app
from app.services.vector_store import VectorStore, get_vector_store
from app.tests.test_vector_store import FakeEmbeddingService
from fastapi.testclient import TestClient

WS = 1


def _chunk(chunk_id: str, text: str, space_id: int, doc_id: int) -> Chunk:
    return Chunk(
        id=chunk_id,
        text=text,
        metadata=ChunkMetadata(
            filename=f"{doc_id}.md",
            chunk_index=0,
            source_type="md",
            workspace_id=str(WS),
            doc_id=str(doc_id),
            knowledge_space_id=str(space_id),
        ),
    )


def test_search_filters_other_space(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    a = _chunk("10-chunk-0000", "有效订单是空间十的口径", 10, 10)
    b = _chunk("11-chunk-0000", "有效订单是空间十一的口径", 11, 11)
    store.upsert_workspace_chunks(WS, [a, b], "huiyi_knowledge")
    hits = store.search_by_workspace("有效订单", WS, 5, "huiyi_knowledge", knowledge_space_ids=[10])
    ids = [hit.id for hit in hits]
    assert "10-chunk-0000" in ids
    assert "11-chunk-0000" not in ids


def test_search_api_empty_space_ids_returns_no_hits(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert_workspace_chunks(WS, [_chunk("10-chunk-0000", "有效订单是空间十的口径", 10, 10)], "huiyi_knowledge")
    app.dependency_overrides[get_vector_store] = lambda: store
    try:
        client = TestClient(app)
        empty = client.post(
            "/api/vector/search",
            json={
                "query": "有效订单",
                "top_k": 5,
                "collection_name": "huiyi_knowledge",
                "workspace_id": WS,
                "knowledge_space_ids": [],
            },
        )
        assert empty.status_code == 200
        assert empty.json()["hits"] == []
        missing = client.post(
            "/api/vector/search",
            json={
                "query": "有效订单",
                "top_k": 5,
                "collection_name": "huiyi_knowledge",
                "workspace_id": WS,
            },
        )
        assert missing.status_code == 200
        assert missing.json()["hits"] == []
    finally:
        app.dependency_overrides.clear()
