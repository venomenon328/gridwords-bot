--liquibase formatted sql

--changeset gridwords:019-record-state-source-order
-- The first accepted timestamp is ordering metadata of a materialized result source.
-- It intentionally remains nullable so an application rollback to the pre-019 binary
-- can still insert states; the current bootstrap reconciles such incomplete legacy rows.
ALTER TABLE record_state
    ADD COLUMN source_game_first_accepted_at TIMESTAMPTZ;

UPDATE record_state state
SET source_game_first_accepted_at = result.created_at
FROM game_result result
WHERE state.source_type = 'GAME_RESULT'
  AND state.source_game_result_id = result.id
  AND state.source_game_first_accepted_at IS NULL;

ALTER TABLE record_state
    DROP CONSTRAINT ck_record_state_source;

ALTER TABLE record_state
    ADD CONSTRAINT ck_record_state_source CHECK (
        (source_type = 'GAME_RESULT'
            AND source_game_result_id > 0
            AND source_game_result_version >= 0
            AND source_game_player_id > 0
            AND source_game_type IN ('GRIDWORDS', 'QUADWORDS')
            AND source_game_date IS NOT NULL
            AND source_streak_metric IS NULL
            AND source_streak_owner_type IS NULL
            AND source_streak_owner_player_id IS NULL
            AND source_streak_start_date IS NULL
            AND running = FALSE)
        OR (source_type = 'STREAK_RUN'
            AND source_game_result_id IS NULL
            AND source_game_result_version IS NULL
            AND source_game_player_id IS NULL
            AND source_game_type IS NULL
            AND source_game_date IS NULL
            AND source_game_first_accepted_at IS NULL
            AND source_streak_metric IS NOT NULL
            AND source_streak_owner_type IN ('PLAYER', 'SHARED')
            AND (source_streak_owner_type = 'PLAYER') = (source_streak_owner_player_id IS NOT NULL)
            AND (source_streak_owner_player_id IS NULL OR source_streak_owner_player_id > 0)
            AND source_streak_start_date IS NOT NULL)
    );
