ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS destination_warehouse_id uuid,
    ADD COLUMN IF NOT EXISTS actual_quantity numeric(19,4),
    ADD COLUMN IF NOT EXISTS variance numeric(19,4),
    ADD COLUMN IF NOT EXISTS adjustment_reason varchar(30);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_destination_warehouse
    ON inventory_transactions (destination_warehouse_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_adjustment_reason
    ON inventory_transactions (adjustment_reason);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_inventory_tx_destination_warehouse'
          AND conrelid = 'inventory_transactions'::regclass
    ) THEN
        ALTER TABLE inventory_transactions
            ADD CONSTRAINT fk_inventory_tx_destination_warehouse
            FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses(id);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION append_inventory_phase3_role_permissions(role_code text, permissions_to_add text[])
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE roles r
    SET permissions = (
            SELECT COALESCE(jsonb_agg(DISTINCT permission), '[]'::jsonb)
            FROM (
                SELECT jsonb_array_elements_text(
                        CASE
                            WHEN jsonb_typeof(COALESCE(r.permissions, '[]'::jsonb)) = 'array'
                                THEN COALESCE(r.permissions, '[]'::jsonb)
                            ELSE '[]'::jsonb
                        END
                    ) AS permission
                UNION ALL
                SELECT unnest(permissions_to_add) AS permission
            ) merged_permissions
            WHERE permission IS NOT NULL AND btrim(permission) <> ''
        ),
        updated_at = now()
    WHERE r.code = role_code
      AND r.is_deleted = false;
END $$;

SELECT append_inventory_phase3_role_permissions('SYSTEM_ADMIN', ARRAY[
    'INVENTORY_TRANSFER',
    'INVENTORY_RETURN',
    'INVENTORY_ADJUSTMENT'
]);

SELECT append_inventory_phase3_role_permissions('STOREKEEPER', ARRAY[
    'INVENTORY_TRANSFER',
    'INVENTORY_RETURN',
    'INVENTORY_ADJUSTMENT'
]);

DROP FUNCTION append_inventory_phase3_role_permissions(text, text[]);
