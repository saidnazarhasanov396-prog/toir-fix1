--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: actual_cost_review_route_overrides; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: actual_costs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.actual_costs (
    amount double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    cost_date timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    reviewed_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    budget_line_id uuid,
    contractor_work_id uuid,
    cost_category_id uuid NOT NULL,
    id uuid NOT NULL,
    repair_request_id uuid,
    reviewed_by_id uuid,
    work_order_id uuid,
    notes text,
    review_comment text,
    status character varying(255) NOT NULL,
    CONSTRAINT actual_costs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: approval_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approval_requests (
    current_step integer NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    document_id uuid NOT NULL,
    id uuid NOT NULL,
    requester_id uuid NOT NULL,
    description text,
    document_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT approval_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: approval_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approval_steps (
    is_deleted boolean DEFAULT false NOT NULL,
    step_number integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    decided_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    approver_id uuid NOT NULL,
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    approver_role character varying(255),
    comment text,
    decision character varying(255) NOT NULL,
    CONSTRAINT approval_steps_decision_check CHECK (((decision)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: brigade_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.brigade_members (
    grade integer,
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    brigade_id uuid NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    qualifications jsonb,
    role_code character varying(255) NOT NULL
);


--
-- Name: brigades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.brigades (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    foreman_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    specialization text
);


--
-- Name: budget_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.budget_lines (
    actual_amount double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    planned_amount double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    budget_id uuid NOT NULL,
    cost_category_id uuid NOT NULL,
    id uuid NOT NULL,
    description character varying(255)
);


--
-- Name: calibration_records; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: certification_types; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: completion_acts; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: condition_readings; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: contractor_contracts; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: contractor_works; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: contractors; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: cost_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cost_categories (
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


--
-- Name: criticality_classes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.criticality_classes (
    ecological_impact integer,
    energy_impact integer,
    is_deleted boolean DEFAULT false NOT NULL,
    production_impact integer,
    repair_priority integer,
    safety_impact integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    failure_consequence text,
    level character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255),
    CONSTRAINT criticality_classes_level_check CHECK (((level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: defect_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_categories (
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


--
-- Name: defect_list_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_list_lines (
    estimated_cost double precision NOT NULL,
    estimated_labor_hours double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    required_quantity double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    defect_id uuid,
    defect_list_id uuid NOT NULL,
    id uuid NOT NULL,
    spare_part_id uuid,
    description text NOT NULL,
    material_specification text,
    work_scope text
);


--
-- Name: defect_lists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_lists (
    is_deleted boolean DEFAULT false NOT NULL,
    total_estimated_cost double precision NOT NULL,
    total_labor_hours double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    approved_by_id uuid,
    created_by_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    repair_request_id uuid,
    work_order_id uuid,
    code character varying(255) NOT NULL,
    notes text,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT defect_lists_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: defect_severities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_severities (
    is_deleted boolean DEFAULT false NOT NULL,
    weight integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255)
);


--
-- Name: defects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defects (
    is_deleted boolean DEFAULT false NOT NULL,
    recurrence_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    detected_at timestamp(6) with time zone NOT NULL,
    resolved_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    equipment_node_id uuid,
    id uuid NOT NULL,
    repair_request_id uuid,
    category character varying(255),
    code character varying(255) NOT NULL,
    description text NOT NULL,
    failure_reason character varying(255),
    root_cause character varying(255),
    severity character varying(255),
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT defects_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_ANALYSIS'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    parent_id uuid,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255),
    type character varying(255) NOT NULL,
    CONSTRAINT departments_type_check CHECK (((type)::text = ANY ((ARRAY['ENTERPRISE'::character varying, 'SITE'::character varying, 'WORKSHOP'::character varying, 'SECTION'::character varying, 'AREA'::character varying, 'LINE'::character varying, 'SERVICE'::character varying, 'ADMINISTRATION'::character varying])::text[])))
);


--
-- Name: downtime_events; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.equipment (
    commissioned_at date,
    is_deleted boolean DEFAULT false NOT NULL,
    warranty_until date,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    criticality_class_id uuid,
    department_id uuid,
    equipment_type_id uuid NOT NULL,
    id uuid NOT NULL,
    location_id uuid,
    parent_id uuid,
    responsible_id uuid,
    category character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    inventory_number character varying(255) NOT NULL,
    manufacturer character varying(255),
    model character varying(255),
    name character varying(255) NOT NULL,
    serial_number character varying(255),
    status character varying(255) NOT NULL,
    technical_number character varying(255),
    CONSTRAINT equipment_category_check CHECK (((category)::text = ANY ((ARRAY['PRODUCTION_EQUIPMENT'::character varying, 'VEHICLE'::character varying, 'ENERGY_EQUIPMENT'::character varying, 'INSTRUMENTATION'::character varying, 'BUILDING_INFRASTRUCTURE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT equipment_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'STANDBY'::character varying, 'IN_REPAIR'::character varying, 'CONSERVATION'::character varying, 'DECOMMISSIONED'::character varying, 'OUT_OF_SERVICE'::character varying])::text[])))
);


--
-- Name: equipment_attribute_definitions; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_attribute_option_items; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_attribute_option_sources; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_attribute_required_criticality; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.equipment_attribute_required_criticality (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    attribute_definition_id uuid NOT NULL,
    criticality_class_id uuid NOT NULL,
    id uuid NOT NULL
);


--
-- Name: equipment_attribute_value_history; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_attribute_values; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_kpis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.equipment_kpis (
    availability double precision,
    downtime_hours double precision,
    failure_count integer,
    is_deleted boolean DEFAULT false NOT NULL,
    mtbf_hours double precision,
    mttr_hours double precision,
    operating_hours double precision,
    period_end date NOT NULL,
    period_start date NOT NULL,
    repair_count integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL
);


--
-- Name: equipment_meters; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_nodes; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_passports; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_spare_parts; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_status_history; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: equipment_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.equipment_types (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    category character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255)
);


--
-- Name: escalation_events; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: failure_reasons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.failure_reasons (
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


--
-- Name: file_assets; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: financial_approval_rules; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: hr_employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hr_employees (
    hire_date date NOT NULL,
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    terminated_date date,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    brigade_id uuid,
    department_id uuid,
    id uuid NOT NULL,
    user_id uuid,
    email character varying(255),
    first_name character varying(255) NOT NULL,
    grade character varying(255),
    last_name character varying(255) NOT NULL,
    middle_name character varying(255),
    personnel_number character varying(255) NOT NULL,
    phone character varying(255),
    "position" character varying(255) NOT NULL
);


--
-- Name: hr_timesheet_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hr_timesheet_entries (
    hours_holiday double precision NOT NULL,
    hours_night double precision NOT NULL,
    hours_overtime double precision NOT NULL,
    hours_regular double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    work_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    cost_category_id uuid,
    employee_id uuid NOT NULL,
    id uuid NOT NULL,
    work_order_id uuid,
    note text,
    status character varying(255) NOT NULL,
    CONSTRAINT hr_timesheet_entries_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: inspection_checkpoints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_checkpoints (
    expected_max double precision,
    expected_min double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    is_mandatory boolean NOT NULL,
    order_index integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid,
    id uuid NOT NULL,
    location_id uuid,
    route_id uuid NOT NULL,
    check_type character varying(255) NOT NULL,
    expected_unit character varying(255),
    instruction text,
    title character varying(255) NOT NULL
);


--
-- Name: inspection_round_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_round_results (
    is_deleted boolean DEFAULT false NOT NULL,
    measured_value double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    checkpoint_id uuid NOT NULL,
    defect_id uuid,
    id uuid NOT NULL,
    round_id uuid NOT NULL,
    comment text,
    measured_unit character varying(255),
    status character varying(255) NOT NULL,
    photo_file_ids jsonb
);


--
-- Name: inspection_rounds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_rounds (
    alarm_count integer NOT NULL,
    findings_count integer NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    performed_by uuid NOT NULL,
    route_id uuid NOT NULL,
    notes text,
    status character varying(255) NOT NULL,
    CONSTRAINT inspection_rounds_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: inspection_routes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_routes (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    target_duration_min integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description text,
    frequency character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: integration_endpoints; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: integration_sync_logs; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: knowledge_articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_articles (
    is_deleted boolean DEFAULT false NOT NULL,
    view_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    author_id uuid,
    defect_id uuid,
    equipment_id uuid,
    equipment_type_id uuid,
    id uuid NOT NULL,
    work_order_id uuid,
    code character varying(255) NOT NULL,
    kind character varying(255) NOT NULL,
    preventive_actions text,
    problem text NOT NULL,
    root_cause text NOT NULL,
    solution text NOT NULL,
    title character varying(255) NOT NULL,
    tags jsonb
);


--
-- Name: labor_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.labor_entries (
    hours double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    rate double precision,
    work_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid,
    work_order_id uuid NOT NULL,
    contractor_name character varying(255),
    description text
);


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    parent_id uuid,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255),
    type character varying(255) NOT NULL,
    CONSTRAINT locations_type_check CHECK (((type)::text = ANY ((ARRAY['SITE'::character varying, 'BUILDING'::character varying, 'WORKSHOP'::character varying, 'SECTION'::character varying, 'LINE'::character varying, 'ZONE'::character varying, 'ROOM'::character varying, 'STORAGE'::character varying, 'WAREHOUSE'::character varying, 'PLATFORM'::character varying])::text[])))
);


--
-- Name: maintenance_budgets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_budgets (
    is_deleted boolean DEFAULT false NOT NULL,
    month integer,
    total_actual double precision NOT NULL,
    total_planned double precision NOT NULL,
    year integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT maintenance_budgets_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'LOCKED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: maintenance_kpis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_kpis (
    average_repair_duration_hours double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    period_end date NOT NULL,
    period_start date NOT NULL,
    ppr_completed_count integer,
    ppr_completion_rate double precision,
    ppr_planned_count integer,
    total_cost double precision,
    unplanned_repair_share double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid NOT NULL,
    id uuid NOT NULL
);


--
-- Name: maintenance_operations; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: maintenance_regulation_attribute_conditions; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: maintenance_regulations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_regulations (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    normative_labor_hours double precision NOT NULL,
    periodicity_value integer NOT NULL,
    requires_shutdown boolean NOT NULL,
    tolerance_days integer,
    trigger_meter_interval double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_type_id uuid NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    maintenance_kind character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    periodicity_unit character varying(255) NOT NULL,
    trigger_meter_type character varying(255),
    CONSTRAINT maintenance_regulations_maintenance_kind_check CHECK (((maintenance_kind)::text = ANY ((ARRAY['PREVENTIVE'::character varying, 'PREDICTIVE'::character varying, 'CONDITION_BASED'::character varying, 'INSPECTION'::character varying, 'DIAGNOSTIC'::character varying, 'CURRENT_REPAIR'::character varying, 'MEDIUM_REPAIR'::character varying, 'OVERHAUL'::character varying, 'SEASONAL'::character varying, 'METROLOGICAL'::character varying, 'ELECTRICAL'::character varying, 'INSTRUMENTATION'::character varying])::text[]))),
    CONSTRAINT maintenance_regulations_periodicity_unit_check CHECK (((periodicity_unit)::text = ANY ((ARRAY['DAY'::character varying, 'WEEK'::character varying, 'MONTH'::character varying, 'QUARTER'::character varying, 'YEAR'::character varying, 'HOUR'::character varying])::text[]))),
    CONSTRAINT maintenance_regulations_trigger_meter_type_check CHECK (((trigger_meter_type)::text = ANY ((ARRAY['ENGINE_HOURS'::character varying, 'MILEAGE_KM'::character varying, 'CYCLES'::character varying, 'TONS_PRODUCED'::character varying, 'KWH_CONSUMED'::character varying, 'CUSTOM'::character varying])::text[])))
);


--
-- Name: maintenance_templates; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: manufacturers; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: materials; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: meter_readings; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: oee_records; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: planned_shutdowns; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: ppr_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppr_plans (
    end_date date NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    approved_by_id uuid,
    created_by_id uuid NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    notes character varying(255),
    status character varying(255) NOT NULL,
    CONSTRAINT ppr_plans_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'GENERATED'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: ppr_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppr_tasks (
    actual_labor_hours double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    planned_labor_hours double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    due_date timestamp(6) without time zone NOT NULL,
    scheduled_end timestamp(6) without time zone NOT NULL,
    scheduled_start timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    equipment_id uuid,
    id uuid NOT NULL,
    plan_id uuid NOT NULL,
    regulation_id uuid NOT NULL,
    code character varying(255) NOT NULL,
    postpone_reason character varying(255),
    priority character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT ppr_tasks_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying, 'EMERGENCY'::character varying])::text[]))),
    CONSTRAINT ppr_tasks_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'OVERDUE'::character varying, 'POSTPONED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: procurement_request_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procurement_request_lines (
    estimated_cost double precision NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    quantity double precision NOT NULL,
    unit_price double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    notes text,
    unit character varying(255) NOT NULL
);


--
-- Name: procurement_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procurement_requests (
    is_deleted boolean DEFAULT false NOT NULL,
    required_by date,
    total_estimated_cost double precision NOT NULL,
    approved_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    ordered_at timestamp(6) with time zone,
    received_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    approved_by uuid,
    department_id uuid,
    id uuid NOT NULL,
    requested_by uuid,
    warehouse_id uuid,
    description text,
    number character varying(255) NOT NULL,
    rejection_reason text,
    source character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT procurement_requests_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'ORDERED'::character varying, 'RECEIVED'::character varying, 'CANCELLED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: rcm_snapshots; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: reliability_metrics; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: repair_campaign_stages; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: repair_campaigns; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: repair_material_usages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repair_material_usages (
    is_deleted boolean DEFAULT false NOT NULL,
    quantity double precision NOT NULL,
    unit_cost double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    work_order_id uuid NOT NULL
);


--
-- Name: repair_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repair_requests (
    is_deleted boolean DEFAULT false NOT NULL,
    actual_completion_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    detected_at timestamp(6) with time zone NOT NULL,
    reacted_at timestamp(6) with time zone,
    target_completion_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    assigned_to_id uuid,
    department_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    id uuid NOT NULL,
    location_id uuid,
    reporter_id uuid NOT NULL,
    clarification_reason text,
    close_result text,
    criticality character varying(255) NOT NULL,
    description text NOT NULL,
    number character varying(255) NOT NULL,
    priority character varying(255) NOT NULL,
    rejection_reason text,
    source character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT repair_requests_criticality_check CHECK (((criticality)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT repair_requests_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying, 'EMERGENCY'::character varying])::text[]))),
    CONSTRAINT repair_requests_source_check CHECK (((source)::text = ANY ((ARRAY['MANUAL'::character varying, 'OPERATOR'::character varying, 'SCADA'::character varying, 'INSPECTION'::character varying, 'MOBILE'::character varying])::text[]))),
    CONSTRAINT repair_requests_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'REGISTERED'::character varying, 'IN_REVIEW'::character varying, 'NEEDS_CLARIFICATION'::character varying, 'REJECTED'::character varying, 'APPROVED'::character varying, 'ASSIGNED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: root_causes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.root_causes (
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


--
-- Name: safety_permits; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: service_classes; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: sla_rules; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: spare_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spare_parts (
    is_deleted boolean DEFAULT false NOT NULL,
    min_stock double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    kind character varying(255) NOT NULL,
    manufacturer character varying(255),
    name character varying(255) NOT NULL,
    sku character varying(255),
    specification character varying(255),
    unit character varying(255) NOT NULL,
    CONSTRAINT spare_parts_kind_check CHECK (((kind)::text = ANY ((ARRAY['SPARE_PART'::character varying, 'MATERIAL'::character varying, 'CONSUMABLE'::character varying])::text[])))
);


--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_movements (
    is_deleted boolean DEFAULT false NOT NULL,
    quantity double precision NOT NULL,
    unit_cost double precision,
    created_at timestamp(6) with time zone NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by_id uuid,
    id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    work_order_id uuid,
    document_number character varying(255),
    notes text,
    type character varying(255) NOT NULL,
    CONSTRAINT stock_movements_type_check CHECK (((type)::text = ANY ((ARRAY['RECEIPT'::character varying, 'ISSUE'::character varying, 'TRANSFER'::character varying, 'RESERVATION'::character varying, 'RELEASE'::character varying, 'ADJUSTMENT'::character varying, 'RETURN'::character varying])::text[])))
);


--
-- Name: technical_documents; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: units_of_measurement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.units_of_measurement (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    name_en character varying(255),
    name_uz character varying(255)
);


--
-- Name: user_certifications; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_login_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    primary_role_id uuid,
    email character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    phone character varying(255),
    "position" character varying(255),
    status character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: vehicle_details; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: warehouse_equipment_items; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: warehouse_stocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouse_stocks (
    avg_daily_usage double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    max_qty double precision,
    min_qty double precision NOT NULL,
    quantity double precision NOT NULL,
    reorder_point double precision,
    reorder_qty double precision,
    reserved_qty double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    bin_location character varying(255)
);


--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    is_active boolean NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    location_id uuid,
    responsible_id uuid,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: webhook_event_log; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: webhook_subscriptions; Type: TABLE; Schema: public; Owner: -
--

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


--
-- Name: work_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_executions (
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    ended_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    performer_id uuid,
    work_order_id uuid NOT NULL,
    notes text,
    result text
);


--
-- Name: work_order_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_order_tasks (
    actual_hours double precision,
    is_deleted boolean DEFAULT false NOT NULL,
    planned_hours double precision,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    assigned_to_id uuid,
    id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    description text,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT work_order_tasks_status_check CHECK (((status)::text = ANY ((ARRAY['TODO'::character varying, 'IN_PROGRESS'::character varying, 'DONE'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: work_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_orders (
    is_deleted boolean DEFAULT false NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    end_planned_at timestamp(6) with time zone,
    start_planned_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    approved_by_id uuid,
    contractor_id uuid,
    created_by_id uuid NOT NULL,
    defect_id uuid,
    department_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    equipment_node_id uuid,
    id uuid NOT NULL,
    ppr_task_id uuid,
    repair_request_id uuid,
    replacement_equipment_id uuid,
    warehouse_id uuid,
    closure_notes text,
    number character varying(255) NOT NULL,
    priority character varying(255) NOT NULL,
    result text,
    status character varying(255) NOT NULL,
    summary text,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    work_type character varying(255) NOT NULL,
    CONSTRAINT work_orders_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying, 'EMERGENCY'::character varying])::text[]))),
    CONSTRAINT work_orders_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PLANNED'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT work_orders_type_check CHECK (((type)::text = ANY ((ARRAY['PLANNED'::character varying, 'EMERGENCY'::character varying, 'DEFECT'::character varying, 'OVERHAUL'::character varying, 'INSPECTION'::character varying])::text[]))),
    CONSTRAINT work_orders_work_type_check CHECK (((work_type)::text = ANY ((ARRAY['REPAIR'::character varying, 'REPLACEMENT'::character varying, 'DIAGNOSTICS'::character varying])::text[])))
);


--
-- Name: actual_cost_review_route_overrides actual_cost_review_route_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actual_cost_review_route_overrides
    ADD CONSTRAINT actual_cost_review_route_overrides_pkey PRIMARY KEY (id);


--
-- Name: actual_costs actual_costs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actual_costs
    ADD CONSTRAINT actual_costs_pkey PRIMARY KEY (id);


--
-- Name: approval_requests approval_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_requests
    ADD CONSTRAINT approval_requests_pkey PRIMARY KEY (id);


--
-- Name: approval_steps approval_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_steps
    ADD CONSTRAINT approval_steps_pkey PRIMARY KEY (id);


--
-- Name: approval_steps approval_steps_request_id_step_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_steps
    ADD CONSTRAINT approval_steps_request_id_step_number_key UNIQUE (request_id, step_number);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: brigade_members brigade_members_brigade_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brigade_members
    ADD CONSTRAINT brigade_members_brigade_id_user_id_key UNIQUE (brigade_id, user_id);


--
-- Name: brigade_members brigade_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brigade_members
    ADD CONSTRAINT brigade_members_pkey PRIMARY KEY (id);


--
-- Name: brigades brigades_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brigades
    ADD CONSTRAINT brigades_code_key UNIQUE (code);


--
-- Name: brigades brigades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brigades
    ADD CONSTRAINT brigades_pkey PRIMARY KEY (id);


--
-- Name: budget_lines budget_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budget_lines
    ADD CONSTRAINT budget_lines_pkey PRIMARY KEY (id);


--
-- Name: calibration_records calibration_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calibration_records
    ADD CONSTRAINT calibration_records_pkey PRIMARY KEY (id);


--
-- Name: certification_types certification_types_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.certification_types
    ADD CONSTRAINT certification_types_code_key UNIQUE (code);


--
-- Name: certification_types certification_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.certification_types
    ADD CONSTRAINT certification_types_pkey PRIMARY KEY (id);


--
-- Name: completion_acts completion_acts_act_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.completion_acts
    ADD CONSTRAINT completion_acts_act_number_key UNIQUE (act_number);


--
-- Name: completion_acts completion_acts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.completion_acts
    ADD CONSTRAINT completion_acts_pkey PRIMARY KEY (id);


--
-- Name: completion_acts completion_acts_work_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.completion_acts
    ADD CONSTRAINT completion_acts_work_order_id_key UNIQUE (work_order_id);


--
-- Name: condition_readings condition_readings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.condition_readings
    ADD CONSTRAINT condition_readings_pkey PRIMARY KEY (id);


--
-- Name: contractor_contracts contractor_contracts_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contractor_contracts
    ADD CONSTRAINT contractor_contracts_number_key UNIQUE (number);


--
-- Name: contractor_contracts contractor_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contractor_contracts
    ADD CONSTRAINT contractor_contracts_pkey PRIMARY KEY (id);


--
-- Name: contractor_works contractor_works_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contractor_works
    ADD CONSTRAINT contractor_works_pkey PRIMARY KEY (id);


--
-- Name: contractors contractors_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contractors
    ADD CONSTRAINT contractors_code_key UNIQUE (code);


--
-- Name: contractors contractors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contractors
    ADD CONSTRAINT contractors_pkey PRIMARY KEY (id);


--
-- Name: cost_categories cost_categories_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cost_categories
    ADD CONSTRAINT cost_categories_code_key UNIQUE (code);


--
-- Name: cost_categories cost_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cost_categories
    ADD CONSTRAINT cost_categories_pkey PRIMARY KEY (id);


--
-- Name: criticality_classes criticality_classes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.criticality_classes
    ADD CONSTRAINT criticality_classes_code_key UNIQUE (code);


--
-- Name: criticality_classes criticality_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.criticality_classes
    ADD CONSTRAINT criticality_classes_pkey PRIMARY KEY (id);


--
-- Name: defect_categories defect_categories_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_categories
    ADD CONSTRAINT defect_categories_code_key UNIQUE (code);


--
-- Name: defect_categories defect_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_categories
    ADD CONSTRAINT defect_categories_pkey PRIMARY KEY (id);


--
-- Name: defect_list_lines defect_list_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_list_lines
    ADD CONSTRAINT defect_list_lines_pkey PRIMARY KEY (id);


--
-- Name: defect_lists defect_lists_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_lists
    ADD CONSTRAINT defect_lists_code_key UNIQUE (code);


--
-- Name: defect_lists defect_lists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_lists
    ADD CONSTRAINT defect_lists_pkey PRIMARY KEY (id);


--
-- Name: defect_severities defect_severities_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_severities
    ADD CONSTRAINT defect_severities_code_key UNIQUE (code);


--
-- Name: defect_severities defect_severities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_severities
    ADD CONSTRAINT defect_severities_pkey PRIMARY KEY (id);


--
-- Name: defects defects_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defects
    ADD CONSTRAINT defects_code_key UNIQUE (code);


--
-- Name: defects defects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defects
    ADD CONSTRAINT defects_pkey PRIMARY KEY (id);


--
-- Name: departments departments_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_code_key UNIQUE (code);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: downtime_events downtime_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.downtime_events
    ADD CONSTRAINT downtime_events_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_definitions equipment_attribute_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_definitions
    ADD CONSTRAINT equipment_attribute_definitions_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_option_items equipment_attribute_option_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_option_items
    ADD CONSTRAINT equipment_attribute_option_items_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_option_sources equipment_attribute_option_sources_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_option_sources
    ADD CONSTRAINT equipment_attribute_option_sources_code_key UNIQUE (code);


--
-- Name: equipment_attribute_option_sources equipment_attribute_option_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_option_sources
    ADD CONSTRAINT equipment_attribute_option_sources_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_required_criticality equipment_attribute_required_criticality_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_required_criticality
    ADD CONSTRAINT equipment_attribute_required_criticality_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_value_history equipment_attribute_value_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_value_history
    ADD CONSTRAINT equipment_attribute_value_history_pkey PRIMARY KEY (id);


--
-- Name: equipment_attribute_values equipment_attribute_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_attribute_values
    ADD CONSTRAINT equipment_attribute_values_pkey PRIMARY KEY (id);


--
-- Name: equipment_kpis equipment_kpis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_kpis
    ADD CONSTRAINT equipment_kpis_pkey PRIMARY KEY (id);


--
-- Name: equipment_meters equipment_meters_equipment_id_meter_type_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_meters
    ADD CONSTRAINT equipment_meters_equipment_id_meter_type_name_key UNIQUE (equipment_id, meter_type, name);


--
-- Name: equipment_meters equipment_meters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_meters
    ADD CONSTRAINT equipment_meters_pkey PRIMARY KEY (id);


--
-- Name: equipment_nodes equipment_nodes_equipment_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_nodes
    ADD CONSTRAINT equipment_nodes_equipment_id_code_key UNIQUE (equipment_id, code);


--
-- Name: equipment_nodes equipment_nodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_nodes
    ADD CONSTRAINT equipment_nodes_pkey PRIMARY KEY (id);


--
-- Name: equipment_passports equipment_passports_equipment_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_passports
    ADD CONSTRAINT equipment_passports_equipment_id_key UNIQUE (equipment_id);


--
-- Name: equipment_passports equipment_passports_passport_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_passports
    ADD CONSTRAINT equipment_passports_passport_number_key UNIQUE (passport_number);


--
-- Name: equipment_passports equipment_passports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_passports
    ADD CONSTRAINT equipment_passports_pkey PRIMARY KEY (id);


--
-- Name: equipment equipment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment
    ADD CONSTRAINT equipment_pkey PRIMARY KEY (id);


--
-- Name: equipment_spare_parts equipment_spare_parts_equipment_id_spare_part_id_position_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_spare_parts
    ADD CONSTRAINT equipment_spare_parts_equipment_id_spare_part_id_position_key UNIQUE (equipment_id, spare_part_id, "position");


--
-- Name: equipment_spare_parts equipment_spare_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_spare_parts
    ADD CONSTRAINT equipment_spare_parts_pkey PRIMARY KEY (id);


--
-- Name: equipment_status_history equipment_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_status_history
    ADD CONSTRAINT equipment_status_history_pkey PRIMARY KEY (id);


--
-- Name: equipment equipment_technical_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment
    ADD CONSTRAINT equipment_technical_number_key UNIQUE (technical_number);


--
-- Name: equipment_types equipment_types_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_types
    ADD CONSTRAINT equipment_types_code_key UNIQUE (code);


--
-- Name: equipment_types equipment_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipment_types
    ADD CONSTRAINT equipment_types_pkey PRIMARY KEY (id);


--
-- Name: escalation_events escalation_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escalation_events
    ADD CONSTRAINT escalation_events_pkey PRIMARY KEY (id);


--
-- Name: failure_reasons failure_reasons_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.failure_reasons
    ADD CONSTRAINT failure_reasons_code_key UNIQUE (code);


--
-- Name: failure_reasons failure_reasons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.failure_reasons
    ADD CONSTRAINT failure_reasons_pkey PRIMARY KEY (id);


--
-- Name: file_assets file_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_assets
    ADD CONSTRAINT file_assets_pkey PRIMARY KEY (id);


--
-- Name: financial_approval_rules financial_approval_rules_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_approval_rules
    ADD CONSTRAINT financial_approval_rules_code_key UNIQUE (code);


--
-- Name: financial_approval_rules financial_approval_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_approval_rules
    ADD CONSTRAINT financial_approval_rules_pkey PRIMARY KEY (id);


--
-- Name: hr_employees hr_employees_personnel_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_personnel_number_key UNIQUE (personnel_number);


--
-- Name: hr_employees hr_employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_pkey PRIMARY KEY (id);


--
-- Name: hr_timesheet_entries hr_timesheet_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hr_timesheet_entries
    ADD CONSTRAINT hr_timesheet_entries_pkey PRIMARY KEY (id);


--
-- Name: inspection_checkpoints inspection_checkpoints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_checkpoints
    ADD CONSTRAINT inspection_checkpoints_pkey PRIMARY KEY (id);


--
-- Name: inspection_round_results inspection_round_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_round_results
    ADD CONSTRAINT inspection_round_results_pkey PRIMARY KEY (id);


--
-- Name: inspection_rounds inspection_rounds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_rounds
    ADD CONSTRAINT inspection_rounds_pkey PRIMARY KEY (id);


--
-- Name: inspection_routes inspection_routes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_routes
    ADD CONSTRAINT inspection_routes_code_key UNIQUE (code);


--
-- Name: inspection_routes inspection_routes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_routes
    ADD CONSTRAINT inspection_routes_pkey PRIMARY KEY (id);


--
-- Name: integration_endpoints integration_endpoints_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_endpoints
    ADD CONSTRAINT integration_endpoints_code_key UNIQUE (code);


--
-- Name: integration_endpoints integration_endpoints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_endpoints
    ADD CONSTRAINT integration_endpoints_pkey PRIMARY KEY (id);


--
-- Name: integration_sync_logs integration_sync_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_sync_logs
    ADD CONSTRAINT integration_sync_logs_pkey PRIMARY KEY (id);


--
-- Name: knowledge_articles knowledge_articles_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_articles
    ADD CONSTRAINT knowledge_articles_code_key UNIQUE (code);


--
-- Name: knowledge_articles knowledge_articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_articles
    ADD CONSTRAINT knowledge_articles_pkey PRIMARY KEY (id);


--
-- Name: labor_entries labor_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.labor_entries
    ADD CONSTRAINT labor_entries_pkey PRIMARY KEY (id);


--
-- Name: locations locations_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_code_key UNIQUE (code);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (id);


--
-- Name: maintenance_budgets maintenance_budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_budgets
    ADD CONSTRAINT maintenance_budgets_pkey PRIMARY KEY (id);


--
-- Name: maintenance_kpis maintenance_kpis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_kpis
    ADD CONSTRAINT maintenance_kpis_pkey PRIMARY KEY (id);


--
-- Name: maintenance_operations maintenance_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_operations
    ADD CONSTRAINT maintenance_operations_pkey PRIMARY KEY (id);


--
-- Name: maintenance_operations maintenance_operations_template_id_sequence_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_operations
    ADD CONSTRAINT maintenance_operations_template_id_sequence_key UNIQUE (template_id, sequence);


--
-- Name: maintenance_regulation_attribute_conditions maintenance_regulation_attribute_conditions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_regulation_attribute_conditions
    ADD CONSTRAINT maintenance_regulation_attribute_conditions_pkey PRIMARY KEY (id);


--
-- Name: maintenance_regulations maintenance_regulations_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_regulations
    ADD CONSTRAINT maintenance_regulations_code_key UNIQUE (code);


--
-- Name: maintenance_regulations maintenance_regulations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_regulations
    ADD CONSTRAINT maintenance_regulations_pkey PRIMARY KEY (id);


--
-- Name: maintenance_templates maintenance_templates_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_templates
    ADD CONSTRAINT maintenance_templates_code_key UNIQUE (code);


--
-- Name: maintenance_templates maintenance_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_templates
    ADD CONSTRAINT maintenance_templates_pkey PRIMARY KEY (id);


--
-- Name: manufacturers manufacturers_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manufacturers
    ADD CONSTRAINT manufacturers_code_key UNIQUE (code);


--
-- Name: manufacturers manufacturers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manufacturers
    ADD CONSTRAINT manufacturers_pkey PRIMARY KEY (id);


--
-- Name: materials materials_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_code_key UNIQUE (code);


--
-- Name: materials materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_pkey PRIMARY KEY (id);


--
-- Name: meter_readings meter_readings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meter_readings
    ADD CONSTRAINT meter_readings_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: oee_records oee_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oee_records
    ADD CONSTRAINT oee_records_pkey PRIMARY KEY (id);


--
-- Name: planned_shutdowns planned_shutdowns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.planned_shutdowns
    ADD CONSTRAINT planned_shutdowns_pkey PRIMARY KEY (id);


--
-- Name: ppr_plans ppr_plans_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppr_plans
    ADD CONSTRAINT ppr_plans_code_key UNIQUE (code);


--
-- Name: ppr_plans ppr_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppr_plans
    ADD CONSTRAINT ppr_plans_pkey PRIMARY KEY (id);


--
-- Name: ppr_tasks ppr_tasks_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppr_tasks
    ADD CONSTRAINT ppr_tasks_code_key UNIQUE (code);


--
-- Name: ppr_tasks ppr_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppr_tasks
    ADD CONSTRAINT ppr_tasks_pkey PRIMARY KEY (id);


--
-- Name: procurement_request_lines procurement_request_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_request_lines
    ADD CONSTRAINT procurement_request_lines_pkey PRIMARY KEY (id);


--
-- Name: procurement_requests procurement_requests_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_requests
    ADD CONSTRAINT procurement_requests_number_key UNIQUE (number);


--
-- Name: procurement_requests procurement_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_requests
    ADD CONSTRAINT procurement_requests_pkey PRIMARY KEY (id);


--
-- Name: rcm_snapshots rcm_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rcm_snapshots
    ADD CONSTRAINT rcm_snapshots_pkey PRIMARY KEY (id);


--
-- Name: reliability_metrics reliability_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reliability_metrics
    ADD CONSTRAINT reliability_metrics_pkey PRIMARY KEY (id);


--
-- Name: repair_campaign_stages repair_campaign_stages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_campaign_stages
    ADD CONSTRAINT repair_campaign_stages_pkey PRIMARY KEY (id);


--
-- Name: repair_campaigns repair_campaigns_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_campaigns
    ADD CONSTRAINT repair_campaigns_code_key UNIQUE (code);


--
-- Name: repair_campaigns repair_campaigns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_campaigns
    ADD CONSTRAINT repair_campaigns_pkey PRIMARY KEY (id);


--
-- Name: repair_material_usages repair_material_usages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_material_usages
    ADD CONSTRAINT repair_material_usages_pkey PRIMARY KEY (id);


--
-- Name: repair_requests repair_requests_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_requests
    ADD CONSTRAINT repair_requests_number_key UNIQUE (number);


--
-- Name: repair_requests repair_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_requests
    ADD CONSTRAINT repair_requests_pkey PRIMARY KEY (id);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (id);


--
-- Name: roles roles_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_code_key UNIQUE (code);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: root_causes root_causes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.root_causes
    ADD CONSTRAINT root_causes_code_key UNIQUE (code);


--
-- Name: root_causes root_causes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.root_causes
    ADD CONSTRAINT root_causes_pkey PRIMARY KEY (id);


--
-- Name: safety_permits safety_permits_permit_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.safety_permits
    ADD CONSTRAINT safety_permits_permit_number_key UNIQUE (permit_number);


--
-- Name: safety_permits safety_permits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.safety_permits
    ADD CONSTRAINT safety_permits_pkey PRIMARY KEY (id);


--
-- Name: safety_permits safety_permits_work_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.safety_permits
    ADD CONSTRAINT safety_permits_work_order_id_key UNIQUE (work_order_id);


--
-- Name: service_classes service_classes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_classes
    ADD CONSTRAINT service_classes_code_key UNIQUE (code);


--
-- Name: service_classes service_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_classes
    ADD CONSTRAINT service_classes_pkey PRIMARY KEY (id);


--
-- Name: sla_rules sla_rules_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sla_rules
    ADD CONSTRAINT sla_rules_code_key UNIQUE (code);


--
-- Name: sla_rules sla_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sla_rules
    ADD CONSTRAINT sla_rules_pkey PRIMARY KEY (id);


--
-- Name: spare_parts spare_parts_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spare_parts
    ADD CONSTRAINT spare_parts_code_key UNIQUE (code);


--
-- Name: spare_parts spare_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spare_parts
    ADD CONSTRAINT spare_parts_pkey PRIMARY KEY (id);


--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);


--
-- Name: technical_documents technical_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.technical_documents
    ADD CONSTRAINT technical_documents_pkey PRIMARY KEY (id);


--
-- Name: units_of_measurement units_of_measurement_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.units_of_measurement
    ADD CONSTRAINT units_of_measurement_code_key UNIQUE (code);


--
-- Name: units_of_measurement units_of_measurement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.units_of_measurement
    ADD CONSTRAINT units_of_measurement_pkey PRIMARY KEY (id);


--
-- Name: user_certifications user_certifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_certifications
    ADD CONSTRAINT user_certifications_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (role_id, user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: vehicle_details vehicle_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_details
    ADD CONSTRAINT vehicle_details_pkey PRIMARY KEY (id);


--
-- Name: warehouse_equipment_items warehouse_equipment_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse_equipment_items
    ADD CONSTRAINT warehouse_equipment_items_pkey PRIMARY KEY (id);


--
-- Name: warehouse_stocks warehouse_stocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse_stocks
    ADD CONSTRAINT warehouse_stocks_pkey PRIMARY KEY (id);


--
-- Name: warehouse_stocks warehouse_stocks_warehouse_id_spare_part_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse_stocks
    ADD CONSTRAINT warehouse_stocks_warehouse_id_spare_part_id_key UNIQUE (warehouse_id, spare_part_id);


--
-- Name: warehouses warehouses_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_code_key UNIQUE (code);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (id);


--
-- Name: webhook_event_log webhook_event_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_event_log
    ADD CONSTRAINT webhook_event_log_pkey PRIMARY KEY (id);


--
-- Name: webhook_subscriptions webhook_subscriptions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_subscriptions
    ADD CONSTRAINT webhook_subscriptions_code_key UNIQUE (code);


--
-- Name: webhook_subscriptions webhook_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_subscriptions
    ADD CONSTRAINT webhook_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: work_executions work_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_executions
    ADD CONSTRAINT work_executions_pkey PRIMARY KEY (id);


--
-- Name: work_order_tasks work_order_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_order_tasks
    ADD CONSTRAINT work_order_tasks_pkey PRIMARY KEY (id);


--
-- Name: work_orders work_orders_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT work_orders_number_key UNIQUE (number);


--
-- Name: work_orders work_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT work_orders_pkey PRIMARY KEY (id);


--
-- Name: idx_approval_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_doc ON public.approval_requests USING btree (document_type, document_id);


--
-- Name: idx_meter_readings_equipment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meter_readings_equipment ON public.meter_readings USING btree (equipment_id, read_at);


--
-- Name: idx_meter_readings_meter; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meter_readings_meter ON public.meter_readings USING btree (meter_id, read_at);


--
-- Name: idx_oee_equipment_shift; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oee_equipment_shift ON public.oee_records USING btree (equipment_id, shift_start);


--
-- Name: idx_sync_log_endpoint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sync_log_endpoint ON public.integration_sync_logs USING btree (endpoint_id, started_at);


--
-- Name: idx_timesheet_employee_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_timesheet_employee_date ON public.hr_timesheet_entries USING btree (employee_id, work_date);


--
-- Name: idx_vehicle_details_equipment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicle_details_equipment ON public.vehicle_details USING btree (equipment_id);


--
-- Name: idx_vehicle_details_plate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicle_details_plate ON public.vehicle_details USING btree (plate_number);


--
-- Name: idx_vehicle_details_vin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicle_details_vin ON public.vehicle_details USING btree (vin);


--
-- Name: ix_calib_eq_next; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_calib_eq_next ON public.calibration_records USING btree (equipment_id, next_due_at);


--
-- Name: ix_cond_equipment_param_ts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_cond_equipment_param_ts ON public.condition_readings USING btree (equipment_id, parameter, recorded_at);


--
-- Name: ix_rcm_snap_eq_ts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rcm_snap_eq_ts ON public.rcm_snapshots USING btree (equipment_id, captured_at);


--
-- Name: ix_user_cert_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_cert_expires ON public.user_certifications USING btree (expires_at);


--
-- Name: ix_user_cert_user_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_cert_user_type ON public.user_certifications USING btree (user_id, type_code);


--
-- Name: ix_webhook_event_ts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_webhook_event_ts ON public.webhook_event_log USING btree (event_code, fired_at);


--
-- Name: budget_lines fk20nth7tg2ugc5ni7g4ta6uyxk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budget_lines
    ADD CONSTRAINT fk20nth7tg2ugc5ni7g4ta6uyxk FOREIGN KEY (budget_id) REFERENCES public.maintenance_budgets(id);


--
-- Name: users fk20xg15hxgl0kpqdd06k2gkyr3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk20xg15hxgl0kpqdd06k2gkyr3 FOREIGN KEY (primary_role_id) REFERENCES public.roles(id);


--
-- Name: inspection_rounds fk3awqv1wi5hikaohpe3f4f50tt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_rounds
    ADD CONSTRAINT fk3awqv1wi5hikaohpe3f4f50tt FOREIGN KEY (route_id) REFERENCES public.inspection_routes(id);


--
-- Name: maintenance_operations fkc86h9extarme57ahxyt6qltm2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_operations
    ADD CONSTRAINT fkc86h9extarme57ahxyt6qltm2 FOREIGN KEY (template_id) REFERENCES public.maintenance_templates(id);


--
-- Name: repair_campaign_stages fkdw9o7621y65t46lg2nh9865qs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repair_campaign_stages
    ADD CONSTRAINT fkdw9o7621y65t46lg2nh9865qs FOREIGN KEY (campaign_id) REFERENCES public.repair_campaigns(id);


--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: inspection_round_results fkk0bxrqx2nqm93xp7cu3jqgwg7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_round_results
    ADD CONSTRAINT fkk0bxrqx2nqm93xp7cu3jqgwg7 FOREIGN KEY (round_id) REFERENCES public.inspection_rounds(id);


--
-- Name: approval_steps fkk3dnioyiy8rmbddlndk92175c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_steps
    ADD CONSTRAINT fkk3dnioyiy8rmbddlndk92175c FOREIGN KEY (request_id) REFERENCES public.approval_requests(id);


--
-- Name: inspection_checkpoints fkkpe69pvbf850klj3wjje3ahk5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_checkpoints
    ADD CONSTRAINT fkkpe69pvbf850klj3wjje3ahk5 FOREIGN KEY (route_id) REFERENCES public.inspection_routes(id);


--
-- Name: defect_list_lines fkmbio0tm9cgv5o7s9ojp3btbdl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_list_lines
    ADD CONSTRAINT fkmbio0tm9cgv5o7s9ojp3btbdl FOREIGN KEY (defect_list_id) REFERENCES public.defect_lists(id);


--
-- Name: ppr_tasks fknsdj33savw3tde2l4supvrute; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppr_tasks
    ADD CONSTRAINT fknsdj33savw3tde2l4supvrute FOREIGN KEY (plan_id) REFERENCES public.ppr_plans(id);


--
-- Name: warehouse_stocks fkqrnh13ttkft8scos6domerx2c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse_stocks
    ADD CONSTRAINT fkqrnh13ttkft8scos6domerx2c FOREIGN KEY (spare_part_id) REFERENCES public.spare_parts(id);


--
-- Name: work_order_tasks fks0jqco19tbggiffba7q7xtgo7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_order_tasks
    ADD CONSTRAINT fks0jqco19tbggiffba7q7xtgo7 FOREIGN KEY (work_order_id) REFERENCES public.work_orders(id);


--
-- Name: users fksbg59w8q63i0oo53rlgvlcnjq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fksbg59w8q63i0oo53rlgvlcnjq FOREIGN KEY (department_id) REFERENCES public.departments(id);


--
-- Name: brigade_members fksdi5srp9geagnnhah9w3fwxyv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.brigade_members
    ADD CONSTRAINT fksdi5srp9geagnnhah9w3fwxyv FOREIGN KEY (brigade_id) REFERENCES public.brigades(id);


--
-- Name: procurement_request_lines fksuq8xeatl7topkpes6v0clnn6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_request_lines
    ADD CONSTRAINT fksuq8xeatl7topkpes6v0clnn6 FOREIGN KEY (request_id) REFERENCES public.procurement_requests(id);


--
-- PostgreSQL database dump complete
--


