"""FastAPI 应用入口：P1 仅提供健康检查，业务接口按阶段挂载。"""

from fastapi import FastAPI

from app.core.config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="HuiYi AI Data Assistant - Python Sidecar（文档解析 / 向量检索 / SQL 校验 / 数据画像）",
)


@app.get("/health", summary="健康检查")
def health() -> dict:
    """返回服务存活状态。"""
    return {"status": "ok"}
