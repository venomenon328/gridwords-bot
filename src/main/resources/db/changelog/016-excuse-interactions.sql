--liquibase formatted sql

--changeset gridwords:016-excuse-interactions
CREATE TABLE game_result_excuse (
    game_result_id BIGINT PRIMARY KEY REFERENCES game_result(id) ON DELETE CASCADE,
    trigger_source_message_id BIGINT REFERENCES submission(source_message_id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    catalog_version VARCHAR(100),
    context_version VARCHAR(100),
    context_generation INTEGER NOT NULL DEFAULT 0,
    offered_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    reroll_used BOOLEAN NOT NULL DEFAULT FALSE,
    selected_round VARCHAR(16),
    selected_position INTEGER,
    selected_template_id VARCHAR(200),
    selected_style VARCHAR(32),
    selected_topic VARCHAR(64),
    selected_rendered_text TEXT,
    selected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_game_result_excuse_status
        CHECK (status IN ('NOT_OFFERED', 'AVAILABLE', 'SELECTED', 'DECLINED', 'EXPIRED', 'INVALIDATED')),
    CONSTRAINT ck_game_result_excuse_context_generation
        CHECK (context_generation >= 0),
    CONSTRAINT ck_game_result_excuse_offer_window
        CHECK ((offered_at IS NULL AND expires_at IS NULL)
            OR (offered_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > offered_at)),
    CONSTRAINT ck_game_result_excuse_selection_snapshot
        CHECK ((selected_round IS NULL AND selected_position IS NULL AND selected_template_id IS NULL
                    AND selected_style IS NULL AND selected_topic IS NULL
                    AND selected_rendered_text IS NULL AND selected_at IS NULL)
            OR (selected_round IS NOT NULL AND selected_position IS NOT NULL AND selected_template_id IS NOT NULL
                    AND selected_style IS NOT NULL AND selected_topic IS NOT NULL
                    AND selected_rendered_text IS NOT NULL AND selected_at IS NOT NULL
                    AND selected_round IN ('INITIAL', 'STYLE_REROLL') AND selected_position BETWEEN 1 AND 3
                    AND btrim(selected_template_id) <> '' AND btrim(selected_rendered_text) <> '')),
    CONSTRAINT ck_game_result_excuse_snapshot_style
        CHECK (selected_style IS NULL OR selected_style IN (
            'TECHNICAL', 'TACTICAL', 'BUREAUCRATIC', 'DRAMATIC', 'COSMIC', 'NORTHERN_GERMAN', 'SPORTING', 'LEGAL')),
    CONSTRAINT ck_game_result_excuse_snapshot_topic
        CHECK (selected_topic IS NULL OR selected_topic IN (
            'GENERAL', 'TECHNICAL_FAILURE', 'LONG_TERM_PLAN', 'RESPONSIBILITY', 'GRID_CONFLICT', 'LATE_SUBMISSION',
            'SLOW_RESULT', 'NOT_SOLVED', 'LAST_ATTEMPT', 'SINGLE_BOARD_BLAME', 'DAILY_OUTLIER')),
    CONSTRAINT ck_game_result_excuse_not_offered
        CHECK (status <> 'NOT_OFFERED' OR (
            trigger_source_message_id IS NULL
            AND catalog_version IS NULL
            AND context_version IS NULL
            AND context_generation = 0
            AND offered_at IS NULL
            AND expires_at IS NULL
            AND reroll_used = FALSE
            AND selected_round IS NULL
            AND selected_position IS NULL
            AND selected_template_id IS NULL
            AND selected_style IS NULL
            AND selected_topic IS NULL
            AND selected_rendered_text IS NULL
            AND selected_at IS NULL)),
    CONSTRAINT ck_game_result_excuse_offered_state
        CHECK (status = 'NOT_OFFERED' OR (
            trigger_source_message_id IS NOT NULL
            AND catalog_version IS NOT NULL
            AND context_version IS NOT NULL
            AND context_generation >= 1
            AND offered_at IS NOT NULL
            AND expires_at IS NOT NULL)),
    CONSTRAINT ck_game_result_excuse_selected_state
        CHECK (status <> 'SELECTED' OR (
            selected_round IS NOT NULL
            AND selected_position IS NOT NULL
            AND selected_template_id IS NOT NULL
            AND selected_style IS NOT NULL
            AND selected_topic IS NOT NULL
            AND selected_rendered_text IS NOT NULL
            AND selected_at IS NOT NULL
            AND selected_at >= offered_at
            AND selected_at <= expires_at)),
    CONSTRAINT ck_game_result_excuse_available_state
        CHECK (status <> 'AVAILABLE' OR selected_template_id IS NULL),
    CONSTRAINT ck_game_result_excuse_selected_round
        CHECK (status <> 'SELECTED' OR (reroll_used = (selected_round = 'STYLE_REROLL'))),
    CONSTRAINT ck_game_result_excuse_timestamps
        CHECK (updated_at >= created_at)
);

CREATE TABLE game_result_excuse_option (
    game_result_id BIGINT NOT NULL REFERENCES game_result_excuse(game_result_id) ON DELETE CASCADE,
    context_generation INTEGER NOT NULL,
    round VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    template_id VARCHAR(200) NOT NULL,
    style VARCHAR(32) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    rendered_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_game_result_excuse_option PRIMARY KEY (game_result_id, context_generation, round, position),
    CONSTRAINT uq_game_result_excuse_option_template UNIQUE (game_result_id, template_id),
    CONSTRAINT uq_game_result_excuse_option_snapshot
        UNIQUE (game_result_id, context_generation, round, position, template_id, style, topic, rendered_text),
    CONSTRAINT ck_game_result_excuse_option_generation CHECK (context_generation >= 1),
    CONSTRAINT ck_game_result_excuse_option_round CHECK (round IN ('INITIAL', 'STYLE_REROLL')),
    CONSTRAINT ck_game_result_excuse_option_position CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT ck_game_result_excuse_option_text CHECK (btrim(template_id) <> '' AND btrim(rendered_text) <> ''),
    CONSTRAINT ck_game_result_excuse_option_style CHECK (style IN (
        'TECHNICAL', 'TACTICAL', 'BUREAUCRATIC', 'DRAMATIC', 'COSMIC', 'NORTHERN_GERMAN', 'SPORTING', 'LEGAL')),
    CONSTRAINT ck_game_result_excuse_option_topic CHECK (topic IN (
        'GENERAL', 'TECHNICAL_FAILURE', 'LONG_TERM_PLAN', 'RESPONSIBILITY', 'GRID_CONFLICT', 'LATE_SUBMISSION',
        'SLOW_RESULT', 'NOT_SOLVED', 'LAST_ATTEMPT', 'SINGLE_BOARD_BLAME', 'DAILY_OUTLIER'))
);

ALTER TABLE game_result_excuse
    ADD CONSTRAINT fk_game_result_excuse_selected_option
    FOREIGN KEY (
        game_result_id, context_generation, selected_round, selected_position,
        selected_template_id, selected_style, selected_topic, selected_rendered_text)
    REFERENCES game_result_excuse_option (
        game_result_id, context_generation, round, position,
        template_id, style, topic, rendered_text)
    DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX ix_game_result_excuse_due
    ON game_result_excuse (expires_at, game_result_id)
    WHERE status = 'AVAILABLE';

CREATE INDEX ix_game_result_excuse_player_game_offered
    ON game_result_excuse (offered_at DESC, game_result_id)
    WHERE offered_at IS NOT NULL;

INSERT INTO game_result_excuse (game_result_id, status, context_generation, reroll_used, created_at, updated_at)
SELECT id, 'NOT_OFFERED', 0, FALSE, created_at, updated_at
FROM game_result;

SELECT 1 / CASE
    WHEN (SELECT COUNT(*) FROM game_result_excuse) = (SELECT COUNT(*) FROM game_result)
        AND NOT EXISTS (
            SELECT 1
            FROM game_result result
            WHERE NOT EXISTS (
                SELECT 1
                FROM game_result_excuse excuse
                WHERE excuse.game_result_id = result.id
                  AND excuse.status = 'NOT_OFFERED'
            )
        )
    THEN 1
    ELSE 0
END;
