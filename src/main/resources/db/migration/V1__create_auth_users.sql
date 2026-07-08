CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
                            id                      UUID         PRIMARY KEY,
                            email                   VARCHAR(255) NOT NULL UNIQUE,
                            password_hash           VARCHAR(255) NOT NULL,
                            credential_updated_at   TIMESTAMPTZ  NOT NULL,
                            status                  VARCHAR(20)  NOT NULL,
                            failure_count           INTEGER      NOT NULL DEFAULT 0,
                            last_failure_at         TIMESTAMPTZ  NULL,
                            locked_until            TIMESTAMPTZ  NULL,
                            created_at              TIMESTAMPTZ  NOT NULL,
                            updated_at              TIMESTAMPTZ  NOT NULL,
                            version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_auth_users_email ON auth.users (email);
CREATE INDEX idx_auth_users_status ON auth.users (status);