UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["PPR_CALENDAR_CREATE"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb) @> '["PPR_PLAN_CREATE"]'::jsonb
  AND NOT (COALESCE(permissions, '[]'::jsonb) @> '["PPR_CALENDAR_CREATE"]'::jsonb);

UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["PPR_CALENDAR_UPDATE"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb) @> '["PPR_PLAN_UPDATE"]'::jsonb
  AND NOT (COALESCE(permissions, '[]'::jsonb) @> '["PPR_CALENDAR_UPDATE"]'::jsonb);

UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["PPR_CALENDAR_DELETE"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb) @> '["PPR_PLAN_DELETE"]'::jsonb
  AND NOT (COALESCE(permissions, '[]'::jsonb) @> '["PPR_CALENDAR_DELETE"]'::jsonb);

UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["PPR_CALENDAR_APPROVE"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb) @> '["PPR_PLAN_APPROVE"]'::jsonb
  AND NOT (COALESCE(permissions, '[]'::jsonb) @> '["PPR_CALENDAR_APPROVE"]'::jsonb);

UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["PPR_CALENDAR_GENERATE"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb) @> '["PPR_PLAN_GENERATE"]'::jsonb
  AND NOT (COALESCE(permissions, '[]'::jsonb) @> '["PPR_CALENDAR_GENERATE"]'::jsonb);
