-- Eliminate duplicated balance storage.
-- Policy metadata is retained in warehouse_stock_policies.
-- warehouse_stocks becomes a read-only compatibility view over WMS balances.

CREATE TABLE warehouse_stock_policies (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts(id),
    min_qty double precision NOT NULL DEFAULT 0,
    max_qty double precision,
    reorder_point double precision,
    reorder_qty double precision,
    avg_daily_usage double precision,
    bin_location varchar(255),
    CONSTRAINT uq_warehouse_stock_policies_identity UNIQUE (warehouse_id, spare_part_id)
);

INSERT INTO warehouse_stock_policies (
    id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id,
    min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location
)
SELECT id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id,
       min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location
FROM warehouse_stocks;

INSERT INTO warehouse_stock_policies (
    id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, min_qty
)
SELECT gen_random_uuid(), now(), now(), false, b.warehouse_id, b.spare_part_id, 0
FROM warehouse_stock_balances b
WHERE b.is_deleted = false
  AND NOT EXISTS (
      SELECT 1
      FROM warehouse_stock_policies p
      WHERE p.warehouse_id = b.warehouse_id
        AND p.spare_part_id = b.spare_part_id
  )
GROUP BY b.warehouse_id, b.spare_part_id;

ALTER TABLE reservations
    ADD COLUMN warehouse_id uuid,
    ADD COLUMN spare_part_id uuid,
    ADD COLUMN bin_id uuid;

UPDATE reservations r
SET warehouse_id = p.warehouse_id,
    spare_part_id = p.spare_part_id
FROM warehouse_stock_policies p
WHERE p.id = r.warehouse_stock_id;

DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND conrelid = 'reservations'::regclass
          AND confrelid = 'warehouse_stocks'::regclass
    LOOP
        EXECUTE format('ALTER TABLE reservations DROP CONSTRAINT %I', fk.conname);
    END LOOP;
END
$$;

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_stock_policy
        FOREIGN KEY (warehouse_stock_id) REFERENCES warehouse_stock_policies(id);

DROP TRIGGER IF EXISTS trg_guard_legacy_warehouse_stock_quantity_write ON warehouse_stocks;
DROP FUNCTION IF EXISTS guard_legacy_warehouse_stock_quantity_write();

DROP TABLE warehouse_stocks;

CREATE VIEW warehouse_stocks AS
SELECT p.id,
       p.created_at,
       GREATEST(p.updated_at, COALESCE(b.updated_at, p.updated_at)) AS updated_at,
       p.is_deleted,
       p.warehouse_id,
       p.spare_part_id,
       COALESCE(b.qty_on_hand, 0)::double precision AS quantity,
       COALESCE(b.qty_reserved, 0)::double precision AS reserved_qty,
       p.min_qty,
       p.max_qty,
       p.reorder_point,
       p.reorder_qty,
       p.avg_daily_usage,
       p.bin_location
FROM warehouse_stock_policies p
LEFT JOIN (
    SELECT warehouse_id,
           spare_part_id,
           SUM(qty_on_hand) AS qty_on_hand,
           SUM(qty_reserved) AS qty_reserved,
           MAX(updated_at) AS updated_at
    FROM warehouse_stock_balances
    WHERE is_deleted = false
    GROUP BY warehouse_id, spare_part_id
) b
  ON b.warehouse_id = p.warehouse_id
 AND b.spare_part_id = p.spare_part_id;

COMMENT ON VIEW warehouse_stocks IS
    'Deprecated read-only compatibility view. WMS balances are authoritative.';

CREATE OR REPLACE FUNCTION seed_legacy_stock_policy_through_view()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF COALESCE(current_setting('toir.legacy_stock_projection', true), 'off') <> 'on' THEN
        RAISE EXCEPTION 'warehouse_stocks is a read-only compatibility view';
    END IF;

    INSERT INTO warehouse_stock_policies (
        id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id,
        min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location
    )
    VALUES (
        NEW.id, COALESCE(NEW.created_at, now()), COALESCE(NEW.updated_at, now()),
        COALESCE(NEW.is_deleted, false), NEW.warehouse_id, NEW.spare_part_id,
        COALESCE(NEW.min_qty, 0), NEW.max_qty, NEW.reorder_point, NEW.reorder_qty,
        NEW.avg_daily_usage, NEW.bin_location
    )
    ON CONFLICT (warehouse_id, spare_part_id) DO UPDATE
    SET min_qty = EXCLUDED.min_qty,
        max_qty = EXCLUDED.max_qty,
        reorder_point = EXCLUDED.reorder_point,
        reorder_qty = EXCLUDED.reorder_qty,
        avg_daily_usage = EXCLUDED.avg_daily_usage,
        bin_location = EXCLUDED.bin_location,
        updated_at = now();

    INSERT INTO warehouse_stock_balances (
        id, created_at, updated_at, is_deleted,
        warehouse_id, spare_part_id, bin_id, lot_number, serial_number,
        identity_key, expiry_date, qty_on_hand, qty_reserved, avg_cost, version
    )
    VALUES (
        gen_random_uuid(), now(), now(), false,
        NEW.warehouse_id, NEW.spare_part_id, NULL, NULL, NULL,
        NEW.warehouse_id::text || '|' || NEW.spare_part_id::text || '|0||',
        NULL, COALESCE(NEW.quantity, 0), COALESCE(NEW.reserved_qty, 0), NULL, 0
    )
    ON CONFLICT (identity_key) DO UPDATE
    SET qty_on_hand = EXCLUDED.qty_on_hand,
        qty_reserved = LEAST(EXCLUDED.qty_reserved, EXCLUDED.qty_on_hand),
        updated_at = now(),
        version = warehouse_stock_balances.version + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_seed_legacy_stock_policy_through_view
INSTEAD OF INSERT OR UPDATE
ON warehouse_stocks
FOR EACH ROW
EXECUTE FUNCTION seed_legacy_stock_policy_through_view();
