-- TABLE: actual_cost_review_route_overrides
CREATE TABLE public.actual_cost_review_route_overrides (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    threshold_hours integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deactivated_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    actual_cost_id uuid NOT NULL,
    created_by_id uuid,
    deactivated_by_id uuid,
    department_id uuid,
    id uuid NOT NULL,
    approval_role_code character varying(255) NOT NULL,
    comment text NOT NULL,
    deactivation_comment text,
    escalation_role_code character varying(255)
);

-- TABLE: audit_logs
CREATE TABLE public.audit_logs (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid,
    action character varying(255) NOT NULL,
    entity_id character varying(255),
    entity_type character varying(255),
    ip_address character varying(255),
    message text,
    module character varying(255) NOT NULL,
    user_agent character varying(255),
    current_snapshot jsonb,
    diff_json jsonb,
    previous_snapshot jsonb,
    CONSTRAINT audit_logs_action_check CHECK (((action)::text = ANY ((ARRAY['LOGIN'::character varying, 'CREATE'::character varying, 'UPDATE'::character varying, 'APPROVE'::character varying, 'CLOSE'::character varying, 'CANCEL'::character varying, 'EXPORT'::character varying, 'DELETE'::character varying])::text[]))),
    CONSTRAINT audit_logs_module_check CHECK (((module)::text = ANY ((ARRAY['EQUIPMENT'::character varying, 'EQUIPMENT_TYPE'::character varying, 'WAREHOUSE'::character varying, 'LOCATION'::character varying, 'DEPARTMENT'::character varying, 'CONTRACTOR'::character varying, 'MANUFACTURER'::character varying, 'MATERIAL'::character varying, 'UNIT_OF_MEASUREMENT'::character varying, 'COST_CATEGORY'::character varying, 'CRITICALITY_CLASS'::character varying, 'DEFECT_CATEGORY'::character varying, 'DEFECT_SEVERITY'::character varying, 'FAILURE_REASON'::character varying, 'ROOT_CAUSE'::character varying, 'SERVICE_CLASS'::character varying, 'ROLE'::character varying, 'USER'::character varying, 'SPARE_PART'::character varying, 'VEHICLE'::character varying, 'PPR_PLAN'::character varying, 'WORK_ORDER'::character varying, 'REPAIR_REQUEST'::character varying, 'DEFECT'::character varying, 'MAINTENANCE_TEMPLATE'::character varying, 'MAINTENANCE_REGULATION'::character varying, 'BRIGADE'::character varying, 'CALIBRATION_RECORD'::character varying, 'OEE_RECORD'::character varying, 'CONTRACTOR_CONTRACT'::character varying, 'ACTUAL_COST'::character varying, 'APPROVAL_REQUEST'::character varying, 'BRIGADE_MEMBER'::character varying, 'COMPLETION_ACT'::character varying, 'CONDITION_READING'::character varying, 'CONTRACTOR_WORK'::character varying, 'DEFECT_LIST'::character varying, 'DEFECT_LIST_LINE'::character varying, 'EQUIPMENT_KPI'::character varying, 'EQUIPMENT_NODE'::character varying, 'EQUIPMENT_SPARE_PART'::character varying, 'FILE_ASSET'::character varying, 'LABOR_ENTRY'::character varying, 'MAINTENANCE_BUDGET'::character varying, 'MAINTENANCE_KPI'::character varying, 'PLANNED_SHUTDOWN'::character varying, 'PROCUREMENT_REQUEST'::character varying, 'REPAIR_CAMPAIGN'::character varying, 'REPAIR_CAMPAIGN_STAGE'::character varying, 'SAFETY_PERMIT'::character varying, 'STOCK_MOVEMENT'::character varying, 'TECHNICAL_DOCUMENT'::character varying, 'WEBHOOK'::character varying, 'RESERVATION'::character varying, 'WORK_EXECUTION'::character varying, 'EQUIPMENT_METER'::character varying, 'METER_READING'::character varying, 'DOWNTIME_EVENT'::character varying, 'INSPECTION_ROUTE'::character varying, 'INSPECTION_CHECKPOINT'::character varying, 'INSPECTION_ROUND'::character varying, 'INSPECTION_ROUND_RESULT'::character varying, 'CERTIFICATION_TYPE'::character varying, 'USER_CERTIFICATION'::character varying, 'RELIABILITY_METRIC'::character varying, 'EMPLOYEE'::character varying, 'TIMESHEET_ENTRY'::character varying, 'PPR_TASK'::character varying, 'REPAIR_MATERIAL_USAGE'::character varying, 'INTEGRATION_SYNC_LOG'::character varying, 'SLA_RULE'::character varying, 'FINANCIAL_APPROVAL_RULE'::character varying, 'INTEGRATION_ENDPOINT'::character varying, 'USERS'::character varying, 'AUTH'::character varying, 'CONTRACTORS'::character varying, 'PROJECTS'::character varying, 'MAINTENANCE'::character varying, 'DEPARTMENTS'::character varying, 'OTHER'::character varying])::text[])))
);

