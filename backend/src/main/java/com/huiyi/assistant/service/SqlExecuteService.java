package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    public SqlExecuteService(@Qualifier("readOnlyJdbcTemplate") JdbcTemplate readOnlyJdbcTemplate) {
        this.readOnlyJdbcTemplate = readOnlyJdbcTemplate;
    }

    /**
     * 执行只读查询。
     *
     * @param sql 已通过安全校验的 SQL
     * @param question 原始问题（用于审计）
     * @return 结果行集（最多 500 行）
     */
    public List<Map<String, Object>> execute(String sql, String question) {
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> rows = readOnlyJdbcTemplate.queryForList(sql);
            long cost = System.currentTimeMillis() - start;
            log.info("[SQL审计] question={}, sql={}, costMs={}, rows={}", question, sql, cost, rows.size());
            return rows;
        } catch (DataAccessException e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[SQL审计] 执行失败 question={}, sql={}, costMs={}, error={}",
                    question, sql, cost, e.getMessage());
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "SQL 执行失败，请调整问题后重试");
        }
    }
}
