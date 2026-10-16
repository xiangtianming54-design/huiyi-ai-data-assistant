"""Chroma 向量库封装：持久化存储、collection 管理、upsert / search / delete。

- 使用 chromadb.PersistentClient 本地持久化，服务重启后数据仍在
- collection 采用 cosine 相似度空间，返回 score = 1 - distance
- upsert 按 chunk id 幂等（同 id 覆盖，不产生重复向量）
"""

import chromadb

from app.core.config import settings
from app.core.schema import Chunk, SearchHit
from app.services.embedding import EmbeddingService


class VectorStore:
    """Chroma 向量库客户端。"""

    def __init__(self, persist_dir: str, embedding_service: EmbeddingService) -> None:
        self._client = chromadb.PersistentClient(path=persist_dir)
        self._embedding = embedding_service

    def _collection(self, name: str):
        """获取或创建 collection（cosine 相似度空间）。"""
        return self._client.get_or_create_collection(
            name=name,
            metadata={"hnsw:space": "cosine"},
        )

    def upsert(self, chunks: list[Chunk], collection_name: str) -> int:
        """embed + upsert，幂等（同 id 覆盖）。返回写入数量。"""
        if not chunks:
            return 0
        collection = self._collection(collection_name)
        embeddings = self._embedding.embed([chunk.text for chunk in chunks])
        collection.upsert(
            ids=[chunk.id for chunk in chunks],
            embeddings=embeddings,
            documents=[chunk.text for chunk in chunks],
            metadatas=[chunk.metadata.model_dump() for chunk in chunks],
        )
        return len(chunks)

    def search(self, query: str, top_k: int, collection_name: str) -> list[SearchHit]:
        """query embed → top_k 检索，score 为 cosine 相似度（越大越相关）。"""
        collection = self._collection(collection_name)
        if collection.count() == 0:
            return []
        query_embedding = self._embedding.embed_single(query)
        result = collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            include=["documents", "metadatas", "distances"],
        )
        hits = []
        for chunk_id, document, metadata, distance in zip(
            result["ids"][0],
            result["documents"][0],
            result["metadatas"][0],
            result["distances"][0],
        ):
            hits.append(
                SearchHit(
                    id=chunk_id,
                    text=document,
                    metadata=metadata,
                    score=round(1.0 - distance, 4),
                )
            )
        return hits

    def similarity(self, text_a: str, text_b: str) -> float:
        """两条文本的 cosine 相似度，范围 [0, 1]（向量夹角，负值截断为 0）。"""
        vectors = self._embedding.embed([text_a, text_b])
        return cosine(vectors[0], vectors[1])

    def delete_by_filename(self, filename: str, collection_name: str) -> int:
        """按 metadata.filename 删除文档全部分块，返回删除数量。"""
        collection = self._collection(collection_name)
        existing = collection.get(where={"filename": filename}, include=[])
        ids = existing.get("ids", [])
        if not ids:
            return 0
        collection.delete(ids=ids)
        return len(ids)


# ===== 延迟初始化的模块级单例（测试可通过依赖注入替换） =====

_vector_store: VectorStore | None = None


def cosine(left: list[float], right: list[float]) -> float:
    """余弦相似度，结果截断到 [0, 1]。"""
    dot = sum(x * y for x, y in zip(left, right))
    norm_left = sum(x * x for x in left) ** 0.5
    norm_right = sum(y * y for y in right) ** 0.5
    if norm_left == 0 or norm_right == 0:
        return 0.0
    return max(0.0, min(1.0, dot / (norm_left * norm_right)))


def get_vector_store() -> VectorStore:
    """获取 VectorStore 单例，首次调用时按配置初始化。"""
    global _vector_store
    if _vector_store is None:
        _vector_store = VectorStore(settings.chroma_persist_dir, EmbeddingService())
    return _vector_store
