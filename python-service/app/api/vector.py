"""向量库接口：upsert / search / delete。"""

from fastapi import APIRouter, Depends

from app.core.schema import (
    DEFAULT_COLLECTION,
    BackfillSpaceIdsRequest,
    BackfillSpaceIdsResponse,
    DeleteRequest,
    DeleteResponse,
    SearchRequest,
    SearchResponse,
    SimilarityRequest,
    SimilarityResponse,
    UpsertRequest,
    UpsertResponse,
)
from app.services.vector_store import VectorStore, get_vector_store

router = APIRouter(prefix="/api/vector", tags=["vector"])


@router.post("/upsert", response_model=UpsertResponse, summary="向量写入（幂等）")
def upsert(
    request: UpsertRequest,
    store: VectorStore = Depends(get_vector_store),
) -> UpsertResponse:
    if request.workspace_id is not None:
        count = store.upsert_workspace_chunks(request.workspace_id, request.chunks, request.collection_name)
    else:
        count = store.upsert(request.chunks, request.collection_name)
    return UpsertResponse(success=True, upserted_count=count, collection_name=request.collection_name)


@router.post("/search", response_model=SearchResponse, summary="向量 Top-K 检索")
def search(
    request: SearchRequest,
    store: VectorStore = Depends(get_vector_store),
) -> SearchResponse:
    if request.collection_name == DEFAULT_COLLECTION and not request.knowledge_space_ids:
        return SearchResponse(success=True, hits=[])
    if request.workspace_id is not None:
        hits = store.search_by_workspace(
            request.query,
            request.workspace_id,
            request.top_k,
            request.collection_name,
            knowledge_space_ids=request.knowledge_space_ids,
        )
    else:
        hits = store.search(
            request.query,
            request.top_k,
            request.collection_name,
            knowledge_space_ids=request.knowledge_space_ids,
        )
    return SearchResponse(success=True, hits=hits)


@router.post("/backfill-space-ids", response_model=BackfillSpaceIdsResponse, summary="回填 chunk 的知识空间 id")
def backfill_space_ids(
    request: BackfillSpaceIdsRequest,
    store: VectorStore = Depends(get_vector_store),
) -> BackfillSpaceIdsResponse:
    assignments = [
        (item.workspace_id, item.doc_id, item.knowledge_space_id) for item in request.assignments
    ]
    count = store.assign_knowledge_spaces(request.collection_name, assignments)
    return BackfillSpaceIdsResponse(success=True, updated_count=count)


@router.post("/delete", response_model=DeleteResponse, summary="按工作区文档或 chunk 删除向量")
def delete(
    request: DeleteRequest,
    store: VectorStore = Depends(get_vector_store),
) -> DeleteResponse:
    if request.workspace_id is not None and request.chunk_ids:
        count = store.delete_by_chunk_ids(request.workspace_id, request.chunk_ids, request.collection_name)
    elif request.workspace_id is not None and request.doc_id is not None:
        count = store.delete_by_doc(request.workspace_id, request.doc_id, request.collection_name)
    else:
        count = store.delete_by_filename(request.filename or "", request.collection_name)
    return DeleteResponse(success=True, deleted_count=count, filename=request.filename)


@router.post("/similarity", response_model=SimilarityResponse, summary="两条文本的 embedding cosine 相似度")
def similarity(
    request: SimilarityRequest,
    store: VectorStore = Depends(get_vector_store),
) -> SimilarityResponse:
    score = store.similarity(request.text_a, request.text_b)
    return SimilarityResponse(success=True, score=round(score, 4))
