ALTER TABLE meter_readings
    ADD COLUMN IF NOT EXISTS repair_request_id uuid,
    ADD COLUMN IF NOT EXISTS work_order_id uuid,
    ADD COLUMN IF NOT EXISTS defect_id uuid,
    ADD COLUMN IF NOT EXISTS reading_context varchar(255) NOT NULL DEFAULT 'MANUAL_UPDATE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'meter_readings_context_check'
    ) THEN
        ALTER TABLE meter_readings
            ADD CONSTRAINT meter_readings_context_check
                CHECK (reading_context IN ('MANUAL_UPDATE', 'FAILURE_DETECTED', 'WORK_STARTED', 'WORK_COMPLETED'));
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.repair_requests') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'fk_meter_readings_repair_request'
       ) THEN
        ALTER TABLE meter_readings
            ADD CONSTRAINT fk_meter_readings_repair_request
                FOREIGN KEY (repair_request_id) REFERENCES repair_requests(id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.work_orders') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'fk_meter_readings_work_order'
       ) THEN
        ALTER TABLE meter_readings
            ADD CONSTRAINT fk_meter_readings_work_order
                FOREIGN KEY (work_order_id) REFERENCES work_orders(id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.defects') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'fk_meter_readings_defect'
       ) THEN
        ALTER TABLE meter_readings
            ADD CONSTRAINT fk_meter_readings_defect
                FOREIGN KEY (defect_id) REFERENCES defects(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_meter_readings_repair_request
    ON meter_readings (repair_request_id, read_at)
    WHERE repair_request_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_meter_readings_work_order
    ON meter_readings (work_order_id, read_at)
    WHERE work_order_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_meter_readings_defect
    ON meter_readings (defect_id, read_at)
    WHERE defect_id IS NOT NULL AND is_deleted = false;
