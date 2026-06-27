CREATE TABLE IF NOT EXISTS external_entity_links (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    source_system VARCHAR(64) NOT NULL,
    source_entity_type VARCHAR(64) NOT NULL,
    source_entity_id VARCHAR(128) NOT NULL,
    target_system VARCHAR(64) NOT NULL,
    target_entity_type VARCHAR(64) NOT NULL,
    target_entity_id UUID,
    natural_key VARCHAR(255),
    last_payload_hash VARCHAR(128),
    last_synced_at TIMESTAMP,
    sync_status VARCHAR(32),
    last_error TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_external_entity_links_source_active
    ON external_entity_links (source_system, source_entity_type, source_entity_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_external_entity_links_target
    ON external_entity_links (target_system, target_entity_type, target_entity_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_external_entity_links_natural_key
    ON external_entity_links (source_system, source_entity_type, natural_key)
    WHERE is_deleted = FALSE;
