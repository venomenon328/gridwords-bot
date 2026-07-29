--liquibase formatted sql

--changeset gridwords:008-gridwords-source-deletion
ALTER TABLE submission
    ADD COLUMN source_delete_claim_token UUID,
    ADD COLUMN source_delete_lease_until TIMESTAMPTZ,
    ADD COLUMN source_delete_failure_class VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE submission
    ADD CONSTRAINT ck_submission_source_delete_failure_class
        CHECK (source_delete_failure_class IN ('NONE', 'RETRYABLE', 'PERMANENT')),
    ADD CONSTRAINT ck_submission_original_delete_completion
        CHECK (
            (processing_state IN ('ORIGINAL_MESSAGE_DELETED', 'COMPLETED') AND original_deleted_at IS NOT NULL)
            OR
            (processing_state NOT IN ('ORIGINAL_MESSAGE_DELETED', 'COMPLETED') AND original_deleted_at IS NULL)
        );
