-- P0 leadership/UAT exact demo chain. Demo-only via the dev,demo-seed profile.
-- MT-01 Operational Cockpit remains deferred and is intentionally not seeded here.

-- 18.01 Exact equipment assets.
INSERT INTO equipment (id, created_at, updated_at, is_deleted, code, name, inventory_number, technical_number, serial_number, model, equipment_type_id, department_id, location_id, parent_id, criticality_class_id, responsible_id, manufacturer, status, category, commissioned_at, warranty_until, description, operation_start_date, expected_lifetime_months, expected_lifetime_years)
VALUES
('20000000-0000-0000-0000-000000000001', now(), now(), false, 'AUTO-PUMP-A1', 'AUTO-PUMP-A1 ammonia feed pump', 'AUTO-INV-PUMP-A1', 'AUTO-TN-PUMP-A1', 'AUTO-SN-PUMP-A1', 'P-250 demo', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a106', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a005', 'Navoiyazot Pump Works', 'ACTIVE', 'PRODUCTION_EQUIPMENT', DATE '2022-02-01', DATE '2027-02-01', 'Happy-path P0 demo pump with complete passport, active meter, due event, template, warehouse and finance traceability.', DATE '2022-02-01', 120, 10),
('20000000-0000-0000-0000-000000000002', now(), now(), false, 'AUTO-PUMP-A2', 'AUTO-PUMP-A2 standby pump', 'AUTO-INV-PUMP-A2', 'AUTO-TN-PUMP-A2', 'AUTO-SN-PUMP-A2', 'P-250 demo standby', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a106', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a005', 'Navoiyazot Pump Works', 'ACTIVE', 'PRODUCTION_EQUIPMENT', DATE '2023-01-15', DATE '2028-01-15', 'Control P0 demo pump with healthy upcoming cycle.', DATE '2023-01-15', 120, 10),
('20000000-0000-0000-0000-000000000003', now(), now(), false, 'AUTO-PUMP-A3-NOMETER', 'AUTO-PUMP-A3-NOMETER incomplete setup pump', 'AUTO-INV-PUMP-A3', 'AUTO-TN-PUMP-A3', 'AUTO-SN-PUMP-A3', 'P-250 demo incomplete', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a106', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a005', 'Navoiyazot Pump Works', 'ACTIVE', 'PRODUCTION_EQUIPMENT', DATE '2024-03-01', DATE '2029-03-01', 'Negative-branch P0 demo pump: missing critical passport setup and no active operating meter.', DATE '2024-03-01', 120, 10)
ON CONFLICT (code) WHERE is_deleted = false DO UPDATE
SET name = EXCLUDED.name,
    inventory_number = EXCLUDED.inventory_number,
    technical_number = EXCLUDED.technical_number,
    serial_number = EXCLUDED.serial_number,
    model = EXCLUDED.model,
    equipment_type_id = EXCLUDED.equipment_type_id,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    criticality_class_id = EXCLUDED.criticality_class_id,
    responsible_id = EXCLUDED.responsible_id,
    manufacturer = EXCLUDED.manufacturer,
    status = EXCLUDED.status,
    category = EXCLUDED.category,
    commissioned_at = EXCLUDED.commissioned_at,
    warranty_until = EXCLUDED.warranty_until,
    description = EXCLUDED.description,
    operation_start_date = EXCLUDED.operation_start_date,
    expected_lifetime_months = EXCLUDED.expected_lifetime_months,
    expected_lifetime_years = EXCLUDED.expected_lifetime_years,
    updated_at = now();

