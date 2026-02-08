-- Add prompt version/snapshot for chat conversations
ALTER TABLE app.conversations
    ADD COLUMN IF NOT EXISTS prompt_version TEXT;

ALTER TABLE app.conversations
    ADD COLUMN IF NOT EXISTS prompt_snapshot TEXT;

COMMENT ON COLUMN app.conversations.prompt_version IS '對話建立時的 prompt 版本';
COMMENT ON COLUMN app.conversations.prompt_snapshot IS '對話建立時的 prompt 快照';
