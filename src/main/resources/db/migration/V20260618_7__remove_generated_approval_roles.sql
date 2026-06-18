DELETE FROM approval_template_steps step
USING approval_templates template
WHERE step.template_id = template.id
  AND step.is_deleted = false
  AND step.approver_id IS NULL
  AND (
      (
          template.code IN (
              'WORK_ORDER_APPROVAL',
              'PPR_PLAN_APPROVAL',
              'PROCUREMENT_APPROVAL',
              'BUDGET_APPROVAL',
              'MAINTENANCE_DUE_EVENT_APPROVAL',
              'MAINTENANCE_REGULATION_APPROVAL',
              'REPAIR_REQUEST_APPROVAL'
          )
          AND step.approver_role IN (
              'WORK_ORDER_APPROVER',
              'PPR_PLAN_APPROVER',
              'PROCUREMENT_APPROVER',
              'BUDGET_APPROVER',
              'MAINTENANCE_EVENT_APPROVER',
              'MAINTENANCE_REGULATION_APPROVER',
              'REPAIR_REQUEST_APPROVER'
          )
      )
      OR (
          template.approver_role IS NULL
          AND (
              (template.route_policy = 'SYSTEM_ADMIN' AND step.approver_role = 'SYSTEM_ADMIN')
              OR (template.route_policy = 'DEPARTMENT_HEAD' AND step.approver_role = 'DEPARTMENT_HEAD')
          )
      )
  );

UPDATE approval_templates
SET approver_role = NULL,
    updated_at = now()
WHERE code IN (
    'WORK_ORDER_APPROVAL',
    'PPR_PLAN_APPROVAL',
    'PROCUREMENT_APPROVAL',
    'BUDGET_APPROVAL',
    'MAINTENANCE_DUE_EVENT_APPROVAL',
    'MAINTENANCE_REGULATION_APPROVAL',
    'REPAIR_REQUEST_APPROVAL'
)
AND approver_role IN (
    'WORK_ORDER_APPROVER',
    'PPR_PLAN_APPROVER',
    'PROCUREMENT_APPROVER',
    'BUDGET_APPROVER',
    'MAINTENANCE_EVENT_APPROVER',
    'MAINTENANCE_REGULATION_APPROVER',
    'REPAIR_REQUEST_APPROVER'
);
