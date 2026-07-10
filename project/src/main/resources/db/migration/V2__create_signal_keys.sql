-- ---------------------------------------------------------------------------
-- V2 — Signal Protocol public key material
--
-- Security notes:
--   * The server stores ONLY the public half of every key. Private keys never
--     leave the client. The server is a blind relay: it hands these blobs to
--     peers so they can initiate a Signal session, but it never verifies or
--     decrypts anything — the fetching client verifies the signed pre-key
--     signature against the identity key itself.
--   * One-time pre-keys (OTPKs) MUST be consumed exactly once. Concurrent
--     session initiations are serialised with SELECT ... FOR UPDATE SKIP LOCKED
--     so two peers can never claim the same OTPK.
-- ---------------------------------------------------------------------------

-- Identity keys — one long-lived public identity key per user.
CREATE TABLE identity_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_key BYTEA NOT NULL,            -- only the public half is stored server-side
    registration_id INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Signed pre-keys — rotated periodically; the newest is served in a bundle.
CREATE TABLE signed_pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    public_key BYTEA NOT NULL,
    signature BYTEA NOT NULL,             -- signed by the user's identity key (client-verified)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, key_id)
);

-- One-time pre-keys — each consumed once per session initiation.
CREATE TABLE one_time_pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    public_key BYTEA NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, key_id)
);

-- Partial index: fast lookup of a user's still-available OTPKs.
CREATE INDEX idx_otpk_user_unconsumed ON one_time_pre_keys(user_id) WHERE consumed = FALSE;
CREATE INDEX idx_signed_pre_keys_user ON signed_pre_keys(user_id);
