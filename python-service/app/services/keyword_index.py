"""落盘 BM25 关键词索引：与 Chroma upsert/delete 同步，中文按字符 bigram。"""

from __future__ import annotations

import json
import math
from collections import Counter
from pathlib import Path

from app.core.schema import Chunk

BM25_K1 = 1.5
BM25_B = 0.75


def tokenize(text: str) -> list[str]:
    """空白折叠后取字符 bigram；单字文本保留自身。"""
    compact = "".join(text.split()).lower()
    if not compact:
        return []
    if len(compact) == 1:
        return [compact]
    return [compact[i : i + 2] for i in range(len(compact) - 1)]


class KeywordIndex:
    """按 collection 落盘的文档表 + 检索时现算 BM25。"""

    def __init__(self, persist_dir: str) -> None:
        self._root = Path(persist_dir) / "keyword_index"
        self._root.mkdir(parents=True, exist_ok=True)

    def upsert(self, collection_name: str, chunks: list[Chunk]) -> None:
        docs = self._load(collection_name)
        for chunk in chunks:
            docs[chunk.id] = {
                "id": chunk.id,
                "text": chunk.text,
                "metadata": chunk.metadata.model_dump(),
                "filename": chunk.metadata.filename,
            }
        self._save(collection_name, docs)

    def delete_by_filename(self, collection_name: str, filename: str) -> int:
        docs = self._load(collection_name)
        remove = [key for key, doc in docs.items() if doc.get("filename") == filename]
        for key in remove:
            del docs[key]
        self._save(collection_name, docs)
        return len(remove)

    def search(self, collection_name: str, query: str, top_k: int) -> list[dict]:
        query_tokens = tokenize(query)
        if not query_tokens or top_k <= 0:
            return []
        docs = list(self._load(collection_name).values())
        if not docs:
            return []
        tokenized = [tokenize(doc["text"]) for doc in docs]
        scored = []
        for doc, tokens in zip(docs, tokenized):
            score = self._bm25(query_tokens, tokens, tokenized)
            if score > 0:
                scored.append({
                    "id": doc["id"],
                    "text": doc["text"],
                    "metadata": doc["metadata"],
                    "score": score,
                })
        scored.sort(key=lambda item: item["score"], reverse=True)
        return scored[:top_k]

    def _bm25(self, query_tokens: list[str], doc_tokens: list[str], corpus: list[list[str]]) -> float:
        if not doc_tokens:
            return 0.0
        query_set = set(query_tokens)
        if not query_set.intersection(doc_tokens):
            return 0.0
        n = len(corpus)
        avgdl = sum(len(tokens) for tokens in corpus) / n
        tf = Counter(doc_tokens)
        dl = len(doc_tokens)
        score = 0.0
        for term in query_set:
            df = sum(1 for tokens in corpus if term in tokens)
            idf = math.log(1.0 + (n - df + 0.5) / (df + 0.5))
            freq = tf[term]
            denom = freq + BM25_K1 * (1 - BM25_B + BM25_B * dl / avgdl)
            score += idf * (freq * (BM25_K1 + 1) / denom)
        return score

    def _path(self, collection_name: str) -> Path:
        safe = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in collection_name)
        return self._root / f"{safe}.json"

    def _load(self, collection_name: str) -> dict[str, dict]:
        path = self._path(collection_name)
        if not path.exists():
            return {}
        return json.loads(path.read_text(encoding="utf-8"))

    def _save(self, collection_name: str, docs: dict[str, dict]) -> None:
        path = self._path(collection_name)
        path.write_text(json.dumps(docs, ensure_ascii=False), encoding="utf-8")
