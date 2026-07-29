--liquibase formatted sql

--changeset gridwords:007-canonical-delivery-attempts
CREATE TABLE canonical_delivery_attempt (
    claim_token UUID PRIMARY KEY,
    game_result_id BIGINT NOT NULL REFERENCES game_result(id),
    source_message_id BIGINT NOT NULL REFERENCES submission(source_message_id),
    refresh_generation BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_canonical_delivery_attempt_result_generation
    ON canonical_delivery_attempt (game_result_id, refresh_generation);