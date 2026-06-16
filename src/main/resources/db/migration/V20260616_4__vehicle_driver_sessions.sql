CREATE TABLE IF NOT EXISTS employee_work_roles (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(64) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    name_en varchar(255),
    name_uz varchar(255),
    description text,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS hr_employee_work_role_assignments (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    employee_id uuid NOT NULL,
    work_role_id uuid NOT NULL,
    CONSTRAINT fk_employee_work_role_assignments_employee
        FOREIGN KEY (employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_employee_work_role_assignments_role
        FOREIGN KEY (work_role_id) REFERENCES employee_work_roles(id),
    CONSTRAINT uq_employee_work_role_assignments_employee_role
        UNIQUE (employee_id, work_role_id)
);

CREATE INDEX IF NOT EXISTS idx_employee_work_role_assignments_employee
    ON hr_employee_work_role_assignments(employee_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_employee_work_role_assignments_role
    ON hr_employee_work_role_assignments(work_role_id)
    WHERE is_deleted = false;

INSERT INTO employee_work_roles (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description, is_active)
VALUES
    ('20000000-0000-0000-0000-000000040001', now(), now(), false, 'DRIVER', 'Driver', 'Driver', 'Haydovchi', 'Vehicle driving work role', true),
    ('20000000-0000-0000-0000-000000040002', now(), now(), false, 'MECHANIC', 'Mechanic', 'Mechanic', 'Mexanik', 'Mechanical work role', true),
    ('20000000-0000-0000-0000-000000040003', now(), now(), false, 'ELECTRICIAN', 'Electrician', 'Electrician', 'Elektrik', 'Electrical work role', true),
    ('20000000-0000-0000-0000-000000040004', now(), now(), false, 'PLUMBER', 'Plumber', 'Plumber', 'Santexnik', 'Plumbing work role', true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_uz = EXCLUDED.name_uz,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = now();

UPDATE vehicle_details vd
SET assigned_driver_id = e.id
FROM hr_employees e
WHERE vd.assigned_driver_id = e.user_id
  AND vd.assigned_driver_id IS NOT NULL
  AND vd.is_deleted = false
  AND e.is_deleted = false;

INSERT INTO hr_employee_work_role_assignments (id, created_at, updated_at, is_deleted, employee_id, work_role_id)
SELECT gen_random_uuid(), now(), now(), false, e.id, r.id
FROM hr_employees e
JOIN employee_work_roles r ON r.code = 'DRIVER' AND r.is_deleted = false
WHERE e.is_deleted = false
  AND (
      lower(coalesce(e.position, '')) LIKE '%driver%'
      OR lower(coalesce(e.position, '')) LIKE '%haydov%'
      OR lower(coalesce(e.position, '')) LIKE '%водител%'
      OR EXISTS (
          SELECT 1
          FROM vehicle_details vd
          WHERE vd.assigned_driver_id = e.id
            AND vd.is_deleted = false
      )
  )
ON CONFLICT (employee_id, work_role_id) DO UPDATE
SET is_deleted = false,
    updated_at = now();

UPDATE vehicle_details vd
SET assigned_driver_id = NULL
WHERE vd.assigned_driver_id IS NOT NULL
  AND vd.is_deleted = false
  AND NOT EXISTS (
      SELECT 1
      FROM hr_employees e
      WHERE e.id = vd.assigned_driver_id
        AND e.is_deleted = false
  );

ALTER TABLE vehicle_details
    DROP CONSTRAINT IF EXISTS fk_vehicle_details_assigned_driver_employee;

ALTER TABLE vehicle_details
    ADD CONSTRAINT fk_vehicle_details_assigned_driver_employee
    FOREIGN KEY (assigned_driver_id) REFERENCES hr_employees(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_vehicle_details_assigned_driver_active
    ON vehicle_details(assigned_driver_id)
    WHERE assigned_driver_id IS NOT NULL
      AND is_deleted = false;

CREATE TABLE IF NOT EXISTS vehicle_driving_sessions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    driver_employee_id uuid NOT NULL,
    started_at timestamptz NOT NULL,
    returned_at timestamptz,
    start_odometer_km double precision,
    end_odometer_km double precision,
    start_engine_hours double precision,
    end_engine_hours double precision,
    issued_by uuid,
    returned_by uuid,
    note text,
    status varchar(32) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT fk_vehicle_driving_sessions_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_vehicle_driving_sessions_driver
        FOREIGN KEY (driver_employee_id) REFERENCES hr_employees(id),
    CONSTRAINT chk_vehicle_driving_sessions_status
        CHECK (status IN ('OPEN', 'RETURNED')),
    CONSTRAINT chk_vehicle_driving_sessions_return_after_start
        CHECK (returned_at IS NULL OR returned_at >= started_at),
    CONSTRAINT chk_vehicle_driving_sessions_odometer
        CHECK (
            start_odometer_km IS NULL
            OR end_odometer_km IS NULL
            OR end_odometer_km >= start_odometer_km
        ),
    CONSTRAINT chk_vehicle_driving_sessions_engine_hours
        CHECK (
            start_engine_hours IS NULL
            OR end_engine_hours IS NULL
            OR end_engine_hours >= start_engine_hours
        )
);

CREATE INDEX IF NOT EXISTS idx_vehicle_driving_sessions_equipment_started
    ON vehicle_driving_sessions(equipment_id, started_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_vehicle_driving_sessions_driver_started
    ON vehicle_driving_sessions(driver_employee_id, started_at DESC)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_vehicle_driving_sessions_open_equipment
    ON vehicle_driving_sessions(equipment_id)
    WHERE is_deleted = false
      AND status = 'OPEN';

CREATE UNIQUE INDEX IF NOT EXISTS ux_vehicle_driving_sessions_open_driver
    ON vehicle_driving_sessions(driver_employee_id)
    WHERE is_deleted = false
      AND status = 'OPEN';
