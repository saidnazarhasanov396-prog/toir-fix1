-- Backfill initial ActualCostReviewEvent for PENDING costs created before the fix.
-- Safe to re-run: NOT EXISTS guard prevents duplicate inserts.
INSERT INTO actual_cost_review_events (
    id,
    is_deleted,
    created_at,
    updated_at,
    actual_cost_id,
    actor_user_id,
    source,
    event_group,
    event_code,
    title,
    description,
    status,
    next_approval_role_code,
    next_threshold_hours,
    occurred_at
)
SELECT
    gen_random_uuid(),
    false,
    now(),
    now(),
    ac.id,
    null,
    'SYSTEM',
    'REVIEW',
    'CREATED',
    'Actual cost submitted for review',
    'Backfilled. Amount: ' || ac.amount::text,
    'PENDING',
    COALESCE(
        (
            SELECT r.required_role_code
            FROM financial_approval_rules r
            WHERE r.is_deleted = false
              AND r.is_active = true
              AND (r.min_amount IS NULL OR r.min_amount <= ac.amount)
              AND (r.max_amount IS NULL OR r.max_amount >= ac.amount)
            ORDER BY r.priority ASC, r.min_amount DESC NULLS LAST
            LIMIT 1
        ),
        'FINANCE_MANAGER'
    ),
    24,
    now()
FROM actual_costs ac
WHERE ac.is_deleted = false
  AND ac.status = 'PENDING'
  AND NOT EXISTS (
      SELECT 1
      FROM actual_cost_review_events e
      WHERE e.actual_cost_id = ac.id
        AND e.is_deleted = false
  );
