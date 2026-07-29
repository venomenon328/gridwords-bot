--liquibase formatted sql
--changeset gridwords:003-canonical-publication-ownership
ALTER TABLE game_result ADD COLUMN canonical_publish_claim_token UUID;
