CREATE INDEX idx_knowledge_docs_workspace ON knowledge_docs (workspace_id, status);
CREATE INDEX idx_knowledge_docs_created_by ON knowledge_docs (created_by);
CREATE INDEX idx_chat_messages_workspace_user ON chat_messages (workspace_id, user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);
