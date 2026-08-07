--liquibase formatted sql

--changeset gridwords:023-record-announcement-stability
ALTER TABLE record_announcement DROP CONSTRAINT ck_record_announcement_state;
ALTER TABLE record_announcement
    ADD CONSTRAINT ck_record_announcement_state
    CHECK (delivery_state IN ('OPEN', 'CLAIMED', 'RETRYABLE', 'DELIVERED', 'SYNCHRONIZED',
                             'FAILED_PERMANENT', 'EXTERNALLY_REMOVED', 'SUPPRESSED'));

-- Existing confirmed creates still contain the legacy description marker. Schedule one ID-based edit to remove it.
UPDATE record_announcement
SET delivery_state = 'OPEN',
    desired_projection = 'EDIT',
    changed_at = NULL
WHERE delivery_state = 'SYNCHRONIZED'
  AND desired_projection = 'CREATE'
  AND published_at IS NOT NULL
  AND deleted_at IS NULL
  AND EXISTS (
      SELECT 1 FROM record_announcement_message message
      WHERE message.announcement_id = record_announcement.id
  );
