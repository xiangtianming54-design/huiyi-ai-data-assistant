"""Chroma 向量库封装：持久化存储、collection 管理、upsert / search / delete。

- 使用 chromadb.PersistentClient 本地持久化，服务重启后数据仍在
- collection 采用 cosine 相似度空间，返回 score = 1 - distance
- upsert 按 chunk id 幂等（同 id 覆盖，不产生重复向量）
"""

import chromadb

from app.core.config import settings
from app.core.schema import Chunk, ChunkMetadata, SearchHit
from app.services.embedding import EmbeddingService
from app.services.hybrid_search import rrf_merge
from app.services.keyword_index import KeywordIndex

VECTOR_SCORE_THRESHOLD = 0.5


class VectorStore:
    """Chroma 向量库客户端。"""

    def __init__(self, persist_dir: str, embedding_service: EmbeddingService) -> None:
        self._client = chromadb.PersistentClient(path=persist_dir)
        self._embedding = embedding_service
        self._keyword = KeywordIndex(persist_dir)

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
            metadatas=[chunk.metadata.model_dump(exclude_none=True) for chunk in chunks],
        )
        self._keyword.upsert(collection_name, chunks)
        return len(chunks)

    def upsert_workspace_chunks(self, workspace_id: int, chunks: list[Chunk], collection_name: str) -> int:
        """写入前补齐并校验 workspace_id，禁止混合工作区。"""
        workspace = str(workspace_id)
        for chunk in chunks:
            current = chunk.metadata.workspace_id
            if current is None:
                chunk.metadata.workspace_id = workspace
            elif str(current) != workspace:
                raise ValueError("不允许混合多个 workspace")
        return self.upsert(chunks, collection_name)

    def search(self, query: str, top_k: int, collection_name: str, workspace_id: int | None = None) -> list[SearchHit]:
        """知识库 collection 走向量+关键词 RRF；其它 collection 仍只做向量。"""
        fetch = top_k * 2 if collection_name == settings.chroma_collection else top_k
        vector_hits = self._vector_search(query, fetch, collection_name, workspace_id=workspace_id)
        if collection_name != settings.chroma_collection:
            return vector_hits[:top_k]
        qualified = [hit for hit in vector_hits if hit.score >= VECTOR_SCORE_THRESHOLD]
        try:
            keyword_raw = self._keyword.search(collection_name, query, fetch, workspace_id=workspace_id)
        except Exception:
            return qualified[:top_k]
        if not keyword_raw:
            return qualified[:top_k]
        merged_ids = rrf_merge(
            [(hit.id, hit.score) for hit in qualified],
            [(item["id"], item["score"]) for item in keyword_raw],
            top_k,
        )
        by_id = {hit.id: hit for hit in vector_hits}
        for item in keyword_raw:
            if item["id"] not in by_id:
                by_id[item["id"]] = SearchHit(
                    id=item["id"],
                    text=item["text"],
                    metadata=item["metadata"],
                    score=0.0,
                )
        return [by_id[doc_id] for doc_id, _ in merged_ids if doc_id in by_id]

    def search_by_workspace(self, query: str, workspace_id: int, top_k: int, collection_name: str) -> list[SearchHit]:
        """在检索阶段按 workspace_id 过滤，禁止先全库 Top-K 再过滤。"""
        return self.search(query, top_k, collection_name, workspace_id=workspace_id)

    def similarity(self, text_a: str, text_b: str) -> float:
        """两条文本的 cosine 相似度，范围 [0, 1]（向量夹角，负值截断为 0）。"""
        vectors = self._embedding.embed([text_a, text_b])
        return cosine(vectors[0], vectors[1])

    def delete_by_filename(self, filename: str, collection_name: str) -> int:
        """按 metadata.filename 删除（仅非知识库 collection）。"""
        collection = self._collection(collection_name)
        existing = collection.get(where={"filename": filename}, include=[])
        ids = existing.get("ids", [])
        if not ids:
            return 0
        collection.delete(ids=ids)
        self._keyword.delete_by_filename(collection_name, filename)
        return len(ids)

    def delete_by_doc(self, workspace_id: int, doc_id: int, collection_name: str) -> int:
        """按工作区 + 文档删除分块，不同工作区同名文件互不影响。"""
        collection = self._collection(collection_name)
        existing = collection.get(
            where={
                "$and": [
                    {"workspace_id": {"$eq": str(workspace_id)}},
                    {"doc_id": {"$eq": str(doc_id)}},
                ]
            },
            include=[],
        )
        ids = existing.get("ids", [])
        if not ids:
            return 0
        collection.delete(ids=ids)
        self._keyword.delete_by_doc(collection_name, workspace_id, doc_id)
        return len(ids)

    def rebuild_assign_workspace(self, collection_name: str, assignments: dict[str, int | str]) -> dict:
        """给缺 workspace_id 的旧 chunk 补齐后重建 collection；无法映射的分块丢弃。"""
        collection = self._collection(collection_name)
        existing = collection.get(include=["documents", "metadatas"])
        ids = existing.get("ids", [])
        documents = existing.get("documents") or []
        metadatas = existing.get("metadatas") or []
        lookup = {str(key): str(value) for key, value in assignments.items()}
        kept: list[Chunk] = []
        assigned = 0
        dropped = 0
        for chunk_id, text, raw_meta in zip(ids, documents, metadatas):
            metadata = dict(raw_meta or {})
            workspace = metadata.get("workspace_id")
            if not workspace:
                workspace = lookup.get(str(metadata.get("doc_id") or "")) or lookup.get(
                    str(metadata.get("filename") or "")
                )
                if workspace:
                    metadata["workspace_id"] = str(workspace)
                    assigned += 1
            if not metadata.get("workspace_id"):
                dropped += 1
                continue
            kept.append(
                Chunk(
                    id=chunk_id,
                    text=text or " ",
                    metadata=ChunkMetadata(
                        filename=str(metadata.get("filename") or "unknown"),
                        chunk_index=int(metadata.get("chunk_index") or 0),
                        source_type=str(metadata.get("source_type") or "md"),
                        workspace_id=str(metadata["workspace_id"]),
                        doc_id=str(metadata["doc_id"]) if metadata.get("doc_id") is not None else None,
                    ),
                )
            )
        try:
            self._client.delete_collection(collection_name)
        except Exception:
            pass
        if kept:
            self.upsert(kept, collection_name)
        self._keyword.replace(collection_name, kept)
        return {
            "kept": len(kept),
            "assigned": assigned,
            "dropped": dropped,
            "missing_workspace": dropped,
        }

    def delete_by_chunk_ids(self, workspace_id: int, chunk_ids: list[str], collection_name: str) -> int:
        """按工作区 + chunk id 删除，忽略不属于该工作区的 id。"""
        if not chunk_ids:
            return 0
        collection = self._collection(collection_name)
        existing = collection.get(
            ids=chunk_ids,
            where={"workspace_id": {"$eq": str(workspace_id)}},
            include=[],
        )
        ids = existing.get("ids", [])
        if not ids:
            return 0
        collection.delete(ids=ids)
        self._keyword.delete_by_chunk_ids(collection_name, workspace_id, ids)
        return len(ids)

    def _vector_search(
        self,
        query: str,
        top_k: int,
        collection_name: str,
        workspace_id: int | None = None,
    ) -> list[SearchHit]:
        """query embed → top_k 检索，score 为 cosine 相似度。知识库必须带 where 过滤。"""
        collection = self._collection(collection_name)
        if collection.count() == 0:
            return []
        query_embedding = self._embedding.embed_single(query)
        kwargs: dict = {
            "query_embeddings": [query_embedding],
            "n_results": min(top_k, collection.count()),
            "include": ["documents", "metadatas", "distances"],
        }
        if workspace_id is not None:
            kwargs["where"] = {"workspace_id": {"$eq": str(workspace_id)}}
        try:
            result = collection.query(**kwargs)
        except Exception:
            return []
        if not result or not result.get("ids") or not result["ids"][0]:
            return []
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
