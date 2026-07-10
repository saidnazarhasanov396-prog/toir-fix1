CREATE TABLE IF NOT EXISTS spare_part_life_rules (
    id uuid PRIMARY KEY,
    spare_part_id uuid NOT NULL,
    equipment_id uuid,
    equipment_node_id uuid,
    normalized_slot_code varchar(128),
    scope_type varchar(32) NOT NULL,
    combination_mode varchar(16) NOT NULL,
    due_action varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    effective_from timestamptz,
    effective_to timestamptz,
    revision integer NOT NULL,
    name varchar(255),
    description text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_life_rule_part FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT fk_sp_life_rule_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_sp_life_rule_node FOREIGN KEY (equipment_node_id) REFERENCES equipment_nodes(id),
    CONSTRAINT chk_sp_life_rule_scope CHECK (scope_type IN ('CATALOG', 'EQUIPMENT', 'NODE', 'NODE_SLOT')),
    CONSTRAINT chk_sp_life_rule_combination CHECK (combination_mode IN ('ANY', 'ALL', 'MANUAL')),
    CONSTRAINT chk_sp_life_rule_due_action CHECK (due_action IN ('WARNING_ONLY', 'MAINTENANCE_REQUIRED', 'BLOCK_OPERATION')),
    CONSTRAINT chk_sp_life_rule_revision CHECK (revision > 0),
    CONSTRAINT chk_sp_life_rule_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_sp_life_rule_scope_shape CHECK (
        (scope_type = 'CATALOG' AND equipment_id IS NULL AND equipment_node_id IS NULL AND normalized_slot_code IS NULL)
        OR (scope_type = 'EQUIPMENT' AND equipment_id IS NOT NULL AND equipment_node_id IS NULL AND normalized_slot_code IS NULL)
        OR (scope_type = 'NODE' AND equipment_id IS NOT NULL AND equipment_node_id IS NOT NULL AND normalized_slot_code IS NULL)
        OR (scope_type = 'NODE_SLOT' AND equipment_id IS NOT NULL AND equipment_node_id IS NOT NULL AND normalized_slot_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_sp_life_rule_scope_revision
    ON spare_part_life_rules (
        spare_part_id,
        COALESCE(equipment_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(equipment_node_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(normalized_slot_code, ''),
        revision
    )
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_life_rules_resolution
    ON spare_part_life_rules (spare_part_id, scope_type, active, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS spare_part_life_limits (
    id uuid PRIMARY KEY,
    rule_id uuid NOT NULL,
    limit_kind varchar(16) NOT NULL,
    calendar_unit varchar(16),
    meter_type varchar(32),
    explicit_equipment_meter_id uuid,
    limit_value numeric(19, 6) NOT NULL,
    warning_before_value numeric(19, 6),
    sequence integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_life_limit_rule FOREIGN KEY (rule_id) REFERENCES spare_part_life_rules(id),
    CONSTRAINT fk_sp_life_limit_meter FOREIGN KEY (explicit_equipment_meter_id) REFERENCES equipment_meters(id),
    CONSTRAINT chk_sp_life_limit_value CHECK (limit_value > 0),
    CONSTRAINT chk_sp_life_limit_warning CHECK (warning_before_value IS NULL OR warning_before_value >= 0),
    CONSTRAINT chk_sp_life_limit_warning_bound CHECK (warning_before_value IS NULL OR warning_before_value <= limit_value),
    CONSTRAINT chk_sp_life_limit_sequence CHECK (sequence >= 0),
    CONSTRAINT chk_sp_life_limit_shape CHECK (
        (limit_kind = 'CALENDAR' AND calendar_unit IN ('DAY', 'MONTH', 'YEAR') AND meter_type IS NULL AND explicit_equipment_meter_id IS NULL)
        OR (limit_kind = 'METER' AND calendar_unit IS NULL AND meter_type IS NOT NULL)
    ),
    UNIQUE (rule_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_sp_life_limits_rule
    ON spare_part_life_limits (rule_id, sequence)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS spare_part_installations (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL,
    equipment_node_id uuid,
    normalized_slot_code varchar(128) NOT NULL,
    position_key varchar(512) NOT NULL,
    position_label_snapshot varchar(255),
    spare_part_id uuid NOT NULL,
    quantity numeric(19, 6) NOT NULL,
    serial_number_snapshot varchar(255),
    lot_number_snapshot varchar(255),
    status varchar(16) NOT NULL,
    installed_at timestamptz NOT NULL,
    removed_at timestamptz,
    install_work_order_id uuid,
    remove_work_order_id uuid,
    source_material_usage_id uuid,
    installed_by uuid,
    removed_by uuid,
    removal_disposition varchar(32),
    removal_reason text,
    replaces_installation_id uuid,
    replaced_by_installation_id uuid,
    replacement_correlation_id uuid,
    applied_life_rule_id uuid,
    applied_rule_revision integer,
    applied_rule_snapshot jsonb,
    lifecycle_evaluation_state varchar(16) NOT NULL DEFAULT 'OK',
    next_calendar_due_at timestamptz,
    last_evaluated_at timestamptz,
    evaluation_details jsonb,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_installation_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_sp_installation_node FOREIGN KEY (equipment_node_id) REFERENCES equipment_nodes(id),
    CONSTRAINT fk_sp_installation_part FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id),
    CONSTRAINT fk_sp_installation_install_wo FOREIGN KEY (install_work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_sp_installation_remove_wo FOREIGN KEY (remove_work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_sp_installation_source_usage FOREIGN KEY (source_material_usage_id) REFERENCES repair_material_usages(id),
    CONSTRAINT fk_sp_installation_installed_by FOREIGN KEY (installed_by) REFERENCES users(id),
    CONSTRAINT fk_sp_installation_removed_by FOREIGN KEY (removed_by) REFERENCES users(id),
    CONSTRAINT fk_sp_installation_replaces FOREIGN KEY (replaces_installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_installation_replaced_by FOREIGN KEY (replaced_by_installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_installation_rule FOREIGN KEY (applied_life_rule_id) REFERENCES spare_part_life_rules(id),
    CONSTRAINT chk_sp_installation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sp_installation_status CHECK (status IN ('ACTIVE', 'REPLACED', 'REMOVED')),
    CONSTRAINT chk_sp_installation_evaluation CHECK (lifecycle_evaluation_state IN ('OK', 'WARNING', 'DUE', 'OVERDUE', 'ERROR')),
    CONSTRAINT chk_sp_installation_status_fields CHECK (
        (status = 'ACTIVE' AND removed_at IS NULL AND removed_by IS NULL AND removal_disposition IS NULL)
        OR (status IN ('REPLACED', 'REMOVED') AND removed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_sp_installations_active_position
    ON spare_part_installations (equipment_id, position_key)
    WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_sp_installations_serial_history
    ON spare_part_installations (spare_part_id, lower(serial_number_snapshot))
    WHERE serial_number_snapshot IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_installations_active_equipment
    ON spare_part_installations (equipment_id, installed_at)
    WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_installations_active_part
    ON spare_part_installations (spare_part_id, installed_at)
    WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_installations_work_orders
    ON spare_part_installations (install_work_order_id, remove_work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_installations_serial
    ON spare_part_installations (serial_number_snapshot)
    WHERE serial_number_snapshot IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_installations_due_scan
    ON spare_part_installations (lifecycle_evaluation_state, next_calendar_due_at)
    WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE TABLE IF NOT EXISTS spare_part_installation_meter_baselines (
    id uuid PRIMARY KEY,
    installation_id uuid NOT NULL,
    equipment_meter_id uuid NOT NULL,
    meter_type varchar(32) NOT NULL,
    baseline_value numeric(19, 6) NOT NULL,
    baseline_recorded_at timestamptz NOT NULL,
    baseline_reading_id uuid,
    rollover_context jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_baseline_installation FOREIGN KEY (installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_baseline_meter FOREIGN KEY (equipment_meter_id) REFERENCES equipment_meters(id),
    CONSTRAINT fk_sp_baseline_reading FOREIGN KEY (baseline_reading_id) REFERENCES meter_readings(id),
    UNIQUE (installation_id, equipment_meter_id)
);

CREATE TABLE IF NOT EXISTS spare_part_installation_material_allocations (
    id uuid PRIMARY KEY,
    installation_id uuid NOT NULL,
    repair_material_usage_id uuid NOT NULL UNIQUE,
    allocated_quantity numeric(19, 6) NOT NULL,
    serial_number_snapshot varchar(255),
    lot_number_snapshot varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_allocation_installation FOREIGN KEY (installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_allocation_usage FOREIGN KEY (repair_material_usage_id) REFERENCES repair_material_usages(id),
    CONSTRAINT chk_sp_allocation_quantity CHECK (allocated_quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_sp_allocations_installation
    ON spare_part_installation_material_allocations (installation_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS spare_part_lifecycle_commands (
    id uuid PRIMARY KEY,
    idempotency_key varchar(255) NOT NULL UNIQUE,
    command_type varchar(16) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    result_installation_id uuid,
    result_removed_installation_id uuid,
    work_order_id uuid,
    created_by uuid NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_command_result_installation FOREIGN KEY (result_installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_command_result_removed FOREIGN KEY (result_removed_installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_command_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_sp_command_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_sp_command_type CHECK (command_type IN ('INSTALL', 'REMOVE', 'REPLACE')),
    CONSTRAINT chk_sp_command_status CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED')),
    CONSTRAINT chk_sp_command_completion CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status = 'SUCCEEDED' AND completed_at IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS spare_part_due_events (
    id uuid PRIMARY KEY,
    installation_id uuid NOT NULL,
    applied_rule_id uuid,
    applied_rule_revision integer,
    cycle_key varchar(255) NOT NULL,
    state varchar(16) NOT NULL,
    due_action varchar(32) NOT NULL,
    due_at timestamptz,
    meter_id uuid,
    meter_type varchar(32),
    due_meter_value numeric(19, 6),
    current_meter_value numeric(19, 6),
    warning_threshold numeric(19, 6),
    reasons jsonb,
    first_detected_at timestamptz NOT NULL,
    last_evaluated_at timestamptz NOT NULL,
    acknowledged_at timestamptz,
    acknowledged_by uuid,
    linked_work_order_id uuid,
    resolved_at timestamptz,
    resolved_by uuid,
    replacement_installation_id uuid,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_sp_due_installation FOREIGN KEY (installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT fk_sp_due_rule FOREIGN KEY (applied_rule_id) REFERENCES spare_part_life_rules(id),
    CONSTRAINT fk_sp_due_meter FOREIGN KEY (meter_id) REFERENCES equipment_meters(id),
    CONSTRAINT fk_sp_due_ack_by FOREIGN KEY (acknowledged_by) REFERENCES users(id),
    CONSTRAINT fk_sp_due_work_order FOREIGN KEY (linked_work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_sp_due_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id),
    CONSTRAINT fk_sp_due_replacement FOREIGN KEY (replacement_installation_id) REFERENCES spare_part_installations(id),
    CONSTRAINT chk_sp_due_state CHECK (state IN ('UPCOMING', 'WARNING', 'DUE', 'OVERDUE', 'RESOLVED')),
    CONSTRAINT chk_sp_due_action CHECK (due_action IN ('WARNING_ONLY', 'MAINTENANCE_REQUIRED', 'BLOCK_OPERATION')),
    UNIQUE (installation_id, cycle_key)
);

CREATE INDEX IF NOT EXISTS idx_sp_due_events_state_due
    ON spare_part_due_events (state, due_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sp_due_events_installation_cycle
    ON spare_part_due_events (installation_id, cycle_key)
    WHERE is_deleted = false;
