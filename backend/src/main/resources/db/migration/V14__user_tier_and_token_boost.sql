ALTER TABLE users
  ADD COLUMN user_tier TINYINT NOT NULL DEFAULT 1;

UPDATE users
SET user_tier = 3
WHERE id = 16 AND username = 'blank';

UPDATE users
SET user_tier = 2
WHERE id = 17 AND username = 'limit';

CREATE TABLE token_boost_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  applicant_user_id BIGINT NOT NULL,
  reviewer_user_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  reason TEXT NOT NULL,
  step_name VARCHAR(64) NOT NULL,
  step_tokens INT NOT NULL,
  quota_date DATE NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  INDEX idx_applicant_quota (applicant_user_id, quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提额申请';

ALTER TABLE llm_calls
  ADD INDEX idx_user_time (user_id, created_at);
