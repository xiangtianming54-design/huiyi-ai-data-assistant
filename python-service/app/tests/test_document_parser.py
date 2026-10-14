"""document_parser 与解析接口单元测试：md/txt/pdf/docx 三格式样例 + 异常分支 + API 契约。"""

import io

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.document_parser import (
    DocumentParseError,
    UnsupportedFileTypeError,
    parse_document,
    source_type_of,
)

client = TestClient(app)


def _make_pdf_bytes(text: str) -> bytes:
    """用 PyMuPDF 生成最小 PDF 样例。"""
    import fitz

    with fitz.open() as doc:
        page = doc.new_page()
        page.insert_text((72, 72), text)
        return doc.tobytes()


def _make_docx_bytes(text: str) -> bytes:
    """用 python-docx 生成最小 Word 样例。"""
    from docx import Document

    doc = Document()
    doc.add_paragraph(text)
    buffer = io.BytesIO()
    doc.save(buffer)
    return buffer.getvalue()


class TestParseText:
    def test_utf8_markdown(self):
        assert parse_document("a.md", "有效订单定义。".encode("utf-8")) == "有效订单定义。"

    def test_gbk_fallback(self):
        assert parse_document("a.txt", "有效订单定义。".encode("gbk")) == "有效订单定义。"

    def test_invalid_bytes_replaced_not_raised(self):
        # utf-8 与 gbk 均无法完整解码时，以替换字符容错而不是抛异常
        text = parse_document("a.txt", b"\xff\xfe\x00invalid")
        assert text


class TestParsePdf:
    def test_extracts_text(self):
        pdf_bytes = _make_pdf_bytes("HuiYi PDF Sample 2026")
        assert "HuiYi PDF Sample 2026" in parse_document("sample.pdf", pdf_bytes)

    def test_corrupted_pdf_raises_parse_error(self):
        with pytest.raises(DocumentParseError):
            parse_document("broken.pdf", b"not a real pdf content")


class TestParseDocx:
    def test_extracts_text(self):
        docx_bytes = _make_docx_bytes("有效订单是指支付成功且未退款的订单。")
        assert "有效订单" in parse_document("sample.docx", docx_bytes)

    def test_corrupted_docx_raises_parse_error(self):
        with pytest.raises(DocumentParseError):
            parse_document("broken.docx", b"not a real docx content")


class TestUnsupported:
    def test_unsupported_extension_raises(self):
        with pytest.raises(UnsupportedFileTypeError):
            parse_document("data.xlsx", b"whatever")

    def test_no_extension_raises(self):
        with pytest.raises(UnsupportedFileTypeError):
            parse_document("README", b"whatever")

    def test_source_type(self):
        assert source_type_of("a.MD") == "md"
        assert source_type_of("b.pdf") == "pdf"


class TestParseApi:
    def test_upload_markdown_returns_contract_fields(self):
        content = "有效订单是指支付成功且未退款的订单。\n\n销售额默认只统计 PAID 订单。".encode("utf-8")
        response = client.post(
            "/api/documents/parse",
            files={"file": ("business-glossary.md", content, "text/markdown")},
        )
        assert response.status_code == 200
        body = response.json()
        assert body["filename"] == "business-glossary.md"
        assert body["chunk_count"] == len(body["chunks"]) >= 1
        first = body["chunks"][0]
        assert first["id"] == "business-glossary-md-chunk-0000"
        assert first["text"]
        assert first["metadata"] == {
            "filename": "business-glossary.md",
            "chunk_index": 0,
            "source_type": "md",
        }

    def test_upload_pdf_sample(self):
        response = client.post(
            "/api/documents/parse",
            files={"file": ("sample.pdf", _make_pdf_bytes("HuiYi PDF Sample"), "application/pdf")},
        )
        assert response.status_code == 200
        assert "HuiYi PDF Sample" in response.json()["chunks"][0]["text"]

    def test_upload_docx_sample(self):
        response = client.post(
            "/api/documents/parse",
            files={"file": ("sample.docx", _make_docx_bytes("HuiYi Docx Sample"), "application/octet-stream")},
        )
        assert response.status_code == 200
        assert "HuiYi Docx Sample" in response.json()["chunks"][0]["text"]

    def test_empty_file_rejected(self):
        response = client.post(
            "/api/documents/parse",
            files={"file": ("empty.md", b"", "text/markdown")},
        )
        assert response.status_code == 400

    def test_unsupported_type_rejected(self):
        response = client.post(
            "/api/documents/parse",
            files={"file": ("data.xlsx", b"whatever", "application/octet-stream")},
        )
        assert response.status_code == 400

    def test_custom_chunk_params(self):
        content = "\n\n".join(f"第{i}段" + "文" * 40 for i in range(30)).encode("utf-8")
        response = client.post(
            "/api/documents/parse",
            files={"file": ("long.md", content, "text/markdown")},
            data={"chunk_size": 100, "chunk_overlap": 20},
        )
        assert response.status_code == 200
        body = response.json()
        assert body["chunk_count"] > 1
        assert all(len(chunk["text"]) <= 100 for chunk in body["chunks"])
