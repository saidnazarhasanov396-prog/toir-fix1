UPDATE approval_templates
SET target_type = 'OTHER',
    active = false,
    is_deleted = true,
    updated_at = now()
WHERE target_type = 'WAREHOUSE_WRITEOFF';

UPDATE approval_requests
SET target_type = 'OTHER',
    updated_at = now()
WHERE target_type = 'WAREHOUSE_WRITEOFF';
