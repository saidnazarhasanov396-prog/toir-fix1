-- Normalize legacy source values left behind by the removed advanced WMS writeoff module.
UPDATE warehouse_stock_ledger_metadata
SET source_type = 'MANUAL'
WHERE source_type = 'WAREHOUSE_WRITEOFF';
