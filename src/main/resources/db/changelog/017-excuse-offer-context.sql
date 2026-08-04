--liquibase formatted sql

--changeset gridwords:017-excuse-offer-context
CREATE TABLE game_result_excuse_offer_context (
    game_result_id BIGINT PRIMARY KEY REFERENCES game_result_excuse(game_result_id) ON DELETE CASCADE,
    original_received_at TIMESTAMPTZ NOT NULL,
    comparison_game_type VARCHAR(16) NOT NULL,
    compared_result_count INTEGER NOT NULL,
    all_compared_results_solved BOOLEAN NOT NULL,
    highest_solved_attempts INTEGER,
    longest_duration_seconds BIGINT NOT NULL,
    context_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_game_result_excuse_offer_context_game_type
        CHECK (comparison_game_type IN ('GRIDWORDS', 'QUADWORDS')),
    CONSTRAINT ck_game_result_excuse_offer_context_count
        CHECK (compared_result_count >= 0),
    CONSTRAINT ck_game_result_excuse_offer_context_snapshot
        CHECK ((compared_result_count = 0
                    AND all_compared_results_solved = FALSE
                    AND highest_solved_attempts IS NULL
                    AND longest_duration_seconds = 0)
            OR (compared_result_count > 0
                    AND longest_duration_seconds >= 0
                    AND (all_compared_results_solved = FALSE OR highest_solved_attempts >= 1))),
    CONSTRAINT ck_game_result_excuse_offer_context_fingerprint
        CHECK (context_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_game_result_excuse_offer_context_timestamps
        CHECK (updated_at >= created_at)
);
