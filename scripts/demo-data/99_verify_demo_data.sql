-- Demo data verification report
-- Purpose:
-- 1) list every public base table except flyway_schema_history
-- 2) count rows per table
-- 3) flag tables below minimum threshold (< 15)
-- 4) allow technical-table exclusions with explicit reason
-- 5) return result ordered by row_count ascending

BEGIN;

-- Adjustable threshold for non-excluded tables
DO $$
BEGIN
  IF current_setting('app.demo_min_rows', true) IS NULL THEN
    PERFORM set_config('app.demo_min_rows', '15', true);
  END IF;
END $$;

DROP TABLE IF EXISTS tmp_demo_verify_exclusions;
CREATE TEMP TABLE tmp_demo_verify_exclusions (
  table_name text PRIMARY KEY,
  reason text NOT NULL
);

-- Edit this list when technical/runtime tables should not fail readiness checks.
INSERT INTO tmp_demo_verify_exclusions(table_name, reason) VALUES
  ('integration_sync_logs', 'Runtime integration log; deterministic demo seed excluded.'),
  ('webhook_event_log', 'Webhook runtime delivery log; deterministic demo seed excluded.')
ON CONFLICT (table_name) DO UPDATE SET reason = EXCLUDED.reason;

DROP TABLE IF EXISTS tmp_demo_table_counts;
CREATE TEMP TABLE tmp_demo_table_counts (
  table_name text PRIMARY KEY,
  row_count bigint NOT NULL
);

DO $$
DECLARE
  rec record;
BEGIN
  FOR rec IN
    SELECT t.table_schema, t.table_name
    FROM information_schema.tables t
    WHERE t.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND t.table_name <> 'flyway_schema_history'
    ORDER BY t.table_name
  LOOP
    EXECUTE format(
      'INSERT INTO tmp_demo_table_counts(table_name, row_count)
       SELECT %L, COUNT(*)::bigint FROM %I.%I',
      rec.table_name,
      rec.table_schema,
      rec.table_name
    );
  END LOOP;
END $$;

SELECT
  c.table_name,
  c.row_count,
  current_setting('app.demo_min_rows')::int AS min_required_rows,
  CASE
    WHEN e.table_name IS NOT NULL THEN 'EXCLUDED_TECHNICAL'
    WHEN c.row_count < current_setting('app.demo_min_rows')::int THEN 'BELOW_TARGET'
    ELSE 'OK'
  END AS status,
  CASE
    WHEN e.table_name IS NOT NULL THEN e.reason
    WHEN c.row_count < current_setting('app.demo_min_rows')::int THEN 'Row count is below minimum required threshold.'
    ELSE 'Meets minimum threshold.'
  END AS note
FROM tmp_demo_table_counts c
LEFT JOIN tmp_demo_verify_exclusions e
  ON e.table_name = c.table_name
ORDER BY
  c.row_count ASC,
  c.table_name ASC;

COMMIT;
