ALTER TABLE approval_templates
    ADD COLUMN IF NOT EXISTS action_type varchar(50) NOT NULL DEFAULT 'APPROVE';

CREATE TABLE IF NOT EXISTS approval_template_steps (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id uuid NOT NULL REFERENCES approval_templates(id) ON DELETE CASCADE,
    step_order integer NOT NULL,
    approver_id uuid,
    approver_role varchar(120),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_approval_template_steps_order UNIQUE (template_id, step_order),
    CONSTRAINT approval_template_steps_approver_required_check CHECK (
        approver_id IS NOT NULL
        OR NULLIF(BTRIM(approver_role), '') IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_approval_template_steps_template
    ON approval_template_steps(template_id, step_order)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_approval_templates_rule_order
    ON approval_templates(target_type, action_type, code)
    WHERE is_deleted = false;

INSERT INTO approval_template_steps (template_id, step_order, approver_id, approver_role)
SELECT
    template.id,
    1,
    template.approver_id,
    CASE
        WHEN template.approver_id IS NOT NULL THEN NULL
        WHEN template.route_policy = 'SYSTEM_ADMIN' THEN 'SYSTEM_ADMIN'
        WHEN template.route_policy = 'DEPARTMENT_HEAD' THEN 'DEPARTMENT_HEAD'
        ELSE template.approver_role
    END
FROM approval_templates template
WHERE template.is_deleted = false
  AND (
      template.approver_id IS NOT NULL
      OR NULLIF(BTRIM(template.approver_role), '') IS NOT NULL
      OR template.route_policy IN ('SYSTEM_ADMIN', 'DEPARTMENT_HEAD')
  )
ON CONFLICT (template_id, step_order) DO NOTHING;
