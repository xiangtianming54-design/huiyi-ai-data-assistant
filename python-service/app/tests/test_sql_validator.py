"""sql_validator 单元测试：写操作 / 多语句 / 非白名单 / INTO OUTFILE / 注释绕过全部拦截。"""

import pytest

from app.services.sql_validator import SqlValidator

validator = SqlValidator(allowed_tables="sales_orders,products,regions,channels", default_limit=200)


class TestWriteOperationsRejected:
    @pytest.mark.parametrize("sql", [
        "DELETE FROM sales_orders",
        "DELETE FROM sales_orders WHERE 1=1 -- 注释绕过尝试",
        "UPDATE products SET price = 0 WHERE id = 1",
        "DROP TABLE sales_orders",
        "INSERT INTO sales_orders (order_no) VALUES ('X')",
        "TRUNCATE TABLE sales_orders",
        "ALTER TABLE sales_orders ADD COLUMN hack INT",
        "CREATE TABLE hack (id INT)",
    ])
    def test_write_sql_rejected(self, sql):
        result = validator.validate(sql)
        assert result["valid"] is False
        assert result["reason"]


class TestMultiStatementRejected:
    @pytest.mark.parametrize("sql", [
        "SELECT * FROM products; DELETE FROM sales_orders",
        "SELECT 1; DROP TABLE sales_orders --",
        "SELECT * FROM products; SELECT * FROM regions",
    ])
    def test_multi_statement_rejected(self, sql):
        result = validator.validate(sql)
        assert result["valid"] is False


class TestTableWhitelist:
    def test_non_whitelist_table_rejected(self):
        result = validator.validate("SELECT * FROM users")
        assert result["valid"] is False
        assert "白名单" in result["reason"]

    def test_non_whitelist_table_in_subquery_rejected(self):
        result = validator.validate("SELECT * FROM sales_orders WHERE product_id IN (SELECT id FROM users)")
        assert result["valid"] is False

    def test_all_whitelist_tables_accepted(self):
        for table in ["sales_orders", "products", "regions", "channels"]:
            assert validator.validate(f"SELECT * FROM {table}")["valid"] is True

    def test_cte_alias_not_treated_as_table(self):
        sql = "WITH t AS (SELECT * FROM sales_orders) SELECT * FROM t"
        result = validator.validate(sql)
        assert result["valid"] is True


class TestDangerousOperations:
    def test_into_outfile_rejected(self):
        result = validator.validate("SELECT * FROM sales_orders INTO OUTFILE '/tmp/dump.csv'")
        assert result["valid"] is False

    def test_sleep_rejected(self):
        result = validator.validate("SELECT SLEEP(5)")
        assert result["valid"] is False

    def test_load_file_rejected(self):
        result = validator.validate("SELECT LOAD_FILE('/etc/passwd')")
        assert result["valid"] is False


class TestValidQueries:
    def test_plain_select_appends_limit(self):
        result = validator.validate("SELECT * FROM sales_orders")
        assert result["valid"] is True
        assert "LIMIT 200" in result["sql"]
        assert result["tables"] == ["sales_orders"]

    def test_existing_limit_preserved(self):
        result = validator.validate("SELECT * FROM sales_orders LIMIT 10")
        assert result["valid"] is True
        assert "LIMIT 10" in result["sql"]
        assert "LIMIT 200" not in result["sql"]

    def test_join_collects_all_tables(self):
        sql = ("SELECT p.product_name, SUM(o.total_amount) AS amt FROM sales_orders o "
               "JOIN products p ON o.product_id = p.id GROUP BY p.product_name")
        result = validator.validate(sql)
        assert result["valid"] is True
        assert result["tables"] == ["products", "sales_orders"]

    def test_trailing_comment_allowed(self):
        result = validator.validate("SELECT * FROM sales_orders -- 查询所有订单")
        assert result["valid"] is True

    def test_trailing_semicolon_tolerated(self):
        result = validator.validate("SELECT * FROM sales_orders;")
        assert result["valid"] is True

    def test_empty_sql_rejected(self):
        assert validator.validate("")["valid"] is False
        assert validator.validate("   ")["valid"] is False
