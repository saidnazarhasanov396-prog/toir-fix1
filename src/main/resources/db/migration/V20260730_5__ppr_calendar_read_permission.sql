UPDATE roles
SET permissions = COALESCE(permissions, '[]'::jsonb)
        || '["PPR_CALENDAR_READ"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = false
  AND COALESCE(permissions, '[]'::jsonb)
        @> '["PPR_PLAN_READ"]'::jsonb
  AND NOT (
      COALESCE(permissions, '[]'::jsonb)
          @> '["PPR_CALENDAR_READ"]'::jsonb
  );
