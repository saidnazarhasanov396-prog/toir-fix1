CREATE TABLE IF NOT EXISTS equipment_usage_sessions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,

    equipment_id uuid NOT NULL,
    operator_employee_id uuid NOT NULL,
    department_id uuid,
    started_at timestamptz NOT NULL,
    returned_at timestamptz,

    meter_id uuid,
    start_meter_value double precision,
    end_meter_value double precision,
    start_odometer_km double precision,
    end_odometer_km double precision,
    start_engine_hours double precision,
    end_engine_hours double precision,

    issued_by uuid,
    returned_by uuid,
    note text,
    status varchar(32) NOT NULL DEFAULT 'OPEN',

    CONSTRAINT fk_equipment_usage_sessions_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_equipment_usage_sessions_operator
        FOREIGN KEY (operator_employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_equipment_usage_sessions_department
        FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_equipment_usage_sessions_meter
        FOREIGN KEY (meter_id) REFERENCES equipment_meters(id),
    CONSTRAINT chk_equipment_usage_sessions_status
        CHECK (status IN ('OPEN', 'RETURNED')),
    CONSTRAINT chk_equipment_usage_sessions_return_after_start
        CHECK (returned_at IS NULL OR returned_at >= started_at),
    CONSTRAINT chk_equipment_usage_sessions_meter_value
        CHECK (
            start_meter_value IS NULL
            OR end_meter_value IS NULL
            OR end_meter_value >= start_meter_value
        ),
    CONSTRAINT chk_equipment_usage_sessions_odometer
        CHECK (
            start_odometer_km IS NULL
            OR end_odometer_km IS NULL
            OR end_odometer_km >= start_odometer_km
        ),
    CONSTRAINT chk_equipment_usage_sessions_engine_hours
        CHECK (
            start_engine_hours IS NULL
            OR end_engine_hours IS NULL
            OR end_engine_hours >= start_engine_hours
        )
);

CREATE INDEX IF NOT EXISTS idx_equipment_usage_sessions_equipment_started
    ON equipment_usage_sessions(equipment_id, started_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_usage_sessions_operator_started
    ON equipment_usage_sessions(operator_employee_id, started_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_usage_sessions_department_started
    ON equipment_usage_sessions(department_id, started_at DESC)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_usage_sessions_open_equipment
    ON equipment_usage_sessions(equipment_id)
    WHERE is_deleted = false
      AND status = 'OPEN';

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_usage_sessions_open_operator
    ON equipment_usage_sessions(operator_employee_id)
    WHERE is_deleted = false
      AND status = 'OPEN';

INSERT INTO equipment_usage_sessions (
    id,
    created_at,
    updated_at,
    is_deleted,
    equipment_id,
    operator_employee_id,
    department_id,
    started_at,
    returned_at,
    start_odometer_km,
    end_odometer_km,
    start_engine_hours,
    end_engine_hours,
    issued_by,
    returned_by,
    note,
    status
)
SELECT
    vds.id,
    vds.created_at,
    vds.updated_at,
    vds.is_deleted,
    vds.equipment_id,
    vds.driver_employee_id AS operator_employee_id,
    COALESCE(e.department_id, e.responsible_department_id) AS department_id,
    vds.started_at,
    vds.returned_at,
    vds.start_odometer_km,
    vds.end_odometer_km,
    vds.start_engine_hours,
    vds.end_engine_hours,
    vds.issued_by,
    vds.returned_by,
    vds.note,
    vds.status
FROM vehicle_driving_sessions vds
JOIN equipment e ON e.id = vds.equipment_id
WHERE vds.is_deleted = false
ON CONFLICT (id) DO NOTHING;
