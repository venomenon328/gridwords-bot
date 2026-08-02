--liquibase formatted sql

--changeset gridwords:014-daily-channel-cleanup
CREATE TABLE canonical_result_retirement (
    game_result_id BIGINT PRIMARY KEY REFERENCES game_result(id),
    retirement_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    retry_after TIMESTAMPTZ,
    last_error VARCHAR(512),
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_canonical_result_retirement_state
        CHECK (retirement_state IN ('ACTIVE','CLAIMED','RETRYABLE','RETIRED','PERMANENT')),
    CONSTRAINT ck_canonical_result_retirement_claim
        CHECK ((retirement_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL)),
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

