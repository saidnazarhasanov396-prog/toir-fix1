DROP INDEX IF EXISTS uq_maintenance_completion_anchors_repair_request;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_completion_anchors_repair_request_regulation_rule
    ON maintenance_completion_anchors (repair_request_id, regulation_id, equipment_maintenance_rule_id)
    WHERE repair_request_id IS NOT NULL
      AND regulation_id IS NOT NULL
      AND equipment_maintenance_rule_id IS NOT NULL
      AND is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_completion_anchors_repair_request_regulation
    ON maintenance_completion_anchors (repair_request_id, regulation_id)
    WHERE repair_request_id IS NOT NULL
      AND regulation_id IS NOT NULL
      AND equipment_maintenance_rule_id IS NULL
      AND is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_completion_anchors_repair_request_rule
    ON maintenance_completion_anchors (repair_request_id, equipment_maintenance_rule_id)
    WHERE repair_request_id IS NOT NULL
      AND regulation_id IS NULL
      AND equipment_maintenance_rule_id IS NOT NULL
      AND is_deleted = false;
