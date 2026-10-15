"""数据画像接口：画像 + 图表推荐。"""

from fastapi import APIRouter

from app.core.schema import DataProfileRequest, DataProfileResponse
from app.services.chart_recommender import recommend_chart
from app.services.data_profile import profile_rows

router = APIRouter(prefix="/api/data", tags=["analysis"])


@router.post("/profile", response_model=DataProfileResponse, summary="数据画像与图表推荐")
def profile(request: DataProfileRequest) -> DataProfileResponse:
    result = profile_rows(request.rows)
    return DataProfileResponse(
        row_count=result["row_count"],
        columns=result["columns"],
        summary=result["summary"],
        chart_type=recommend_chart(request.rows),
        empty=result["empty"],
    )