-- TABLE: calibration_records
CREATE TABLE public.calibration_records (
    is_deleted boolean DEFAULT false NOT NULL,
    measured_error double precision,
    next_due_at date,
    performed_at date NOT NULL,
    tolerance double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    document_file_id uuid,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    certificate_number character varying(255),
    notes text,
    performed_by character varying(255),
    result character varying(255) NOT NULL,
    unit character varying(255)
);

-- TABLE: certification_types
CREATE TABLE public.certification_types (
    is_deleted boolean DEFAULT false NOT NULL,
    validity_months integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    category character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    description text,
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255)
);

-- TABLE: completion_acts
CREATE TABLE public.completion_acts (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    signed_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    signed_by_id uuid,
    work_order_id uuid NOT NULL,
    act_number character varying(255) NOT NULL,
    summary text
);

-- TABLE: condition_readings
CREATE TABLE public.condition_readings (
    alarm_high double precision,
    alarm_low double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    value double precision NOT NULL,
    warn_high double precision,
    warn_low double precision,
    created_at timestamp(6) with time zone NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    recorded_by uuid,
    notes text,
    parameter character varying(255) NOT NULL,
    severity character varying(255) NOT NULL,
    unit character varying(255) NOT NULL,
    CONSTRAINT condition_readings_parameter_check CHECK (((parameter)::text = ANY ((ARRAY['VIBRATION'::character varying, 'TEMPERATURE'::character varying, 'PRESSURE'::character varying, 'RUN_HOURS'::character varying, 'CURRENT'::character varying, 'OIL_LEVEL'::character varying, 'NOISE'::character varying, 'FLOW_RATE'::character varying, 'OTHER'::character varying])::text[])))
);

-- TABLE: contractor_contracts
CREATE TABLE public.contractor_contracts (
    amount double precision,
    end_date date,
    is_deleted boolean DEFAULT false NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    contractor_id uuid NOT NULL,
    id uuid NOT NULL,
    number character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    subject character varying(255) NOT NULL,
    CONSTRAINT contractor_contracts_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'TERMINATED'::character varying])::text[])))
);

-- TABLE: contractor_works
CREATE TABLE public.contractor_works (
    cost double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    accepted_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    accepted_by_id uuid,
    contractor_id uuid NOT NULL,
    created_by_id uuid,
    id uuid NOT NULL,
    work_order_id uuid,
    acceptance_comment text,
    description text NOT NULL,
    result text,
    status character varying(255) NOT NULL,
    CONSTRAINT contractor_works_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'ACCEPTED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: contractors
CREATE TABLE public.contractors (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    contact_person character varying(255),
    email character varying(255),
    name character varying(255) NOT NULL,
    phone character varying(255),
    specialization character varying(255),
    status character varying(255) NOT NULL,
    tax_number character varying(255),
    CONSTRAINT contractors_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'BLOCKED'::character varying])::text[])))
);

