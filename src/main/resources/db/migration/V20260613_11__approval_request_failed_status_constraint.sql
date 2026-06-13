ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_status_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_status_check
    CHECK (status IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED',
        'EXPIRED',
        'FAILED'
    ));
