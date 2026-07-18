ALTER TABLE erp_employee_clearance_outbox
    ADD COLUMN offboarding_case_id uuid;

CREATE INDEX idx_erp_employee_clearance_outbox_case
    ON erp_employee_clearance_outbox (offboarding_case_id, source_revision)
    WHERE offboarding_case_id IS NOT NULL;
