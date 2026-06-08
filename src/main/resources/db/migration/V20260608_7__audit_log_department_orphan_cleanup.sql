-- Keep audit-log listing resilient on legacy databases with stale department links.
-- The application no longer dereferences User.department for audit-log rows, but
-- cleaning nullable orphan references prevents the same Hibernate failure elsewhere.

DO
$$
BEGIN
    IF to_regclass('public.departments') IS NULL THEN
        RETURN;
    END IF;

    IF to_regclass('public.users') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = current_schema()
             AND table_name = 'users'
             AND column_name = 'department_id'
       ) THEN
        UPDATE users u
        SET department_id = NULL
        WHERE u.department_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM departments d
              WHERE d.id = u.department_id
          );

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint c
            WHERE c.contype = 'f'
              AND c.conrelid = 'users'::regclass
              AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (department_id) references %departments%'
        ) THEN
            ALTER TABLE users
                ADD CONSTRAINT fk_users_department_id
                    FOREIGN KEY (department_id) REFERENCES departments (id);
        END IF;
    END IF;

    IF to_regclass('public.audit_logs') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = current_schema()
             AND table_name = 'audit_logs'
             AND column_name = 'department_id'
       ) THEN
        UPDATE audit_logs al
        SET department_id = NULL
        WHERE al.department_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM departments d
              WHERE d.id = al.department_id
          );
    END IF;
END
$$;
