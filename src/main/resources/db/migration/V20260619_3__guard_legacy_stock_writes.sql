-- Option B enforcement:
-- WMS balances/ledgers are authoritative.
-- Legacy warehouse quantities may only be refreshed by LegacyStockProjectionService.
-- Legacy stock movements remain compatibility history and are append-only.

CREATE OR REPLACE FUNCTION guard_legacy_warehouse_stock_quantity_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    projection_write_allowed boolean :=
        COALESCE(current_setting('toir.legacy_stock_projection', true), 'off') = 'on';
BEGIN
    IF TG_OP = 'INSERT' AND NOT projection_write_allowed THEN
        RAISE EXCEPTION
            'warehouse_stocks is a read-only WMS projection; create stock through WMS';
    END IF;

    IF TG_OP = 'DELETE' AND NOT projection_write_allowed THEN
        RAISE EXCEPTION
            'warehouse_stocks is a read-only WMS projection; delete is forbidden';
    END IF;

    IF TG_OP = 'UPDATE'
       AND (
           NEW.quantity IS DISTINCT FROM OLD.quantity
           OR NEW.reserved_qty IS DISTINCT FROM OLD.reserved_qty
       )
       AND NOT projection_write_allowed THEN
        RAISE EXCEPTION
            'warehouse_stocks quantity is a read-only WMS projection';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_legacy_warehouse_stock_quantity_write
    ON warehouse_stocks;

CREATE TRIGGER trg_guard_legacy_warehouse_stock_quantity_write
BEFORE INSERT OR UPDATE OR DELETE
ON warehouse_stocks
FOR EACH ROW
EXECUTE FUNCTION guard_legacy_warehouse_stock_quantity_write();

CREATE OR REPLACE FUNCTION guard_legacy_stock_movement_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'stock_movements is append-only compatibility history; use WMS ledgers for corrections';
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_legacy_stock_movement_mutation
    ON stock_movements;

CREATE TRIGGER trg_guard_legacy_stock_movement_mutation
BEFORE UPDATE OR DELETE
ON stock_movements
FOR EACH ROW
EXECUTE FUNCTION guard_legacy_stock_movement_mutation();

COMMENT ON TABLE warehouse_stocks IS
    'Deprecated compatibility projection. Quantities are sourced from warehouse_stock_balances.';

COMMENT ON COLUMN warehouse_stocks.quantity IS
    'Read-only projection of WMS qty_on_hand.';

COMMENT ON COLUMN warehouse_stocks.reserved_qty IS
    'Read-only projection of WMS qty_reserved.';

COMMENT ON TABLE stock_movements IS
    'Deprecated append-only compatibility history. WMS history is warehouse_stock_ledgers.';
