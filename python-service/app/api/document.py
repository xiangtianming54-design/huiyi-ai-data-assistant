"""文档解析接口：multipart 上传 → 解析 → 分块 → 返回结构化结果。"""

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.core.config import settings
from app.core.schema import Chunk, ChunkMetadata, ParseResponse
from app.services.chunker import chunk_text, make_chunk_id
from app.services.document_parser import parse_document, source_type_of

router = APIRouter(prefix="/api/documents", tags=["document"])

# 单文件大小上限 10MB
MAX_FILE_SIZE = 10 * 1024 * 1024


@router.post("/parse", response_model=ParseResponse, summary="解析文档并分块")
async def parse(
    file: UploadFile = File(description="待解析文件，支持 md/txt/pdf/docx"),
    chunk_size: int | None = Form(default=None, description="单块最大字符数"),
    chunk_overlap: int | None = Form(default=None, description="块间重叠字符数"),
) -> ParseResponse:
    chunk_size = chunk_size or settings.chunk_size
    chunk_overlap = chunk_overlap if chunk_overlap is not None else settings.chunk_overlap

    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="文件内容为空")
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="文件超过 10MB 大小限制")

    text = parse_document(file.filename, content)
    try:
        pieces = chunk_text(text, chunk_size=chunk_size, overlap=chunk_overlap)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    source_type = source_type_of(file.filename)
    chunks = [
        Chunk(
            id=make_chunk_id(file.filename, index),
            text=piece,
            metadata=ChunkMetadata(
                filename=file.filename,
                chunk_index=index,
                source_type=source_type,
            ),
        )
        for index, piece in enumerate(pieces)
    ]
    return ParseResponse(filename=file.filename, chunk_count=len(chunks), chunks=chunks)
