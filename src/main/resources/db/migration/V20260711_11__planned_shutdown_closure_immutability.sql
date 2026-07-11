DO $$
BEGIN
    IF EXISTS (
        SELECT planned_shutdown_id
        FROM planned_shutdown_closure_snapshots
        GROUP BY planned_shutdown_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'PLANNED_SHUTDOWN_CLOSURE_SNAPSHOT_DUPLICATES_PRESENT';
    END IF;
END $$;

DROP INDEX IF EXISTS uq_planned_shutdown_closure_snapshots_active;

ALTER TABLE planned_shutdown_closure_snapshots
    ADD CONSTRAINT uq_planned_shutdown_closure_snapshots_shutdown
    UNIQUE (planned_shutdown_id);

CREATE OR REPLACE FUNCTION reject_planned_shutdown_closure_snapshot_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'PLANNED_SHUTDOWN_CLOSURE_SNAPSHOT_IMMUTABLE';
END;
$$;

CREATE TRIGGER trg_planned_shutdown_closure_snapshot_immutable
    BEFORE UPDATE OR DELETE ON planned_shutdown_closure_snapshots
    FOR EACH ROW
    EXECUTE FUNCTION reject_planned_shutdown_closure_snapshot_mutation();
