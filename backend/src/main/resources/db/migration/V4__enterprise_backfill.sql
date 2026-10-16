-- Placeholder user cannot login (ARCHIVED + non-BCrypt sentinel hash).
INSERT INTO users (username, password_hash, display_name, status)
SELECT 'migrated', 'DISABLED-MIGRATED-USER-NOT-A-PASSWORD-HASH', 'Migrated Data', 'ARCHIVED'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'migrated');

INSERT INTO workspaces (code, name, owner_id, status)
SELECT 'default', 'Default Workspace', u.id, 'ACTIVE'
FROM users u
WHERE u.username = 'migrated'
  AND NOT EXISTS (SELECT 1 FROM workspaces WHERE code = 'default');

INSERT INTO workspace_members (workspace_id, user_id, role_code, status)
SELECT w.id, u.id, 'OWNER', 'ARCHIVED'
FROM workspaces w
JOIN users u ON u.username = 'migrated'
WHERE w.code = 'default'
  AND NOT EXISTS (
    SELECT 1 FROM workspace_members m
    WHERE m.workspace_id = w.id AND m.user_id = u.id
  );

UPDATE knowledge_docs
SET workspace_id = (SELECT id FROM workspaces WHERE code = 'default')
WHERE workspace_id IS NULL;

UPDATE knowledge_docs
SET created_by = (SELECT id FROM users WHERE username = 'migrated')
WHERE created_by IS NULL;

UPDATE knowledge_docs
SET status = 'READY'
WHERE status IS NULL OR status IN ('', 'ACTIVE', 'PARSED');

UPDATE chat_messages
SET workspace_id = (SELECT id FROM workspaces WHERE code = 'default')
WHERE workspace_id IS NULL;

UPDATE chat_messages
SET user_id = (SELECT id FROM users WHERE username = 'migrated')
WHERE user_id IS NULL;

INSERT INTO chat_sessions (session_id, workspace_id, user_id, status, last_message_at, created_at)
SELECT m.session_id,
       (SELECT id FROM workspaces WHERE code = 'default'),
       (SELECT id FROM users WHERE username = 'migrated'),
       'ACTIVE',
       MAX(m.created_at),
       MIN(m.created_at)
FROM chat_messages m
WHERE m.session_id IS NOT NULL AND m.session_id <> ''
GROUP BY m.session_id
ON DUPLICATE KEY UPDATE last_message_at = VALUES(last_message_at);

ALTER TABLE knowledge_docs
  MODIFY COLUMN workspace_id BIGINT NOT NULL,
  MODIFY COLUMN created_by BIGINT NOT NULL;

ALTER TABLE chat_messages
  MODIFY COLUMN workspace_id BIGINT NOT NULL,
  MODIFY COLUMN user_id BIGINT NOT NULL;
