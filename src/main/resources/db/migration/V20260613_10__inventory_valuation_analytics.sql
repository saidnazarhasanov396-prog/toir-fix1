ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS average_cost numeric(19,2),
    ADD COLUMN IF NOT EXISTS last_purchase_cost numeric(19,2),
    ADD COLUMN IF NOT EXISTS inventory_value numeric(19,2),
    ADD COLUMN IF NOT EXISTS criticality varchar(20);

UPDATE spare_parts
SET criticality = 'LOW'
WHERE criticality IS NULL;

CREATE INDEX IF NOT EXISTS idx_spare_parts_criticality
    ON spare_parts (criticality)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_spare_parts_inventory_value
    ON spare_parts (inventory_value)
    WHERE is_deleted = false;

CREATE OR REPLACE FUNCTION append_inventory_phase5_role_permissions(role_code text, permissions_to_add text[])
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

SELECT append_inventory_phase5_role_permissions('SYSTEM_ADMIN', ARRAY[
    'INVENTORY_ANALYTICS_READ',
    'INVENTORY_VALUATION_READ'
]);

SELECT append_inventory_phase5_role_permissions('STOREKEEPER', ARRAY[
    'INVENTORY_ANALYTICS_READ',
    'INVENTORY_VALUATION_READ'
]);

DROP FUNCTION append_inventory_phase5_role_permissions(text, text[]);
