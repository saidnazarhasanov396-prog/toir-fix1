CREATE TABLE ppr_planning_operation_receipts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    operation_type VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ppr_planning_operation_receipt_session
        FOREIGN KEY (session_id) REFERENCES ppr_planning_sessions(id) ON DELETE CASCADE,
    CONSTRAINT uq_ppr_planning_operation_receipt
        UNIQUE (session_id, operation_type, idempotency_key)
);

CREATE INDEX idx_ppr_planning_operation_receipts_session
    ON ppr_planning_operation_receipts (session_id)
    WHERE is_deleted = FALSE;
