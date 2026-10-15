"""data_profile 与 chart_recommender 单元测试。"""

from app.services.chart_recommender import recommend_chart
from app.services.data_profile import profile_rows

SALES_ROWS = [
    {"product_name": "无线蓝牙耳机 Pro", "sales_amount": 8970.0},
    {"product_name": "智能手表 S2", "sales_amount": 7192.0},
    {"product_name": "机械键盘 87 键", "sales_amount": 3990.0},
]

TREND_ROWS = [
    {"order_date": "2026-09-01", "daily_amount": 1200.0},
    {"order_date": "2026-09-02", "daily_amount": 1500.0},
    {"order_date": "2026-09-03", "daily_amount": 980.0},
]


class TestProfileRows:
    def test_basic_profile(self):
        result = profile_rows(SALES_ROWS)
        assert result["row_count"] == 3
        assert result["columns"] == ["product_name", "sales_amount"]
        assert result["empty"] is False
        assert result["summary"]["sales_amount"] == {"sum": 20152.0, "max": 8970.0, "min": 3990.0}

    def test_empty_rows(self):
        result = profile_rows([])
        assert result["row_count"] == 0
        assert result["empty"] is True
        assert result["summary"] == {}

    def test_text_columns_not_summarized(self):
        result = profile_rows(SALES_ROWS)
        assert "product_name" not in result["summary"]


class TestRecommendChart:
    def test_category_plus_single_numeric_is_bar(self):
        assert recommend_chart(SALES_ROWS) == "bar"

    def test_date_column_is_line(self):
        assert recommend_chart(TREND_ROWS) == "line"

    def test_ratio_column_is_pie(self):
        rows = [{"channel": "线上商城", "销售占比": 0.45}, {"channel": "线下门店", "销售占比": 0.3}]
        assert recommend_chart(rows) == "pie"

    def test_single_row_is_table(self):
        assert recommend_chart([{"total": 100}]) == "table"

    def test_empty_is_table(self):
        assert recommend_chart([]) == "table"
