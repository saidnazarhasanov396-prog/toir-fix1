CREATE TABLE IF NOT EXISTS maintenance_template_spare_part_requirements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    template_id uuid NOT NULL,
    operation_id uuid,
    spare_part_id uuid NOT NULL,
    quantity double precision NOT NULL,
    unit varchar(64) NOT NULL,
    criticality varchar(64),
    notes text,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_mt_spare_req_template
        FOREIGN KEY (template_id) REFERENCES maintenance_templates(id),
    CONSTRAINT fk_mt_spare_req_operation
        FOREIGN KEY (operation_id) REFERENCES maintenance_operations(id),
    CONSTRAINT fk_mt_spare_req_spare_part
        FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT chk_mt_spare_req_quantity CHECK (quantity > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mt_spare_req_active_unique
    ON maintenance_template_spare_part_requirements (
        template_id,
        COALESCE(operation_id, '00000000-0000-0000-0000-000000000000'::uuid),
        spare_part_id
    )
    WHERE is_deleted = false AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_mt_spare_req_template
    ON maintenance_template_spare_part_requirements(template_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mt_spare_req_spare_part
    ON maintenance_template_spare_part_requirements(spare_part_id)
    WHERE is_deleted = false AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_mt_spare_req_operation
    ON maintenance_template_spare_part_requirements(operation_id)
    WHERE operation_id IS NOT NULL AND is_deleted = false;
