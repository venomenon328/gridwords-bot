--liquibase formatted sql

--changeset gridwords:021-record-day-close
CREATE TABLE record_day_close (
    guild_id BIGINT NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    game_date DATE NOT NULL,
    close_state VARCHAR(24) NOT NULL,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    failure_category VARCHAR(16),
    safe_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_record_day_close PRIMARY KEY (guild_id, definition_version, game_date),
    CONSTRAINT ck_record_day_close_guild CHECK (guild_id > 0),
    CONSTRAINT ck_record_day_close_state CHECK (close_state IN ('OPEN','CLAIMED','RETRYABLE','SUCCEEDED','FAILED_PERMANENT')),
    CONSTRAINT ck_record_day_close_claim CHECK ((close_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL)),
    CONSTRAINT ck_record_day_close_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_record_day_close_retry CHECK ((close_state = 'RETRYABLE') = (next_retry_at IS NOT NULL)),
    CONSTRAINT ck_record_day_close_completion CHECK ((close_state IN ('SUCCEEDED','FAILED_PERMANENT')) = (completed_at IS NOT NULL)),
    CONSTRAINT ck_record_day_close_failure CHECK (
        (close_state NOT IN ('RETRYABLE','FAILED_PERMANENT') AND failure_category IS NULL AND safe_error IS NULL)
        OR (close_state = 'RETRYABLE' AND failure_category IN ('RETRYABLE','UNKNOWN') AND safe_error IS NOT NULL)
        OR (close_state = 'FAILED_PERMANENT' AND failure_category = 'PERMANENT' AND safe_error IS NOT NULL)),
    CONSTRAINT ck_record_day_close_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_record_day_close_success ON record_day_close (guild_id, definition_version, game_date DESC)
    WHERE close_state = 'SUCCEEDED';
