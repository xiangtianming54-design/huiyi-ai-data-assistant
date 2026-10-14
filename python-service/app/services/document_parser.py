"""文档解析：按扩展名分发到对应解析器，统一输出纯文本。

支持格式：
- .md / .txt：直接按 utf-8 解码，失败时容错 gbk
- .pdf：PyMuPDF 逐页提取文本
- .docx：python-docx 提取段落文本
"""

import io
from pathlib import Path

SUPPORTED_EXTENSIONS = (".md", ".txt", ".pdf", ".docx")


class UnsupportedFileTypeError(Exception):
    """文件类型不在支持范围内。"""


class DocumentParseError(Exception):
    """解析过程发生错误（文件损坏、格式异常等）。"""


def source_type_of(filename: str) -> str:
    """从文件名提取来源类型（扩展名，不含点）。"""
    return Path(filename).suffix.lower().lstrip(".")


def parse_document(filename: str, content: bytes) -> str:
    """按扩展名解析文件内容为纯文本。

    :raises UnsupportedFileTypeError: 扩展名不支持
    :raises DocumentParseError: 解析失败
    """
    ext = Path(filename).suffix.lower()
    if ext in (".md", ".txt"):
        return _parse_text(content)
    if ext == ".pdf":
        return _parse_pdf(content)
    if ext == ".docx":
        return _parse_docx(content)
    raise UnsupportedFileTypeError(
        f"不支持的文件类型: {ext or '(无扩展名)'}，仅支持 {', '.join(SUPPORTED_EXTENSIONS)}"
    )


def _parse_text(content: bytes) -> str:
    """Markdown / 纯文本：utf-8 优先，容错 gbk。"""
    try:
        return content.decode("utf-8")
    except UnicodeDecodeError:
        return content.decode("gbk", errors="replace")


def _parse_pdf(content: bytes) -> str:
    """PDF：PyMuPDF 逐页提取，页间以空行分隔。"""
    try:
        import fitz  # PyMuPDF，延迟导入以加快服务启动

        with fitz.open(stream=content, filetype="pdf") as doc:
            pages = [page.get_text().strip() for page in doc]
        text = "\n\n".join(page for page in pages if page)
        if not text:
            raise DocumentParseError("PDF 中未提取到文本内容")
        return text
    except DocumentParseError:
        raise
    except Exception as e:
        raise DocumentParseError(f"PDF 解析失败: {e}") from e


def _parse_docx(content: bytes) -> str:
    """Word：提取全部非空段落。"""
    try:
        from docx import Document

        doc = Document(io.BytesIO(content))
        paragraphs = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
        text = "\n\n".join(paragraphs)
        if not text:
            raise DocumentParseError("Word 文档中未提取到文本内容")
        return text
    except DocumentParseError:
        raise
    except Exception as e:
        raise DocumentParseError(f"Word 解析失败: {e}") from e
