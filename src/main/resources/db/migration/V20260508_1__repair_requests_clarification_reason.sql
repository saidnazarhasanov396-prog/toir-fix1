ALTER TABLE repair_requests
    ADD COLUMN clarification_reason text;

UPDATE repair_requests
SET clarification_reason = rejection_reason
WHERE status = 'NEEDS_CLARIFICATION'
  AND clarification_reason IS NULL
  AND rejection_reason IS NOT NULL;

UPDATE repair_requests
SET rejection_reason = NULL
WHERE status = 'NEEDS_CLARIFICATION';
