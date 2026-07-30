ALTER TABLE maintenance_schedule_calculation_items
    ADD COLUMN IF NOT EXISTS source_code_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_name_snapshot VARCHAR(255);

DROP TRIGGER IF EXISTS trg_ms_calculation_items_immutable
    ON maintenance_schedule_calculation_items;

UPDATE maintenance_schedule_calculation_items item
SET source_code_snapshot = coalesce(
        (
            SELECT rule.code
            FROM equipment_maintenance_rules rule
            WHERE rule.id = item.maintenance_rule_id
        ),
        (
            SELECT regulation.code
            FROM maintenance_regulations regulation
            WHERE regulation.id = item.regulation_id
        )
    ),
    source_name_snapshot = coalesce(
        item.maintenance_rule_name_snapshot,
        item.regulation_name_snapshot,
        (
            SELECT rule.name
            FROM equipment_maintenance_rules rule
            WHERE rule.id = item.maintenance_rule_id
        ),
        (
            SELECT regulation.name
            FROM maintenance_regulations regulation
            WHERE regulation.id = item.regulation_id
        )
    )
WHERE item.source_code_snapshot IS NULL
   OR item.source_name_snapshot IS NULL;

CREATE TRIGGER trg_ms_calculation_items_immutable
    BEFORE UPDATE OR DELETE ON maintenance_schedule_calculation_items
    FOR EACH ROW
    EXECUTE FUNCTION reject_ms_calculation_item_mutation();

COMMENT ON COLUMN maintenance_schedule_calculation_items.source_code_snapshot
    IS 'Immutable source code displayed for the calculation revision';

COMMENT ON COLUMN maintenance_schedule_calculation_items.source_name_snapshot
    IS 'Immutable source name displayed for the calculation revision';
