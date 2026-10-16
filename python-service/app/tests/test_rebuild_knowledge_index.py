"""一次性重建知识索引：给缺 workspace_id 的旧 chunk 补齐后重建 collection。"""

from app.core.schema import Chunk, ChunkMetadata
from app.services.vector_store import VectorStore
from app.tests.test_vector_store import FakeEmbeddingService

LEGACY = Chunk(
    id="business-glossary-md-chunk-0000",
    text="有效订单是指支付成功且未退款的订单。",
    metadata=ChunkMetadata(
        filename="business-glossary.md",
        chunk_index=0,
        source_type="md",
        doc_id="12",
    ),
)
SCOPED = Chunk(
    id="other-md-chunk-0000",
    text="华东地区各渠道的有效订单量。",
    metadata=ChunkMetadata(
        filename="other.md",
        chunk_index=0,
        source_type="md",
        workspace_id="2",
        doc_id="99",
    ),
)


def test_rebuild_assigns_workspace_and_drops_unmapped(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([LEGACY, SCOPED], "huiyi_knowledge")

    result = store.rebuild_assign_workspace(
        "huiyi_knowledge",
        {"12": 7, "business-glossary.md": 7},
    )

    assert result["kept"] == 2
    assert result["assigned"] == 1
    assert result["dropped"] == 0
    assert result["missing_workspace"] == 0
    hits = store.search_by_workspace("有效订单", 7, 5, "huiyi_knowledge")
    assert hits
    assert all(str(hit.metadata.get("workspace_id")) == "7" for hit in hits)
    other = store.search_by_workspace("渠道", 2, 5, "huiyi_knowledge")
    assert other
    assert all(str(hit.metadata.get("workspace_id")) == "2" for hit in other)


def test_rebuild_drops_chunks_without_mapping(tmp_path):
    store = VectorStore(str(tmp_path), FakeEmbeddingService())
    store.upsert([LEGACY], "huiyi_knowledge")
    result = store.rebuild_assign_workspace("huiyi_knowledge", {})
    assert result["dropped"] == 1
    assert store.search_by_workspace("有效订单", 7, 5, "huiyi_knowledge") == []
