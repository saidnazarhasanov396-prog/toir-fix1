ALTER TABLE repair_campaigns
    DROP CONSTRAINT IF EXISTS chk_repair_campaign_approval_scope;

ALTER TABLE repair_campaigns
    ADD CONSTRAINT chk_repair_campaign_approval_scope CHECK (
        (approval_scope_version IS NULL AND approval_scope_hash IS NULL)
        OR (approval_scope_version IS NOT NULL AND approval_scope_hash IS NOT NULL)
    );
