-- ---------------------------------------------------------------------------
-- V4 — Messages and delivery receipts
--
-- Security notes:
--   * ciphertext holds Signal Protocol ciphertext as an opaque BYTEA. The server
--     stores and forwards it but NEVER decrypts it (blind relay).
--   * status / receipt type are VARCHAR + CHECK (not native enums) to match JPA
--     @Enumerated(STRING) and Hibernate ddl-auto: validate.
--   * expires_at drives disappearing messages; a scheduled job hard-deletes rows
--     past their expiry, and history queries also exclude already-expired rows.
-- ---------------------------------------------------------------------------

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    ciphertext BYTEA NOT NULL,                 -- Signal ciphertext — server never decrypts
    ciphertext_type INTEGER NOT NULL,          -- 1=WhisperMessage, 3=PreKeyWhisperMessage
    reply_to_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'SENT',
    expires_at TIMESTAMPTZ,                     -- disappearing messages; null = never
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_message_status CHECK (status IN ('SENT', 'DELIVERED', 'READ'))
);

CREATE TABLE message_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_receipt_type CHECK (type IN ('DELIVERED', 'READ')),
    UNIQUE(message_id, user_id, type)
);

-- Cursor pagination: newest-first history within a chat, keyed on (created_at, id).
CREATE INDEX idx_messages_chat_created ON messages(chat_id, created_at DESC, id DESC);
-- Partial index for the disappearing-message cleanup sweep.
CREATE INDEX idx_messages_expires_at ON messages(expires_at) WHERE expires_at IS NOT NULL;
