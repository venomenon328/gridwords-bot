--liquibase formatted sql

--changeset gridwords:014-daily-channel-cleanup
CREATE TABLE canonical_result_retirement (
    game_result_id BIGINT PRIMARY KEY REFERENCES game_result(id),
    retirement_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    publication_claim_token UUID,
    publication_claim_until TIMESTAMPTZ,
    retry_after TIMESTAMPTZ,
    last_error VARCHAR(512),
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_canonical_result_retirement_state
        CHECK (retirement_state IN ('ACTIVE','CLAIMED','RETRYABLE','RETIRED','PERMANENT')),
    CONSTRAINT ck_canonical_result_retirement_claim
        CHECK ((retirement_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL)),
    CONSTRAINT ck_canonical_result_publication_claim
        CHECK ((publication_claim_token IS NULL) = (publication_claim_until IS NULL)),
    CONSTRAINT ck_canonical_result_retirement_completed
        CHECK (retirement_state <> 'RETIRED' OR retired_at IS NOT NULL)
);

CREATE TABLE reminder_message_retirement (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    game_date DATE NOT NULL,
    reminder_stage INTEGER NOT NULL,
    retirement_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    retry_after TIMESTAMPTZ,
    last_error VARCHAR(512),
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reminder_message_retirement
        PRIMARY KEY (guild_id, channel_id, game_date, reminder_stage),
    CONSTRAINT fk_reminder_message_retirement_delivery
        FOREIGN KEY (guild_id, channel_id, game_date, reminder_stage)
        REFERENCES reminder_delivery (guild_id, channel_id, game_date, reminder_stage),
    CONSTRAINT ck_reminder_message_retirement_state
        CHECK (retirement_state IN ('ACTIVE','CLAIMED','RETRYABLE','RETIRED','PERMANENT')),
    CONSTRAINT ck_reminder_message_retirement_claim
        CHECK ((retirement_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL)),
    CONSTRAINT ck_reminder_message_retirement_completed
        CHECK (retirement_state <> 'RETIRED' OR retired_at IS NOT NULL)
);

CREATE INDEX ix_canonical_result_retirement_open
    ON canonical_result_retirement (retirement_state, retry_after, claim_until);
CREATE INDEX ix_reminder_message_retirement_open
    ON reminder_message_retirement (retirement_state, retry_after, claim_until);

-- Both publication and retirement serialize on the same canonical_result_retirement row. The trigger mirrors the
-- current publication lease into that row while holding its row lock. Retirement's ON CONFLICT update takes the same
-- lock and can only win when no unexpired publication lease exists. Thus exactly one intent wins every race.
CREATE FUNCTION guard_canonical_publication_against_retirement()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    current_retirement_state VARCHAR(20);
BEGIN
    INSERT INTO canonical_result_retirement (
        game_result_id, retirement_state, created_at, updated_at)
    VALUES (NEW.id, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (game_result_id) DO NOTHING;

    SELECT retirement_state
    INTO current_retirement_state
    FROM canonical_result_retirement
    WHERE game_result_id = NEW.id
    FOR UPDATE;

    IF current_retirement_state <> 'ACTIVE' THEN
        RETURN NULL;
    END IF;

    UPDATE canonical_result_retirement
    SET publication_claim_token = NEW.canonical_publish_claim_token,
        publication_claim_until = CASE
            WHEN NEW.canonical_publish_claim_token IS NULL THEN NULL
            ELSE NEW.canonical_publish_lease_until
        END,
        updated_at = CURRENT_TIMESTAMP
    WHERE game_result_id = NEW.id;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_canonical_publication_against_retirement
BEFORE UPDATE OF canonical_publish_claim_token ON game_result
FOR EACH ROW
EXECUTE FUNCTION guard_canonical_publication_against_retirement();