INSERT INTO equipment_nodes (id, created_at, updated_at, is_deleted, equipment_id, parent_id, code, name, node_type, serial_number, description)
VALUES
('20000000-0000-0000-0000-000000000011', now(), now(), false, '20000000-0000-0000-0000-000000000001', NULL, 'AUTO-PUMP-A1-PUMP-END', 'Pump end assembly', 'ASSEMBLY', 'AUTO-SN-PUMP-A1-PE', 'Pump end node for A1 demo'),
('20000000-0000-0000-0000-000000000012', now(), now(), false, '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000011', 'AUTO-PUMP-A1-BRG-DE', 'Drive-end bearing unit', 'UNIT', 'AUTO-SN-PUMP-A1-BRG', 'Bearing node for material traceability'),
('20000000-0000-0000-0000-000000000013', now(), now(), false, '20000000-0000-0000-0000-000000000002', NULL, 'AUTO-PUMP-A2-PUMP-END', 'Pump end assembly', 'ASSEMBLY', 'AUTO-SN-PUMP-A2-PE', 'Pump end node for A2 demo')
ON CONFLICT (equipment_id, code) DO UPDATE
SET name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    node_type = EXCLUDED.node_type,
    serial_number = EXCLUDED.serial_number,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO equipment_passports (id, created_at, updated_at, is_deleted, equipment_id, passport_number, manufacturer_serial, factory_number, install_date, last_inspection_date, pressure_bar, throughput, power_kw, voltage_v, notes)
VALUES
('20000000-0000-0000-0000-000000000021', now(), now(), false, '20000000-0000-0000-0000-000000000001', 'PASS-AUTO-PUMP-A1', 'AUTO-MS-PUMP-A1', 'AUTO-FN-PUMP-A1', DATE '2022-02-05', DATE '2026-05-20', 9.5, 62.0, 45.0, 380, 'Complete passport for P0 happy-path pump'),
('20000000-0000-0000-0000-000000000022', now(), now(), false, '20000000-0000-0000-0000-000000000002', 'PASS-AUTO-PUMP-A2', 'AUTO-MS-PUMP-A2', 'AUTO-FN-PUMP-A2', DATE '2023-01-20', DATE '2026-05-15', 8.8, 58.0, 37.0, 380, 'Complete passport for P0 control pump')
ON CONFLICT (equipment_id) DO UPDATE
SET passport_number = EXCLUDED.passport_number,
    manufacturer_serial = EXCLUDED.manufacturer_serial,
    factory_number = EXCLUDED.factory_number,
    install_date = EXCLUDED.install_date,
    last_inspection_date = EXCLUDED.last_inspection_date,
    pressure_bar = EXCLUDED.pressure_bar,
    throughput = EXCLUDED.throughput,
    power_kw = EXCLUDED.power_kw,
    voltage_v = EXCLUDED.voltage_v,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO equipment_attribute_values (id, created_at, updated_at, is_deleted, equipment_id, attribute_definition_id, value_number, value_text, value_option, value_boolean, value_date, value_json)
SELECT v.id, now(), now(), false, e.id, d.id, v.value_number, NULL, v.value_option, NULL, NULL, NULL
FROM (VALUES
    ('20000000-0000-0000-0000-000000000031'::uuid, 'AUTO-PUMP-A1', 'operating_pressure', 9.5::double precision, NULL),
    ('20000000-0000-0000-0000-000000000032'::uuid, 'AUTO-PUMP-A1', 'lubrication_type', NULL, 'OIL_ISO46'),
    ('20000000-0000-0000-0000-000000000033'::uuid, 'AUTO-PUMP-A2', 'operating_pressure', 8.8::double precision, NULL),
    ('20000000-0000-0000-0000-000000000034'::uuid, 'AUTO-PUMP-A2', 'lubrication_type', NULL, 'OIL_ISO68')
) AS v(id, equipment_code, attribute_key, value_number, value_option)
JOIN equipment e ON e.code = v.equipment_code AND e.is_deleted = false
JOIN equipment_attribute_definitions d ON d.equipment_type_id = e.equipment_type_id AND d.attribute_key = v.attribute_key AND d.is_deleted = false
ON CONFLICT (equipment_id, attribute_definition_id) WHERE is_deleted = false DO UPDATE
SET value_number = EXCLUDED.value_number,
    value_option = EXCLUDED.value_option,
    updated_at = now();

