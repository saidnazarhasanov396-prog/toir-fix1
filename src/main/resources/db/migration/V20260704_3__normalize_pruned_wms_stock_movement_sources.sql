-- Normalize legacy source values left behind by the removed advanced WMS writeoff module.
UPDATE stock_movements
SET source_type = 'MANUAL'
WHERE source_type = 'WAREHOUSE_WRITEOFF';
