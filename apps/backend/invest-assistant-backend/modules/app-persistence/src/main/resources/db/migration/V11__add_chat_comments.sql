-- Add comments for chat tables/columns
COMMENT ON TABLE app.conversations IS '聊天對話主表';
COMMENT ON COLUMN app.conversations.id IS '主鍵';
COMMENT ON COLUMN app.conversations.user_id IS '使用者 ID';
COMMENT ON COLUMN app.conversations.title IS '對話標題（可選）';
COMMENT ON COLUMN app.conversations.summary IS '摘要（v2 以後使用）';
COMMENT ON COLUMN app.conversations.created_at IS '建立時間';
COMMENT ON COLUMN app.conversations.updated_at IS '更新時間';

COMMENT ON TABLE app.conversation_messages IS '聊天訊息表';
COMMENT ON COLUMN app.conversation_messages.id IS '主鍵';
COMMENT ON COLUMN app.conversation_messages.conversation_id IS '對話 ID';
COMMENT ON COLUMN app.conversation_messages.role IS '角色：system/user/assistant';
COMMENT ON COLUMN app.conversation_messages.content IS '訊息內容';
COMMENT ON COLUMN app.conversation_messages.status IS '訊息狀態（PENDING/COMPLETED/FAILED，可選）';
COMMENT ON COLUMN app.conversation_messages.client_message_id IS '客戶端訊息 ID（防重送）';
COMMENT ON COLUMN app.conversation_messages.created_at IS '建立時間';
