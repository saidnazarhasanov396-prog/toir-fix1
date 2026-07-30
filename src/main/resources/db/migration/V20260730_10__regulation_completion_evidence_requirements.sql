CREATE TABLE maintenance_regulation_required_evidence (
    regulation_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    CONSTRAINT fk_regulation_required_evidence_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id) ON DELETE CASCADE,
    CONSTRAINT pk_regulation_required_evidence
        PRIMARY KEY (regulation_id, evidence_type),
    CONSTRAINT chk_regulation_required_evidence_type
        CHECK (evidence_type IN (
            'BEFORE_PHOTO',
            'AFTER_PHOTO',
            'MEASUREMENT',
            'DOCUMENT',
            'REPAIR_ACT',
            'STOPPAGE_ACT',
            'OTHER'
        ))
);

CREATE TABLE equipment_maintenance_rule_required_evidence (
    maintenance_rule_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    CONSTRAINT fk_rule_required_evidence_rule
        FOREIGN KEY (maintenance_rule_id) REFERENCES equipment_maintenance_rules(id) ON DELETE CASCADE,
    CONSTRAINT pk_rule_required_evidence
        PRIMARY KEY (maintenance_rule_id, evidence_type),
    CONSTRAINT chk_rule_required_evidence_type
        CHECK (evidence_type IN (
            'BEFORE_PHOTO',
            'AFTER_PHOTO',
            'MEASUREMENT',
            'DOCUMENT',
            'REPAIR_ACT',
            'STOPPAGE_ACT',
            'OTHER'
        ))
);
