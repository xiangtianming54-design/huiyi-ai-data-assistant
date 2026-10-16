"""一次性重建 huiyi_knowledge：给旧 chunk 补 workspace_id。

用法：
  python -m app.tools.rebuild_knowledge_index --mapping mapping.json

mapping.json 示例：
  { "12": 1, "business-glossary.md": 1 }
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from app.core.config import settings
from app.core.schema import DEFAULT_COLLECTION
from app.services.embedding import EmbeddingService
from app.services.vector_store import VectorStore


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Rebuild knowledge vectors with workspace_id")
    parser.add_argument("--mapping", required=True, help="JSON object: doc_id or filename -> workspace_id")
    parser.add_argument("--persist-dir", default=settings.chroma_persist_dir)
    parser.add_argument("--collection", default=DEFAULT_COLLECTION)
    args = parser.parse_args(argv)

    mapping_path = Path(args.mapping)
    assignments = json.loads(mapping_path.read_text(encoding="utf-8"))
    if not isinstance(assignments, dict):
        raise SystemExit("mapping 必须是对象：doc_id/filename -> workspace_id")

    store = VectorStore(args.persist_dir, EmbeddingService())
    result = store.rebuild_assign_workspace(args.collection, assignments)
    print(json.dumps(result, ensure_ascii=False))
    if result["missing_workspace"]:
        print("仍有 chunk 缺少 workspace_id，已丢弃以免越权检索", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
