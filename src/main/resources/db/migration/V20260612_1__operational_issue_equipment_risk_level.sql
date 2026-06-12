ALTER TABLE operational_issues
    ADD COLUMN IF NOT EXISTS equipment_risk_level varchar(50);

-- Backfill existing EQUIPMENT_LIFECYCLE issues based on severity
UPDATE operational_issues
SET equipment_risk_level = CASE severity
    WHEN 'CRITICAL' THEN 'CRITICAL'
    WHEN 'WARNING'  THEN 'HIGH'
    WHEN 'INFO'     THEN 'LOW'
    ELSE 'LOW'
END
WHERE type = 'EQUIPMENT_LIFECYCLE'
  AND equipment_risk_level IS NULL
  AND is_deleted = false;
