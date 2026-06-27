CREATE TABLE IF NOT EXISTS mxik (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(255) NOT NULL,
    name_uz_latn varchar(255),
    name_ru varchar(255),
    kod varchar(20) NOT NULL,
    type varchar(50) NOT NULL,
    group_name varchar(255),
    group_name_ru varchar(255),
    group_name_cyril varchar(255),
    class_name varchar(255),
    class_name_ru varchar(255),
    class_name_cyril varchar(255),
    position_name varchar(255),
    position_name_ru varchar(255),
    position_name_cyril varchar(255),
    sub_position_name varchar(255),
    sub_position_name_ru varchar(255),
    sub_position_name_cyril varchar(255),
    brand_name varchar(255),
    brand_name_ru varchar(255),
    brand_name_cyril varchar(255),
    attribute_name varchar(255),
    attribute_name_ru varchar(255),
    attribute_name_cyril varchar(255),
    barcode varchar(255),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mxik_kod_active
    ON mxik (lower(kod))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mxik_search_active
    ON mxik (lower(name), lower(kod), lower(type), lower(barcode))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mxik_catalog_active
    ON mxik (group_name, class_name, position_name, sub_position_name)
    WHERE is_deleted = false;
