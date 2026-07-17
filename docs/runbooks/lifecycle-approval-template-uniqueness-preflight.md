# Lifecycle Approval Template Uniqueness Preflight

Run this procedure before deploying
`V20260716_1__unique_active_lifecycle_approval_templates.sql`. The migration rollout must not begin until
the duplicate-group diagnostic returns zero rows.

## 1. Find duplicate lifecycle template groups

```sql
WITH duplicate_groups AS (
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
)
SELECT target_type, normalized_action, duplicate_count
FROM duplicate_groups
ORDER BY target_type, normalized_action;
```

If this returns no rows, proceed with the migration rollout. If it returns any row, stop and complete
the administrator review and remediation below.

## 2. Inspect the candidate rows

```sql
SELECT id, code, name, target_type,
       COALESCE(action_type, 'APPROVE') AS normalized_action,
       active, is_deleted, created_at, updated_at
FROM approval_templates
WHERE active = true
  AND is_deleted = false
  AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
  AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
ORDER BY target_type, normalized_action, updated_at DESC, id DESC;
```

For every duplicate group, an administrator must select the approved canonical template ID and record
that decision in the rollout ticket. Put only those reviewed decisions in
`lifecycle-template-remediation.csv`, with one explicit `keep_id,deactivate_id` pair for every losing
template ID. The CSV must contain only administrator-approved decisions attached to the rollout ticket.
Never infer a winner with “latest wins” logic.

## 3. Deactivate only administrator-approved losing IDs

Run this ID-bound transaction with `psql` from the directory containing the reviewed CSV:

```sql
BEGIN;
CREATE TEMP TABLE approved_lifecycle_template_remediation (
    keep_id uuid NOT NULL,
    deactivate_id uuid PRIMARY KEY,
    CHECK (keep_id <> deactivate_id)
) ON COMMIT DROP;
\copy approved_lifecycle_template_remediation(keep_id, deactivate_id) FROM 'lifecycle-template-remediation.csv' CSV HEADER

LOCK TABLE approval_templates IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    expected_update_count bigint;
    updated_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO expected_update_count
    FROM approved_lifecycle_template_remediation;

    IF expected_update_count = 0 THEN
        RAISE EXCEPTION 'Lifecycle template remediation CSV is empty';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM approved_lifecycle_template_remediation remediation
        LEFT JOIN approval_templates keep_template ON keep_template.id = remediation.keep_id
        LEFT JOIN approval_templates losing_template ON losing_template.id = remediation.deactivate_id
        WHERE keep_template.id IS NULL
           OR losing_template.id IS NULL
           OR keep_template.target_type NOT IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
           OR losing_template.target_type NOT IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
           OR COALESCE(keep_template.action_type, 'APPROVE') <> 'APPROVE'
           OR COALESCE(losing_template.action_type, 'APPROVE') <> 'APPROVE'
           OR keep_template.active = false
           OR keep_template.is_deleted = true
           OR losing_template.active = false
           OR losing_template.is_deleted = true
           OR keep_template.target_type <> losing_template.target_type
           OR COALESCE(keep_template.action_type, 'APPROVE')
              <> COALESCE(losing_template.action_type, 'APPROVE')
    ) THEN
        RAISE EXCEPTION
            'Invalid lifecycle template remediation CSV: IDs must be active, non-deleted rows in the same lifecycle APPROVE group';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM approved_lifecycle_template_remediation remediation
        JOIN approved_lifecycle_template_remediation other
          ON other.deactivate_id = remediation.keep_id
    ) THEN
        RAISE EXCEPTION 'Invalid lifecycle template remediation CSV: a keep_id cannot be deactivated';
    END IF;

    IF EXISTS (
        SELECT keep_template.target_type,
               COALESCE(keep_template.action_type, 'APPROVE')
        FROM approved_lifecycle_template_remediation remediation
        JOIN approval_templates keep_template ON keep_template.id = remediation.keep_id
        GROUP BY keep_template.target_type,
                 COALESCE(keep_template.action_type, 'APPROVE')
        HAVING COUNT(DISTINCT remediation.keep_id) <> 1
    ) THEN
        RAISE EXCEPTION
            'Invalid lifecycle template remediation CSV: each duplicate group must have exactly one administrator-approved keep_id';
    END IF;

    IF EXISTS (
        WITH active_lifecycle_templates AS (
            SELECT id,
                   target_type,
                   COALESCE(action_type, 'APPROVE') AS normalized_action
            FROM approval_templates
            WHERE active = true
              AND is_deleted = false
              AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
              AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
        ),
        duplicate_groups AS (
            SELECT target_type, normalized_action
            FROM active_lifecycle_templates
            GROUP BY target_type, normalized_action
            HAVING COUNT(*) > 1
        ),
        approved_groups AS (
            SELECT keep_template.target_type,
                   COALESCE(keep_template.action_type, 'APPROVE') AS normalized_action,
                   remediation.keep_id
            FROM approved_lifecycle_template_remediation remediation
            JOIN approval_templates keep_template ON keep_template.id = remediation.keep_id
            GROUP BY keep_template.target_type,
                     COALESCE(keep_template.action_type, 'APPROVE'),
                     remediation.keep_id
        )
        SELECT 1
        FROM duplicate_groups duplicate_group
        LEFT JOIN approved_groups approved_group
          ON approved_group.target_type = duplicate_group.target_type
         AND approved_group.normalized_action = duplicate_group.normalized_action
        WHERE approved_group.keep_id IS NULL
           OR EXISTS (
                SELECT 1
                FROM active_lifecycle_templates template
                WHERE template.target_type = duplicate_group.target_type
                  AND template.normalized_action = duplicate_group.normalized_action
                  AND template.id <> approved_group.keep_id
                  AND NOT EXISTS (
                      SELECT 1
                      FROM approved_lifecycle_template_remediation remediation
                      WHERE remediation.keep_id = approved_group.keep_id
                        AND remediation.deactivate_id = template.id
                  )
           )
        UNION ALL
        SELECT 1
        FROM approved_groups approved_group
        LEFT JOIN duplicate_groups duplicate_group
          ON duplicate_group.target_type = approved_group.target_type
         AND duplicate_group.normalized_action = approved_group.normalized_action
        WHERE duplicate_group.target_type IS NULL
    ) THEN
        RAISE EXCEPTION
            'Invalid lifecycle template remediation CSV: reviewed pairs must exactly cover every active duplicate group';
    END IF;

    UPDATE approval_templates template
    SET active = false, updated_at = now()
    FROM approved_lifecycle_template_remediation remediation
    WHERE template.id = remediation.deactivate_id
      AND template.active = true
      AND template.is_deleted = false;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    IF updated_count <> expected_update_count THEN
        RAISE EXCEPTION
            'Lifecycle template remediation update count mismatch: expected %, updated %',
            expected_update_count,
            updated_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM approval_templates
        WHERE active = true
          AND is_deleted = false
          AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
          AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
        GROUP BY target_type, COALESCE(action_type, 'APPROVE')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'MULTIPLE_ACTIVE_TEMPLATES remain after approved remediation';
    END IF;
END $$;
COMMIT;
```

The table lock prevents concurrent template writes between validation, deactivation, and the post-update
invariant check. Any validation failure or update-count mismatch rolls back the entire transaction. The
script never chooses a canonical template: every `keep_id` and every losing `deactivate_id` must come from
the administrator-approved rollout-ticket attachment. Rerun the duplicate-group diagnostic from step 1.
Do not begin the migration rollout until it returns zero rows.
