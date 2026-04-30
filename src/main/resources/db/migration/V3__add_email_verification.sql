-- Company verification: new companies must verify via email before using the platform
ALTER TABLE companies
    ADD COLUMN verification_status       VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN verification_token        VARCHAR(255),
    ADD COLUMN verification_token_expiry TIMESTAMP;

-- Existing companies are considered already verified (registered before this feature)
UPDATE companies SET verification_status = 'VERIFIED';

CREATE UNIQUE INDEX idx_companies_verification_token
    ON companies (verification_token) WHERE verification_token IS NOT NULL;
