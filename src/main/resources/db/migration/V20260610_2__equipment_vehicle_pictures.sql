CREATE TABLE IF NOT EXISTS equipment_pictures (
    id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL REFERENCES equipment(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    picture_type VARCHAR(64),
    picture_name VARCHAR(255),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_equipment_pictures_equipment_id
    ON equipment_pictures (equipment_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_equipment_pictures_file_id
    ON equipment_pictures (file_id);

CREATE INDEX IF NOT EXISTS idx_equipment_pictures_equipment_picture_type
    ON equipment_pictures (equipment_id, picture_type)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS vehicle_pictures (
    id UUID PRIMARY KEY,
    vehicle_details_id UUID NOT NULL REFERENCES vehicle_details(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    picture_type VARCHAR(64),
    picture_name VARCHAR(255),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_vehicle_pictures_vehicle_details_id
    ON vehicle_pictures (vehicle_details_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_vehicle_pictures_file_id
    ON vehicle_pictures (file_id);

CREATE INDEX IF NOT EXISTS idx_vehicle_pictures_vehicle_picture_type
    ON vehicle_pictures (vehicle_details_id, picture_type)
    WHERE is_deleted = FALSE;
