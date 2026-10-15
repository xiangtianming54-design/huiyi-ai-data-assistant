package com.huiyi.assistant.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL 执行配置：独立的只读数据源（四道防线之数据库层只读约束）。
 *
 * @author huiyi
 */
@Configuration
public class SqlExecuteConfig {

    /** 查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /** 最大返回行数 */
    private static final int MAX_ROWS = 500;

    /**
     * 只读数据源（huiyi_ro 账号，仅有 SELECT 权限）。
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource readOnlyDataSource(SqlProperties sqlProperties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(sqlProperties.getRoUrl());
        dataSource.setUsername(sqlProperties.getRoUsername());
        dataSource.setPassword(sqlProperties.getRoPassword());
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(4);
        dataSource.setPoolName("huiyi-read-only-pool");
        return dataSource;
    }

    /**
     * 只读查询 JdbcTemplate：5s 超时 + 500 行上限。
     */
    @Bean
    public JdbcTemplate readOnlyJdbcTemplate(HikariDataSource readOnlyDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(readOnlyDataSource);
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        jdbcTemplate.setMaxRows(MAX_ROWS);
        return jdbcTemplate;
    }
}
