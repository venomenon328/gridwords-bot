--liquibase formatted sql

--changeset gridwords:009-quadwords-boards
ALTER TABLE game_result
    ADD COLUMN quadwords_top_left_board TEXT,
    ADD COLUMN quadwords_top_right_board TEXT,
    ADD COLUMN quadwords_bottom_left_board TEXT,
    ADD COLUMN quadwords_bottom_right_board TEXT;

ALTER TABLE game_result
    DROP CONSTRAINT ck_game_result_board;

ALTER TABLE game_result
    ADD CONSTRAINT ck_game_result_board CHECK (
        (game_type = 'GRIDWORDS'
            AND normalized_board IS NOT NULL
            AND quadwords_top_left_board IS NULL
            AND quadwords_top_right_board IS NULL
            AND quadwords_bottom_left_board IS NULL
            AND quadwords_bottom_right_board IS NULL)
        OR
        (game_type = 'QUADWORDS'
            AND normalized_board IS NULL
            AND quadwords_top_left_board IS NOT NULL
            AND quadwords_top_right_board IS NOT NULL
            AND quadwords_bottom_left_board IS NOT NULL
            AND quadwords_bottom_right_board IS NOT NULL)
    );
