"""图表推荐规则：按结果集特征推荐 line / bar / pie / table。

规则（按优先级）：
1. 含日期/时间列 → line（趋势）
2. 类别列 + 单数值列 → bar（对比）
3. 列名含占比类关键词 → pie（占比）
4. 其他 / 单行结果 → table
"""

import re

import pandas as pd

# 日期值特征：YYYY-MM-DD 或 YYYY-MM-DD HH:MM:SS
_DATE_VALUE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?$")

# 占比类列名关键词
_RATIO_KEYWORDS = ("占比", "比率", "比例", "ratio", "percent", "pct")


def recommend_chart(rows: list[dict]) -> str:
    """根据结果集推荐图表类型：line / bar / pie / table。"""
    if not rows:
        return "table"

    frame = pd.DataFrame(rows)
    if len(frame) <= 1:
        return "table"

    columns = [str(c) for c in frame.columns]
    numeric_columns = list(frame.select_dtypes(include="number").columns)

    # 占比类列名优先（如「销售占比」）
    if any(any(kw in col.lower() for kw in _RATIO_KEYWORDS) for col in columns):
        return "pie"

    # 含日期值列 → 趋势折线
    if any(_is_date_column(frame[col]) for col in frame.columns):
        return "line"

    # 类别列 + 恰好一个数值列 → 柱状对比
    if len(numeric_columns) == 1 and len(columns) >= 2:
        return "bar"

    return "table"


def _is_date_column(series: pd.Series) -> bool:
    """抽样判断列值是否形如日期。"""
    sample = series.dropna().head(5)
    if sample.empty:
        return False
    return all(bool(_DATE_VALUE_PATTERN.match(str(v))) for v in sample)
