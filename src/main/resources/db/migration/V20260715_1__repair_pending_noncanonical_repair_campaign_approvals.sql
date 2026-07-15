WITH expected_route(step_number, approver_role) AS (VALUES
    (1, 'REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER'),
    (2, 'REPAIR_CAMPAIGN_PRODUCTION_APPROVER'),
    (3, 'REPAIR_CAMPAIGN_WAREHOUSE_APPROVER'),
    (4, 'REPAIR_CAMPAIGN_PROCUREMENT_APPROVER'),
    (5, 'REPAIR_CAMPAIGN_FINANCE_APPROVER'),
    (6, 'REPAIR_CAMPAIGN_HSE_APPROVER'),
    (7, 'REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER')
), pending_repair_campaign_approvals AS (
    SELECT ar.id
    FROM approval_requests ar
    WHERE ar.status = 'PENDING'
      AND ar.is_deleted = false
      AND COALESCE(ar.target_type, ar.document_type) = 'REPAIR_CAMPAIGN'
      AND COALESCE(ar.action_type, 'APPROVE') = 'APPROVE'
), noncanonical AS (
    SELECT pending.id
    FROM pending_repair_campaign_approvals pending
    WHERE (
        SELECT COUNT(*)
        FROM approval_steps step
        WHERE step.request_id = pending.id
          AND step.is_deleted = false
    ) <> 7
    OR EXISTS (
        SELECT 1
        FROM expected_route expected
        WHERE (
            SELECT COUNT(*)
            FROM approval_steps step
            WHERE step.request_id = pending.id
              AND step.is_deleted = false
              AND step.step_number = expected.step_number
              AND step.approver_role = expected.approver_role
        ) <> 1
    )
    OR EXISTS (
        SELECT 1
        FROM approval_steps step
        WHERE step.request_id = pending.id
          AND step.is_deleted = false
          AND NOT EXISTS (
              SELECT 1
              FROM expected_route expected
              WHERE expected.step_number = step.step_number
                AND expected.approver_role = step.approver_role
          )
    )
), updated AS (
    UPDATE approval_requests ar
    SET status = 'CANCELLED',
        completed_at = now(),
        failure_reason = 'NONCANONICAL_REPAIR_CAMPAIGN_ROUTE',
        updated_at = now()
    FROM noncanonical n
    WHERE ar.id = n.id
      AND ar.status = 'PENDING'
    RETURNING ar.id, ar.target_type, ar.target_id, ar.action_type
)
INSERT INTO approval_history (
    id, approval_id, old_status, new_status, changed_by, comment, changed_at, action_type,
    target_type, target_id, is_deleted, created_at, updated_at
)
SELECT gen_random_uuid(), updated.id, 'PENDING', 'CANCELLED', NULL,
       'NONCANONICAL_REPAIR_CAMPAIGN_ROUTE', now(), 'CANCEL',
       updated.target_type, updated.target_id, false, now(), now()
FROM updated;