-- TABLE: downtime_events
CREATE TABLE public.downtime_events (
    duration_minutes integer,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    end_at timestamp(6) with time zone,
    start_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    work_order_id uuid,
    description text,
    type character varying(255) NOT NULL,
    CONSTRAINT downtime_events_type_check CHECK (((type)::text = ANY ((ARRAY['PLANNED'::character varying, 'UNPLANNED'::character varying, 'EMERGENCY'::character varying])::text[])))
);

-- TABLE: equipment_attribute_definitions
CREATE TABLE public.equipment_attribute_definitions (
    is_deleted boolean DEFAULT false NOT NULL,
    is_required boolean NOT NULL,
    max_value double precision,
    min_value double precision,
    sort_order integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_type_id uuid NOT NULL,
    id uuid NOT NULL,
    option_source_id uuid,
    attribute_key character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    group_name character varying(255),
    label character varying(255) NOT NULL,
    label_ru character varying(255),
    label_uz character varying(255),
    options_json text,
    unit character varying(255),
    CONSTRAINT equipment_attribute_definitions_data_type_check CHECK (((data_type)::text = ANY ((ARRAY['TEXT'::character varying, 'NUMBER'::character varying, 'DATE'::character varying, 'BOOLEAN'::character varying, 'SELECT'::character varying, 'MULTI_SELECT'::character varying, 'FILE'::character varying, 'REFERENCE'::character varying, 'RANGE'::character varying, 'JSON'::character varying])::text[])))
);

-- TABLE: equipment_attribute_option_items
CREATE TABLE public.equipment_attribute_option_items (
    active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    option_source_id uuid NOT NULL,
    label character varying(255) NOT NULL,
    label_ru character varying(255),
    label_uz character varying(255),
    option_id character varying(255) NOT NULL
);

-- TABLE: equipment_attribute_option_sources
CREATE TABLE public.equipment_attribute_option_sources (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_ru character varying(255),
    name_uz character varying(255)
);

-- TABLE: equipment_attribute_required_criticality
CREATE TABLE public.equipment_attribute_required_criticality (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    attribute_definition_id uuid NOT NULL,
    criticality_class_id uuid NOT NULL,
    id uuid NOT NULL
);

-- TABLE: equipment_attribute_value_history
CREATE TABLE public.equipment_attribute_value_history (
    is_deleted boolean DEFAULT false NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    attribute_definition_id uuid NOT NULL,
    changed_by uuid,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    attribute_key character varying(255) NOT NULL,
    attribute_label character varying(255) NOT NULL,
    new_value text,
    old_value text,
    reason text,
    source character varying(255) NOT NULL,
    CONSTRAINT equipment_attribute_value_history_source_check CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'SYSTEM'::character varying, 'IMPORT'::character varying, 'API'::character varying])::text[])))
);

-- TABLE: equipment_attribute_values
CREATE TABLE public.equipment_attribute_values (
    is_deleted boolean DEFAULT false NOT NULL,
    value_boolean boolean,
    value_date date,
    value_number double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    attribute_definition_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    value_json text,
    value_option character varying(255),
    value_text text
);

-- TABLE: equipment_meters
CREATE TABLE public.equipment_meters (
    current_value double precision NOT NULL,
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    rollover_value double precision,
    created_at timestamp(6) with time zone NOT NULL,
    last_read_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    meter_type character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    unit character varying(255) NOT NULL,
    CONSTRAINT equipment_meters_meter_type_check CHECK (((meter_type)::text = ANY ((ARRAY['ENGINE_HOURS'::character varying, 'MILEAGE_KM'::character varying, 'CYCLES'::character varying, 'TONS_PRODUCED'::character varying, 'KWH_CONSUMED'::character varying, 'CUSTOM'::character varying])::text[])))
);

