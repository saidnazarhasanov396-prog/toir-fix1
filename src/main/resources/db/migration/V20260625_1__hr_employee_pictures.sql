CREATE TABLE IF NOT EXISTS hr_employee_pictures (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    picture_type VARCHAR(64),
    picture_name VARCHAR(255),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_hr_employee_pictures_employee_id
    ON hr_employee_pictures (employee_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_hr_employee_pictures_file_id
    ON hr_employee_pictures (file_id);

CREATE INDEX IF NOT EXISTS idx_hr_employee_pictures_employee_picture_type
    ON hr_employee_pictures (employee_id, picture_type)
    WHERE is_deleted = FALSE;
