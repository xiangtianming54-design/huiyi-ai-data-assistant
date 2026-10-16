"""Reciprocal Rank Fusion：合并向量与关键词两条排序。"""

RRF_K = 60


def rrf_merge(vector_ranked: list[tuple[str, float]], keyword_ranked: list[tuple[str, float]], top_k: int) -> list[tuple[str, float]]:
    """输入 [(id, score), ...] 已按相关度降序。返回 [(id, rrf), ...] 取 top_k。"""
    rrf: dict[str, float] = {}
    for rank, (doc_id, _) in enumerate(vector_ranked, start=1):
        rrf[doc_id] = rrf.get(doc_id, 0.0) + 1.0 / (RRF_K + rank)
    for rank, (doc_id, _) in enumerate(keyword_ranked, start=1):
        rrf[doc_id] = rrf.get(doc_id, 0.0) + 1.0 / (RRF_K + rank)
    ordered = sorted(rrf.items(), key=lambda item: item[1], reverse=True)
    return ordered[:top_k]
