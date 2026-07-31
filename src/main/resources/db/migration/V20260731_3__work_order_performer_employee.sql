ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS performer_employee_id UUID;

CREATE INDEX IF NOT EXISTS idx_work_orders_performer_employee_id
    ON work_orders (performer_employee_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_work_orders_performer_employee') THEN
        ALTER TABLE work_orders
            ADD CONSTRAINT fk_work_orders_performer_employee
                FOREIGN KEY (performer_employee_id) REFERENCES hr_employees (id);
    END IF;
END $$;

UPDATE work_orders wo
SET performer_employee_id = unique_employee.employee_id
FROM brigade_members bm
JOIN LATERAL (
    select (array_agg(e.id order by e.id))[1] as employee_id
    FROM hr_employees e
    WHERE e.user_id = bm.user_id
      AND e.is_deleted = false
    HAVING count(*) = 1
) unique_employee ON true
WHERE wo.brigade_member_id = bm.id
  AND wo.performer_employee_id IS NULL;
