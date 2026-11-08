"""应用配置：全部通过环境变量注入，禁止硬编码密钥。"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Sidecar 服务配置项，环境变量优先级高于默认值。"""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # 服务基础信息
    app_name: str = "huiyi-python-service"
    app_version: str = "0.1.0"
    port: int = 8001

    # Chroma 向量库持久化目录（P3 启用）
    chroma_persist_dir: str = "./chroma_store"
    chroma_collection: str = "huiyi_knowledge"

    # 文档分块参数（P2 启用）
    chunk_size: int = 500
    chunk_overlap: int = 50

    # LLM OpenAI 兼容 API。向量可单独指定服务商，留空则沿用对话的地址和密钥。
    llm_base_url: str = ""
    llm_api_key: str = ""
    llm_model: str = ""
    llm_embedding_model: str = ""
    embedding_base_url: str = ""
    embedding_api_key: str = ""

    # SQL 安全校验：表白名单（逗号分隔）与自动追加的 LIMIT
    sql_allowed_tables: str = "sales_orders,products,regions,channels"
    sql_default_limit: int = 200


settings = Settings()
