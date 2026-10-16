"""API 数据模型：字段与《开发文档》7.2 节契约一致。"""

from pydantic import BaseModel, ConfigDict, Field


class ChunkMetadata(BaseModel):
    """分块元数据：来源文件名、序号与文件类型；允许扩展字段（如意图示例句的 intent）。"""

    model_config = ConfigDict(extra="allow")

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


# ===== 向量库接口（/api/vector/*），契约与《开发文档》7.2 节一致 =====

DEFAULT_COLLECTION = "huiyi_knowledge"


class UpsertRequest(BaseModel):
    """向量写入请求。"""

    collection_name: str = Field(default=DEFAULT_COLLECTION)
    chunks: list[Chunk]


class UpsertResponse(BaseModel):
    """向量写入响应。"""

    success: bool
    upserted_count: int = Field(ge=0)
    collection_name: str


class SearchRequest(BaseModel):
    """向量检索请求。"""

    query: str = Field(min_length=1)
    top_k: int = Field(default=5, ge=1, le=20)
    collection_name: str = Field(default=DEFAULT_COLLECTION)


class SearchHit(BaseModel):
    """检索命中项：id / text / metadata / score（cosine 相似度）。"""

    id: str
    text: str
    metadata: dict
    score: float


class SearchResponse(BaseModel):
    """向量检索响应。"""

    success: bool
    hits: list[SearchHit]


class DeleteRequest(BaseModel):
    """向量删除请求：按来源文件名删除全部分块。"""

    filename: str = Field(min_length=1)
    collection_name: str = Field(default=DEFAULT_COLLECTION)


class DeleteResponse(BaseModel):
    """向量删除响应。"""

    success: bool
    deleted_count: int = Field(ge=0)
    filename: str


# ===== SQL 校验与数据画像接口（/api/sql/*、/api/data/*），契约与《开发文档》7.2 节一致 =====


class SqlValidateRequest(BaseModel):
    """SQL 校验请求。"""

    sql: str = Field(min_length=1)


class SqlValidateResponse(BaseModel):
    """SQL 校验响应：通过时返回修正后的 sql 与涉及表，拒绝时给出原因。"""

    valid: bool
    sql: str | None = None
    tables: list[str] | None = None
    reason: str | None = None


class DataProfileRequest(BaseModel):
    """数据画像请求：SQL 查询结果行集。"""

    rows: list[dict]


class DataProfileResponse(BaseModel):
    """数据画像响应：行数、列、数值摘要、图表推荐、空结果标记。"""

    row_count: int = Field(ge=0)
    columns: list[str]
    summary: dict
    chart_type: str
    empty: bool
