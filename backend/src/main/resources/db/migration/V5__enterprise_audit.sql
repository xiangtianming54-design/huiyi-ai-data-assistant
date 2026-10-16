CREATE TABLE IF NOT EXISTS audit_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NULL,
  workspace_id BIGINT NULL,
  user_id BIGINT NULL,
  username VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NULL,
  resource_id VARCHAR(64) NULL,
  result_status VARCHAR(32) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  detail_json JSON NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_workspace_time (workspace_id, created_at),
  INDEX idx_user_time (user_id, created_at),
  INDEX idx_action_time (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计事件';
