-- Forward-only correction for the already-released V20260728_5 snapshot schema.
-- Preserve immutable task lineage across plan boundaries and soft deletion.
LOCK TABLE maintenance_schedule_calculation_items IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE ppr_tasks IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ppr_tasks task
        JOIN maintenance_schedule_calculation_items item
          ON item.id = task.source_calculation_item_id
        WHERE task.source_calculation_item_id IS NOT NULL
          AND task.plan_id <> item.plan_id
    ) THEN
        RAISE EXCEPTION
            'MS_SNAPSHOT_CROSS_PLAN_TASK_LINK_REMEDIATION_REQUIRED';
    END IF;

    IF EXISTS (
        SELECT task.source_calculation_item_id
        FROM ppr_tasks task
        WHERE task.source_calculation_item_id IS NOT NULL
        GROUP BY task.source_calculation_item_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'MS_SNAPSHOT_DUPLICATE_TASK_LINK_REMEDIATION_REQUIRED';
    END IF;
END $$;

ALTER TABLE maintenance_schedule_calculation_items
    ADD CONSTRAINT uq_ms_calc_items_id_plan
        UNIQUE (id, plan_id);

ALTER TABLE ppr_tasks
    ADD CONSTRAINT fk_ppr_tasks_source_calculation_item_plan
        FOREIGN KEY (source_calculation_item_id, plan_id)
        REFERENCES maintenance_schedule_calculation_items(id, plan_id)
        ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_ppr_tasks_source_calculation_item
    ON ppr_tasks (source_calculation_item_id)
    WHERE source_calculation_item_id IS NOT NULL;

DROP INDEX uq_ppr_tasks_active_source_calculation_item;

CREATE INDEX idx_ms_calc_items_equipment
    ON maintenance_schedule_calculation_items (equipment_id)
    WHERE equipment_id IS NOT NULL;

CREATE INDEX idx_ms_calc_items_regulation
    ON maintenance_schedule_calculation_items (regulation_id)
    WHERE regulation_id IS NOT NULL;

CREATE INDEX idx_ms_calc_items_maintenance_rule
    ON maintenance_schedule_calculation_items (maintenance_rule_id)
    WHERE maintenance_rule_id IS NOT NULL;

CREATE INDEX idx_ms_calc_items_template
    ON maintenance_schedule_calculation_items (template_id)
    WHERE template_id IS NOT NULL;

CREATE INDEX idx_ms_calc_items_department
    ON maintenance_schedule_calculation_items (department_id)
    WHERE department_id IS NOT NULL;
