--liquibase formatted sql

--changeset gridwords:006-canonical-refresh-reconciliation
ALTER TABLE game_result
    ADD COLUMN canonical_refresh_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN canonical_refresh_generation BIGINT NOT NULL DEFAULT 0;