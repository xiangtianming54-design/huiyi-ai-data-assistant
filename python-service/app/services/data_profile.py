"""数据画像：SQL 查询结果的行数、列类型与数值摘要（pandas）。"""

import pandas as pd


def profile_rows(rows: list[dict]) -> dict:
    """对查询结果做画像。

    :param rows: 查询结果行集（dict 列表）
    :return: {row_count, columns, summary, empty}
             summary 中每个数值列给出 sum/max/min
    """
    if not rows:
        return {"row_count": 0, "columns": [], "summary": {}, "empty": True}

    frame = pd.DataFrame(rows)
    summary: dict[str, dict[str, float]] = {}
    for column in frame.select_dtypes(include="number").columns:
        series = frame[column].dropna()
        if series.empty:
            continue
        summary[column] = {
            "sum": round(float(series.sum()), 2),
            "max": round(float(series.max()), 2),
            "min": round(float(series.min()), 2),
        }

    return {
        "row_count": int(len(frame)),
        "columns": [str(c) for c in frame.columns],
        "summary": summary,
        "empty": False,
    }
