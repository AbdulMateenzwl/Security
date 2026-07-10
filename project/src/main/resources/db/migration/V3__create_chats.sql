-- ---------------------------------------------------------------------------
-- V3 — Chats and membership
--
-- Design notes:
--   * chat_type / member_role are stored as VARCHAR + CHECK rather than native
--     Postgres ENUM types: this maps cleanly to JPA @Enumerated(STRING) and
--     passes Hibernate's ddl-auto: validate, which is fussy about native enums.
--   * A DIRECT chat is a 1:1 conversation (name is null); a GROUP chat has a
--     name and an admin/member role model. Authorization (membership + admin)
--     is enforced in the service layer.
-- ---------------------------------------------------------------------------

CREATE TABLE chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(10) NOT NULL,
    name VARCHAR(100),                        -- null for DIRECT chats
    avatar_url VARCHAR(500),
    disappearing_message_ttl INTEGER,         -- seconds; null = disappearing off
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_chat_type CHECK (type IN ('DIRECT', 'GROUP'))
);

CREATE TABLE chat_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_member_role CHECK (role IN ('ADMIN', 'MEMBER')),
    UNIQUE(chat_id, user_id)
);

CREATE INDEX idx_chat_members_chat ON chat_members(chat_id);
CREATE INDEX idx_chat_members_user ON chat_members(user_id);
