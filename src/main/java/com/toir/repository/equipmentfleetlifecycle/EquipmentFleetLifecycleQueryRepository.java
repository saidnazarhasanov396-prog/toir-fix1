package com.toir.repository.equipmentfleetlifecycle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentFleetLifecycleQueryRepository {

    private static final String EQUIPMENT_SQL = """
            select
                e.id,
                e.code,
                e.name,
                e.inventory_number,
                e.technical_number,
                e.serial_number,
                e.model,
                e.equipment_type_id,
                e.department_id,
                e.responsible_department_id,
                e.location_id,
                e.criticality_class_id,
                e.status,
                e.category,
                e.manufacturer,
                e.commissioned_at,
                e.arrival_date,
                e.operation_start_date,
                e.expected_lifetime_months,
                e.expected_lifetime_years,
                e.expected_lifetime_hours,
                e.lifetime_counter_type,
                e.lifetime_meter_id,
                e.lifetime_limit_value
            from equipment e
            where e.is_deleted = false
              and :denyAll = false
              and (
                    cast(:scopeDepartmentId as uuid) is null
                    or coalesce(e.responsible_department_id, e.department_id) = :scopeDepartmentId
              )
              and (cast(:afterExclusive as uuid) is null or e.id > :afterExclusive)
            order by e.id asc
            limit :limit
            """;

    private static final String METERS_SQL = """
            select
                meter.equipment_id,
                meter.id as meter_id,
                meter.name,
                meter.meter_type,
                meter.unit,
                meter.is_active,
                meter.is_primary,
                meter.rollover_value,
                meter.current_value,
                meter.last_read_at
            from equipment_meters meter
            where meter.equipment_id in (:equipmentIds)
              and meter.is_deleted = false
            order by meter.equipment_id asc, meter.meter_type asc, meter.name asc, meter.id asc
            """;

    private static final String LATEST_READINGS_SQL = """
            with ranked_readings as (
                select
                    meter.equipment_id,
                    reading.meter_id,
                    reading.id as reading_id,
                    reading.value,
                    reading.delta,
                    reading.read_at,
                    reading.source,
                    reading.reading_context,
                    reading.repair_request_id,
                    reading.work_order_id,
                    reading.defect_id,
                    row_number() over (
                        partition by reading.meter_id
                        order by reading.read_at desc, reading.id desc
                    ) as reading_rank
                from equipment_meters meter
                join meter_readings reading
                  on reading.meter_id = meter.id
                 and reading.equipment_id = meter.equipment_id
                where meter.equipment_id in (:equipmentIds)
                  and meter.is_deleted = false
                  and reading.is_deleted = false
                  and reading.read_at <= :asOf
            )
            select
                equipment_id,
                meter_id,
                reading_id,
                value,
                delta,
                read_at,
                source,
                reading_context,
                repair_request_id,
                work_order_id,
                defect_id
            from ranked_readings
            where reading_rank = 1
            order by equipment_id asc, meter_id asc
            """;

    private static final String REPAIRS_SQL = """
            select
                w.equipment_id,
                w.id as work_order_id,
                w.number as work_order_number,
                w.title,
                w.type as repair_type,
                w.work_type,
                w.status,
                w.priority,
                w.equipment_node_id,
                w.defect_id,
                w.repair_request_id,
                w.ppr_task_id,
                w.maintenance_due_event_id,
                w.start_planned_at,
                w.end_planned_at,
                w.started_at,
                w.completed_at,
                floor(extract(epoch from (w.completed_at - w.started_at)) / 60)::bigint
                    as duration_minutes,
                w.summary,
                w.result,
                w.closure_notes,
                d.id is not null as defect_link_valid,
                d.description as defect_description,
                d.failure_reason,
                d.root_cause,
                rr.id is not null as repair_request_link_valid,
                rr.description as repair_request_description
            from work_orders w
            left join defects d
              on d.id = w.defect_id
             and d.equipment_id = w.equipment_id
             and d.is_deleted = false
            left join repair_requests rr
              on rr.id = w.repair_request_id
             and rr.equipment_id = w.equipment_id
             and rr.is_deleted = false
            where w.equipment_id in (:equipmentIds)
              and w.is_deleted = false
              and w.work_type = 'REPAIR'
              and w.status in ('COMPLETED', 'CLOSED')
              and w.completed_at is not null
              and w.completed_at <= :asOf
            order by w.equipment_id asc, w.completed_at asc, w.id asc
            """;

    private static final String REPAIR_METER_SNAPSHOTS_SQL = """
            with qualifying_repairs as (
                select
                    w.id as work_order_id,
                    w.equipment_id,
                    w.completed_at
                from work_orders w
                where w.equipment_id in (:equipmentIds)
                  and w.is_deleted = false
                  and w.work_type = 'REPAIR'
                  and w.status in ('COMPLETED', 'CLOSED')
                  and w.completed_at is not null
                  and w.completed_at <= :asOf
            ),
            batch_meters as (
                select
                    meter.id as meter_id,
                    meter.equipment_id,
                    meter.meter_type,
                    meter.unit
                from equipment_meters meter
                where meter.equipment_id in (:equipmentIds)
                  and meter.is_deleted = false
            )
            select
                repair.equipment_id,
                repair.work_order_id,
                meter.meter_id,
                meter.meter_type,
                meter.unit,
                reading.reading_id,
                reading.value,
                reading.read_at
            from qualifying_repairs repair
            join batch_meters meter on meter.equipment_id = repair.equipment_id
            left join lateral (
                select
                    reading.id as reading_id,
                    reading.value,
                    reading.read_at
                from meter_readings reading
                where reading.meter_id = meter.meter_id
                  and reading.equipment_id = repair.equipment_id
                  and reading.is_deleted = false
                  and reading.read_at <= repair.completed_at
                order by reading.read_at desc, reading.id desc
                limit 1
            ) reading on true
            order by repair.equipment_id asc, repair.completed_at asc, repair.work_order_id asc,
                     meter.meter_type asc, meter.meter_id asc
            """;

    private static final RowMapper<EquipmentRow> EQUIPMENT_ROW_MAPPER = (rs, rowNum) ->
            new EquipmentRow(
                    uuid(rs, "id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("inventory_number"),
                    rs.getString("technical_number"),
                    rs.getString("serial_number"),
                    rs.getString("model"),
                    uuid(rs, "equipment_type_id"),
                    uuid(rs, "department_id"),
                    uuid(rs, "responsible_department_id"),
                    uuid(rs, "location_id"),
                    uuid(rs, "criticality_class_id"),
                    rs.getString("status"),
                    rs.getString("category"),
                    rs.getString("manufacturer"),
                    localDate(rs, "commissioned_at"),
                    localDate(rs, "arrival_date"),
                    localDate(rs, "operation_start_date"),
                    integer(rs, "expected_lifetime_months"),
                    integer(rs, "expected_lifetime_years"),
                    longValue(rs, "expected_lifetime_hours"),
                    rs.getString("lifetime_counter_type"),
                    uuid(rs, "lifetime_meter_id"),
                    doubleValue(rs, "lifetime_limit_value"));

    private static final RowMapper<MeterRow> METER_ROW_MAPPER = (rs, rowNum) ->
            new MeterRow(
                    uuid(rs, "equipment_id"),
                    uuid(rs, "meter_id"),
                    rs.getString("name"),
                    rs.getString("meter_type"),
                    rs.getString("unit"),
                    rs.getBoolean("is_active"),
                    rs.getBoolean("is_primary"),
                    doubleValue(rs, "rollover_value"),
                    doubleValue(rs, "current_value"),
                    instant(rs, "last_read_at"));

    private static final RowMapper<LatestReadingRow> LATEST_READING_ROW_MAPPER = (rs, rowNum) ->
            new LatestReadingRow(
                    uuid(rs, "equipment_id"),
                    uuid(rs, "meter_id"),
                    uuid(rs, "reading_id"),
                    doubleValue(rs, "value"),
                    doubleValue(rs, "delta"),
                    instant(rs, "read_at"),
                    rs.getString("source"),
                    rs.getString("reading_context"),
                    uuid(rs, "repair_request_id"),
                    uuid(rs, "work_order_id"),
                    uuid(rs, "defect_id"));

    private static final RowMapper<RepairRow> REPAIR_ROW_MAPPER = (rs, rowNum) ->
            new RepairRow(
                    uuid(rs, "equipment_id"),
                    uuid(rs, "work_order_id"),
                    rs.getString("work_order_number"),
                    rs.getString("title"),
                    rs.getString("repair_type"),
                    rs.getString("work_type"),
                    rs.getString("status"),
                    rs.getString("priority"),
                    uuid(rs, "equipment_node_id"),
                    uuid(rs, "defect_id"),
                    uuid(rs, "repair_request_id"),
                    uuid(rs, "ppr_task_id"),
                    uuid(rs, "maintenance_due_event_id"),
                    instant(rs, "start_planned_at"),
                    instant(rs, "end_planned_at"),
                    instant(rs, "started_at"),
                    instant(rs, "completed_at"),
                    longValue(rs, "duration_minutes"),
                    rs.getString("summary"),
                    rs.getString("result"),
                    rs.getString("closure_notes"),
                    rs.getBoolean("defect_link_valid"),
                    rs.getString("defect_description"),
                    rs.getString("failure_reason"),
                    rs.getString("root_cause"),
                    rs.getBoolean("repair_request_link_valid"),
                    rs.getString("repair_request_description"));

    private static final RowMapper<RepairMeterSnapshotRow> REPAIR_METER_SNAPSHOT_ROW_MAPPER =
            (rs, rowNum) -> new RepairMeterSnapshotRow(
                    uuid(rs, "equipment_id"),
                    uuid(rs, "work_order_id"),
                    uuid(rs, "meter_id"),
                    rs.getString("meter_type"),
                    rs.getString("unit"),
                    uuid(rs, "reading_id"),
                    doubleValue(rs, "value"),
                    instant(rs, "read_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public EquipmentFleetLifecycleQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EquipmentRow> findEquipmentBatch(
            UUID scopeDepartmentId, boolean denyAll, UUID afterExclusive, int limit) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("scopeDepartmentId", scopeDepartmentId)
                .addValue("denyAll", denyAll)
                .addValue("afterExclusive", afterExclusive)
                .addValue("limit", limit);
        return jdbc.query(EQUIPMENT_SQL, parameters, EQUIPMENT_ROW_MAPPER);
    }

    public List<MeterRow> findMeters(List<UUID> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(METERS_SQL, batchParameters(equipmentIds), METER_ROW_MAPPER);
    }

    public List<LatestReadingRow> findLatestReadings(List<UUID> equipmentIds, Instant asOf) {
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                LATEST_READINGS_SQL,
                batchParameters(equipmentIds).addValue("asOf", asOf),
                LATEST_READING_ROW_MAPPER);
    }

    public List<RepairRow> findRepairs(List<UUID> equipmentIds, Instant asOf) {
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                REPAIRS_SQL,
                batchParameters(equipmentIds).addValue("asOf", asOf),
                REPAIR_ROW_MAPPER);
    }

    public List<RepairMeterSnapshotRow> findRepairMeterSnapshots(
            List<UUID> equipmentIds, Instant asOf) {
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                REPAIR_METER_SNAPSHOTS_SQL,
                batchParameters(equipmentIds).addValue("asOf", asOf),
                REPAIR_METER_SNAPSHOT_ROW_MAPPER);
    }

    private static MapSqlParameterSource batchParameters(List<UUID> equipmentIds) {
        return new MapSqlParameterSource("equipmentIds", List.copyOf(equipmentIds));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Long longValue(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Double doubleValue(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.doubleValue();
    }

    public record EquipmentRow(
            UUID id,
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            String serialNumber,
            String model,
            UUID equipmentTypeId,
            UUID departmentId,
            UUID responsibleDepartmentId,
            UUID locationId,
            UUID criticalityClassId,
            String status,
            String category,
            String manufacturer,
            LocalDate commissionedAt,
            LocalDate arrivalDate,
            LocalDate operationStartDate,
            Integer expectedLifetimeMonths,
            Integer expectedLifetimeYears,
            Long expectedLifetimeHours,
            String lifetimeCounterType,
            UUID lifetimeMeterId,
            Double lifetimeLimitValue) {
    }

    public record MeterRow(
            UUID equipmentId,
            UUID meterId,
            String name,
            String meterType,
            String unit,
            boolean active,
            boolean primary,
            Double rolloverValue,
            Double cachedCurrentValue,
            Instant cachedLastReadAt) {
    }

    public record LatestReadingRow(
            UUID equipmentId,
            UUID meterId,
            UUID readingId,
            Double value,
            Double delta,
            Instant readAt,
            String source,
            String readingContext,
            UUID repairRequestId,
            UUID workOrderId,
            UUID defectId) {
    }

    public record RepairRow(
            UUID equipmentId,
            UUID workOrderId,
            String workOrderNumber,
            String title,
            String repairType,
            String workType,
            String status,
            String priority,
            UUID equipmentNodeId,
            UUID defectId,
            UUID repairRequestId,
            UUID pprTaskId,
            UUID maintenanceDueEventId,
            Instant startPlannedAt,
            Instant endPlannedAt,
            Instant startedAt,
            Instant completedAt,
            Long durationMinutes,
            String summary,
            String result,
            String closureNotes,
            boolean defectLinkValid,
            String defectDescription,
            String failureReason,
            String rootCause,
            boolean repairRequestLinkValid,
            String repairRequestDescription) {
    }

    public record RepairMeterSnapshotRow(
            UUID equipmentId,
            UUID workOrderId,
            UUID meterId,
            String meterType,
            String unit,
            UUID readingId,
            Double value,
            Instant readAt) {
    }
}
