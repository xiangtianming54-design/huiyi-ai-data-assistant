"""通用分块器：段落优先切分，chunk_size / overlap 可配置。

切分策略：
1. 按空行切分段落；
2. 超过 chunk_size 的段落按字符硬切；
3. 贪心合并段落至 chunk_size 上限，块间按 overlap 保留尾部上下文；
4. chunk_id = {slug(filename)}-chunk-{index:04d}，保证唯一稳定。
"""

import re

PARAGRAPH_SEPARATOR = "\n\n"

# 段落分隔：一个或多个空行
_PARAGRAPH_PATTERN = re.compile(r"\n\s*\n")

# slug：小写字母数字，其余折叠为连字符
_NON_ALNUM_PATTERN = re.compile(r"[^a-z0-9]+")


def slugify(filename: str) -> str:
    """文件名转 slug，如 business-glossary.md -> business-glossary-md。"""
    slug = _NON_ALNUM_PATTERN.sub("-", filename.lower()).strip("-")
    return slug or "document"


def make_chunk_id(filename: str, index: int) -> str:
    """生成稳定唯一的分块 ID。"""
    return f"{slugify(filename)}-chunk-{index:04d}"


def chunk_text(text: str, chunk_size: int = 500, overlap: int = 50) -> list[str]:
    """将纯文本切分为若干非空分块。

    :param text: 待切分文本，空白文本返回空列表
    :param chunk_size: 单块最大字符数
    :param overlap: 相邻块间保留的上下文字符数（须小于 chunk_size）
    :raises ValueError: 参数非法
    """
    if chunk_size <= 0:
        raise ValueError("chunk_size 必须为正整数")
    if not 0 <= overlap < chunk_size:
        raise ValueError("overlap 必须满足 0 <= overlap < chunk_size")

    text = text.strip()
    if not text:
        return []

    pieces = _split_pieces(text, chunk_size)
    return _merge_pieces(pieces, chunk_size, overlap)


def _split_pieces(text: str, chunk_size: int) -> list[str]:
    """按段落切分，超长段落按字符硬切为不超过 chunk_size 的片段。"""
    pieces: list[str] = []
    for para in _PARAGRAPH_PATTERN.split(text):
        para = para.strip()
        if not para:
            continue
        if len(para) <= chunk_size:
            pieces.append(para)
        else:
            pieces.extend(para[i : i + chunk_size] for i in range(0, len(para), chunk_size))
    return pieces


def _merge_pieces(pieces: list[str], chunk_size: int, overlap: int) -> list[str]:
    """贪心合并片段：不超过 chunk_size；块间保留不超过 overlap 的尾部上下文。"""
    sep = PARAGRAPH_SEPARATOR
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0

    for piece in pieces:
        projected = current_len + len(piece) + (len(sep) if current else 0)
        if current and projected > chunk_size:
            chunks.append(sep.join(current))
            # 仅保留尾部片段作为重叠上下文
            while current and current_len > overlap:
                head = current.pop(0)
                current_len -= len(head) + (len(sep) if current else 0)
            projected = current_len + len(piece) + (len(sep) if current else 0)
            if projected > chunk_size:
                # 上下文无法保留时直接开启新块，避免超限
                current, current_len, projected = [], 0, len(piece)
        current.append(piece)
        current_len = projected

    if current:
        chunks.append(sep.join(current))
    return chunks
