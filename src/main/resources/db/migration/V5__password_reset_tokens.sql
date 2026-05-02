-- One-time tokens used by the "forgot password" flow.
-- A UUID is generated, SHA-256 hashed, stored here. The raw UUID is sent in a
-- link (https://.../reset-password?token=UUID); only its hash lives in the DB.
-- Even with a stolen DB an attacker can't reset anyone's password.

CREATE TABLE password_reset_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 hex digest (64 chars)
    expires_at TIMESTAMP    NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
