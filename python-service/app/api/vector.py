"""向量库接口：upsert / search / delete。"""

from fastapi import APIRouter, Depends

from app.core.schema import (
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
    count = store.upsert(request.chunks, request.collection_name)
    return UpsertResponse(success=True, upserted_count=count, collection_name=request.collection_name)


@router.post("/search", response_model=SearchResponse, summary="向量 Top-K 检索")
def search(
    request: SearchRequest,
    store: VectorStore = Depends(get_vector_store),
) -> SearchResponse:
    hits = store.search(request.query, request.top_k, request.collection_name)
    return SearchResponse(success=True, hits=hits)


@router.post("/delete", response_model=DeleteResponse, summary="按文件名删除向量")
def delete(
    request: DeleteRequest,
    store: VectorStore = Depends(get_vector_store),
) -> DeleteResponse:
    count = store.delete_by_filename(request.filename, request.collection_name)
    return DeleteResponse(success=True, deleted_count=count, filename=request.filename)


@router.post("/similarity", response_model=SimilarityResponse, summary="两条文本的 embedding cosine 相似度")
def similarity(
    request: SimilarityRequest,
    store: VectorStore = Depends(get_vector_store),
) -> SimilarityResponse:
    score = store.similarity(request.text_a, request.text_b)
    return SimilarityResponse(success=True, score=round(score, 4))
