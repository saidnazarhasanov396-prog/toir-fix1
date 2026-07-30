ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS chk_approval_requests_calculation_tuple;

ALTER TABLE approval_requests
    ADD CONSTRAINT chk_approval_requests_calculation_tuple
        CHECK (
            (
                calculation_revision IS NULL
                AND calculation_content_hash IS NULL
                AND calculation_content_hash_version IS NULL
            )
            OR
            (
                calculation_revision IS NOT NULL
                AND calculation_content_hash IS NOT NULL
                AND calculation_content_hash_version IS NOT NULL
                AND calculation_revision >= 1
                AND calculation_content_hash ~ '^[0-9a-f]{64}$'
                AND calculation_content_hash_version >= 1
            )
        );
