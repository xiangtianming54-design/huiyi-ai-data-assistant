"""chunker 单元测试：边界、overlap、id 唯一、空文档。"""

import pytest

from app.services.chunker import chunk_text, make_chunk_id, slugify


class TestSlugify:
    def test_kebab_case_filename(self):
        assert slugify("business-glossary.md") == "business-glossary-md"

    def test_spaces_and_underscores_fold_to_dash(self):
        assert slugify("My Doc_v2.PDF") == "my-doc-v2-pdf"

    def test_non_latin_falls_back_to_document(self):
        assert slugify("口径手册.md") == "md"

    def test_all_symbols_falls_back_to_document(self):
        assert slugify("@@@") == "document"


class TestMakeChunkId:
    def test_format_matches_contract(self):
        assert make_chunk_id("business-glossary.md", 0) == "business-glossary-md-chunk-0000"

    def test_index_padded_to_four_digits(self):
        assert make_chunk_id("a.md", 12) == "a-md-chunk-0012"

    def test_ids_unique_for_long_document(self):
        ids = {make_chunk_id("a.md", i) for i in range(1000)}
        assert len(ids) == 1000


class TestChunkTextEmpty:
    def test_empty_string_returns_no_chunks(self):
        assert chunk_text("") == []

    def test_whitespace_only_returns_no_chunks(self):
        assert chunk_text("   \n\n  \t ") == []


class TestChunkTextBasic:
    def test_short_text_single_chunk(self):
        text = "销售额默认只统计 status = PAID 的订单。"
        chunks = chunk_text(text, chunk_size=500, overlap=50)
        assert chunks == [text]

    def test_chunks_never_empty(self):
        text = "\n\n".join(f"第{i}段内容。" for i in range(50))
        chunks = chunk_text(text, chunk_size=60, overlap=10)
        assert all(chunk.strip() for chunk in chunks)

    def test_chunks_respect_size_limit(self):
        text = "\n\n".join(f"第{i}段" + "内容" * 20 for i in range(50))
        chunks = chunk_text(text, chunk_size=100, overlap=20)
        assert len(chunks) > 1
        assert all(len(chunk) <= 100 for chunk in chunks)

    def test_no_content_loss_across_paragraph_chunks(self):
        paragraphs = [f"第{i:03d}段" + "文" * 30 for i in range(30)]
        chunks = chunk_text("\n\n".join(paragraphs), chunk_size=200, overlap=0)
        for para in paragraphs:
            assert any(para in chunk for chunk in chunks)


class TestChunkTextHardSplit:
    def test_long_paragraph_hard_split_respects_size(self):
        text = "字" * 1200
        chunks = chunk_text(text, chunk_size=500, overlap=50)
        assert [len(c) for c in chunks] == [500, 500, 200]

    def test_hard_split_reconstructs_original(self):
        text = "数" * 999
        chunks = chunk_text(text, chunk_size=400, overlap=0)
        assert "".join(chunks) == text


class TestChunkTextOverlap:
    def test_overlap_carries_tail_context(self):
        # 40 个 40 字符段落，size=200 时每块约 4 段，overlap=50 应带出 1 段上下文
        paragraphs = [f"P{i:02d}" + "段" * 37 for i in range(40)]
        chunks = chunk_text("\n\n".join(paragraphs), chunk_size=200, overlap=50)
        assert len(chunks) > 2
        first_chunk_paras = set(chunks[0].split("\n\n"))
        second_chunk_paras = set(chunks[1].split("\n\n"))
        assert first_chunk_paras & second_chunk_paras, "相邻块应存在重叠段落"

    def test_zero_overlap_no_shared_paragraphs(self):
        paragraphs = [f"P{i:02d}" + "段" * 37 for i in range(40)]
        chunks = chunk_text("\n\n".join(paragraphs), chunk_size=200, overlap=0)
        first_chunk_paras = set(chunks[0].split("\n\n"))
        second_chunk_paras = set(chunks[1].split("\n\n"))
        assert not (first_chunk_paras & second_chunk_paras)


class TestChunkTextParams:
    def test_overlap_must_be_less_than_chunk_size(self):
        with pytest.raises(ValueError):
            chunk_text("内容", chunk_size=100, overlap=100)

    def test_negative_overlap_rejected(self):
        with pytest.raises(ValueError):
            chunk_text("内容", chunk_size=100, overlap=-1)

    def test_zero_chunk_size_rejected(self):
        with pytest.raises(ValueError):
            chunk_text("内容", chunk_size=0, overlap=0)
