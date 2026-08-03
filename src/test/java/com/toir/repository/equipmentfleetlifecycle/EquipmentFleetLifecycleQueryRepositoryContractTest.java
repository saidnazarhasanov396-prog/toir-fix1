package com.toir.repository.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class EquipmentFleetLifecycleQueryRepositoryContractTest {

    private static final UUID SCOPE_DEPARTMENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID AFTER_EXCLUSIVE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant AS_OF = Instant.parse("2026-08-03T12:00:00Z");

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private EquipmentFleetLifecycleQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EquipmentFleetLifecycleQueryRepository(jdbc);
    }

    @Test
    void equipmentBatchBindsScopeDenyAllKeysetAndLimit() {
        repository.findEquipmentBatch(SCOPE_DEPARTMENT_ID, true, AFTER_EXCLUSIVE, 25);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains(
                "e.is_deleted = false",
                ":denyAll = false",
                "coalesce(e.responsible_department_id, e.department_id) = :scopeDepartmentId",
                "e.id > :afterExclusive",
                "order by e.id asc",
                "limit :limit");
        assertThat(query.parameters().getValue("scopeDepartmentId")).isEqualTo(SCOPE_DEPARTMENT_ID);
        assertThat(query.parameters().getValue("denyAll")).isEqualTo(true);
        assertThat(query.parameters().getValue("afterExclusive")).isEqualTo(AFTER_EXCLUSIVE);
        assertThat(query.parameters().getValue("limit")).isEqualTo(25);
        assertThat(query.sql()).doesNotContain(
                SCOPE_DEPARTMENT_ID.toString(), AFTER_EXCLUSIVE.toString());
    }

    @Test
    void meterAndLatestReadingQueriesBindBatchAndPreserveSoftDeleteAndTieBreakRules() {
        List<UUID> equipmentIds = List.of(EQUIPMENT_ID);

        repository.findMeters(equipmentIds);
        CapturedQuery meterQuery = captureQuery();
        assertThat(meterQuery.sql()).contains(
                "meter.equipment_id in (:equipmentIds)",
                "meter.is_deleted = false",
                "order by meter.equipment_id asc, meter.meter_type asc, meter.name asc, meter.id asc");
        assertThat(meterQuery.parameters().getValue("equipmentIds")).isEqualTo(equipmentIds);
        assertThat(meterQuery.sql()).doesNotContain(EQUIPMENT_ID.toString());

        org.mockito.Mockito.clearInvocations(jdbc);
        repository.findLatestReadings(equipmentIds, AS_OF);
        CapturedQuery readingQuery = captureQuery();
        assertThat(readingQuery.sql()).contains(
                "meter.equipment_id in (:equipmentIds)",
                "meter.is_deleted = false",
                "reading.is_deleted = false",
                "reading.read_at <= :asOf",
                "row_number() over",
                "partition by reading.meter_id",
                "order by reading.read_at desc, reading.id desc");
        assertThat(readingQuery.parameters().getValue("equipmentIds")).isEqualTo(equipmentIds);
        assertThat(readingQuery.parameters().getValue("asOf")).isEqualTo(AS_OF);
        assertThat(readingQuery.sql()).doesNotContain(EQUIPMENT_ID.toString(), AS_OF.toString());
    }

    @Test
    void repairQueryBindsBatchAndAsOfAndProtectsLinkedReasons() {
        List<UUID> equipmentIds = List.of(EQUIPMENT_ID);

        repository.findRepairs(equipmentIds, AS_OF);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains(
                "w.equipment_id in (:equipmentIds)",
                "w.is_deleted = false",
                "w.work_type = 'REPAIR'",
                "w.status in ('COMPLETED', 'CLOSED')",
                "w.completed_at <= :asOf",
                "d.id = w.defect_id",
                "d.equipment_id = w.equipment_id",
                "d.is_deleted = false",
                "rr.id = w.repair_request_id",
                "rr.equipment_id = w.equipment_id",
                "rr.is_deleted = false",
                "d.id is not null as defect_link_valid",
                "rr.id is not null as repair_request_link_valid");
        assertThat(query.parameters().getValue("equipmentIds")).isEqualTo(equipmentIds);
        assertThat(query.parameters().getValue("asOf")).isEqualTo(AS_OF);
        assertThat(query.sql()).doesNotContain(EQUIPMENT_ID.toString(), AS_OF.toString());
    }

    @Test
    void snapshotQueryBindsBatchAndAsOfAndUsesBoundedLateralLookup() {
        List<UUID> equipmentIds = List.of(EQUIPMENT_ID);

        repository.findRepairMeterSnapshots(equipmentIds, AS_OF);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains(
                "w.equipment_id in (:equipmentIds)",
                "w.is_deleted = false",
                "w.work_type = 'REPAIR'",
                "w.status in ('COMPLETED', 'CLOSED')",
                "w.completed_at <= :asOf",
                "meter.is_deleted = false",
                "left join lateral",
                "reading.is_deleted = false",
                "reading.read_at <= repair.completed_at",
                "order by reading.read_at desc, reading.id desc",
                "limit 1");
        assertThat(query.parameters().getValue("equipmentIds")).isEqualTo(equipmentIds);
        assertThat(query.parameters().getValue("asOf")).isEqualTo(AS_OF);
        assertThat(query.sql()).doesNotContain(EQUIPMENT_ID.toString(), AS_OF.toString());
    }

    @Test
    void emptyEquipmentBatchesReturnWithoutCallingJdbc() {
        assertThat(repository.findMeters(List.of())).isEmpty();
        assertThat(repository.findLatestReadings(List.of(), AS_OF)).isEmpty();
        assertThat(repository.findRepairs(List.of(), AS_OF)).isEmpty();
        assertThat(repository.findRepairMeterSnapshots(List.of(), AS_OF)).isEmpty();

        verify(jdbc, never()).query(
                any(String.class), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CapturedQuery captureQuery() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        return new CapturedQuery(sql.getValue(), (MapSqlParameterSource) parameters.getValue());
    }

    private record CapturedQuery(String sql, MapSqlParameterSource parameters) {
    }
}
