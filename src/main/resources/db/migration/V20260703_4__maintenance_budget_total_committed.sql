-- Finance upgrade FAZA 4: budget-level committed aggregate (mirrors line committed_amount sum).

ALTER TABLE maintenance_budgets
    ADD COLUMN IF NOT EXISTS total_committed double precision NOT NULL DEFAULT 0.0;

UPDATE maintenance_budgets mb
SET total_committed = COALESCE((
    SELECT SUM(bl.committed_amount)
    FROM budget_lines bl
    WHERE bl.budget_id = mb.id
      AND bl.is_deleted = false
), 0.0)
WHERE mb.is_deleted = false;