-- TABLE: equipment_nodes
CREATE TABLE public.equipment_nodes (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    parent_id uuid,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    node_type character varying(255) NOT NULL,
    serial_number character varying(255),
    CONSTRAINT equipment_nodes_node_type_check CHECK (((node_type)::text = ANY ((ARRAY['ASSEMBLY'::character varying, 'UNIT'::character varying, 'SUBUNIT'::character varying, 'COMPONENT'::character varying, 'INSTRUMENT'::character varying])::text[])))
);

-- TABLE: equipment_passports
CREATE TABLE public.equipment_passports (
    install_date date,
    is_deleted boolean DEFAULT false NOT NULL,
    last_inspection_date date,
    power_kw double precision,
    pressure_bar double precision,
    throughput double precision,
    voltage_v double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    factory_number character varying(255),
    manufacturer_serial character varying(255),
    notes text,
    passport_number character varying(255)
);

-- TABLE: equipment_spare_parts
CREATE TABLE public.equipment_spare_parts (
    consumption_rate_per_year double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    quantity_per_unit double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    criticality character varying(255),
    notes text,
    "position" character varying(255)
);

-- TABLE: equipment_status_history
CREATE TABLE public.equipment_status_history (
    is_deleted boolean DEFAULT false NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    changed_by uuid,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    related_entity_id uuid,
    related_entity_type character varying(100),
    note character varying(1000),
    reason character varying(1000) NOT NULL,
    from_status character varying(255) NOT NULL,
    source character varying(255) NOT NULL,
    to_status character varying(255) NOT NULL,
    CONSTRAINT equipment_status_history_from_status_check CHECK (((from_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'STANDBY'::character varying, 'IN_REPAIR'::character varying, 'CONSERVATION'::character varying, 'DECOMMISSIONED'::character varying, 'OUT_OF_SERVICE'::character varying])::text[]))),
    CONSTRAINT equipment_status_history_source_check CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'WORK_ORDER'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT equipment_status_history_to_status_check CHECK (((to_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'STANDBY'::character varying, 'IN_REPAIR'::character varying, 'CONSERVATION'::character varying, 'DECOMMISSIONED'::character varying, 'OUT_OF_SERVICE'::character varying])::text[])))
);

-- TABLE: escalation_events
CREATE TABLE public.escalation_events (
    is_deleted boolean DEFAULT false NOT NULL,
    acknowledged_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    raised_at timestamp(6) with time zone NOT NULL,
    resolved_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    acknowledged_by_id uuid,
    id uuid NOT NULL,
    resolved_by_id uuid,
    sla_rule_id uuid,
    entity_id character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    notes text,
    status character varying(255) NOT NULL,
    trigger_type character varying(255) NOT NULL,
    CONSTRAINT escalation_events_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKNOWLEDGED'::character varying, 'RESOLVED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT escalation_events_trigger_type_check CHECK (((trigger_type)::text = ANY ((ARRAY['REQUEST_EMERGENCY'::character varying, 'REQUEST_OVERDUE'::character varying, 'PPR_OVERDUE'::character varying, 'WORK_ORDER_OVERDUE'::character varying, 'DEFECT_REPEAT'::character varying])::text[])))
);

-- TABLE: file_assets
CREATE TABLE public.file_assets (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    size_bytes bigint NOT NULL,
    id uuid NOT NULL,
    uploaded_by_id uuid,
    entity_id character varying(255),
    entity_type character varying(255),
    file_name character varying(255) NOT NULL,
    mime_type character varying(255) NOT NULL,
    original_name character varying(255) NOT NULL,
    storage_path character varying(255) NOT NULL
);

-- TABLE: financial_approval_rules
CREATE TABLE public.financial_approval_rules (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    max_amount double precision,
    min_amount double precision,
    priority integer NOT NULL,
    threshold_hours integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    escalate_to_role_code character varying(255),
    name character varying(255) NOT NULL,
    notes text,
    required_role_code character varying(255) NOT NULL
);

