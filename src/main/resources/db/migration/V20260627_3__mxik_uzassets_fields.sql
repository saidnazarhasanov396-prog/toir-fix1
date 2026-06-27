ALTER TABLE mxik
    ADD COLUMN IF NOT EXISTS name_uz_latn varchar(255),
    ADD COLUMN IF NOT EXISTS name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS group_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS group_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS class_name varchar(255),
    ADD COLUMN IF NOT EXISTS class_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS class_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS position_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS position_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS sub_position_name varchar(255),
    ADD COLUMN IF NOT EXISTS sub_position_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS sub_position_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS brand_name varchar(255),
    ADD COLUMN IF NOT EXISTS brand_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS brand_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS attribute_name varchar(255),
    ADD COLUMN IF NOT EXISTS attribute_name_ru varchar(255),
    ADD COLUMN IF NOT EXISTS attribute_name_cyril varchar(255),
    ADD COLUMN IF NOT EXISTS barcode varchar(255);

DROP INDEX IF EXISTS idx_mxik_search_active;
CREATE INDEX IF NOT EXISTS idx_mxik_search_active
    ON mxik (lower(name), lower(kod), lower(type), lower(barcode))
    WHERE is_deleted = false;

DROP INDEX IF EXISTS idx_mxik_catalog_active;
CREATE INDEX IF NOT EXISTS idx_mxik_catalog_active
    ON mxik (group_name, class_name, position_name, sub_position_name)
    WHERE is_deleted = false;
