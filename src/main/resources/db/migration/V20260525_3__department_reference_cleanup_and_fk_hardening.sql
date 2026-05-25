-- Production-safe repair migration:
-- 1) nullify orphan nullable department references
-- 2) harden FK coverage for key operational tables
-- This migration is additive and avoids truncate/drop/delete of business rows.

DO
$$
BEGIN
    IF to_regclass('public.departments') IS NULL THEN
        RETURN;
    END IF;

    IF to_regclass('public.brigades') IS NOT NULL THEN
        -- Orphan cleanup: nullable reference, safe to set NULL for broken links.
        UPDATE brigades b
        SET department_id = NULL
        WHERE b.department_id IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
            FROM departments d
            WHERE d.id = b.department_id
        );

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint c
            WHERE c.contype = 'f'
              AND c.conrelid = 'brigades'::regclass
              AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (department_id) references %departments%'
        ) THEN
            ALTER TABLE brigades
                ADD CONSTRAINT fk_brigades_department_id
                    FOREIGN KEY (department_id) REFERENCES departments (id);
        END IF;
    END IF;

    IF to_regclass('public.warehouses') IS NOT NULL THEN
        -- Invalid reference cleanup: nullable reference, preserve rows while removing bad link.
        UPDATE warehouses w
        SET department_id = NULL
        WHERE w.department_id IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
            FROM departments d
            WHERE d.id = w.department_id
        );

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint c
            WHERE c.contype = 'f'
              AND c.conrelid = 'warehouses'::regclass
              AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (department_id) references %departments%'
        ) THEN
            ALTER TABLE warehouses
                ADD CONSTRAINT fk_warehouses_department_id
                    FOREIGN KEY (department_id) REFERENCES departments (id);
        END IF;
    END IF;
END
$$;
