-- Prune advanced WMS modules from TOIR while keeping lightweight warehouse stock flows.

CREATE OR REPLACE FUNCTION remove_role_permissions(permissions_to_remove text[])
RETURNS void AS $$
BEGIN
    UPDATE roles r
    SET permissions = COALESCE((
        SELECT jsonb_agg(permission ORDER BY permission)
        FROM (
            SELECT DISTINCT permission
            FROM jsonb_array_elements_text(COALESCE(r.permissions, '[]'::jsonb)) AS existing(permission)
            WHERE permission <> ALL (permissions_to_remove)
        ) cleaned
    ), '[]'::jsonb),
        updated_at = now()
    WHERE r.permissions ?| permissions_to_remove;
END;
$$ LANGUAGE plpgsql;

SELECT remove_role_permissions(ARRAY[
    'WAREHOUSE_BIN_READ',
    'WAREHOUSE_BIN_MANAGE',
    'WAREHOUSE_TASK_READ',
    'WAREHOUSE_TASK_ASSIGN',
    'WAREHOUSE_TASK_EXECUTE',
    'WAREHOUSE_RECEIVE',
    'WAREHOUSE_PUTAWAY',
    'WAREHOUSE_PICK',
    'WAREHOUSE_COUNT_CREATE',
    'WAREHOUSE_COUNT_EXECUTE',
    'WAREHOUSE_COUNT_APPROVE',
    'WAREHOUSE_WRITEOFF_REQUEST',
    'WAREHOUSE_WRITEOFF_APPROVE',
    'WAREHOUSE_DOCUMENT_UPLOAD'
]);

DROP FUNCTION remove_role_permissions(text[]);

UPDATE approval_requests
SET target_type = 'OTHER', updated_at = now()
WHERE target_type = 'WAREHOUSE_WRITEOFF';

DO $$
DECLARE
    r record;
BEGIN
    IF to_regclass('warehouse_bins') IS NOT NULL THEN
        FOR r IN
            SELECT conrelid::regclass AS table_name, conname
            FROM pg_constraint
            WHERE confrelid = 'warehouse_bins'::regclass
        LOOP
            EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', r.table_name, r.conname);
        END LOOP;
    END IF;
END $$;

DROP TABLE IF EXISTS warehouse_writeoff_allocations;
DROP TABLE IF EXISTS warehouse_writeoff_requests;
DROP TABLE IF EXISTS inventory_count_lines;
DROP TABLE IF EXISTS inventory_count_sessions;
DROP TABLE IF EXISTS warehouse_task_lines;
DROP TABLE IF EXISTS warehouse_tasks;
DROP TABLE IF EXISTS wms_label_events;
DROP TABLE IF EXISTS warehouse_bins;

DROP INDEX IF EXISTS idx_warehouse_bins_zone_type;
DROP INDEX IF EXISTS uq_warehouse_bins_barcode;
DROP INDEX IF EXISTS idx_inventory_count_lines_session;
DROP INDEX IF EXISTS idx_inventory_count_lines_identity;
DROP INDEX IF EXISTS idx_warehouse_writeoff_requests_status;
DROP INDEX IF EXISTS uq_warehouse_writeoff_request_number;
DROP INDEX IF EXISTS idx_wms_label_events_target;
DROP INDEX IF EXISTS idx_wms_label_events_scan;
