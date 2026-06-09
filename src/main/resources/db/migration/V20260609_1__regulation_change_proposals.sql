CREATE TABLE IF NOT EXISTS regulation_change_proposals (
                                                           id                        uuid PRIMARY KEY,
                                                           created_at                timestamptz NOT NULL,
                                                           updated_at                timestamptz NOT NULL,
                                                           is_deleted                boolean NOT NULL DEFAULT false,

                                                           regulation_id             uuid NOT NULL REFERENCES maintenance_regulations(id),
    rcm_snapshot_id           uuid,
    title                     varchar(255) NOT NULL,
    description               text,
    proposed_periodicity_value integer,
    proposed_periodicity_unit varchar(64),
    proposed_template_id      uuid,
    change_reason             text,
    status                    varchar(64) NOT NULL DEFAULT 'DRAFT',
    created_by_id             uuid,
    reviewed_by_id            uuid,
    reviewed_at               timestamptz,
    review_comment            text
    );

CREATE INDEX IF NOT EXISTS ix_rcm_change_proposal_regulation
    ON regulation_change_proposals(regulation_id);

CREATE INDEX IF NOT EXISTS ix_rcm_change_proposal_status
    ON regulation_change_proposals(status);