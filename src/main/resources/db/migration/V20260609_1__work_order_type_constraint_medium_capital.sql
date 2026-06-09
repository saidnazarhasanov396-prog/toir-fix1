ALTER TABLE work_orders
    DROP CONSTRAINT IF EXISTS work_orders_type_check;

ALTER TABLE work_orders
    ADD CONSTRAINT work_orders_type_check
    CHECK (type IN (
        'PLANNED',
        'EMERGENCY',
        'DEFECT',
        'OVERHAUL',
        'INSPECTION',
        'MEDIUM_REPAIR',
        'CAPITAL_REPAIR'
    ));
