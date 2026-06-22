ALTER TABLE equipment
    DROP CONSTRAINT IF EXISTS equipment_status_check;

ALTER TABLE equipment
    ADD CONSTRAINT equipment_status_check
        CHECK (status IN (
            'ACTIVE',
            'STANDBY',
            'IN_REPAIR',
            'CONSERVATION',
            'DECOMMISSIONED',
            'OUT_OF_SERVICE'
        ));

ALTER TABLE equipment_status_history
    DROP CONSTRAINT IF EXISTS equipment_status_history_from_status_check;

ALTER TABLE equipment_status_history
    ADD CONSTRAINT equipment_status_history_from_status_check
        CHECK (from_status IS NULL OR from_status IN (
            'ACTIVE',
            'STANDBY',
            'IN_REPAIR',
            'CONSERVATION',
            'DECOMMISSIONED',
            'OUT_OF_SERVICE'
        ));

ALTER TABLE equipment_status_history
    DROP CONSTRAINT IF EXISTS equipment_status_history_to_status_check;

ALTER TABLE equipment_status_history
    ADD CONSTRAINT equipment_status_history_to_status_check
        CHECK (to_status IN (
            'ACTIVE',
            'STANDBY',
            'IN_REPAIR',
            'CONSERVATION',
            'DECOMMISSIONED',
            'OUT_OF_SERVICE'
        ));
