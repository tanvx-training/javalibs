-- Reference schema for the javalibs-datahub Transactional Outbox and consumer
-- idempotency support (PostgreSQL dialect).
--
-- Copy these statements into a Flyway migration of your service, e.g.
--   src/main/resources/db/migration/V20260713__datahub_outbox.sql
-- The library never executes DDL itself.

CREATE TABLE datahub_outbox_event (
    id             VARCHAR(64)  PRIMARY KEY,
    topic          VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    source         VARCHAR(255),
    correlation_id VARCHAR(128),
    partition_key  VARCHAR(255),
    headers_json   TEXT,
    payload_json   TEXT         NOT NULL,
    payload_type   VARCHAR(512),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_datahub_outbox_pending
    ON datahub_outbox_event (status, created_at);

CREATE TABLE datahub_processed_event (
    handler      VARCHAR(255) NOT NULL,
    event_id     VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (handler, event_id)
);

CREATE INDEX idx_datahub_processed_cleanup
    ON datahub_processed_event (processed_at);
