-- Assert there are no PM-visible "DEMO" terms in seeded public data.
-- Scans every text/varchar column in public base tables (except flyway_schema_history).

BEGIN;

DROP TABLE IF EXISTS tmp_visible_demo_findings;
CREATE TEMP TABLE tmp_visible_demo_findings (
  table_name text NOT NULL,
  column_name text NOT NULL,
  match_count bigint NOT NULL,
  sample_value text NULL
);

DO $$
DECLARE
  rec record;
BEGIN
  FOR rec IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema
     AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name <> 'flyway_schema_history'
      AND c.data_type IN ('text', 'character varying', 'character')
    ORDER BY c.table_name, c.ordinal_position
  LOOP
    EXECUTE format(
      'INSERT INTO tmp_visible_demo_findings(table_name, column_name, match_count, sample_value)
       SELECT %L, %L, COUNT(*)::bigint, MIN(LEFT(%I::text, 220))
       FROM public.%I
       WHERE %I IS NOT NULL
         AND %I::text ILIKE ''%%demo%%''
       HAVING COUNT(*) > 0',
      rec.table_name,
      rec.column_name,
      rec.column_name,
      rec.table_name,
      rec.column_name,
      rec.column_name
    );
  END LOOP;
END $$;

SELECT
  table_name,
  column_name,
  match_count,
  sample_value
FROM tmp_visible_demo_findings
ORDER BY match_count DESC, table_name ASC, column_name ASC;

SELECT
  COUNT(*)::int AS violating_columns,
  COALESCE(SUM(match_count), 0)::bigint AS violating_rows
FROM tmp_visible_demo_findings;

COMMIT;
