package com.huiyi.assistant.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 数据源配置：多数据源场景下显式声明主数据源（MyBatis-Plus 业务读写使用）。
 * 另一个只读数据源见 {@link SqlExecuteConfig}，仅供 Text-to-SQL 执行。
 *
 * @author huiyi
 */
@Configuration
public class DataSourceConfig {

    /**
     * 主数据源（@Primary，注入歧义时优先选中）。
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
