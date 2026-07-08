CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.members (
                                id                          UUID         PRIMARY KEY,
                                name_ciphertext             TEXT         NOT NULL,
                                name_key_version            INTEGER      NOT NULL,
                                birth_date_ciphertext       TEXT         NOT NULL,
                                birth_date_key_version      INTEGER      NOT NULL,
                                phone_number_ciphertext     TEXT         NOT NULL,
                                phone_number_key_version    INTEGER      NOT NULL,
                                phone_number_hash           VARCHAR(64)  NOT NULL UNIQUE,
                                created_at                  TIMESTAMPTZ  NOT NULL,
                                updated_at                  TIMESTAMPTZ  NOT NULL,
                                version                     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_member_members_phone_hash ON member.members (phone_number_hash);