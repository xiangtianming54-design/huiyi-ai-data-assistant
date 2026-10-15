package com.huiyi.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Text-to-SQL 配置项（app.sql.*）。
 *
 * @author huiyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sql")
public class SqlProperties {

    /** SQL 白名单表 */
    private List<String> allowedTables = List.of("sales_orders", "products", "regions", "channels");

    /** 只读数据源 JDBC URL */
    private String roUrl;

    /** 只读账号用户名 */
    private String roUsername;

    /** 只读账号密码 */
    private String roPassword;
}
