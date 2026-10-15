"""SQL 安全校验接口。"""

from fastapi import APIRouter

from app.core.schema import SqlValidateRequest, SqlValidateResponse
from app.services.sql_validator import SqlValidator

router = APIRouter(prefix="/api/sql", tags=["sql-guard"])

_validator = SqlValidator()


@router.post("/validate", response_model=SqlValidateResponse, summary="SQL 安全校验")
def validate(request: SqlValidateRequest) -> SqlValidateResponse:
    result = _validator.validate(request.sql)
    return SqlValidateResponse(**result)
