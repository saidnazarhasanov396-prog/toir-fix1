-- Performance indexes for frequently filtered foreign key columns
-- that were missing from all previous migrations.
-- All three use CREATE INDEX IF NOT EXISTS for safe re-run idempotency.

-- 1. notifications.recipient_id
--    Used in every query that loads notifications for a specific user.
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_id
    ON notifications (recipient_id);

-- 2. repair_requests.equipment_id
--    Used in equipment detail pages, warranty checks, and repair history queries.
CREATE INDEX IF NOT EXISTS idx_repair_requests_equipment_id
    ON repair_requests (equipment_id);

-- 3. defects.equipment_id
--    Used in equipment defect history, Pareto/RCA analytics, and reliability passport.
--    Note: idx_defects_equipment_node_id already exists on a different column (equipment_node_id)
--    and is NOT a duplicate of this index.
CREATE INDEX IF NOT EXISTS idx_defects_equipment_id
    ON defects (equipment_id);
