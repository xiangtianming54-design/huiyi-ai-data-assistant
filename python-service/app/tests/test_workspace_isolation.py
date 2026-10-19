"""知识库按 workspace_id 隔离：检索过滤、删除按文档、禁止仅按文件名删除。"""

from app.core.schema import Chunk, ChunkMetadata
from app.main import app
from app.services.keyword_index import KeywordIndex
from app.services.vector_store import VectorStore, get_vector_store
from app.tests.test_vector_store import FakeEmbeddingService
from fastapi.testclient import TestClient

WS1 = 1
WS2 = 2


def _chunk(chunk_id: str, text: str, workspace_id: int, doc_id: int, filename: str) -> Chunk:
    return Chunk(
        id=chunk_id,
        text=text,
        metadata=ChunkMetadata(
            filename=filename,
            chunk_index=0,
            source_type="md",
            workspace_id=str(workspace_id),
            doc_id=str(doc_id),
            knowledge_space_id="100",
        ),
    )


def _dump(chunk: Chunk) -> dict:
    return chunk.model_dump()


class TestKnowledgeApiRequiresWorkspace:
    def test_search_without_workspace_id_rejected(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        app.dependency_overrides[get_vector_store] = lambda: store
        try:
            client = TestClient(app)
            resp = client.post(
                "/api/vector/search",
                json={"query": "有效订单", "top_k": 5, "collection_name": "huiyi_knowledge"},
            )
            assert resp.status_code == 422
        finally:
            app.dependency_overrides.clear()

    def test_upsert_without_workspace_id_rejected(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        app.dependency_overrides[get_vector_store] = lambda: store
        try:
            client = TestClient(app)
            chunk = _chunk("12-chunk-0000", "有效订单是支付成功的订单", WS1, 12, "glossary.md")
            payload = chunk.model_dump()
            payload["metadata"].pop("workspace_id", None)
            resp = client.post(
                "/api/vector/upsert",
                json={"collection_name": "huiyi_knowledge", "chunks": [payload]},
            )
            assert resp.status_code == 422
        finally:
            app.dependency_overrides.clear()

    def test_delete_by_filename_only_rejected(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        app.dependency_overrides[get_vector_store] = lambda: store
        try:
            client = TestClient(app)
            resp = client.post(
                "/api/vector/delete",
                json={"filename": "glossary.md", "collection_name": "huiyi_knowledge"},
            )
            assert resp.status_code == 422
        finally:
            app.dependency_overrides.clear()


class TestWorkspaceSearchIsolation:
    def test_search_does_not_return_other_workspace(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        a = _chunk("12-chunk-0000", "有效订单是工作区甲的口径", WS1, 12, "glossary.md")
        b = _chunk("99-chunk-0000", "有效订单是工作区乙的口径", WS2, 99, "glossary.md")
        store.upsert_workspace_chunks(WS1, [a], "huiyi_knowledge")
        store.upsert_workspace_chunks(WS2, [b], "huiyi_knowledge")
        hits = store.search_by_workspace("有效订单", WS1, 5, "huiyi_knowledge")
        assert hits
        assert all(str(h.metadata.get("workspace_id")) == str(WS1) for h in hits)
        assert "12-chunk-0000" in [h.id for h in hits]
        assert "99-chunk-0000" not in [h.id for h in hits]

    def test_search_api_isolates_workspace(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        a = _chunk("12-chunk-0000", "有效订单是工作区甲的口径", WS1, 12, "glossary.md")
        b = _chunk("99-chunk-0000", "有效订单是工作区乙的口径", WS2, 99, "glossary.md")
        store.upsert_workspace_chunks(WS1, [a], "huiyi_knowledge")
        store.upsert_workspace_chunks(WS2, [b], "huiyi_knowledge")
        app.dependency_overrides[get_vector_store] = lambda: store
        try:
            client = TestClient(app)
            resp = client.post(
                "/api/vector/search",
                json={
                    "query": "有效订单",
                    "top_k": 5,
                    "collection_name": "huiyi_knowledge",
                    "workspace_id": WS1,
                    "knowledge_space_ids": [100],
                },
            )
            assert resp.status_code == 200
            ids = [hit["id"] for hit in resp.json()["hits"]]
            assert "12-chunk-0000" in ids
            assert "99-chunk-0000" not in ids
        finally:
            app.dependency_overrides.clear()


class TestDeleteByDoc:
    def test_delete_by_doc_does_not_touch_other_workspace(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        a = _chunk("12-chunk-0000", "有效订单是工作区甲的口径", WS1, 12, "glossary.md")
        b = _chunk("99-chunk-0000", "有效订单是工作区乙的口径", WS2, 99, "glossary.md")
        store.upsert_workspace_chunks(WS1, [a], "huiyi_knowledge")
        store.upsert_workspace_chunks(WS2, [b], "huiyi_knowledge")
        deleted = store.delete_by_doc(WS1, 12, "huiyi_knowledge")
        assert deleted == 1
        assert store.search_by_workspace("有效订单", WS1, 5, "huiyi_knowledge") == []
        remaining = store.search_by_workspace("有效订单", WS2, 5, "huiyi_knowledge")
        assert remaining and remaining[0].id == "99-chunk-0000"

    def test_delete_api_by_workspace_and_doc_id(self, tmp_path):
        store = VectorStore(str(tmp_path), FakeEmbeddingService())
        a = _chunk("12-chunk-0000", "有效订单是工作区甲的口径", WS1, 12, "glossary.md")
        store.upsert_workspace_chunks(WS1, [a], "huiyi_knowledge")
        app.dependency_overrides[get_vector_store] = lambda: store
        try:
            client = TestClient(app)
            resp = client.post(
                "/api/vector/delete",
                json={"workspace_id": WS1, "doc_id": 12, "collection_name": "huiyi_knowledge"},
            )
            assert resp.status_code == 200
            assert resp.json()["deleted_count"] == 1
        finally:
            app.dependency_overrides.clear()


class TestKeywordIndexWorkspace:
    def test_keyword_search_filters_workspace_before_scoring(self, tmp_path):
        index = KeywordIndex(str(tmp_path))
        index.upsert("huiyi_knowledge", [
            _chunk("12-chunk-0000", "有效订单是工作区甲的口径", WS1, 12, "glossary.md"),
            _chunk("99-chunk-0000", "有效订单是工作区乙的口径", WS2, 99, "glossary.md"),
        ])
        hits = index.search("huiyi_knowledge", "有效订单", top_k=5, workspace_id=WS1)
        assert hits
        assert all(str(h["metadata"].get("workspace_id")) == str(WS1) for h in hits)
        assert hits[0]["id"] == "12-chunk-0000"