-- TABLE: integration_endpoints
CREATE TABLE public.integration_endpoints (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    port integer,
    sync_defects boolean DEFAULT false NOT NULL,
    sync_downtimes boolean DEFAULT false NOT NULL,
    sync_interval_minutes integer,
    sync_production boolean DEFAULT false NOT NULL,
    sync_scada boolean DEFAULT false NOT NULL,
    sync_work_orders boolean DEFAULT false NOT NULL,
    timeout_seconds integer,
    created_at timestamp(6) with time zone NOT NULL,
    last_sync_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    url character varying(2048) NOT NULL,
    api_key character varying(255),
    auth_type character varying(255),
    base_path character varying(255),
    code character varying(255) NOT NULL,
    last_error text,
    last_sync_status character varying(255),
    name character varying(255) NOT NULL,
    password character varying(255),
    system character varying(255) NOT NULL,
    username character varying(255),
    CONSTRAINT integration_endpoints_last_sync_status_check CHECK (((last_sync_status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying])::text[])))
);

-- TABLE: integration_sync_logs
CREATE TABLE public.integration_sync_logs (
    is_deleted boolean DEFAULT false NOT NULL,
    records_received integer,
    records_sent integer,
    created_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    endpoint_id uuid NOT NULL,
    id uuid NOT NULL,
    direction character varying(255) NOT NULL,
    error_message text,
    module character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT integration_sync_logs_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying])::text[])))
);

-- TABLE: maintenance_operations
CREATE TABLE public.maintenance_operations (
    control_max double precision,
    control_min double precision,
    duration_hours double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    sequence integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    template_id uuid NOT NULL,
    consumables_required text,
    control_parameter character varying(255),
    control_unit character varying(255),
    description character varying(255),
    instruction_url character varying(255),
    name character varying(255) NOT NULL,
    required_skill character varying(255),
    safety_notes text,
    spare_parts_required text,
    tools_required text
);

-- TABLE: maintenance_regulation_attribute_conditions
CREATE TABLE public.maintenance_regulation_attribute_conditions (
    is_deleted boolean DEFAULT false NOT NULL,
    value_boolean boolean,
    value_date date,
    value_number double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    regulation_id uuid NOT NULL,
    attribute_key character varying(255) NOT NULL,
    operator character varying(255) NOT NULL,
    value_option character varying(255),
    value_text text,
    CONSTRAINT maintenance_regulation_attribute_conditions_operator_check CHECK (((operator)::text = ANY ((ARRAY['EQUALS'::character varying, 'NOT_EQUALS'::character varying, 'GREATER_THAN'::character varying, 'GREATER_THAN_OR_EQUALS'::character varying, 'LESS_THAN'::character varying, 'LESS_THAN_OR_EQUALS'::character varying, 'EXISTS'::character varying, 'NOT_EXISTS'::character varying])::text[])))
);

-- TABLE: maintenance_templates
CREATE TABLE public.maintenance_templates (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    normative_labor_hours double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_type_id uuid NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    maintenance_kind character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT maintenance_templates_maintenance_kind_check CHECK (((maintenance_kind)::text = ANY ((ARRAY['PREVENTIVE'::character varying, 'PREDICTIVE'::character varying, 'CONDITION_BASED'::character varying, 'INSPECTION'::character varying, 'DIAGNOSTIC'::character varying, 'CURRENT_REPAIR'::character varying, 'MEDIUM_REPAIR'::character varying, 'OVERHAUL'::character varying, 'SEASONAL'::character varying, 'METROLOGICAL'::character varying, 'ELECTRICAL'::character varying, 'INSTRUMENTATION'::character varying])::text[])))
);

-- TABLE: manufacturers
CREATE TABLE public.manufacturers (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    contact_info character varying(255),
    country character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255),
    website character varying(255)
);

-- TABLE: materials
CREATE TABLE public.materials (
    is_deleted boolean DEFAULT false NOT NULL,
    min_stock double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    kind character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    specification character varying(255),
    unit character varying(255) NOT NULL,
    CONSTRAINT materials_kind_check CHECK (((kind)::text = ANY ((ARRAY['SPARE_PART'::character varying, 'MATERIAL'::character varying, 'CONSUMABLE'::character varying])::text[])))
);

