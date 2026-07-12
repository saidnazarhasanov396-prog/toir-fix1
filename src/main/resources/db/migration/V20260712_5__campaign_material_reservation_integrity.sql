DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reservations WHERE quantity < 0 OR quantity::text IN ('NaN','Infinity','-Infinity'))
       OR EXISTS (SELECT 1 FROM work_order_spare_part_requirements WHERE required_qty < 0 OR required_qty::text IN ('NaN','Infinity','-Infinity')) THEN
        RAISE EXCEPTION 'RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED';
    END IF;
END $$;

ALTER TABLE reservations
    ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric, 4);
ALTER TABLE work_order_spare_part_requirements
    ALTER COLUMN required_qty TYPE numeric(19,4) USING round(required_qty::numeric, 4);

CREATE TABLE repair_campaign_material_requirements (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    work_item_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    required_quantity numeric(19,4) NOT NULL,
    critical boolean NOT NULL DEFAULT false,
    procurement_required boolean NOT NULL DEFAULT false,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_rc_material_campaign FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_rc_material_work_item FOREIGN KEY (work_item_id, repair_campaign_id)
        REFERENCES repair_campaign_work_items(id, repair_campaign_id),
    CONSTRAINT fk_rc_material_spare_part FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT fk_rc_material_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT chk_rc_material_required_quantity CHECK (required_quantity > 0)
);
CREATE UNIQUE INDEX uq_rc_material_active_identity
    ON repair_campaign_material_requirements(repair_campaign_id, work_item_id, spare_part_id, warehouse_id)
    WHERE is_deleted=false;

ALTER TABLE work_order_spare_part_requirements
    ADD COLUMN campaign_requirement_id uuid,
    ADD CONSTRAINT fk_wo_spare_req_campaign_requirement FOREIGN KEY (campaign_requirement_id)
        REFERENCES repair_campaign_material_requirements(id);
ALTER TABLE work_order_spare_part_requirements DROP CONSTRAINT chk_wo_spare_req_source_type;
ALTER TABLE work_order_spare_part_requirements
    ADD CONSTRAINT chk_wo_spare_req_source_type CHECK (source_type IN
        ('TEMPLATE_REQUIRED_SPARE_PART','REGULATION_REQUIRED_SPARE_PART','MANUAL','REPAIR_CAMPAIGN_WORK_ITEM'));
CREATE UNIQUE INDEX uq_wo_spare_req_active_campaign_requirement
    ON work_order_spare_part_requirements(work_order_id, campaign_requirement_id)
    WHERE is_deleted=false AND campaign_requirement_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reservations
        WHERE is_deleted=false AND status='ACTIVE'
          AND work_order_id IS NOT NULL AND requirement_id IS NOT NULL AND spare_part_id IS NOT NULL
        GROUP BY work_order_id, requirement_id, spare_part_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'RC_V5_DUPLICATE_ACTIVE_RESERVATION_REMEDIATION_REQUIRED';
    END IF;
END $$;
CREATE UNIQUE INDEX uq_reservations_active_work_requirement_spare
    ON reservations(work_order_id, requirement_id, spare_part_id)
    WHERE is_deleted=false AND status='ACTIVE'
      AND work_order_id IS NOT NULL AND requirement_id IS NOT NULL AND spare_part_id IS NOT NULL;
