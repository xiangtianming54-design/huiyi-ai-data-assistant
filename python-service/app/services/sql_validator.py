"""SQL 安全校验（sqlglot 静态分析）：四道防线之静态校验层。

规则：
1. 只允许单条 SQL（多语句拒绝，注释无法绕过）
2. 只允许 SELECT（含 CTE / UNION 等查询组合）
3. 只允许访问白名单表（子查询同样受限）
4. 禁止危险函数与 INTO OUTFILE；无 LIMIT 自动追加 LIMIT 200
"""

import sqlglot
from sqlglot import exp

from app.core.config import settings

# 危险函数黑名单（文件读取 / DoS）
DANGEROUS_FUNCTIONS = {"LOAD_FILE", "SLEEP", "BENCHMARK"}

# 查询类根节点允许类型
ALLOWED_ROOT_TYPES = (exp.Select, exp.Union, exp.With, exp.Subquery, exp.TableSample)


class SqlValidator:
    """SQL 静态校验器。"""

    def __init__(self, allowed_tables: str | None = None, default_limit: int | None = None) -> None:
        tables = allowed_tables or settings.sql_allowed_tables
        self._allowed = {t.strip().lower() for t in tables.split(",") if t.strip()}
        self._default_limit = default_limit or settings.sql_default_limit

    def validate(self, sql: str) -> dict:
        """校验 SQL，返回 {valid, sql, tables} 或 {valid: False, reason}。"""
        sql = (sql or "").strip().rstrip(";")
        if not sql:
            return self._reject("SQL 为空")

        try:
            statements = sqlglot.parse(sql, read="mysql")
        except Exception:
            return self._reject("SQL 语法无法解析")

        statements = [s for s in statements if s is not None]
        if len(statements) != 1:
            return self._reject("仅允许单条 SQL 语句")

        statement = statements[0]

        if not isinstance(statement, ALLOWED_ROOT_TYPES):
            return self._reject("仅允许 SELECT 查询")

        # 写操作节点拦截（防御性：根节点类型之外的嵌套写操作）
        for forbidden in (exp.Insert, exp.Update, exp.Delete, exp.Drop, exp.Alter, exp.Create, exp.TruncateTable):
            if statement.find(forbidden) is not None:
                return self._reject("仅允许 SELECT 查询")

        # INTO OUTFILE / INTO DUMPFILE 拦截
        if statement.find(exp.Into) is not None:
            return self._reject("禁止 INTO OUTFILE 等文件导出操作")

        # 表白名单（排除 CTE 别名）
        tables = self._collect_real_tables(statement)
        outsiders = sorted(t for t in tables if t not in self._allowed)
        if outsiders:
            return self._reject(f"表不在白名单内: {', '.join(outsiders)}")

        # 危险函数拦截
        dangerous = self._collect_dangerous_functions(statement)
        if dangerous:
            return self._reject(f"禁止使用危险函数: {', '.join(sorted(dangerous))}")

        final_sql = statement.sql(dialect="mysql")
        if statement.args.get("limit") is None:
            final_sql = f"{final_sql} LIMIT {self._default_limit}"

        return {"valid": True, "sql": final_sql, "tables": sorted(tables)}

    @staticmethod
    def _collect_real_tables(statement: exp.Expression) -> set[str]:
        """收集真实表名（排除 CTE 别名）。"""
        cte_aliases = {cte.alias.lower() for cte in statement.find_all(exp.CTE)}
        tables = set()
        for table in statement.find_all(exp.Table):
            name = table.name.lower()
            if name and name not in cte_aliases:
                tables.add(name)
        return tables

    @staticmethod
    def _collect_dangerous_functions(statement: exp.Expression) -> set[str]:
        """收集命中的危险函数名（未注册函数在 sqlglot 中为 Anonymous 节点，真名在 this）。"""
        found = set()
        for func in statement.find_all(exp.Func):
            if isinstance(func, exp.Anonymous):
                name = str(func.this).upper()
            else:
                name = func.sql_name().upper()
            if name in DANGEROUS_FUNCTIONS:
                found.add(name)
        return found

    @staticmethod
    def _reject(reason: str) -> dict:
        return {"valid": False, "reason": reason}
