DO $$
DECLARE invalid boolean;
BEGIN
    IF to_regclass('public.reservations') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'reservations'
             AND column_name = 'quantity' AND data_type IN ('double precision', 'real'))
       THEN
        EXECUTE 'SELECT EXISTS (SELECT 1 FROM reservations
                 WHERE quantity::text IN (''NaN'', ''Infinity'', ''-Infinity'')
                    OR quantity < 0 OR abs(quantity) >= 1000000000000000)' INTO invalid;
        IF invalid THEN
            RAISE EXCEPTION 'RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED';
        END IF;
    END IF;

    IF to_regclass('public.work_order_spare_part_requirements') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'work_order_spare_part_requirements'
             AND column_name = 'required_qty' AND data_type IN ('double precision', 'real'))
       THEN
        EXECUTE 'SELECT EXISTS (SELECT 1 FROM work_order_spare_part_requirements
                 WHERE required_qty::text IN (''NaN'', ''Infinity'', ''-Infinity'')
                    OR required_qty < 0 OR abs(required_qty) >= 1000000000000000)' INTO invalid;
        IF invalid THEN
            RAISE EXCEPTION 'RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED';
        END IF;
    END IF;
END $$;
