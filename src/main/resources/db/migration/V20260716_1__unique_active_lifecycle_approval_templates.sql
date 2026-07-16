LOCK TABLE approval_templates IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE duplicate_row record;
BEGIN
    FOR duplicate_row IN
        SELECT target_type,
               COALESCE(action_type, 'APPROVE') AS normalized_action,
               COUNT(*) AS duplicate_count
        FROM approval_templates
        WHERE active = true
          AND is_deleted = false
          AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
          AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
        GROUP BY target_type, COALESCE(action_type, 'APPROVE')
        HAVING COUNT(*) > 1
    LOOP
        RAISE EXCEPTION
            'MULTIPLE_ACTIVE_TEMPLATES target=% action=% count=%',
            duplicate_row.target_type,
            duplicate_row.normalized_action,
            duplicate_row.duplicate_count;
    END LOOP;
END $$;

CREATE UNIQUE INDEX uq_active_lifecycle_approval_template
    ON approval_templates (target_type, COALESCE(action_type, 'APPROVE'))
    WHERE active = true
      AND is_deleted = false
      AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
      AND COALESCE(action_type, 'APPROVE') = 'APPROVE';
