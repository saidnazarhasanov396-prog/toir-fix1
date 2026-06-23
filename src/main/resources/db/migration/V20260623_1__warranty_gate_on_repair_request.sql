ALTER TABLE repair_requests
    ADD COLUMN warranty_active_at_creation BOOLEAN,
    ADD COLUMN warranty_handling VARCHAR(50),
    ADD COLUMN warranty_decision_comment TEXT,
    ADD COLUMN supplier_contacted_at TIMESTAMP,
    ADD COLUMN supplier_response TEXT,
    ADD COLUMN emergency_reason TEXT,
    ADD COLUMN warranty_decision_at TIMESTAMP,
    ADD COLUMN warranty_decision_by_user_id UUID;
