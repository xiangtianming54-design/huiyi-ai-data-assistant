package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SqlExecuteService 测试：H2 内存库（MySQL 兼容模式）。
 *
 * @author huiyi
 */
class SqlExecuteServiceTest {

    private HikariDataSource dataSource;

    private SqlExecuteService sqlExecuteService;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS demo_orders");
        jdbcTemplate.execute("CREATE TABLE demo_orders (id INT PRIMARY KEY, amount DECIMAL(10,2))");
        jdbcTemplate.execute("INSERT INTO demo_orders (id, amount) VALUES (1, 100.50), (2, 200.00)");

        JdbcTemplate readOnlyTemplate = new JdbcTemplate(dataSource);
        readOnlyTemplate.setQueryTimeout(5);
        readOnlyTemplate.setMaxRows(500);
        com.huiyi.assistant.observability.ObservabilityProperties properties =
                new com.huiyi.assistant.observability.ObservabilityProperties();
        properties.setMetricsEnabled(false);
        sqlExecuteService = new SqlExecuteService(readOnlyTemplate,
                new com.huiyi.assistant.observability.ObservabilityMetrics(
                        properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void executeReturnsRows() {
        List<Map<String, Object>> rows = sqlExecuteService.execute(
                "SELECT * FROM demo_orders ORDER BY id", "测试查询");

        assertEquals(2, rows.size());
        assertEquals(100.50, ((Number) rows.get(0).get("AMOUNT")).doubleValue(), 0.001);
    }

    @Test
    void badSqlThrowsBizException() {
        assertThrows(BizException.class,
                () -> sqlExecuteService.execute("SELECT * FROM not_exist_table", "坏查询"));
    }
}
