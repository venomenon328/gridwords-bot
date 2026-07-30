--liquibase formatted sql

--changeset gridwords:011-daily-status-reminders
ALTER TABLE daily_status_message ADD COLUMN delivery_state VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE daily_status_message ADD COLUMN claim_token UUID;
ALTER TABLE daily_status_message ADD COLUMN claim_until TIMESTAMPTZ;
ALTER TABLE daily_status_message ADD COLUMN content_fingerprint VARCHAR(128);
ALTER TABLE daily_status_message ADD COLUMN retry_after TIMESTAMPTZ;
ALTER TABLE daily_status_message ADD COLUMN last_error VARCHAR(512);
ALTER TABLE daily_status_message ADD CONSTRAINT ck_daily_status_delivery_state
    CHECK (delivery_state IN ('PENDING','CLAIMED','DELIVERED','RETRYABLE','PERMANENT'));
ALTER TABLE daily_status_message ADD CONSTRAINT ck_daily_status_claim
    CHECK ((delivery_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL));

CREATE TABLE reminder_delivery (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    game_date DATE NOT NULL,
    reminder_stage INTEGER NOT NULL,
    scheduled_time TIME NOT NULL,
    delivery_state VARCHAR(20) NOT NULL,
    discord_message_id BIGINT,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    retry_after TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reminder_delivery PRIMARY KEY (guild_id, channel_id, game_date, reminder_stage),
    CONSTRAINT ck_reminder_delivery_guild_id CHECK (guild_id > 0),
    CONSTRAINT ck_reminder_delivery_channel_id CHECK (channel_id > 0),
    CONSTRAINT ck_reminder_delivery_stage CHECK (reminder_stage IN (1, 2)),
    CONSTRAINT ck_reminder_delivery_message_id CHECK (discord_message_id IS NULL OR discord_message_id > 0),
    CONSTRAINT ck_reminder_delivery_state CHECK
        (delivery_state IN ('PENDING','CLAIMED','SENT','NO_CANDIDATES','SUPERSEDED','EXPIRED','RETRYABLE','PERMANENT')),
    CONSTRAINT ck_reminder_delivery_claim
        CHECK ((delivery_state = 'CLAIMED') = (claim_token IS NOT NULL AND claim_until IS NOT NULL))
);