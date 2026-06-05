CREATE TABLE IF NOT EXISTS work_order_spare_part_requirements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    work_order_id uuid NOT NULL,
    source_type varchar(64) NOT NULL DEFAULT 'TEMPLATE_REQUIRED_SPARE_PART',
    source_requirement_id uuid,
    template_id uuid,
    operation_id uuid,
    spare_part_id uuid NOT NULL,
    required_qty double precision NOT NULL,
    unit varchar(64) NOT NULL,
    criticality varchar(64),
    notes text,
    status varchar(64) NOT NULL DEFAULT 'PLANNED',
    CONSTRAINT fk_wo_spare_req_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_wo_spare_req_source_requirement
        FOREIGN KEY (source_requirement_id) REFERENCES maintenance_template_spare_part_requirements(id),
    CONSTRAINT fk_wo_spare_req_template
        FOREIGN KEY (template_id) REFERENCES maintenance_templates(id),
    CONSTRAINT fk_wo_spare_req_operation
        FOREIGN KEY (operation_id) REFERENCES maintenance_operations(id),
    CONSTRAINT fk_wo_spare_req_spare_part
        FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT chk_wo_spare_req_required_qty CHECK (required_qty > 0),
    CONSTRAINT chk_wo_spare_req_source_type CHECK (source_type IN ('TEMPLATE_REQUIRED_SPARE_PART', 'MANUAL')),
    CONSTRAINT chk_wo_spare_req_status CHECK (status IN ('PLANNED', 'CANCELLED', 'RESERVED', 'ISSUED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_wo_spare_req_template_source
    ON work_order_spare_part_requirements (work_order_id, source_type, source_requirement_id)
    WHERE is_deleted = false AND source_requirement_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wo_spare_req_work_order
    ON work_order_spare_part_requirements(work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_wo_spare_req_template
    ON work_order_spare_part_requirements(template_id)
    WHERE template_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_wo_spare_req_spare_part
    ON work_order_spare_part_requirements(spare_part_id)
    WHERE is_deleted = false;
