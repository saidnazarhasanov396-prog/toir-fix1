UPDATE operational_issues oi
SET severity = 'WARNING'
WHERE oi.type       = 'EQUIPMENT_LIFECYCLE'
  AND oi.status     = 'OPEN'
  AND oi.severity   = 'CRITICAL'
  AND oi.is_deleted = FALSE
  AND EXISTS (
      SELECT 1 FROM equipment e
      WHERE e.id         = oi.equipment_id
        AND e.is_deleted = FALSE
        AND e.status NOT IN ('IN_REPAIR', 'DECOMMISSIONED')
  );
