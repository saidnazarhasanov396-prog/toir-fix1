CREATE TABLE maintenance_schedule_calculation_items (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    calculation_revision BIGINT NOT NULL,
    source_item_key VARCHAR(64) NOT NULL,
    source_item_key_version INTEGER NOT NULL,
    equipment_id UUID,
    regulation_id UUID,
    maintenance_rule_id UUID,
    template_id UUID,
    maintenance_type VARCHAR(64),
    trigger_type VARCHAR(64),
    trigger_discriminator VARCHAR(255),
    cycle_ordinal BIGINT NOT NULL,
    planned_date DATE NOT NULL,
    scheduled_start TIMESTAMP WITHOUT TIME ZONE,
    scheduled_end TIMESTAMP WITHOUT TIME ZONE,
    due_date TIMESTAMP WITHOUT TIME ZONE,
    normative_labor_hours NUMERIC(19, 4),
    priority VARCHAR(64),
    department_id UUID,
    equipment_code_snapshot VARCHAR(255),
    equipment_name_snapshot VARCHAR(255),
    regulation_name_snapshot VARCHAR(255),
    maintenance_rule_name_snapshot VARCHAR(255),
    template_name_snapshot VARCHAR(255),
    task_title_snapshot VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ms_calc_items_plan_revision_source_key
        UNIQUE (plan_id, calculation_revision, source_item_key),
    CONSTRAINT chk_ms_calc_items_revision
        CHECK (calculation_revision >= 1),
    CONSTRAINT chk_ms_calc_items_source_key_version
        CHECK (source_item_key_version >= 1),
    CONSTRAINT chk_ms_calc_items_source_key
        CHECK (source_item_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_ms_calc_items_plan
        FOREIGN KEY (plan_id) REFERENCES ppr_plans(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ms_calc_items_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ms_calc_items_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ms_calc_items_rule
        FOREIGN KEY (maintenance_rule_id) REFERENCES equipment_maintenance_rules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ms_calc_items_template
        FOREIGN KEY (template_id) REFERENCES maintenance_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ms_calc_items_department
        FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT
);

CREATE FUNCTION reject_ms_calculation_item_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'maintenance_schedule_calculation_items are immutable';
END;
$$;

CREATE TRIGGER trg_ms_calculation_items_immutable
    BEFORE UPDATE OR DELETE ON maintenance_schedule_calculation_items
    FOR EACH ROW
    EXECUTE FUNCTION reject_ms_calculation_item_mutation();

ALTER TABLE ppr_tasks
    ADD COLUMN IF NOT EXISTS source_calculation_item_id UUID;

ALTER TABLE ppr_tasks
    ADD CONSTRAINT fk_ppr_tasks_source_calculation_item
        FOREIGN KEY (source_calculation_item_id)
        REFERENCES maintenance_schedule_calculation_items(id)
        ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_ppr_tasks_active_source_calculation_item
    ON ppr_tasks (source_calculation_item_id)
    WHERE source_calculation_item_id IS NOT NULL
      AND is_deleted = FALSE;
