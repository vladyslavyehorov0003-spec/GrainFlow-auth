CREATE TABLE companies
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    address    VARCHAR(500),
    phone      VARCHAR(50),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE users
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    employee_id VARCHAR(50)  UNIQUE,
    pin         VARCHAR(255),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    token      TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_company_id ON users (company_id);
CREATE INDEX idx_users_employee_id ON users (employee_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
