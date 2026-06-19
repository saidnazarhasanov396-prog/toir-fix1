-- Backfill the WMS ledger from legacy history without changing live WMS balances.
--
-- Historical stock_movements before the original WMS migration are imported.
-- The two known post-cutover write gaps are also imported explicitly:
--   * purchase-order receipts
--   * repair/work-order material usage
--
-- Explicit reconciliation entries then make each ledger total equal its
-- authoritative WMS balance. Deterministic idempotency keys make the migration
-- safe to retry.

INSERT INTO warehouse_stock_balances (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    identity_key,
    expiry_date,
    qty_on_hand,
    qty_reserved,
    avg_cost,
    version
)
SELECT
    gen_random_uuid(),
    COALESCE(ws.created_at, now()),
    COALESCE(ws.updated_at, now()),
    false,
    ws.warehouse_id,
    ws.spare_part_id,
    NULL,
    NULL,
    NULL,
    ws.warehouse_id::text || '|' || ws.spare_part_id::text || '|0||',
    NULL,
    GREATEST(COALESCE(ws.quantity, 0), 0)::numeric(19,4),
    LEAST(
        GREATEST(COALESCE(ws.reserved_qty, 0), 0),
        GREATEST(COALESCE(ws.quantity, 0), 0)
    )::numeric(19,4),
    NULL,
    0
FROM warehouse_stocks ws
WHERE COALESCE(ws.is_deleted, false) = false
  AND ws.warehouse_id IS NOT NULL
  AND ws.spare_part_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM warehouse_stock_balances b
      WHERE b.identity_key = ws.warehouse_id::text || '|' || ws.spare_part_id::text || '|0||'
        AND b.is_deleted = false
  );

WITH wms_cutover AS (
    SELECT COALESCE(
        (
            SELECT installed_on
            FROM flyway_schema_history
            WHERE version = '20260613.14'
              AND success = true
            ORDER BY installed_rank DESC
            LIMIT 1
        ),
        TIMESTAMP '2026-06-13 00:00:00'
    ) AS installed_on
),
physical_history AS (
    SELECT sm.*
    FROM stock_movements sm
    CROSS JOIN wms_cutover cutover
    WHERE sm.is_deleted = false
      AND sm.spare_part_id IS NOT NULL
      AND sm.type IN ('RECEIPT', 'RETURN', 'ISSUE', 'TRANSFER', 'ADJUSTMENT')
      AND (
          sm.created_at < cutover.installed_on
          OR sm.source_type = 'PURCHASE_ORDER'
          OR EXISTS (
              SELECT 1
              FROM repair_material_usages usage
              WHERE usage.stock_movement_id = sm.id
                AND usage.is_deleted = false
          )
      )
)
INSERT INTO warehouse_stock_ledgers (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    movement_type,
    quantity,
    unit_cost,
    total_cost,
    reference_type,
    reference_id,
    reference_doc_no,
    idempotency_key,
    posted_at,
    notes
)
SELECT
    gen_random_uuid(),
    COALESCE(sm.created_at, now()),
    COALESCE(sm.updated_at, now()),
    false,
    sm.warehouse_id,
    sm.spare_part_id,
    NULL,
    NULL,
    NULL,
    CASE
        WHEN sm.type = 'RECEIPT' THEN 'RECEIPT'
        WHEN sm.type = 'RETURN' THEN 'RETURN'
        WHEN sm.type = 'ISSUE' THEN 'ISSUE'
        WHEN sm.type = 'TRANSFER' AND sm.source_type = 'MANUAL' THEN 'TRANSFER_OUT'
        WHEN sm.type = 'TRANSFER' AND sm.quantity >= 0 THEN 'TRANSFER_IN'
        WHEN sm.type = 'TRANSFER' THEN 'TRANSFER_OUT'
        WHEN sm.type = 'ADJUSTMENT' AND sm.quantity >= 0 THEN 'ADJUSTMENT_INC'
        ELSE 'ADJUSTMENT_DEC'
    END,
    CASE
        WHEN sm.type IN ('RECEIPT', 'RETURN') THEN abs(sm.quantity)
        WHEN sm.type = 'ISSUE' THEN -abs(sm.quantity)
        WHEN sm.type = 'TRANSFER' AND sm.source_type = 'MANUAL' THEN -abs(sm.quantity)
        WHEN sm.type = 'TRANSFER' THEN sm.quantity
        ELSE sm.quantity
    END::numeric(19,4),
    sm.unit_cost::numeric(19,4),
    CASE
        WHEN sm.unit_cost IS NULL THEN NULL
        ELSE (
            CASE
                WHEN sm.type IN ('RECEIPT', 'RETURN') THEN abs(sm.quantity)
                WHEN sm.type = 'ISSUE' THEN -abs(sm.quantity)
                WHEN sm.type = 'TRANSFER' AND sm.source_type = 'MANUAL' THEN -abs(sm.quantity)
                WHEN sm.type = 'TRANSFER' THEN sm.quantity
                ELSE sm.quantity
            END * sm.unit_cost
        )::numeric(19,2)
    END,
    'LEGACY_STOCK_MOVEMENT',
    sm.id,
    sm.document_number,
    'legacy-stock-movement:' || sm.id::text,
    COALESCE(sm.occurred_at, sm.created_at, now()),
    COALESCE(sm.notes, sm.comment, 'Backfilled from legacy stock movement')
