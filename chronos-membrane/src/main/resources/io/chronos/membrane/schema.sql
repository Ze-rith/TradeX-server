CREATE TABLE IF NOT EXISTS event_store (
    global_seq        BIGSERIAL    PRIMARY KEY,
    cell_id           INT          NOT NULL,
    aggregate_type    TEXT         NOT NULL,
    aggregate_id      UUID         NOT NULL,
    seq_no            BIGINT       NOT NULL,
    event_id          UUID         NOT NULL UNIQUE,
    event_type        TEXT         NOT NULL,
    event_version     INT          NOT NULL,
    payload           JSONB        NOT NULL,
    valid_time        TIMESTAMPTZ  NOT NULL,
    transaction_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    correction_of     BIGINT       NULL REFERENCES event_store (global_seq),
    UNIQUE (aggregate_id, seq_no)
);

CREATE TABLE IF NOT EXISTS aggregate_snapshot (
    aggregate_id  UUID         PRIMARY KEY,
    seq_no        BIGINT       NOT NULL,
    state         JSONB        NOT NULL,
    taken_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS projection_offset (
    projection_name  TEXT    PRIMARY KEY,
    cell_id          INT     NOT NULL,
    last_global_seq  BIGINT  NOT NULL
);
