CREATE TABLE IF NOT EXISTS hr_employee_specialisations (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    name_ru varchar(255) NOT NULL,
    name_en varchar(255) NOT NULL,
    name_uz varchar(255) NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);

ALTER TABLE hr_employees
    ADD COLUMN IF NOT EXISTS specialisation_id uuid;

CREATE INDEX IF NOT EXISTS idx_hr_employees_specialisation
    ON hr_employees(specialisation_id)
    WHERE specialisation_id IS NOT NULL
      AND is_deleted = false;

ALTER TABLE hr_employees
    DROP CONSTRAINT IF EXISTS fk_hr_employees_specialisation;

ALTER TABLE hr_employees
    ADD CONSTRAINT fk_hr_employees_specialisation
    FOREIGN KEY (specialisation_id) REFERENCES hr_employee_specialisations(id);
