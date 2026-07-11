ALTER TABLE planned_shutdowns
    ADD COLUMN scope_version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_planned_shutdown_scope_version CHECK (scope_version >= 0);
