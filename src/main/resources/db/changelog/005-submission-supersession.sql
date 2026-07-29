--liquibase formatted sql

--changeset gridwords:005-submission-supersession
ALTER TABLE submission DROP CONSTRAINT ck_submission_state;

ALTER TABLE submission
    ADD CONSTRAINT ck_submission_state CHECK (processing_state IN (
        'RECEIVED',
        'VALIDATED',
        'RESULT_STORED',
        'CANONICAL_MESSAGE_PUBLISHED',
        'ORIGINAL_MESSAGE_DELETED',
        'COMPLETED',
        'PARSE_REJECTED',
        'FAILED_RETRYABLE',
        'FAILED_FINAL',
        'SUPERSEDED'));
