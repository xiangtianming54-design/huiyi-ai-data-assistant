-- Nullable first; V4 backfills then tightens NOT NULL.
ALTER TABLE knowledge_docs
  ADD COLUMN workspace_id BIGINT NULL,
  ADD COLUMN knowledge_space_id BIGINT NULL,
  ADD COLUMN created_by BIGINT NULL;

ALTER TABLE chat_messages
  ADD COLUMN workspace_id BIGINT NULL,
  ADD COLUMN user_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS chat_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_message_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_workspace_user_time (workspace_id, user_id, last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话归属';
