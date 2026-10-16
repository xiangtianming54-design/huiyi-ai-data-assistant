package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行服务：只读数据源 + 5s 超时 + 500 行上限 + 审计日志。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class SqlExecuteService {

    private final JdbcTemplate readOnlyJdbcTemplate;
    private final ObservabilityMetrics observabilityMetrics;

    public SqlExecuteService(@Qualifier("readOnlyJdbcTemplate") JdbcTemplate readOnlyJdbcTemplate,
                             ObservabilityMetrics observabilityMetrics) {
        this.readOnlyJdbcTemplate = readOnlyJdbcTemplate;
        this.observabilityMetrics = observabilityMetrics;
    }

    /**
     * 执行只读查询。
     *
     * @param sql 已通过安全校验的 SQL
     * @param question 原始问题（用于审计）
     * @return 结果行集（最多 500 行）
     */
    public List<Map<String, Object>> execute(String sql, String question) {
        long start = System.nanoTime();
        try {
            List<Map<String, Object>> rows = readOnlyJdbcTemplate.queryForList(sql);
            Duration latency = Duration.ofNanos(System.nanoTime() - start);
            boolean limited = rows.size() >= 500;
            observabilityMetrics.sqlExecute(true, false, limited, latency);
            log.info("[SQL审计] question={}, sql={}, costMs={}, rows={}", question, sql, latency.toMillis(), rows.size());
            return rows;
        } catch (DataAccessException e) {
            Duration latency = Duration.ofNanos(System.nanoTime() - start);
            boolean timeout = e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout");
            observabilityMetrics.sqlExecute(false, timeout, false, latency);
            log.warn("[SQL审计] 执行失败 question={}, sql={}, costMs={}, error={}",
                    question, sql, latency.toMillis(), e.getMessage());
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "SQL 执行失败，请调整问题后重试");
        }
    }
}
