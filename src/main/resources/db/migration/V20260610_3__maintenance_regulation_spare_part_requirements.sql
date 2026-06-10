CREATE TABLE IF NOT EXISTS maintenance_regulation_spare_part_requirements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    regulation_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    quantity double precision NOT NULL,
    unit varchar(64) NOT NULL,
    criticality varchar(64),
    notes text,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_mr_spare_req_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id),
    CONSTRAINT fk_mr_spare_req_spare_part
        FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT chk_mr_spare_req_quantity CHECK (quantity > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mr_spare_req_active_unique
    ON maintenance_regulation_spare_part_requirements (regulation_id, spare_part_id)
    WHERE is_deleted = false AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_mr_spare_req_regulation
    ON maintenance_regulation_spare_part_requirements(regulation_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_mr_spare_req_spare_part
    ON maintenance_regulation_spare_part_requirements(spare_part_id)
    WHERE is_deleted = false AND is_active = true;

ALTER TABLE work_order_spare_part_requirements
    ADD COLUMN IF NOT EXISTS regulation_requirement_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_wo_spare_req_regulation_requirement'
          AND conrelid = 'work_order_spare_part_requirements'::regclass
    ) THEN
        ALTER TABLE work_order_spare_part_requirements
            ADD CONSTRAINT fk_wo_spare_req_regulation_requirement
                FOREIGN KEY (regulation_requirement_id) REFERENCES maintenance_regulation_spare_part_requirements(id);
    END IF;
END $$;

ALTER TABLE work_order_spare_part_requirements
    DROP CONSTRAINT IF EXISTS chk_wo_spare_req_source_type;

ALTER TABLE work_order_spare_part_requirements
    ADD CONSTRAINT chk_wo_spare_req_source_type
        CHECK (source_type IN ('TEMPLATE_REQUIRED_SPARE_PART', 'REGULATION_REQUIRED_SPARE_PART', 'MANUAL'));

CREATE UNIQUE INDEX IF NOT EXISTS ux_wo_spare_req_regulation_source
    ON work_order_spare_part_requirements (work_order_id, source_type, regulation_requirement_id)
    WHERE is_deleted = false AND regulation_requirement_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wo_spare_req_regulation_requirement
    ON work_order_spare_part_requirements(regulation_requirement_id)
    WHERE regulation_requirement_id IS NOT NULL AND is_deleted = false;
