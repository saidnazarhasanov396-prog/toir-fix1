ALTER TABLE repair_campaigns
    ADD COLUMN IF NOT EXISTS closing_notes text;

CREATE TABLE IF NOT EXISTS repair_campaign_risks (
    id uuid PRIMARY KEY,
    campaign_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    likelihood varchar(16) NOT NULL,
    impact varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    owner_id uuid,
    mitigation_plan text,
    due_date date,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_repair_campaign_risks_campaign FOREIGN KEY (campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_repair_campaign_risks_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT chk_repair_campaign_risks_likelihood CHECK (likelihood IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_repair_campaign_risks_impact CHECK (impact IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_repair_campaign_risks_status CHECK (status IN ('OPEN','MITIGATING','ACCEPTED','CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_repair_campaign_risks_campaign
    ON repair_campaign_risks(campaign_id, created_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_campaign_risks_owner
    ON repair_campaign_risks(owner_id)
    WHERE is_deleted = false AND owner_id IS NOT NULL;
