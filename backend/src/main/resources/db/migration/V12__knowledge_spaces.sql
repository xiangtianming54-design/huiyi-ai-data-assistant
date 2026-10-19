CREATE TABLE knowledge_spaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  is_default TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ks_workspace_status (workspace_id, status),
  UNIQUE KEY uk_ks_workspace_name (workspace_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间';

INSERT INTO knowledge_spaces (workspace_id, name, status, is_default, created_by)
SELECT w.id, '默认', 'ACTIVE', 1, COALESCE(w.owner_id, 0)
FROM workspaces w
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_spaces s WHERE s.workspace_id = w.id AND s.is_default = 1
);

UPDATE knowledge_docs d
INNER JOIN knowledge_spaces s ON s.workspace_id = d.workspace_id AND s.is_default = 1
SET d.knowledge_space_id = s.id
WHERE d.knowledge_space_id IS NULL;

ALTER TABLE knowledge_docs
  MODIFY knowledge_space_id BIGINT NOT NULL;
