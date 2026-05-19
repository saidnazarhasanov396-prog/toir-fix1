DO
$$
BEGIN
    IF to_regclass('public.integration_endpoints') IS NULL THEN
        CREATE TABLE integration_endpoints (
            id uuid PRIMARY KEY,
            created_at timestamptz NOT NULL,
            updated_at timestamptz NOT NULL,
            is_deleted boolean NOT NULL DEFAULT false,
            code varchar(255) NOT NULL,
            name varchar(255) NOT NULL,
            system varchar(255) NOT NULL,
            url varchar(2048) NOT NULL,
            auth_type varchar(255),
            is_active boolean NOT NULL DEFAULT true,
            last_sync_at timestamptz,
            last_sync_status varchar(255),
            port integer,
            base_path varchar(255),
            api_key varchar(255),
            username varchar(255),
            password varchar(255),
            timeout_seconds integer,
            sync_interval_minutes integer,
            sync_work_orders boolean NOT NULL DEFAULT false,
            sync_downtimes boolean NOT NULL DEFAULT false,
            sync_defects boolean NOT NULL DEFAULT false,
            sync_scada boolean NOT NULL DEFAULT false,
            sync_production boolean NOT NULL DEFAULT false,
            last_error text
        );
    END IF;
END
$$;

ALTER TABLE integration_endpoints
    ADD COLUMN IF NOT EXISTS sync_work_orders boolean,
    ADD COLUMN IF NOT EXISTS sync_downtimes boolean,
    ADD COLUMN IF NOT EXISTS sync_defects boolean,
    ADD COLUMN IF NOT EXISTS sync_scada boolean,
    ADD COLUMN IF NOT EXISTS sync_production boolean;

UPDATE integration_endpoints
SET sync_work_orders = COALESCE(sync_work_orders, false),
    sync_downtimes = COALESCE(sync_downtimes, false),
    sync_defects = COALESCE(sync_defects, false),
    sync_scada = COALESCE(sync_scada, false),
    sync_production = COALESCE(sync_production, false);

ALTER TABLE integration_endpoints
    ALTER COLUMN sync_work_orders SET DEFAULT false,
    ALTER COLUMN sync_downtimes SET DEFAULT false,
    ALTER COLUMN sync_defects SET DEFAULT false,
    ALTER COLUMN sync_scada SET DEFAULT false,
    ALTER COLUMN sync_production SET DEFAULT false;

ALTER TABLE integration_endpoints
    ALTER COLUMN sync_work_orders SET NOT NULL,
    ALTER COLUMN sync_downtimes SET NOT NULL,
    ALTER COLUMN sync_defects SET NOT NULL,
    ALTER COLUMN sync_scada SET NOT NULL,
    ALTER COLUMN sync_production SET NOT NULL;
