-- ---------------------------------------------------------------------------
-- V1 — Users and authentication sessions
--
-- Security notes:
--   * Passwords are stored only as BCrypt hashes (never plaintext).
--   * user_sessions backs JWT revocation: every issued token carries a session
--     id (jti); a session may be revoked server-side to invalidate its tokens
--     immediately, even before the short-lived access token expires.
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,           -- BCrypt hash only
    github_username VARCHAR(100),
    identity_key_fingerprint VARCHAR(66),          -- hex fingerprint for out-of-band (MITM) verification
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_jti VARCHAR(36) UNIQUE NOT NULL,         -- session id carried by the JWT, used for revocation
    device_info VARCHAR(255),
    ip_address VARCHAR(45),                         -- VARCHAR (not INET) for JPA validate compatibility; holds IPv4/IPv6
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_user_sessions_jti ON user_sessions(token_jti);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
