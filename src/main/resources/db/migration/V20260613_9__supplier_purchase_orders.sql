CREATE TABLE IF NOT EXISTS suppliers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    contact_person varchar(255),
    phone varchar(255),
    email varchar(255),
    address text,
    tax_number varchar(255),
    active boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_suppliers_code
    ON suppliers (code)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_suppliers_active
    ON suppliers (active)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    number varchar(100) NOT NULL UNIQUE,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    procurement_request_id uuid REFERENCES procurement_requests(id),
    status varchar(30) NOT NULL,
    order_date date NOT NULL,
    expected_delivery_date date,
    received_date date,
    total_amount numeric(19,2) NOT NULL DEFAULT 0,
    comment text,
    created_by uuid
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_status
    ON purchase_orders (status)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_id
    ON purchase_orders (supplier_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_purchase_orders_warehouse_id
    ON purchase_orders (warehouse_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    purchase_order_id uuid NOT NULL REFERENCES purchase_orders(id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts(id),
    ordered_quantity numeric(19,4) NOT NULL,
    received_quantity numeric(19,4) NOT NULL DEFAULT 0,
    remaining_quantity numeric(19,4) NOT NULL,
    unit_price numeric(19,2) NOT NULL DEFAULT 0,
    total_amount numeric(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT chk_purchase_order_lines_ordered_positive CHECK (ordered_quantity > 0),
    CONSTRAINT chk_purchase_order_lines_received_nonnegative CHECK (received_quantity >= 0),
    CONSTRAINT chk_purchase_order_lines_remaining_nonnegative CHECK (remaining_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_order_lines_purchase_order_id
    ON purchase_order_lines (purchase_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_purchase_order_lines_spare_part_id
    ON purchase_order_lines (spare_part_id)
    WHERE is_deleted = false;

ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS preferred_supplier_id uuid,
    ADD COLUMN IF NOT EXISTS lead_time_days integer,
    ADD COLUMN IF NOT EXISTS last_purchase_price numeric(19,2);

CREATE INDEX IF NOT EXISTS idx_spare_parts_preferred_supplier_id
    ON spare_parts (preferred_supplier_id)
    WHERE is_deleted = false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_spare_parts_preferred_supplier'
          AND conrelid = 'spare_parts'::regclass
    ) THEN
        ALTER TABLE spare_parts
            ADD CONSTRAINT fk_spare_parts_preferred_supplier
            FOREIGN KEY (preferred_supplier_id) REFERENCES suppliers(id);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION append_procurement_phase4_role_permissions(role_code text, permissions_to_add text[])
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

SELECT append_procurement_phase4_role_permissions('SYSTEM_ADMIN', ARRAY[
    'SUPPLIER_READ',
    'SUPPLIER_CREATE',
    'SUPPLIER_UPDATE',
    'PURCHASE_ORDER_READ',
    'PURCHASE_ORDER_CREATE',
    'PURCHASE_ORDER_APPROVE',
    'PURCHASE_ORDER_SEND',
    'PURCHASE_ORDER_RECEIVE',
    'PURCHASE_ORDER_CANCEL'
]);

SELECT append_procurement_phase4_role_permissions('STOREKEEPER', ARRAY[
    'SUPPLIER_READ',
    'PURCHASE_ORDER_READ',
    'PURCHASE_ORDER_CREATE',
    'PURCHASE_ORDER_RECEIVE'
]);

DROP FUNCTION append_procurement_phase4_role_permissions(text, text[]);
