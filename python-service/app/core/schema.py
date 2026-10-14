"""API 数据模型：字段与《开发文档》7.2 节契约一致。"""

from pydantic import BaseModel, Field


class ChunkMetadata(BaseModel):
    """分块元数据：来源文件名、序号与文件类型。"""

    filename: str = Field(description="来源文件名")
    chunk_index: int = Field(ge=0, description="分块序号，从 0 开始")
    source_type: str = Field(description="来源文件类型：md/txt/pdf/docx")


class Chunk(BaseModel):
    """文本分块：稳定唯一 id + 正文 + 元数据。"""

    id: str = Field(description="分块 ID，格式 {slug(filename)}-chunk-{index:04d}")
    text: str = Field(min_length=1, description="分块正文，不允许为空")
    metadata: ChunkMetadata


class ParseResponse(BaseModel):
    """文档解析响应。"""

    filename: str
    chunk_count: int = Field(ge=0)
    chunks: list[Chunk]
