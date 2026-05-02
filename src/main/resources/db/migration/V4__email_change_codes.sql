-- One-time challenge for confirming an email change. Two-factor:
--   token     — UUID embedded in a link sent to the OLD email (proof you control it).
--   code_hash — BCrypt of a 6-digit code sent to the NEW email (proof you own it).
-- Both must be presented together to apply the change. Lives ~1 hour.
--
-- Token is stored plaintext (it's a 122-bit UUID — brute-force-resistant).
-- Code IS hashed because 6 digits has only ~20 bits of entropy.

CREATE TABLE email_change_codes (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    code_hash  VARCHAR(255) NOT NULL,
    new_email  VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_change_codes_user_id ON email_change_codes(user_id);
CREATE INDEX idx_email_change_codes_token   ON email_change_codes(token);
