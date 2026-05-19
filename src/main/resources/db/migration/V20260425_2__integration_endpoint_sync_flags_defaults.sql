DO
$$
BEGIN
    IF to_regclass('public.integration_endpoints') IS NOT NULL THEN
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
    END IF;
END
$$;
