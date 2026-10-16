package com.huiyi.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 已有库增量建会话摘要表（可重复执行）。
 *
 * @author huiyi
 */
@Slf4j
@Component
public class SessionSummarySchemaInitializer implements ApplicationRunner {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS chat_session_summaries (
              session_id VARCHAR(64) PRIMARY KEY,
              summary TEXT NOT NULL,
              covered_until_id BIGINT NOT NULL,
              updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private final DataSource dataSource;

    public SessionSummarySchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(DDL);
            log.info("已确保 chat_session_summaries 表存在");
        } catch (Exception e) {
            log.warn("创建 chat_session_summaries 失败（将在改写时降级）: {}", e.getMessage());
        }
    }
}
