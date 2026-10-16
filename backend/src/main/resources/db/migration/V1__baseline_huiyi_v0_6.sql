-- v0.6 application tables. IF NOT EXISTS keeps existing demo volumes intact.
CREATE TABLE IF NOT EXISTS knowledge_docs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_name VARCHAR(256) NOT NULL,
  file_type VARCHAR(32),
  file_size BIGINT,
  chunk_count INT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'UPLOADED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档表';

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT,
  intent VARCHAR(32),
  sql_text TEXT,
  metadata_json TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话消息表';

CREATE TABLE IF NOT EXISTS chat_session_summaries (
  session_id VARCHAR(64) PRIMARY KEY,
  summary TEXT NOT NULL,
  covered_until_id BIGINT NOT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话历史摘要';
