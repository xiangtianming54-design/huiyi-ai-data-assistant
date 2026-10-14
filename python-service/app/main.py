"""FastAPI 应用入口：健康检查 + 分阶段挂载业务路由。"""

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api import document, vector
from app.core.config import settings
from app.services.document_parser import DocumentParseError, UnsupportedFileTypeError
from app.services.embedding import EmbeddingError

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="HuiYi AI Data Assistant - Python Sidecar（文档解析 / 向量检索 / SQL 校验 / 数据画像）",
)

app.include_router(document.router)
app.include_router(vector.router)


@app.exception_handler(UnsupportedFileTypeError)
def handle_unsupported_file_type(_: Request, exc: UnsupportedFileTypeError) -> JSONResponse:
    """不支持的文件类型 → 400。"""
    return JSONResponse(status_code=400, content={"detail": str(exc)})


@app.exception_handler(DocumentParseError)
def handle_parse_error(_: Request, exc: DocumentParseError) -> JSONResponse:
    """解析失败（文件损坏等）→ 422。"""
    return JSONResponse(status_code=422, content={"detail": str(exc)})


@app.exception_handler(EmbeddingError)
def handle_embedding_error(_: Request, exc: EmbeddingError) -> JSONResponse:
    """Embedding 调用失败 → 502（上游服务异常）。"""
    return JSONResponse(status_code=502, content={"detail": str(exc)})


@app.get("/health", summary="健康检查")
def health() -> dict:
    """返回服务存活状态。"""
    return {"status": "ok"}
