--liquibase formatted sql
--changeset gridwords:002-canonical-publication
ALTER TABLE game_result ADD COLUMN canonical_publish_lease_until TIMESTAMPTZ;
