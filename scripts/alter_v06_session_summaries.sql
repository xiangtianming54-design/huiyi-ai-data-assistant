-- 已有 Docker 数据卷增量：可重复执行
CREATE TABLE IF NOT EXISTS chat_session_summaries (
  session_id VARCHAR(64) PRIMARY KEY,
  summary TEXT NOT NULL,
  covered_until_id BIGINT NOT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话历史摘要';
