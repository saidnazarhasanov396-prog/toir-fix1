ALTER TABLE repair_request_template_actions
    ADD COLUMN IF NOT EXISTS specialisation_id uuid
        REFERENCES hr_employee_specialisations(id);

CREATE INDEX IF NOT EXISTS idx_rr_template_actions_specialisation
    ON repair_request_template_actions(specialisation_id)
    WHERE specialisation_id IS NOT NULL AND is_deleted = false;
