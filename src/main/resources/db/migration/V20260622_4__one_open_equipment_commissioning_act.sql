WITH ranked_open_acts AS (
    SELECT
        id,
        approval_request_id,
        ROW_NUMBER() OVER (
            PARTITION BY equipment_id
            ORDER BY
                CASE status
                    WHEN 'PENDING_APPROVAL' THEN 0
                    ELSE 1
                END,
                updated_at DESC,
                created_at DESC,
                id DESC
        ) AS row_number
    FROM equipment_commissioning_acts
    WHERE is_deleted = false
      AND status IN ('DRAFT', 'PENDING_APPROVAL')
)
UPDATE approval_requests
SET status = 'CANCELLED',
    completed_at = COALESCE(completed_at, now()),
    updated_at = now()
WHERE status = 'PENDING'
  AND id IN (
      SELECT approval_request_id
      FROM ranked_open_acts
      WHERE row_number > 1
        AND approval_request_id IS NOT NULL
  );

WITH ranked_open_acts AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY equipment_id
            ORDER BY
                CASE status
                    WHEN 'PENDING_APPROVAL' THEN 0
                    ELSE 1
                END,
                updated_at DESC,
                created_at DESC,
                id DESC
        ) AS row_number
    FROM equipment_commissioning_acts
    WHERE is_deleted = false
      AND status IN ('DRAFT', 'PENDING_APPROVAL')
)
UPDATE equipment_commissioning_acts
SET status = 'CANCELLED',
    updated_at = now()
WHERE id IN (
    SELECT id
    FROM ranked_open_acts
    WHERE row_number > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_commissioning_open_equipment
    ON equipment_commissioning_acts (equipment_id)
    WHERE is_deleted = false
      AND status IN ('DRAFT', 'PENDING_APPROVAL');