INSERT INTO technical_documents (id, created_at, updated_at, is_deleted, equipment_id, equipment_node_id, file_id, uploaded_by_id, title, type, revision, document_date)
VALUES
('20000000-0000-0000-0000-000000000041', now(), now(), false, '20000000-0000-0000-0000-000000000001', NULL, NULL, '00000000-0000-0000-0000-00000000a005', 'AUTO-PUMP-A1 passport scan', 'PASSPORT', 'R0', DATE '2026-05-20'),
('20000000-0000-0000-0000-000000000042', now(), now(), false, '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000011', NULL, '00000000-0000-0000-0000-00000000a005', 'AUTO-PUMP-A1 assembly drawing', 'DRAWING', 'R1', DATE '2026-05-21')
ON CONFLICT (id) DO UPDATE
SET title = EXCLUDED.title,
    type = EXCLUDED.type,
    revision = EXCLUDED.revision,
    document_date = EXCLUDED.document_date,
    updated_at = now();

INSERT INTO equipment_meters (id, created_at, updated_at, is_deleted, equipment_id, name, meter_type, unit, current_value, rollover_value, last_read_at, is_active)
VALUES
('20000000-0000-0000-0000-000000000051', now(), now(), false, '20000000-0000-0000-0000-000000000001', 'AUTO-PUMP-A1 operating cycles', 'CYCLES', 'cycles', 12540, NULL, now() - interval '2 hours', true),
('20000000-0000-0000-0000-000000000052', now(), now(), false, '20000000-0000-0000-0000-000000000002', 'AUTO-PUMP-A2 operating cycles', 'CYCLES', 'cycles', 6040, NULL, now() - interval '1 day', true)
ON CONFLICT (equipment_id, meter_type, name) DO UPDATE
SET current_value = EXCLUDED.current_value,
    unit = EXCLUDED.unit,
    last_read_at = EXCLUDED.last_read_at,
    is_active = true,
    updated_at = now();

INSERT INTO meter_readings (id, created_at, updated_at, is_deleted, meter_id, equipment_id, source, value, delta, read_at, recorded_by_user_id, device_id, note)
VALUES
('20000000-0000-0000-0000-000000000061', now(), now(), false, '20000000-0000-0000-0000-000000000051', '20000000-0000-0000-0000-000000000001', 'MANUAL', 12460, 80, now() - interval '2 days', '00000000-0000-0000-0000-00000000a005', 'AUTO-METER-A1', 'P0 demo pre-threshold reading'),
('20000000-0000-0000-0000-000000000062', now(), now(), false, '20000000-0000-0000-0000-000000000051', '20000000-0000-0000-0000-000000000001', 'MANUAL', 12540, 80, now() - interval '2 hours', '00000000-0000-0000-0000-00000000a005', 'AUTO-METER-A1', 'P0 demo overdue reading'),
('20000000-0000-0000-0000-000000000063', now(), now(), false, '20000000-0000-0000-0000-000000000052', '20000000-0000-0000-0000-000000000002', 'MANUAL', 6040, 40, now() - interval '1 day', '00000000-0000-0000-0000-00000000a005', 'AUTO-METER-A2', 'P0 demo healthy reading')
ON CONFLICT (id) DO UPDATE
SET value = EXCLUDED.value,
    delta = EXCLUDED.delta,
    read_at = EXCLUDED.read_at,
    note = EXCLUDED.note,
    updated_at = now();

