-- W2: serialize duplicate active approvals for the same immutable calculation tuple.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approval_requests
        WHERE status = 'PENDING'
          AND is_deleted = FALSE
          AND calculation_revision IS NOT NULL
        GROUP BY
            COALESCE(target_type, document_type),
            COALESCE(target_id, document_id),
            COALESCE(action_type, 'APPROVE'),
            calculation_revision,
            calculation_content_hash,
            calculation_content_hash_version
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'PPR_ACTIVE_EXACT_APPROVAL_DUPLICATES_REMEDIATION_REQUIRED';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_approval_requests_active_exact_calculation
    ON approval_requests (
        COALESCE(target_type, document_type),
        COALESCE(target_id, document_id),
        COALESCE(action_type, 'APPROVE'),
        calculation_revision,
        calculation_content_hash,
        calculation_content_hash_version
    )
    WHERE status = 'PENDING'
      AND is_deleted = FALSE
      AND calculation_revision IS NOT NULL
      AND calculation_content_hash IS NOT NULL
      AND calculation_content_hash_version IS NOT NULL;