FROM physical_history sm
ON CONFLICT (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false
DO NOTHING;

WITH wms_cutover AS (
    SELECT COALESCE(
        (
            SELECT installed_on
            FROM flyway_schema_history
            WHERE version = '20260613.14'
              AND success = true
            ORDER BY installed_rank DESC
            LIMIT 1
        ),
        TIMESTAMP '2026-06-13 00:00:00'
    ) AS installed_on
),
reservation_history AS (
    SELECT sm.*
    FROM stock_movements sm
    CROSS JOIN wms_cutover cutover
    WHERE sm.is_deleted = false
      AND sm.spare_part_id IS NOT NULL
      AND sm.type IN ('RESERVATION', 'RELEASE')
      AND sm.created_at < cutover.installed_on
)
INSERT INTO warehouse_reservation_ledgers (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    movement_type,
    quantity,
    reference_type,
    reference_id,
    reference_doc_no,
    idempotency_key,
    posted_at,
    notes
)
SELECT
    gen_random_uuid(),
    COALESCE(sm.created_at, now()),
    COALESCE(sm.updated_at, now()),
    false,
    sm.warehouse_id,
    sm.spare_part_id,
    NULL,
    NULL,
    NULL,
    CASE WHEN sm.type = 'RESERVATION' THEN 'RESERVE' ELSE 'RELEASE' END,
    CASE
        WHEN sm.type = 'RESERVATION' THEN abs(sm.quantity)
        ELSE -abs(sm.quantity)
    END::numeric(19,4),
    'LEGACY_STOCK_MOVEMENT',
    sm.id,
    sm.document_number,
    'legacy-reservation-movement:' || sm.id::text,
    COALESCE(sm.occurred_at, sm.created_at, now()),
    COALESCE(sm.notes, sm.comment, 'Backfilled from legacy reservation movement')
FROM reservation_history sm
ON CONFLICT (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false
DO NOTHING;

WITH balance_totals AS (
    SELECT warehouse_id,
           spare_part_id,
           SUM(qty_on_hand)::numeric(19,4) AS qty_on_hand
    FROM warehouse_stock_balances
    WHERE is_deleted = false
    GROUP BY warehouse_id, spare_part_id
),
ledger_totals AS (
    SELECT warehouse_id,
           spare_part_id,
           SUM(quantity)::numeric(19,4) AS quantity
    FROM warehouse_stock_ledgers
    WHERE is_deleted = false
      AND (
          idempotency_key IS NULL
          OR idempotency_key NOT LIKE 'wms-backfill-opening-stock:%'
      )
    GROUP BY warehouse_id, spare_part_id
),
reconciliation AS (
    SELECT b.warehouse_id,
           b.spare_part_id,
           b.qty_on_hand - COALESCE(l.quantity, 0) AS delta
    FROM balance_totals b
    LEFT JOIN ledger_totals l
           ON l.warehouse_id = b.warehouse_id
          AND l.spare_part_id = b.spare_part_id
)
INSERT INTO warehouse_stock_ledgers (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    movement_type,
    quantity,
    unit_cost,
    total_cost,
    reference_type,
    reference_id,
    reference_doc_no,
    idempotency_key,
    posted_at,
    notes
)
SELECT
    gen_random_uuid(),
    now(),
    now(),
    false,
    r.warehouse_id,
    r.spare_part_id,
    NULL,
    NULL,
    NULL,
    CASE WHEN r.delta >= 0 THEN 'ADJUSTMENT_INC' ELSE 'ADJUSTMENT_DEC' END,
    r.delta,
    NULL,
    NULL,
    'WMS_BACKFILL_OPENING',
    NULL,
    NULL,
    'wms-backfill-opening-stock:' || r.warehouse_id::text || ':' || r.spare_part_id::text,
    now(),
    'Opening reconciliation after legacy stock movement backfill'
FROM reconciliation r
WHERE r.delta <> 0
ON CONFLICT (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false
DO UPDATE SET movement_type = EXCLUDED.movement_type,
              quantity = EXCLUDED.quantity,
              updated_at = now(),
              notes = EXCLUDED.notes;

WITH balance_totals AS (
    SELECT warehouse_id,
           spare_part_id,
           SUM(qty_reserved)::numeric(19,4) AS qty_reserved
    FROM warehouse_stock_balances
    WHERE is_deleted = false
    GROUP BY warehouse_id, spare_part_id
),
ledger_totals AS (
    SELECT warehouse_id,
           spare_part_id,
           SUM(quantity)::numeric(19,4) AS quantity
    FROM warehouse_reservation_ledgers
    WHERE is_deleted = false
      AND (
          idempotency_key IS NULL
          OR idempotency_key NOT LIKE 'wms-backfill-opening-reservation:%'
      )
    GROUP BY warehouse_id, spare_part_id
),
reconciliation AS (
    SELECT b.warehouse_id,
           b.spare_part_id,
           b.qty_reserved - COALESCE(l.quantity, 0) AS delta
    FROM balance_totals b
    LEFT JOIN ledger_totals l
           ON l.warehouse_id = b.warehouse_id
          AND l.spare_part_id = b.spare_part_id
)
INSERT INTO warehouse_reservation_ledgers (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    movement_type,
    quantity,
    reference_type,
    reference_id,
    reference_doc_no,
    idempotency_key,
    posted_at,
    notes
)
SELECT
    gen_random_uuid(),
    now(),
    now(),
    false,
    r.warehouse_id,
    r.spare_part_id,
    NULL,
    NULL,
    NULL,
    CASE WHEN r.delta >= 0 THEN 'RESERVE' ELSE 'RELEASE' END,
    r.delta,
    'WMS_BACKFILL_OPENING',
    NULL,
    NULL,
    'wms-backfill-opening-reservation:' || r.warehouse_id::text || ':' || r.spare_part_id::text,
    now(),
    'Opening reconciliation after legacy reservation movement backfill'
FROM reconciliation r
WHERE r.delta <> 0
ON CONFLICT (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false
DO UPDATE SET movement_type = EXCLUDED.movement_type,
              quantity = EXCLUDED.quantity,
              updated_at = now(),
              notes = EXCLUDED.notes;
