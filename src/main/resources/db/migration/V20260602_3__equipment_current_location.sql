ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS current_location_type varchar(64),
    ADD COLUMN IF NOT EXISTS current_warehouse_id uuid,
    ADD COLUMN IF NOT EXISTS responsible_department_id uuid,
    ADD COLUMN IF NOT EXISTS outside_reason varchar(64),
    ADD COLUMN IF NOT EXISTS outside_taken_by varchar(255),
    ADD COLUMN IF NOT EXISTS outside_recipient_user_id uuid,
    ADD COLUMN IF NOT EXISTS outside_started_date date,
    ADD COLUMN IF NOT EXISTS outside_expected_return_date date,
    ADD COLUMN IF NOT EXISTS outside_destination varchar(255),
    ADD COLUMN IF NOT EXISTS outside_reason_note text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_current_location_type'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_current_location_type
                CHECK (
                    current_location_type IS NULL
                    OR current_location_type IN ('DEPARTMENT', 'WAREHOUSE', 'OUTSIDE_FACILITY')
                );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_outside_reason'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_outside_reason
                CHECK (
                    outside_reason IS NULL
                    OR outside_reason IN (
                        'BUSINESS_TRIP',
                        'SERVICE',
                        'RENTED_OUT',
                        'ON_ROAD',
                        'TEMPORARY_USE',
                        'EXTERNAL_ORGANIZATION',
                        'INSTALLATION',
                        'CALIBRATION',
                        'INSPECTION',
                        'OTHER'
                    )
                );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_outside_expected_return_date'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_outside_expected_return_date
                CHECK (
                    outside_expected_return_date IS NULL
                    OR outside_started_date IS NULL
                    OR outside_expected_return_date >= outside_started_date
                );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_current_warehouse'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_current_warehouse
                FOREIGN KEY (current_warehouse_id) REFERENCES warehouses (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_responsible_department'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_responsible_department
                FOREIGN KEY (responsible_department_id) REFERENCES departments (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_outside_recipient_user'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_outside_recipient_user
                FOREIGN KEY (outside_recipient_user_id) REFERENCES users (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_equipment_current_location_type
    ON equipment (current_location_type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_responsible_department_id
    ON equipment (responsible_department_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_current_warehouse_id
    ON equipment (current_warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_outside_expected_return_date
    ON equipment (outside_expected_return_date)
    WHERE is_deleted = false AND outside_expected_return_date IS NOT NULL;

CREATE TABLE IF NOT EXISTS equipment_location_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    equipment_id uuid NOT NULL,

    from_location_type varchar(64),
    from_department_id uuid,
    from_warehouse_id uuid,
    from_outside_reason varchar(64),
    from_outside_taken_by varchar(255),
    from_outside_recipient_user_id uuid,
    from_outside_started_date date,
    from_outside_expected_return_date date,
    from_outside_destination varchar(255),
    from_outside_reason_note text,

    to_location_type varchar(64) NOT NULL,
    to_department_id uuid,
    to_warehouse_id uuid,
    to_outside_reason varchar(64),
    to_outside_taken_by varchar(255),
    to_outside_recipient_user_id uuid,
    to_outside_started_date date,
    to_outside_expected_return_date date,
    to_outside_destination varchar(255),
    to_outside_reason_note text,

    responsible_department_id uuid,
    changed_by uuid,
    changed_at timestamptz NOT NULL,
    note text
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_location_history_equipment'
    ) THEN
        ALTER TABLE equipment_location_history
            ADD CONSTRAINT fk_equipment_location_history_equipment
                FOREIGN KEY (equipment_id) REFERENCES equipment (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_location_history_responsible_department'
    ) THEN
        ALTER TABLE equipment_location_history
            ADD CONSTRAINT fk_equipment_location_history_responsible_department
                FOREIGN KEY (responsible_department_id) REFERENCES departments (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_equipment_location_history_changed_by'
    ) THEN
        ALTER TABLE equipment_location_history
            ADD CONSTRAINT fk_equipment_location_history_changed_by
                FOREIGN KEY (changed_by) REFERENCES users (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_equipment_id
    ON equipment_location_history (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_changed_at
    ON equipment_location_history (changed_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_changed_by
    ON equipment_location_history (changed_by)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_to_location_type
    ON equipment_location_history (to_location_type)
    WHERE is_deleted = false;

UPDATE equipment
SET current_location_type = 'DEPARTMENT',
    responsible_department_id = COALESCE(responsible_department_id, department_id)
WHERE is_deleted = false
  AND department_id IS NOT NULL
  AND current_location_type IS NULL;

UPDATE equipment e
SET current_location_type = 'WAREHOUSE',
    current_warehouse_id = wei.warehouse_id,
    responsible_department_id = COALESCE(e.responsible_department_id, w.department_id),
    location_id = CASE
        WHEN e.location_id = wei.warehouse_id THEN w.location_id
        ELSE e.location_id
    END
FROM warehouse_equipment_items wei
JOIN warehouses w ON w.id = wei.warehouse_id AND w.is_deleted = false
WHERE e.id = wei.equipment_id
  AND e.is_deleted = false
  AND wei.active = true
  AND wei.is_deleted = false
  AND e.department_id IS NULL
  AND e.current_location_type IS NULL;
