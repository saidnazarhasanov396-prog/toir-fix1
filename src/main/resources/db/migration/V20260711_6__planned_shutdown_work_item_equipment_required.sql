DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM planned_shutdown_work_items
        WHERE equipment_id IS NULL
          AND is_deleted = false
    ) THEN
        RAISE EXCEPTION
            'Cannot require planned_shutdown_work_items.equipment_id: active rows with null equipment exist';
    END IF;
END $$;

-- Deleted historical rows are not valid canonical work items either. Refuse the DDL rather than guessing an asset.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM planned_shutdown_work_items WHERE equipment_id IS NULL) THEN
        RAISE EXCEPTION
            'Cannot require planned_shutdown_work_items.equipment_id: historical rows with null equipment exist';
    END IF;
END $$;

ALTER TABLE planned_shutdown_work_items
    ALTER COLUMN equipment_id SET NOT NULL;
