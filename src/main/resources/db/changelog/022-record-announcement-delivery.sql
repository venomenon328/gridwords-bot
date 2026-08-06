--liquibase formatted sql

--changeset gridwords:022-record-announcement-delivery
ALTER TABLE record_announcement DROP CONSTRAINT ck_record_announcement_state;
ALTER TABLE record_announcement
    ADD CONSTRAINT ck_record_announcement_state
    CHECK (delivery_state IN ('OPEN', 'CLAIMED', 'RETRYABLE', 'SYNCHRONIZED', 'FAILED_PERMANENT',
                             'EXTERNALLY_REMOVED', 'SUPPRESSED'));

CREATE INDEX idx_record_announcement_delivery_order
    ON record_announcement (delivery_state, next_retry_at, claim_until, updated_at, id);
