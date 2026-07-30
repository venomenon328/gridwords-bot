--liquibase formatted sql

--changeset gridwords:011-daily-status-reminders
CREATE TABLE daily_status_message (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    game_date DATE NOT NULL,
    discord_message_id BIGINT,
    delivery_state VARCHAR(20) NOT NULL,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    content_fingerprint VARCHAR(128),
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_daily_status_message PRIMARY KEY (guild_id, channel_id, game_date)
);

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
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reminder_delivery PRIMARY KEY (guild_id, channel_id, game_date, reminder_stage),
    CONSTRAINT ck_reminder_delivery_stage CHECK (reminder_stage IN (1, 2))
);
