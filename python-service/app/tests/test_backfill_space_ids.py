"""存量 chunk 回填 knowledge_space_id。"""

from app.core.schema import Chunk, ChunkMetadata
from app.main import app
from app.services.vector_store import VectorStore, get_vector_store
from app.tests.test_vector_store import FakeEmbeddingService
from fastapi.testclient import TestClient


def test_backfill_updates_metadata(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    chunk = Chunk(
        id="10-chunk-0000",
        text="有效订单是空间十的口径",
        metadata=ChunkMetadata(
            filename="10.md",
            chunk_index=0,
            source_type="md",
            workspace_id="1",
            doc_id="10",
            knowledge_space_id="10",
        ),
    )
    store.upsert_workspace_chunks(1, [chunk], "huiyi_knowledge")
    app.dependency_overrides[get_vector_store] = lambda: store
    try:
        client = TestClient(app)
        resp = client.post(
            "/api/vector/backfill-space-ids",
            json={
                "collection_name": "huiyi_knowledge",
                "assignments": [
                    {"workspace_id": 1, "doc_id": 10, "knowledge_space_id": 11},
                ],
            },
        )
        assert resp.status_code == 200
        hits = store.search_by_workspace("有效订单", 1, 5, "huiyi_knowledge", knowledge_space_ids=[11])
        assert any(hit.id == "10-chunk-0000" for hit in hits)
        old = store.search_by_workspace("有效订单", 1, 5, "huiyi_knowledge", knowledge_space_ids=[10])
        assert old == []
    finally:
        app.dependency_overrides.clear()


def test_backfill_equal_id_keeps_hit_and_two_docs(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    first = Chunk(
        id="10-chunk-0000",
        text="空间十一口径甲",
        metadata=ChunkMetadata(
            filename="10.md",
            chunk_index=0,
            source_type="md",
            workspace_id="1",
            doc_id="10",
            knowledge_space_id="11",
        ),
    )
    second = Chunk(
        id="12-chunk-0000",
        text="空间十二口径乙",
        metadata=ChunkMetadata(
            filename="12.md",
            chunk_index=0,
            source_type="md",
            workspace_id="1",
            doc_id="12",
            knowledge_space_id="10",
        ),
    )
    store.upsert_workspace_chunks(1, [first, second], "huiyi_knowledge")
    app.dependency_overrides[get_vector_store] = lambda: store
    try:
        client = TestClient(app)
        resp = client.post(
            "/api/vector/backfill-space-ids",
            json={
                "collection_name": "huiyi_knowledge",
                "assignments": [
                    {"workspace_id": 1, "doc_id": 10, "knowledge_space_id": 11},
                    {"workspace_id": 1, "doc_id": 12, "knowledge_space_id": 12},
                ],
            },
        )
        assert resp.status_code == 200
        same = store.search_by_workspace("口径甲", 1, 5, "huiyi_knowledge", knowledge_space_ids=[11])
        assert any(hit.id == "10-chunk-0000" for hit in same)
        moved = store.search_by_workspace("口径乙", 1, 5, "huiyi_knowledge", knowledge_space_ids=[12])
        assert any(hit.id == "12-chunk-0000" for hit in moved)
    finally:
        app.dependency_overrides.clear()