-- 18.02 Template/regulation with copied operation source traceability.
INSERT INTO maintenance_templates (id, created_at, updated_at, is_deleted, code, name, description, equipment_type_id, maintenance_kind, normative_labor_hours, is_active)
VALUES ('20000000-0000-0000-0000-000000000101', now(), now(), false, 'AUTO-PUMP-PM-2026', 'AUTO pump monthly PM package', 'P0 demo pump template: copied into work orders with operation source traceability.', '00000000-0000-0000-0000-00000000f001', 'PREVENTIVE', 6.5, true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    equipment_type_id = EXCLUDED.equipment_type_id,
    maintenance_kind = EXCLUDED.maintenance_kind,
    normative_labor_hours = EXCLUDED.normative_labor_hours,
    is_active = true,
    updated_at = now();

-- Operation identifiers:
-- AUTO-PUMP-PM-2026-OP-01 natural key: template AUTO-PUMP-PM-2026, sequence 1.
-- AUTO-PUMP-PM-2026-OP-02 natural key: template AUTO-PUMP-PM-2026, sequence 2.
-- AUTO-PUMP-PM-2026-OP-03 natural key: template AUTO-PUMP-PM-2026, sequence 3.
INSERT INTO maintenance_operations (id, created_at, updated_at, is_deleted, template_id, sequence, name, description, duration_hours, required_skill, control_parameter, control_min, control_max, control_unit, tools_required, spare_parts_required, consumables_required, safety_notes, instruction_url)
VALUES
('20000000-0000-0000-0000-000000000111', now(), now(), false, '20000000-0000-0000-0000-000000000101', 1, 'AUTO-PUMP-PM-2026-OP-01 isolate and inspect', 'Apply LOTO, inspect leakage and bearing temperature.', 1.5, 'FOREMAN', 'bearing_temperature', 20, 80, 'C', 'LOTO kit, thermometer', 'gaskets', 'cleaner', 'Permit and PPE required', 'https://toir.local/demo/auto-pump/op-01'),
('20000000-0000-0000-0000-000000000112', now(), now(), false, '20000000-0000-0000-0000-000000000101', 2, 'AUTO-PUMP-PM-2026-OP-02 lubricate and replace wear parts', 'Lubricate bearings and replace planned wear materials.', 3.0, 'MECHANIC', 'vibration', 0, 4.5, 'mm/s', 'Grease gun, puller', 'bearing,seals', 'oil', 'Use approved lifting practice', 'https://toir.local/demo/auto-pump/op-02'),
('20000000-0000-0000-0000-000000000113', now(), now(), false, '20000000-0000-0000-0000-000000000101', 3, 'AUTO-PUMP-PM-2026-OP-03 test run and handover', 'Run pump, verify pressure, vibration and handover evidence.', 2.0, 'FOREMAN', 'discharge_pressure', 7, 11, 'bar', 'Vibration probe, pressure gauge', NULL, NULL, 'No handover without test evidence', 'https://toir.local/demo/auto-pump/op-03')
ON CONFLICT (template_id, sequence) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    duration_hours = EXCLUDED.duration_hours,
    required_skill = EXCLUDED.required_skill,
    control_parameter = EXCLUDED.control_parameter,
    control_min = EXCLUDED.control_min,
    control_max = EXCLUDED.control_max,
    control_unit = EXCLUDED.control_unit,
    tools_required = EXCLUDED.tools_required,
    spare_parts_required = EXCLUDED.spare_parts_required,
    consumables_required = EXCLUDED.consumables_required,
    safety_notes = EXCLUDED.safety_notes,
    instruction_url = EXCLUDED.instruction_url,
    updated_at = now();

INSERT INTO maintenance_template_spare_part_requirements (id, created_at, updated_at, is_deleted, template_id, operation_id, spare_part_id, quantity, unit, criticality, notes, is_active)
VALUES
('20000000-0000-0000-0000-000000000121', now(), now(), false, '20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000040001', 2, 'pc', 'CRITICAL', 'Bearing set for AUTO pump PM', true),
('20000000-0000-0000-0000-000000000122', now(), now(), false, '20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000040002', 1, 'pc', 'CRITICAL', 'Mechanical seal for AUTO pump PM', true)
ON CONFLICT (
    template_id,
    (COALESCE(operation_id, '00000000-0000-0000-0000-000000000000'::uuid)),
    spare_part_id
) WHERE is_deleted = false AND is_active = true DO UPDATE
SET quantity = EXCLUDED.quantity,
    unit = EXCLUDED.unit,
    criticality = EXCLUDED.criticality,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO maintenance_regulations (id, created_at, updated_at, is_deleted, code, name, description, equipment_type_id, maintenance_kind, normative_labor_hours, is_active, periodicity_unit, periodicity_value, tolerance_days, requires_shutdown, trigger_meter_type, trigger_meter_interval, template_id, trigger_policy, recalculation_policy, automation_action, duplicate_policy, lead_time_days, lead_meter_percent, default_department_id, default_responsible_id, default_priority, requires_approval, approval_role, approval_permission, initial_schedule_policy, approval_result_action)
VALUES ('20000000-0000-0000-0000-000000000131', now(), now(), false, 'AUTO-PUMP-PM-2026', 'AUTO pump meter-based PM', 'P0 demo regulation: requires approval and creates a work order from AUTO-PUMP-PM-2026 template.', '00000000-0000-0000-0000-00000000f001', 'PREVENTIVE', 6.5, true, 'MONTH', 1, 5, false, 'CYCLES', 500, '20000000-0000-0000-0000-000000000101', 'ANY', 'FROM_ACTUAL_COMPLETION', 'REQUIRE_APPROVAL', 'ONE_ITEM_PER_CYCLE', 3, 20, '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a004', 'HIGH', true, 'CHIEF_MECHANIC', NULL, 'FROM_OPERATION_START', 'CREATE_WORK_ORDER')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    equipment_type_id = EXCLUDED.equipment_type_id,
    maintenance_kind = EXCLUDED.maintenance_kind,
    normative_labor_hours = EXCLUDED.normative_labor_hours,
    is_active = true,
    trigger_meter_type = EXCLUDED.trigger_meter_type,
    trigger_meter_interval = EXCLUDED.trigger_meter_interval,
    template_id = EXCLUDED.template_id,
    trigger_policy = EXCLUDED.trigger_policy,
    recalculation_policy = EXCLUDED.recalculation_policy,
    automation_action = EXCLUDED.automation_action,
    duplicate_policy = EXCLUDED.duplicate_policy,
    default_department_id = EXCLUDED.default_department_id,
    default_responsible_id = EXCLUDED.default_responsible_id,
    default_priority = EXCLUDED.default_priority,
    requires_approval = EXCLUDED.requires_approval,
    approval_role = EXCLUDED.approval_role,
    approval_permission = EXCLUDED.approval_permission,
    initial_schedule_policy = EXCLUDED.initial_schedule_policy,
    approval_result_action = EXCLUDED.approval_result_action,
    updated_at = now();

-- 18.03 Stock/budget rows used by the demo route.
INSERT INTO warehouse_stocks (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, quantity, reserved_qty, min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location)
VALUES
('20000000-0000-0000-0000-000000000141', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040001', 26, 2, 10, 80, 12, 20, 0.25, 'P0-A-01'),
('20000000-0000-0000-0000-000000000142', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040002', 12, 1, 6, 40, 8, 12, 0.15, 'P0-A-02')
ON CONFLICT (warehouse_id, spare_part_id) DO UPDATE
SET quantity = GREATEST(warehouse_stocks.reserved_qty, EXCLUDED.quantity),
    reserved_qty = LEAST(EXCLUDED.reserved_qty, GREATEST(warehouse_stocks.reserved_qty, EXCLUDED.quantity)),
    min_qty = EXCLUDED.min_qty,
    max_qty = EXCLUDED.max_qty,
    reorder_point = EXCLUDED.reorder_point,
    reorder_qty = EXCLUDED.reorder_qty,
    avg_daily_usage = EXCLUDED.avg_daily_usage,
    bin_location = EXCLUDED.bin_location,
    updated_at = now();

INSERT INTO maintenance_budgets (id, created_at, updated_at, is_deleted, year, month, department_id, status, total_planned, total_actual)
VALUES ('20000000-0000-0000-0000-000000000151', now(), now(), false, 2026, 6, '00000000-0000-0000-0000-00000000d002', 'APPROVED', 65000000.00, 1245000.00)
ON CONFLICT (id) DO UPDATE
SET status = EXCLUDED.status,
    total_planned = EXCLUDED.total_planned,
    total_actual = EXCLUDED.total_actual,
    updated_at = now();

INSERT INTO budget_lines (id, created_at, updated_at, is_deleted, budget_id, cost_category_id, description, planned_amount, actual_amount)
VALUES
('20000000-0000-0000-0000-000000000152', now(), now(), false, '20000000-0000-0000-0000-000000000151', '00000000-0000-0000-0000-00000000c101', 'P0 AUTO pump spare material budget', 45000000.00, 245000.00),
('20000000-0000-0000-0000-000000000153', now(), now(), false, '20000000-0000-0000-0000-000000000151', '00000000-0000-0000-0000-00000000c102', 'P0 AUTO pump labor budget', 20000000.00, 1000000.00)
ON CONFLICT (id) DO UPDATE
SET planned_amount = EXCLUDED.planned_amount,
    actual_amount = EXCLUDED.actual_amount,
    description = EXCLUDED.description,
    updated_at = now();

-- 18.04 Due events: happy path, control path, and blocked negative path.
INSERT INTO maintenance_due_events (id, created_at, updated_at, is_deleted, equipment_id, regulation_id, equipment_maintenance_rule_id, template_id, status, due_status, trigger_source, cycle_key, due_at, meter_type, meter_current_value, meter_anchor_value, meter_interval, meter_remaining, created_task_id, created_work_order_id, detected_at, resolved_at, resolution_reason, explanation)
VALUES
('20000000-0000-0000-0000-000000000201', now(), now(), false, '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000131', NULL, '20000000-0000-0000-0000-000000000101', 'AWAITING_APPROVAL', 'OVERDUE', 'METER_READING', 'AUTO-PUMP-A1:CYCLES:2026-06', now() - interval '3 days', 'CYCLES', 12540, 12000, 500, -40, NULL, NULL, now() - interval '2 hours', NULL, NULL, 'Cycles trigger overdue: current 12540 cycles, last completion anchor 12000 cycles, interval 500 cycles, remaining -40 cycles. Approval creates a work order from template AUTO-PUMP-PM-2026.'),
('20000000-0000-0000-0000-000000000202', now(), now(), false, '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000131', NULL, '20000000-0000-0000-0000-000000000101', 'DETECTED', 'UPCOMING', 'METER_READING', 'AUTO-PUMP-A2:CYCLES:2026-06', now() + interval '14 days', 'CYCLES', 6040, 5600, 500, 60, NULL, NULL, now() - interval '1 day', NULL, NULL, 'Cycles trigger upcoming: current 6040 cycles, last completion anchor 5600 cycles, interval 500 cycles, remaining 60 cycles. No blocking setup issue.'),
('20000000-0000-0000-0000-000000000203', now(), now(), false, '20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000131', NULL, '20000000-0000-0000-0000-000000000101', 'DETECTED', 'BLOCKED', 'MANUAL_RECALCULATION', 'AUTO-PUMP-A3-NOMETER:CYCLES:BLOCKED:MISSING_ACTIVE_METER', NULL, 'CYCLES', NULL, NULL, 500, NULL, NULL, NULL, now() - interval '1 hour', NULL, NULL, 'Blocked: active CYCLES meter is required before this meter-based maintenance can create a work order. Fix equipment meter setup from the equipment card.')
ON CONFLICT (equipment_id, regulation_id, cycle_key) WHERE is_deleted = false DO UPDATE
SET template_id = EXCLUDED.template_id,
    status = EXCLUDED.status,
    due_status = EXCLUDED.due_status,
    trigger_source = EXCLUDED.trigger_source,
    due_at = EXCLUDED.due_at,
    meter_type = EXCLUDED.meter_type,
    meter_current_value = EXCLUDED.meter_current_value,
    meter_anchor_value = EXCLUDED.meter_anchor_value,
    meter_interval = EXCLUDED.meter_interval,
    meter_remaining = EXCLUDED.meter_remaining,
    explanation = EXCLUDED.explanation,
    updated_at = now();

-- 18.05 Historical A1 closed work order for history/finance evidence.
INSERT INTO work_orders (id, created_at, updated_at, is_deleted, number, title, equipment_id, department_id, repair_request_id, defect_id, ppr_task_id, contractor_id, warehouse_id, replacement_equipment_id, status, type, work_type, priority, start_planned_at, end_planned_at, started_at, completed_at, summary, result, closure_notes, created_by_id, approved_by_id, maintenance_due_event_id, cycle_key)
VALUES ('20000000-0000-0000-0000-000000000301', now(), now(), false, 'AUTO-WO-A1-HISTORY-2026-05', 'AUTO-PUMP-A1 previous monthly PM', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000d002', NULL, NULL, NULL, NULL, '00000000-0000-0000-0000-000000030001', NULL, 'CLOSED', 'PLANNED', 'REPAIR', 'HIGH', TIMESTAMP '2026-05-20 08:00:00+05', TIMESTAMP '2026-05-20 16:00:00+05', TIMESTAMP '2026-05-20 08:10:00+05', TIMESTAMP '2026-05-20 15:40:00+05', 'Historical P0 demo PM copied from AUTO-PUMP-PM-2026.', 'Pump test run accepted; next cycle is visible on equipment card.', 'Closed with labor, material and source-linked finance rows.', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', NULL, 'AUTO-PUMP-A1:CYCLES:2026-05')
ON CONFLICT (number) DO UPDATE
SET status = EXCLUDED.status,
    summary = EXCLUDED.summary,
    result = EXCLUDED.result,
    closure_notes = EXCLUDED.closure_notes,
    maintenance_due_event_id = EXCLUDED.maintenance_due_event_id,
    cycle_key = EXCLUDED.cycle_key,
    updated_at = now();

INSERT INTO work_order_tasks (id, created_at, updated_at, is_deleted, work_order_id, title, description, status, assigned_to_id, planned_hours, actual_hours, source_template_id, source_operation_id, started_at, completed_at)
VALUES
('20000000-0000-0000-0000-000000000311', now(), now(), false, '20000000-0000-0000-0000-000000000301', 'AUTO-PUMP-PM-2026-OP-01 isolate and inspect', 'Copied from AUTO-PUMP-PM-2026 operation 1.', 'DONE', '00000000-0000-0000-0000-00000000a004', 1.5, 1.5, '20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000111', TIMESTAMP '2026-05-20 08:10:00+05', TIMESTAMP '2026-05-20 09:30:00+05'),
('20000000-0000-0000-0000-000000000312', now(), now(), false, '20000000-0000-0000-0000-000000000301', 'AUTO-PUMP-PM-2026-OP-02 lubricate and replace wear parts', 'Copied from AUTO-PUMP-PM-2026 operation 2.', 'DONE', '00000000-0000-0000-0000-00000000a004', 3.0, 3.0, '20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000112', TIMESTAMP '2026-05-20 09:30:00+05', TIMESTAMP '2026-05-20 13:00:00+05'),
('20000000-0000-0000-0000-000000000313', now(), now(), false, '20000000-0000-0000-0000-000000000301', 'AUTO-PUMP-PM-2026-OP-03 test run and handover', 'Copied from AUTO-PUMP-PM-2026 operation 3.', 'DONE', '00000000-0000-0000-0000-00000000a004', 2.0, 2.0, '20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000113', TIMESTAMP '2026-05-20 13:00:00+05', TIMESTAMP '2026-05-20 15:40:00+05')
ON CONFLICT (id) DO UPDATE
SET title = EXCLUDED.title,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    planned_hours = EXCLUDED.planned_hours,
    actual_hours = EXCLUDED.actual_hours,
    source_template_id = EXCLUDED.source_template_id,
    source_operation_id = EXCLUDED.source_operation_id,
    updated_at = now();

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
VALUES ('20000000-0000-0000-0000-000000000321', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040002', '20000000-0000-0000-0000-000000000301', 'ISSUE', 1, 245000.00, 'AUTO-SM-A1-ISSUE-2026-05', '00000000-0000-0000-0000-00000000a006', TIMESTAMP '2026-05-20 11:00:00+05', 'Issued seal to AUTO-PUMP-A1 historical PM')
ON CONFLICT (id) DO UPDATE
SET quantity = EXCLUDED.quantity,
    unit_cost = EXCLUDED.unit_cost,
    document_number = EXCLUDED.document_number,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO repair_material_usages (id, created_at, updated_at, is_deleted, work_order_id, warehouse_id, spare_part_id, quantity, unit_cost, stock_movement_id, issued_by_id, issued_at, notes)
VALUES ('20000000-0000-0000-0000-000000000331', now(), now(), false, '20000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040002', 1, 245000.00, '20000000-0000-0000-0000-000000000321', '00000000-0000-0000-0000-00000000a006', TIMESTAMP '2026-05-20 11:05:00+05', 'Known-cost material issue source for finance traceability')
ON CONFLICT (id) DO UPDATE
SET quantity = EXCLUDED.quantity,
    unit_cost = EXCLUDED.unit_cost,
    stock_movement_id = EXCLUDED.stock_movement_id,
    issued_by_id = EXCLUDED.issued_by_id,
    issued_at = EXCLUDED.issued_at,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO labor_entries (id, created_at, updated_at, is_deleted, work_order_id, user_id, contractor_name, work_date, hours, rate, description)
VALUES ('20000000-0000-0000-0000-000000000341', now(), now(), false, '20000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-00000000a004', NULL, DATE '2026-05-20', 6.5, 153846.15, 'Known-cost labor source for AUTO-PUMP-A1 historical PM')
ON CONFLICT (id) DO UPDATE
SET hours = EXCLUDED.hours,
    rate = EXCLUDED.rate,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO actual_costs (id, created_at, updated_at, is_deleted, work_order_id, repair_request_id, contractor_work_id, budget_line_id, cost_category_id, status, reviewed_by_id, reviewed_at, review_comment, amount, cost_date, notes, source_type, source_id)
VALUES
('20000000-0000-0000-0000-000000000351', now(), now(), false, '20000000-0000-0000-0000-000000000301', NULL, NULL, '20000000-0000-0000-0000-000000000152', '00000000-0000-0000-0000-00000000c101', 'APPROVED', '00000000-0000-0000-0000-00000000a008', TIMESTAMP '2026-05-20 16:10:00+05', 'Approved material source row with technical source.', 245000.00, TIMESTAMP '2026-05-20 11:05:00+05', 'Material issue actual cost: sourceType/sourceId visible in finance review.', 'MATERIAL_ISSUE', '20000000-0000-0000-0000-000000000331'),
('20000000-0000-0000-0000-000000000352', now(), now(), false, '20000000-0000-0000-0000-000000000301', NULL, NULL, '20000000-0000-0000-0000-000000000153', '00000000-0000-0000-0000-00000000c102', 'APPROVED', '00000000-0000-0000-0000-00000000a008', TIMESTAMP '2026-05-20 16:20:00+05', 'Approved labor source row with technical source.', 1000000.00, TIMESTAMP '2026-05-20 15:40:00+05', 'Labor actual cost: sourceType/sourceId visible in finance review.', 'LABOR_ENTRY', '20000000-0000-0000-0000-000000000341')
ON CONFLICT (source_type, source_id) WHERE is_deleted = false AND source_type IS NOT NULL AND source_id IS NOT NULL DO UPDATE
SET amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    reviewed_by_id = EXCLUDED.reviewed_by_id,
    reviewed_at = EXCLUDED.reviewed_at,
    review_comment = EXCLUDED.review_comment,
    notes = EXCLUDED.notes,
    budget_line_id = EXCLUDED.budget_line_id,
    cost_category_id = EXCLUDED.cost_category_id,
    updated_at = now();

INSERT INTO maintenance_completion_anchors (id, created_at, updated_at, is_deleted, equipment_id, regulation_id, equipment_maintenance_rule_id, work_order_id, ppr_task_id, maintenance_due_event_id, performed_at, planned_due_at, planned_meter_value, recalculation_policy, meter_snapshots, source, note)
VALUES
('20000000-0000-0000-0000-000000000361', now(), now(), false, '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000131', NULL, '20000000-0000-0000-0000-000000000301', NULL, NULL, TIMESTAMP '2026-05-20 15:40:00+05', TIMESTAMP '2026-05-20 16:00:00+05', 12000.0000, 'FROM_ACTUAL_COMPLETION', '[{"meterType":"CYCLES","value":12000}]'::jsonb, 'WORK_ORDER_CLOSE', 'Historical completion anchor for AUTO-PUMP-A1 next-cycle calculation.'),
('20000000-0000-0000-0000-000000000362', now(), now(), false, '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000131', NULL, NULL, NULL, NULL, TIMESTAMP '2026-05-25 10:00:00+05', TIMESTAMP '2026-05-25 10:00:00+05', 5600.0000, 'FROM_ACTUAL_COMPLETION', '[{"meterType":"CYCLES","value":5600}]'::jsonb, 'DEMO_SEED', 'Healthy control anchor for AUTO-PUMP-A2.')
ON CONFLICT (id) DO UPDATE
SET performed_at = EXCLUDED.performed_at,
    planned_due_at = EXCLUDED.planned_due_at,
    planned_meter_value = EXCLUDED.planned_meter_value,
    meter_snapshots = EXCLUDED.meter_snapshots,
    source = EXCLUDED.source,
    note = EXCLUDED.note,
    updated_at = now();
