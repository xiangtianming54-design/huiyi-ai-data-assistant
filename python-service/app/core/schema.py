"""API 数据模型：字段与《开发文档》7.2 节契约一致。"""

from pydantic import BaseModel, ConfigDict, Field, model_serializer, model_validator


class ChunkMetadata(BaseModel):
    """分块元数据：来源文件名、序号与文件类型；允许扩展字段（如意图示例句的 intent）。"""

    model_config = ConfigDict(extra="allow")

    filename: str = Field(description="来源文件名")
    chunk_index: int = Field(ge=0, description="分块序号，从 0 开始")
    source_type: str = Field(description="来源文件类型：md/txt/pdf/docx")
    workspace_id: str | None = Field(default=None, description="工作区 ID，知识库分块必填")
    doc_id: str | None = Field(default=None, description="知识文档 ID")

    @model_serializer(mode="wrap")
    def _omit_empty_scope_fields(self, handler):
        data = handler(self)
        return {key: value for key, value in data.items() if value is not None}


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
    workspace_id: int | None = None
    chunks: list[Chunk]

    @model_validator(mode="after")
    def knowledge_chunks_must_share_workspace(self):
        if self.collection_name != DEFAULT_COLLECTION:
            return self
        if self.workspace_id is None:
            raise ValueError("workspace_id 必填")
        workspace = str(self.workspace_id)
        for chunk in self.chunks:
            if not chunk.metadata.workspace_id:
                raise ValueError("每个 chunk.metadata.workspace_id 必填")
            if str(chunk.metadata.workspace_id) != workspace:
                raise ValueError("不允许混合多个 workspace")
            if not chunk.metadata.doc_id:
                raise ValueError("每个 chunk.metadata.doc_id 必填")
        return self


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
    workspace_id: int | None = None

    @model_validator(mode="after")
    def knowledge_search_requires_workspace(self):
        if self.collection_name == DEFAULT_COLLECTION and self.workspace_id is None:
            raise ValueError("workspace_id 必填")
        return self


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
    """向量删除请求：知识库按 workspace + doc_id / chunk_ids，禁止仅按文件名。"""

    collection_name: str = Field(default=DEFAULT_COLLECTION)
    workspace_id: int | None = None
    doc_id: int | None = None
    chunk_ids: list[str] | None = None
    filename: str | None = None

    @model_validator(mode="after")
    def knowledge_delete_must_be_scoped(self):
        if self.collection_name != DEFAULT_COLLECTION:
            return self
        if self.filename:
            raise ValueError("禁止仅按文件名删除，请使用 workspace_id + doc_id 或 chunk_ids")
        if self.workspace_id is None:
            raise ValueError("workspace_id 必填")
        if self.doc_id is None and not self.chunk_ids:
            raise ValueError("必须提供 doc_id 或 chunk_ids")
        return self


class DeleteResponse(BaseModel):
    """向量删除响应。"""

    success: bool
    deleted_count: int = Field(ge=0)
    filename: str | None = None


class SimilarityRequest(BaseModel):
    """文本相似度请求：两条文本做 cosine。"""

    text_a: str = Field(min_length=1)
    text_b: str = Field(min_length=1)


class SimilarityResponse(BaseModel):
    """文本相似度响应：score 为 cosine，范围 [0, 1]。"""

    success: bool
    score: float


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
