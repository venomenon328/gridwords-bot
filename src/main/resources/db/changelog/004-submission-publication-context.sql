--liquibase formatted sql

--changeset gridwords:004-submission-publication-context
ALTER TABLE submission
    ADD COLUMN personal_complete_established BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN personal_perfect_established BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN shared_complete_established BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN shared_perfect_established BOOLEAN NOT NULL DEFAULT FALSE;