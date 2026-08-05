--liquibase formatted sql

--changeset gridwords:020-record-live-evaluation
CREATE TABLE record_live_evaluation (
    guild_id BIGINT NOT NULL,
    game_result_id BIGINT NOT NULL,
    game_result_version BIGINT NOT NULL,
    processing_origin VARCHAR(32) NOT NULL,
    evaluation_state VARCHAR(24) NOT NULL,
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
    CONSTRAINT pk_record_live_evaluation
        PRIMARY KEY (guild_id, game_result_id, game_result_version),
    CONSTRAINT ck_record_live_evaluation_guild CHECK (guild_id > 0),
    CONSTRAINT ck_record_live_evaluation_result CHECK (game_result_id > 0 AND game_result_version >= 0),
    CONSTRAINT ck_record_live_evaluation_origin CHECK (
        processing_origin IN (
            'LIVE_SUBMISSION', 'NORMAL_CORRECTION', 'REPLAY',
            'IMPORT', 'BACKFILL', 'ADMINISTRATIVE_REPAIR')),
    CONSTRAINT ck_record_live_evaluation_state CHECK (
        evaluation_state IN (
            'OPEN', 'CLAIMED', 'RETRYABLE', 'SUCCEEDED',
            'FAILED_PERMANENT', 'SUPERSEDED')),
    CONSTRAINT ck_record_live_evaluation_claim CHECK (
        (evaluation_state = 'CLAIMED') =
        (claim_token IS NOT NULL AND claim_until IS NOT NULL)),
    CONSTRAINT ck_record_live_evaluation_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_record_live_evaluation_retry CHECK (
        (evaluation_state = 'RETRYABLE') = (next_retry_at IS NOT NULL)),
    CONSTRAINT ck_record_live_evaluation_completion CHECK (
        (evaluation_state IN ('SUCCEEDED', 'FAILED_PERMANENT', 'SUPERSEDED')) =
        (completed_at IS NOT NULL)),
    CONSTRAINT ck_record_live_evaluation_failure CHECK (
        (
            evaluation_state NOT IN ('RETRYABLE', 'FAILED_PERMANENT')
            AND failure_category IS NULL
            AND safe_error IS NULL
        ) OR (
            evaluation_state = 'RETRYABLE'
            AND failure_category IN ('RETRYABLE', 'UNKNOWN')
            AND safe_error IS NOT NULL
        ) OR (
            evaluation_state = 'FAILED_PERMANENT'
            AND failure_category = 'PERMANENT'
            AND safe_error IS NOT NULL
        )),
    CONSTRAINT ck_record_live_evaluation_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_record_live_evaluation_claimable
    ON record_live_evaluation (evaluation_state, next_retry_at, claim_until, created_at);

CREATE INDEX idx_record_live_evaluation_result
    ON record_live_evaluation (guild_id, game_result_id, game_result_version DESC);

--changeset gridwords:020-record-live-evaluation-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION register_record_live_evaluation_from_submission()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    persisted_version BIGINT;
    requested_origin VARCHAR(32);
    requested_state VARCHAR(24);
    registered_at TIMESTAMPTZ := CURRENT_TIMESTAMP;
BEGIN
    IF NEW.processing_state <> 'RESULT_STORED' OR NEW.game_result_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT version
      INTO persisted_version
      FROM game_result
     WHERE id = NEW.game_result_id;

    IF persisted_version IS NULL THEN
        RAISE EXCEPTION 'linked game result % is missing', NEW.game_result_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM record_live_evaluation
         WHERE guild_id = NEW.guild_id
           AND game_result_id = NEW.game_result_id
           AND game_result_version = persisted_version
    ) THEN
        RETURN NEW;
    END IF;

    requested_origin := CASE
        WHEN EXISTS (
            SELECT 1
              FROM record_live_evaluation
             WHERE guild_id = NEW.guild_id
               AND game_result_id = NEW.game_result_id)
        THEN 'NORMAL_CORRECTION'
        ELSE 'LIVE_SUBMISSION'
    END;

    requested_state := CASE
        WHEN EXISTS (
            SELECT 1
              FROM record_live_evaluation
             WHERE guild_id = NEW.guild_id
               AND game_result_id = NEW.game_result_id
               AND game_result_version > persisted_version)
        THEN 'SUPERSEDED'
        ELSE 'OPEN'
    END;

    INSERT INTO record_live_evaluation (
        guild_id,
        game_result_id,
        game_result_version,
        processing_origin,
        evaluation_state,
        attempt_count,
        completed_at,
        created_at,
        updated_at)
    VALUES (
        NEW.guild_id,
        NEW.game_result_id,
        persisted_version,
        requested_origin,
        requested_state,
        0,
        CASE WHEN requested_state = 'SUPERSEDED' THEN registered_at ELSE NULL END,
        registered_at,
        registered_at)
    ON CONFLICT (guild_id, game_result_id, game_result_version) DO NOTHING;

    IF requested_state = 'OPEN' THEN
        UPDATE record_live_evaluation
           SET evaluation_state = 'SUPERSEDED',
               claim_token = NULL,
               claim_until = NULL,
               next_retry_at = NULL,
               failure_category = NULL,
               safe_error = NULL,
               completed_at = registered_at,
               updated_at = registered_at
         WHERE guild_id = NEW.guild_id
           AND game_result_id = NEW.game_result_id
           AND game_result_version < persisted_version
           AND evaluation_state IN ('OPEN', 'CLAIMED', 'RETRYABLE');
    END IF;

    RETURN NEW;
END;
$$;

--changeset gridwords:020-record-live-evaluation-trigger
CREATE TRIGGER trg_submission_record_live_evaluation
AFTER INSERT OR UPDATE ON submission
FOR EACH ROW
EXECUTE FUNCTION register_record_live_evaluation_from_submission();
