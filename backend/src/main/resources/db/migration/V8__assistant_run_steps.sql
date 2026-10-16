CREATE TABLE IF NOT EXISTS assistant_run_steps (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64),
  model VARCHAR(128),
  input_summary TEXT,
  output_summary TEXT,
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  total_tokens INT DEFAULT 0,
  latency_ms INT,
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_run_id (run_id),
  INDEX idx_step_type_status (step_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '请求级步骤 Trace';
