package com.toir.repository.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentFleetLifecycleQueryRepositoryPostgresTest {

    private static final UUID SCOPE = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID FOREIGN_SCOPE = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID EQUIPMENT_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FOREIGN_EQUIPMENT = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID ACTIVE_METER = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID INACTIVE_METER = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID TIED_READING_LOW = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TIED_READING_HIGH = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID COMPLETED_REPAIR = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID CLOSED_REPAIR = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID VALID_DEFECT = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID CROSS_DEFECT = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID VALID_REQUEST = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CROSS_REQUEST = UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final Instant AS_OF = Instant.parse("2026-08-03T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private EquipmentFleetLifecycleQueryRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new EquipmentFleetLifecycleQueryRepository(new NamedParameterJdbcTemplate(dataSource));
        recreateSchema();
        insertFixtures();
    }

    @Test
    void selectsScopeVisibleEquipmentWithStableUuidKeysetAndAllNonDeletedMeters() {
        assertThat(repository.findEquipmentBatch(SCOPE, false, null, 1))
                .extracting(EquipmentFleetLifecycleQueryRepository.EquipmentRow::id)
                .containsExactly(EQUIPMENT_ONE);
        assertThat(repository.findEquipmentBatch(SCOPE, false, EQUIPMENT_ONE, 10))
                .extracting(EquipmentFleetLifecycleQueryRepository.EquipmentRow::id)
                .containsExactly(EQUIPMENT_TWO);
        assertThat(repository.findEquipmentBatch(null, true, null, 10)).isEmpty();
        assertThat(repository.findEquipmentBatch(null, false, null, 10))
                .extracting(EquipmentFleetLifecycleQueryRepository.EquipmentRow::id)
                .containsExactly(EQUIPMENT_ONE, EQUIPMENT_TWO, FOREIGN_EQUIPMENT);

        assertThat(repository.findMeters(List.of(EQUIPMENT_ONE, EQUIPMENT_TWO)))
                .extracting(
                        EquipmentFleetLifecycleQueryRepository.MeterRow::meterId,
                        EquipmentFleetLifecycleQueryRepository.MeterRow::active)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ACTIVE_METER, true),
                        org.assertj.core.groups.Tuple.tuple(INACTIVE_METER, false));
    }

    @Test
    void selectsLatestReadingByTimestampThenUuidAndExcludesFutureAndDeletedReadings() {
        assertThat(repository.findLatestReadings(List.of(EQUIPMENT_ONE), AS_OF))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.meterId()).isEqualTo(ACTIVE_METER);
                    assertThat(row.readingId()).isEqualTo(TIED_READING_HIGH);
                    assertThat(row.value()).isEqualTo(20.0);
                });
    }

    @Test
    void returnsOnlyCanonicalRepairsAndMarksCrossEquipmentLinksInvalid() {
        List<EquipmentFleetLifecycleQueryRepository.RepairRow> repairs =
                repository.findRepairs(List.of(EQUIPMENT_ONE), AS_OF);

        assertThat(repairs)
                .extracting(EquipmentFleetLifecycleQueryRepository.RepairRow::workOrderId)
                .containsExactly(COMPLETED_REPAIR, CLOSED_REPAIR);
        assertThat(repairs.get(0).defectLinkValid()).isTrue();
        assertThat(repairs.get(0).defectDescription()).isEqualTo("valid defect");
        assertThat(repairs.get(0).repairRequestLinkValid()).isFalse();
        assertThat(repairs.get(1).defectLinkValid()).isFalse();
        assertThat(repairs.get(1).repairRequestLinkValid()).isTrue();
        assertThat(repairs.get(1).repairRequestDescription()).isEqualTo("valid request");
    }

    @Test
    void repairSnapshotsUseLastReadingAtCompletionAndKeepMissingMeterRowsExplicit() {
        List<EquipmentFleetLifecycleQueryRepository.RepairMeterSnapshotRow> snapshots =
                repository.findRepairMeterSnapshots(List.of(EQUIPMENT_ONE), AS_OF);

        assertThat(snapshots).hasSize(4);
        assertThat(snapshots)
                .filteredOn(row -> row.workOrderId().equals(COMPLETED_REPAIR)
                        && row.meterId().equals(ACTIVE_METER))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.readingId()).isEqualTo(TIED_READING_HIGH);
                    assertThat(row.value()).isEqualTo(20.0);
                    assertThat(row.readAt()).isEqualTo(Instant.parse("2026-08-03T09:00:00Z"));
                });
        assertThat(snapshots)
                .filteredOn(row -> row.workOrderId().equals(COMPLETED_REPAIR)
                        && row.meterId().equals(INACTIVE_METER))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.readingId()).isNull();
                    assertThat(row.value()).isNull();
                    assertThat(row.readAt()).isNull();
                });
    }

    private void recreateSchema() {
        jdbc.execute("DROP TABLE IF EXISTS meter_readings, equipment_meters, work_orders, defects, repair_requests, equipment CASCADE");
        jdbc.execute("""
                CREATE TABLE equipment (
                    id uuid PRIMARY KEY, code text NOT NULL, name text NOT NULL,
                    inventory_number text NOT NULL, technical_number text, serial_number text, model text,
                    equipment_type_id uuid NOT NULL, department_id uuid, responsible_department_id uuid,
                    location_id uuid, criticality_class_id uuid, status text NOT NULL, category text NOT NULL,
                    manufacturer text, commissioned_at date, arrival_date date, operation_start_date date,
                    expected_lifetime_months integer, expected_lifetime_years integer,
                    expected_lifetime_hours bigint, lifetime_counter_type text, lifetime_meter_id uuid,
                    lifetime_limit_value double precision, is_deleted boolean NOT NULL DEFAULT false
                );
                CREATE TABLE equipment_meters (
                    id uuid PRIMARY KEY, equipment_id uuid NOT NULL, meter_type text NOT NULL, name text NOT NULL,
                    unit text NOT NULL, current_value double precision NOT NULL, last_read_at timestamptz,
                    rollover_value double precision, is_active boolean NOT NULL, is_primary boolean NOT NULL,
                    is_deleted boolean NOT NULL DEFAULT false
                );
                CREATE TABLE meter_readings (
                    id uuid PRIMARY KEY, meter_id uuid NOT NULL, equipment_id uuid NOT NULL,
                    value double precision NOT NULL, delta double precision, read_at timestamptz NOT NULL,
                    source text NOT NULL, reading_context text NOT NULL, repair_request_id uuid,
                    work_order_id uuid, defect_id uuid, is_deleted boolean NOT NULL DEFAULT false
                );
                CREATE TABLE work_orders (
                    id uuid PRIMARY KEY, number text NOT NULL, title text NOT NULL, equipment_id uuid NOT NULL,
                    equipment_node_id uuid, defect_id uuid, repair_request_id uuid, ppr_task_id uuid,
                    maintenance_due_event_id uuid, type text NOT NULL, work_type text NOT NULL, status text NOT NULL,
                    priority text NOT NULL, start_planned_at timestamptz, end_planned_at timestamptz,
                    started_at timestamptz, completed_at timestamptz, summary text, result text,
                    closure_notes text, is_deleted boolean NOT NULL DEFAULT false
                );
                CREATE TABLE defects (
                    id uuid PRIMARY KEY, equipment_id uuid NOT NULL, description text NOT NULL,
                    failure_reason text, root_cause text, is_deleted boolean NOT NULL DEFAULT false
                );
                CREATE TABLE repair_requests (
                    id uuid PRIMARY KEY, equipment_id uuid NOT NULL, description text NOT NULL,
                    is_deleted boolean NOT NULL DEFAULT false
                )
                """);
    }

    private void insertFixtures() {
        jdbc.update("""
                INSERT INTO equipment
                    (id, code, name, inventory_number, equipment_type_id, department_id,
                     responsible_department_id, status, category, is_deleted)
                VALUES (?, ?, ?, ?, ?::uuid, ?, ?, 'ACTIVE', 'PRODUCTION_EQUIPMENT', false)
                """, EQUIPMENT_ONE, "EQ-1", "Pump", "INV-1", UUID.randomUUID(), SCOPE, null);
        jdbc.update("""
                INSERT INTO equipment
                    (id, code, name, inventory_number, equipment_type_id, department_id,
                     responsible_department_id, status, category, is_deleted)
                VALUES (?, ?, ?, ?, ?::uuid, ?, ?, 'ACTIVE', 'PRODUCTION_EQUIPMENT', false)
                """, EQUIPMENT_TWO, "EQ-2", "Motor", "INV-2", UUID.randomUUID(), FOREIGN_SCOPE, SCOPE);
        jdbc.update("""
                INSERT INTO equipment
                    (id, code, name, inventory_number, equipment_type_id, department_id,
                     responsible_department_id, status, category, is_deleted)
                VALUES (?, ?, ?, ?, ?::uuid, ?, ?, 'ACTIVE', 'PRODUCTION_EQUIPMENT', false)
                """, FOREIGN_EQUIPMENT, "EQ-3", "Foreign", "INV-3", UUID.randomUUID(), FOREIGN_SCOPE, null);

        insertMeter(ACTIVE_METER, EQUIPMENT_ONE, "HOURS", "Hours", true, false);
        insertMeter(INACTIVE_METER, EQUIPMENT_ONE, "CYCLES", "Cycles", false, false);
        insertMeter(UUID.fromString("40000000-0000-0000-0000-000000000003"), EQUIPMENT_ONE,
                "HOURS", "Deleted", true, true);

        insertReading(TIED_READING_LOW, ACTIVE_METER, EQUIPMENT_ONE, 10, "2026-08-03T09:00:00Z", false);
        insertReading(TIED_READING_HIGH, ACTIVE_METER, EQUIPMENT_ONE, 20, "2026-08-03T09:00:00Z", false);
        insertReading(UUID.fromString("50000000-0000-0000-0000-000000000004"), ACTIVE_METER,
                EQUIPMENT_ONE, 40, "2026-08-03T13:00:00Z", false);
        insertReading(UUID.fromString("50000000-0000-0000-0000-000000000005"), ACTIVE_METER,
                EQUIPMENT_ONE, 50, "2026-08-03T11:00:00Z", true);

        jdbc.update("INSERT INTO defects (id, equipment_id, description, failure_reason, root_cause) VALUES (?, ?, ?, ?, ?)",
                VALID_DEFECT, EQUIPMENT_ONE, "valid defect", "wear", "age");
        jdbc.update("INSERT INTO defects (id, equipment_id, description) VALUES (?, ?, ?)",
                CROSS_DEFECT, FOREIGN_EQUIPMENT, "cross defect");
        jdbc.update("INSERT INTO repair_requests (id, equipment_id, description) VALUES (?, ?, ?)",
                VALID_REQUEST, EQUIPMENT_ONE, "valid request");
        jdbc.update("INSERT INTO repair_requests (id, equipment_id, description) VALUES (?, ?, ?)",
                CROSS_REQUEST, FOREIGN_EQUIPMENT, "cross request");

        insertWorkOrder(COMPLETED_REPAIR, EQUIPMENT_ONE, "COMPLETED", "REPAIR",
                "2026-08-03T10:00:00Z", false, VALID_DEFECT, CROSS_REQUEST);
        insertWorkOrder(CLOSED_REPAIR, EQUIPMENT_ONE, "CLOSED", "REPAIR",
                "2026-08-03T11:00:00Z", false, CROSS_DEFECT, VALID_REQUEST);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000003"), EQUIPMENT_ONE,
                "IN_PROGRESS", "REPAIR", "2026-08-03T09:30:00Z", false, null, null);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000004"), EQUIPMENT_ONE,
                "CANCELLED", "REPAIR", "2026-08-03T09:30:00Z", false, null, null);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000005"), EQUIPMENT_ONE,
                "COMPLETED", "DIAGNOSTICS", "2026-08-03T09:30:00Z", false, null, null);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000006"), EQUIPMENT_ONE,
                "COMPLETED", "REPAIR", "2026-08-03T09:30:00Z", true, null, null);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000007"), EQUIPMENT_ONE,
                "COMPLETED", "REPAIR", "2026-08-04T09:30:00Z", false, null, null);
        insertWorkOrder(UUID.fromString("60000000-0000-0000-0000-000000000008"), FOREIGN_EQUIPMENT,
                "COMPLETED", "REPAIR", "2026-08-03T09:30:00Z", false, null, null);
    }

    private void insertMeter(UUID id, UUID equipmentId, String type, String name, boolean active, boolean deleted) {
        jdbc.update("""
                INSERT INTO equipment_meters
                    (id, equipment_id, meter_type, name, unit, current_value, last_read_at,
                     rollover_value, is_active, is_primary, is_deleted)
                VALUES (?, ?, ?, ?, 'unit', 30, '2026-08-03T10:30:00Z', 999, ?, false, ?)
                """, id, equipmentId, type, name, active, deleted);
    }

    private void insertReading(
            UUID id, UUID meterId, UUID equipmentId, double value, String readAt, boolean deleted) {
        jdbc.update("""
                INSERT INTO meter_readings
                    (id, meter_id, equipment_id, value, delta, read_at, source, reading_context, is_deleted)
                VALUES (?, ?, ?, ?, 1, ?::timestamptz, 'MANUAL', 'MANUAL_UPDATE', ?)
                """, id, meterId, equipmentId, value, readAt, deleted);
    }

    private void insertWorkOrder(
            UUID id,
            UUID equipmentId,
            String status,
            String workType,
            String completedAt,
            boolean deleted,
            UUID defectId,
            UUID requestId) {
        jdbc.update("""
                INSERT INTO work_orders
                    (id, number, title, equipment_id, defect_id, repair_request_id, type, work_type,
                     status, priority, started_at, completed_at, summary, result, closure_notes, is_deleted)
                VALUES (?, ?, 'Repair', ?, ?, ?, 'CORRECTIVE', ?, ?, 'MEDIUM',
                        '2026-08-03T08:00:00Z', ?::timestamptz, 'summary', 'result', 'closed', ?)
                """, id, "WO-" + id, equipmentId, defectId, requestId, workType, status, completedAt, deleted);
    }
}
