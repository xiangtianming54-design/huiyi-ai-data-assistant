CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(128) NULL,
  display_name VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  token_version INT NOT NULL DEFAULT 0,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  status_changed_at DATETIME NULL,
  status_changed_by BIGINT NULL,
  status_reason_code VARCHAR(64) NULL,
  status_remark VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户账号';

CREATE TABLE IF NOT EXISTS workspaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  owner_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作区';

CREATE TABLE IF NOT EXISTS workspace_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  changed_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  changed_by BIGINT NULL,
  reason_code VARCHAR(64) NULL,
  remark VARCHAR(255) NULL,
  UNIQUE KEY uk_workspace_user (workspace_id, user_id),
  INDEX idx_user_status (user_id, status),
  INDEX idx_workspace_status (workspace_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作区成员';

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  replaced_by_token_hash VARCHAR(255) NULL,
  user_agent VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_status_expire (user_id, status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Refresh Token 哈希';
