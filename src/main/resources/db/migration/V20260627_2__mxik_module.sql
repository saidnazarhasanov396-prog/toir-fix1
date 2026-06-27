CREATE TABLE IF NOT EXISTS mxik (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(255) NOT NULL,
    kod varchar(20) NOT NULL,
    type varchar(50) NOT NULL,
    group_name varchar(255),
    position_name varchar(255),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mxik_kod_active
    ON mxik (lower(kod))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mxik_search_active
    ON mxik (lower(name), lower(kod), lower(type))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mxik_catalog_active
    ON mxik (group_name, position_name)
    WHERE is_deleted = false;
