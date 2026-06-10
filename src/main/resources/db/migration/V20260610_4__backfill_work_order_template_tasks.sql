WITH work_order_templates AS (
    SELECT DISTINCT
        w.id AS work_order_id,
        COALESCE(mde.template_id, emr.template_id, mr.template_id) AS template_id
    FROM work_orders w
    LEFT JOIN maintenance_due_events mde
        ON mde.id = w.maintenance_due_event_id
       AND mde.is_deleted = false
    LEFT JOIN equipment_maintenance_rules emr
        ON emr.id = mde.equipment_maintenance_rule_id
       AND emr.is_deleted = false
    LEFT JOIN maintenance_regulations mr
        ON mr.id = mde.regulation_id
       AND mr.is_deleted = false
    WHERE w.is_deleted = false
      AND w.maintenance_due_event_id IS NOT NULL
      AND COALESCE(mde.template_id, emr.template_id, mr.template_id) IS NOT NULL

    UNION

    SELECT DISTINCT
        w.id AS work_order_id,
        COALESCE(mde.template_id, task_rule.template_id, event_rule.template_id, task_regulation.template_id) AS template_id
    FROM work_orders w
    JOIN ppr_tasks pt
        ON pt.id = w.ppr_task_id
       AND pt.is_deleted = false
    LEFT JOIN maintenance_due_events mde
        ON mde.id = pt.maintenance_due_event_id
       AND mde.is_deleted = false
    LEFT JOIN equipment_maintenance_rules task_rule
        ON task_rule.id = pt.equipment_maintenance_rule_id
       AND task_rule.is_deleted = false
    LEFT JOIN equipment_maintenance_rules event_rule
        ON event_rule.id = mde.equipment_maintenance_rule_id
       AND event_rule.is_deleted = false
    LEFT JOIN maintenance_regulations task_regulation
        ON task_regulation.id = pt.regulation_id
       AND task_regulation.is_deleted = false
    WHERE w.is_deleted = false
      AND w.ppr_task_id IS NOT NULL
      AND COALESCE(mde.template_id, task_rule.template_id, event_rule.template_id, task_regulation.template_id) IS NOT NULL
),
template_operations AS (
    SELECT
        wot.work_order_id,
        wot.template_id,
        mo.id AS operation_id,
        mo.name,
        mo.description,
        mo.duration_hours,
        mo.required_skill,
        mo.safety_notes,
        mo.tools_required,
        mo.spare_parts_required,
        mo.consumables_required,
        mo.control_parameter,
        mo.instruction_url
    FROM work_order_templates wot
    JOIN maintenance_templates mt
        ON mt.id = wot.template_id
       AND mt.is_deleted = false
    JOIN maintenance_operations mo
        ON mo.template_id = mt.id
       AND mo.is_deleted = false
)
INSERT INTO work_order_tasks (
    id,
    created_at,
    updated_at,
    is_deleted,
    work_order_id,
    title,
    description,
    status,
    planned_hours,
    actual_hours,
    assigned_to_id,
    started_at,
    completed_at,
    source_template_id,
    source_operation_id
)
SELECT
    gen_random_uuid(),
    now(),
    now(),
    false,
    template_operations.work_order_id,
    template_operations.name,
    NULLIF(CONCAT_WS(E'\n',
        NULLIF(template_operations.description, ''),
        CASE WHEN NULLIF(template_operations.required_skill, '') IS NULL
            THEN NULL ELSE 'Required skill: ' || template_operations.required_skill END,
        CASE WHEN NULLIF(template_operations.safety_notes, '') IS NULL
            THEN NULL ELSE 'Safety: ' || template_operations.safety_notes END,
        CASE WHEN NULLIF(template_operations.tools_required, '') IS NULL
            THEN NULL ELSE 'Tools: ' || template_operations.tools_required END,
        CASE WHEN NULLIF(template_operations.spare_parts_required, '') IS NULL
            THEN NULL ELSE 'Spare parts: ' || template_operations.spare_parts_required END,
        CASE WHEN NULLIF(template_operations.consumables_required, '') IS NULL
            THEN NULL ELSE 'Consumables: ' || template_operations.consumables_required END,
        CASE WHEN NULLIF(template_operations.control_parameter, '') IS NULL
            THEN NULL ELSE 'Control parameter: ' || template_operations.control_parameter END,
        CASE WHEN NULLIF(template_operations.instruction_url, '') IS NULL
            THEN NULL ELSE 'Instruction: ' || template_operations.instruction_url END
    ), ''),
    'TODO',
    CASE WHEN template_operations.duration_hours > 0 THEN template_operations.duration_hours ELSE NULL END,
    NULL,
    NULL,
    NULL,
    NULL,
    template_operations.template_id,
    template_operations.operation_id
FROM template_operations
WHERE NOT EXISTS (
    SELECT 1
    FROM work_order_tasks existing
    WHERE existing.work_order_id = template_operations.work_order_id
      AND existing.is_deleted = false
      AND (
          existing.source_operation_id = template_operations.operation_id
          OR (
              existing.source_operation_id IS NULL
              AND existing.title = template_operations.name
          )
      )
);