-- TABLE: meter_readings
CREATE TABLE public.meter_readings (
    delta double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    value double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    read_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    meter_id uuid NOT NULL,
    recorded_by_user_id uuid,
    device_id character varying(255),
    note text,
    source character varying(255) NOT NULL,
    CONSTRAINT meter_readings_source_check CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'IOT'::character varying, 'SCADA'::character varying, 'IMPORT'::character varying])::text[])))
);

-- TABLE: notifications
CREATE TABLE public.notifications (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    read_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    channel character varying(255) NOT NULL,
    entity_id character varying(255),
    entity_type character varying(255),
    message text NOT NULL,
    severity character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT notifications_channel_check CHECK (((channel)::text = ANY ((ARRAY['WEB'::character varying, 'EMAIL'::character varying, 'TELEGRAM'::character varying])::text[]))),
    CONSTRAINT notifications_severity_check CHECK (((severity)::text = ANY ((ARRAY['INFO'::character varying, 'WARNING'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'READ'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: oee_records
CREATE TABLE public.oee_records (
    availability double precision NOT NULL,
    good_count double precision NOT NULL,
    ideal_cycle_seconds double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    oee double precision NOT NULL,
    performance double precision NOT NULL,
    planned_production_minutes double precision NOT NULL,
    quality double precision NOT NULL,
    run_minutes double precision NOT NULL,
    total_count double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    shift_end timestamp(6) with time zone NOT NULL,
    shift_start timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    notes text
);

-- TABLE: planned_shutdowns
CREATE TABLE public.planned_shutdowns (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    end_at timestamp(6) with time zone NOT NULL,
    start_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid NOT NULL,
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    reason text NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT planned_shutdowns_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'GENERATED'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: rcm_snapshots
CREATE TABLE public.rcm_snapshots (
    consequence integer NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    mtbf_hours double precision NOT NULL,
    mttr_hours double precision NOT NULL,
    probability integer NOT NULL,
    repair_priority integer,
    risk_score integer NOT NULL,
    captured_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    open_defects bigint NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    criticality_class character varying(255),
    equipment_code character varying(255) NOT NULL,
    equipment_name character varying(255) NOT NULL
);

-- TABLE: reliability_metrics
CREATE TABLE public.reliability_metrics (
    availability double precision,
    failure_rate double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    metric_date date NOT NULL,
    mtbf_hours double precision,
    mttr_hours double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL
);

-- TABLE: repair_campaign_stages
CREATE TABLE public.repair_campaign_stages (
    actual_cost double precision NOT NULL,
    end_date date NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    planned_cost double precision NOT NULL,
    sequence integer NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    notes text,
    status character varying(255) NOT NULL,
    CONSTRAINT repair_campaign_stages_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: repair_campaigns
CREATE TABLE public.repair_campaigns (
    end_date date NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    quarter integer,
    start_date date NOT NULL,
    total_actual double precision NOT NULL,
    total_budget double precision NOT NULL,
    year integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    notes text,
    scope text,
    status character varying(255) NOT NULL,
    CONSTRAINT repair_campaigns_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: reservations
CREATE TABLE public.reservations (
    is_deleted boolean DEFAULT false NOT NULL,
    quantity double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    repair_request_id uuid,
    reserved_by_id uuid,
    warehouse_stock_id uuid NOT NULL,
    work_order_id uuid,
    status character varying(255) NOT NULL,
    CONSTRAINT reservations_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'FULFILLED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: roles
CREATE TABLE public.roles (
    is_deleted boolean DEFAULT false NOT NULL,
    is_system boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255),
    permissions jsonb
);

-- TABLE: safety_permits
CREATE TABLE public.safety_permits (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    issued_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    valid_until timestamp(6) with time zone,
    id uuid NOT NULL,
    issued_by_id uuid,
    work_order_id uuid NOT NULL,
    notes text,
    permit_number character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT safety_permits_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ISSUED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- TABLE: service_classes
CREATE TABLE public.service_classes (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255)
);

-- TABLE: sla_rules
CREATE TABLE public.sla_rules (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    threshold_hours integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    trigger_type character varying(255) NOT NULL,
    CONSTRAINT sla_rules_entity_type_check CHECK (((entity_type)::text = ANY ((ARRAY['REPAIR_REQUEST'::character varying, 'PPR_TASK'::character varying, 'WORK_ORDER'::character varying, 'DEFECT'::character varying])::text[]))),
    CONSTRAINT sla_rules_trigger_type_check CHECK (((trigger_type)::text = ANY ((ARRAY['REQUEST_EMERGENCY'::character varying, 'REQUEST_OVERDUE'::character varying, 'PPR_OVERDUE'::character varying, 'WORK_ORDER_OVERDUE'::character varying, 'DEFECT_REPEAT'::character varying])::text[])))
);

-- TABLE: technical_documents
CREATE TABLE public.technical_documents (
    document_date date,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    equipment_node_id uuid,
    file_id uuid,
    id uuid NOT NULL,
    uploaded_by_id uuid,
    revision character varying(255),
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT technical_documents_type_check CHECK (((type)::text = ANY ((ARRAY['PASSPORT'::character varying, 'MANUAL'::character varying, 'DRAWING'::character varying, 'CERTIFICATE'::character varying, 'PHOTO'::character varying, 'REPORT'::character varying, 'ACT'::character varying, 'OTHER'::character varying])::text[])))
);

-- TABLE: uploaded_files (NOT FOUND)
-- TABLE: user_certifications
CREATE TABLE public.user_certifications (
    expires_at date,
    is_deleted boolean DEFAULT false NOT NULL,
    issued_at date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    document_file_id uuid,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    certificate_number character varying(255),
    grade_or_level character varying(255),
    issued_by character varying(255),
    notes text,
    status character varying(255) NOT NULL,
    type_code character varying(255) NOT NULL
);

-- TABLE: vehicle_details
CREATE TABLE public.vehicle_details (
    carrying_capacity double precision,
    current_engine_hours double precision NOT NULL,
    current_odometer_km double precision NOT NULL,
    fuel_tank_capacity double precision,
    insurance_expiry_date date,
    is_deleted boolean DEFAULT false NOT NULL,
    manufacture_year integer,
    seat_count integer,
    technical_inspection_expiry_date date,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    assigned_driver_id uuid,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    body_number character varying(255),
    brand character varying(255),
    chassis_number character varying(255),
    engine_number character varying(255),
    fuel_type character varying(255),
    gps_device_id character varying(255),
    insurance_policy_number character varying(255),
    model character varying(255),
    plate_number character varying(255) NOT NULL,
    registration_certificate_number character varying(255),
    vehicle_type character varying(255) NOT NULL,
    vin character varying(255),
    CONSTRAINT vehicle_details_vehicle_type_check CHECK (((vehicle_type)::text = ANY ((ARRAY['PASSENGER_CAR'::character varying, 'TRUCK'::character varying, 'BUS'::character varying, 'SPECIAL_EQUIPMENT'::character varying, 'FORKLIFT'::character varying, 'TRAILER'::character varying, 'OTHER'::character varying])::text[])))
);

-- TABLE: warehouse_equipment_items
CREATE TABLE public.warehouse_equipment_items (
    active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT warehouse_equipment_items_status_check CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RESERVED'::character varying, 'INSTALLED'::character varying, 'OUT_OF_SERVICE'::character varying])::text[])))
);

-- TABLE: webhook_event_log
CREATE TABLE public.webhook_event_log (
    http_status integer,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    fired_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    error text,
    event_code character varying(255) NOT NULL,
    payload text
);

-- TABLE: webhook_subscriptions
CREATE TABLE public.webhook_subscriptions (
    failure_count integer NOT NULL,
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_delivery_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    last_delivery_status character varying(255),
    name character varying(255) NOT NULL,
    secret character varying(255),
    target_url text NOT NULL,
    events jsonb NOT NULL
);

