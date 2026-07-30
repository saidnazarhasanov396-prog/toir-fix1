package com.toir.repository.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarOccurrenceSourceType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PprEquipmentCalendarJdbcRepository
        implements PprEquipmentCalendarQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PprEquipmentCalendarJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EquipmentIdPage findEquipmentPage(CalendarQuery query) {
        MapSqlParameterSource params = parameters(query);
        params.addValue("limit", query.filter().size());
        params.addValue("offset", Math.multiplyExact((long) query.filter().page(), query.filter().size()));
        String candidates = candidateCte(query);
        String equipmentPredicate = equipmentPredicate(query, "e");
        String orderBy = equipmentOrderBy(query.filter());
        String from = """
                FROM candidate_ids candidate
                JOIN equipment e ON e.id = candidate.equipment_id
                WHERE 1 = 1
                """ + equipmentPredicate;
        Long total = jdbc.queryForObject(
                candidates + "\nSELECT count(*) " + from,
                params,
                Long.class);
        String pageSql = candidates + "\nSELECT e.id\n" + from
                + "\nORDER BY " + orderBy
                + "\nLIMIT :limit OFFSET :offset\n";
        List<UUID> ids = jdbc.query(
                pageSql,
                params,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        return new EquipmentIdPage(ids, total == null ? 0 : total);
    }

    @Override
    public List<CalendarEquipment> findEquipmentMetadata(List<UUID> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT e.id,
                       e.code,
                       e.name,
                       e.inventory_number,
                       e.technical_number,
                       e.status,
                       e.is_deleted,
                       criticality.code AS criticality_code,
                       physical_department.id AS physical_department_id,
                       physical_department.code AS physical_department_code,
                       physical_department.name AS physical_department_name,
                       responsible_department.id AS responsible_department_id,
                       responsible_department.code AS responsible_department_code,
                       responsible_department.name AS responsible_department_name,
                       parent.id AS parent_id,
                       parent.code AS parent_code,
                       parent.name AS parent_name,
                       location.id AS location_id,
                       location.code AS location_code,
                       location.name AS location_name,
                       equipment_type.id AS equipment_type_id,
                       equipment_type.code AS equipment_type_code,
                       equipment_type.name AS equipment_type_name
                FROM equipment e
                LEFT JOIN criticality_classes criticality ON criticality.id = e.criticality_class_id
                LEFT JOIN departments physical_department ON physical_department.id = e.department_id
                LEFT JOIN departments responsible_department ON responsible_department.id = e.responsible_department_id
                LEFT JOIN equipment parent ON parent.id = e.parent_id
                LEFT JOIN locations location ON location.id = e.location_id
                LEFT JOIN equipment_types equipment_type ON equipment_type.id = e.equipment_type_id
                WHERE e.id IN (:equipmentIds)
                """,
                new MapSqlParameterSource("equipmentIds", equipmentIds),
                this::mapEquipment);
    }

    @Override
    public List<CalendarOccurrence> findOccurrences(
            CalendarQuery query,
            List<UUID> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = parameters(query)
                .addValue("equipmentIds", equipmentIds);
        String sql = query.authoritativeSource()
                == PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT
                ? snapshotOccurrenceSql(query)
                : taskOccurrenceSql(query);
        return jdbc.query(sql, params, this::mapOccurrence);
    }

    @Override
    public CalendarDiagnostics findDiagnostics(CalendarQuery query) {
        MapSqlParameterSource params = parameters(query);
        String base = occurrenceBase(query, false);
        String accessibleResolvedPredicate = equipmentPredicate(query, "e");
        boolean explicitEquipmentFilter = hasExplicitEquipmentFilter(query.filter());
        String matchingRow = explicitEquipmentFilter
                ? "e.id IS NOT NULL " + accessibleResolvedPredicate
                : "(e.id IS NULL OR (1 = 1 " + accessibleResolvedPredicate + "))";
        return jdbc.queryForObject("""
                WITH occurrence_base AS (
                """ + base + """
                )
                SELECT count(*) FILTER (
                           WHERE occurrence_base.equipment_id IS NULL
                             AND (%1$s)
                       ) AS missing_equipment,
                       count(*) FILTER (
                           WHERE occurrence_base.equipment_id IS NOT NULL
                             AND e.id IS NULL
                             AND (%1$s)
                       ) AS unresolved_equipment,
                       count(*) FILTER (
                           WHERE e.id IS NOT NULL
                             AND (%1$s)
                             AND (
                                 occurrence_base.effective_date IS NULL
                                 OR occurrence_base.effective_date < :yearStart
                                 OR occurrence_base.effective_date > :yearEnd
                             )
                       ) AS outside_plan_year,
                       count(*) FILTER (
                           WHERE e.id IS NOT NULL
                             AND (%1$s)
                             AND (
                                 occurrence_base.effective_date IS NULL
                                 OR occurrence_base.effective_date < :planStart
                                 OR occurrence_base.effective_date > :planEnd
                             )
                       ) AS outside_plan_range
                FROM occurrence_base
                LEFT JOIN equipment e ON e.id = occurrence_base.equipment_id
                """.formatted(matchingRow),
                params,
                (resultSet, rowNumber) -> new CalendarDiagnostics(
                        resultSet.getLong("missing_equipment"),
                        resultSet.getLong("unresolved_equipment"),
                        resultSet.getLong("outside_plan_year"),
                        resultSet.getLong("outside_plan_range")));
    }

    @Override
    public ApprovalStatus findLatestApprovalStatus(UUID planId) {
        List<ApprovalStatus> statuses = jdbc.query("""
                SELECT status
                FROM approval_requests
                WHERE is_deleted = false
                  AND coalesce(target_type, document_type) = 'PPR_PLAN'
                  AND coalesce(target_id, document_id) = :planId
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("planId", planId),
                (resultSet, rowNumber) -> enumValue(
                        ApprovalStatus.class, resultSet.getString("status")));
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private String candidateCte(CalendarQuery query) {
        String work = occurrenceBase(query, true);
        String targets = query.filter().onlyWithWork()
                ? ""
                : """
                        UNION
                        SELECT target.equipment_id
                        FROM ppr_plan_targets target
                        WHERE target.plan_id = :planId
                          AND target.is_deleted = false
                          AND target.target_type = 'EQUIPMENT'
                          AND target.equipment_id IS NOT NULL
                        UNION
                        SELECT scoped_equipment.id
                        FROM ppr_plan_targets target
                        JOIN equipment scoped_equipment
                          ON scoped_equipment.equipment_type_id =
                             target.equipment_type_id
                        WHERE target.plan_id = :planId
                          AND target.is_deleted = false
                          AND target.target_type = 'EQUIPMENT_TYPE'
                          AND target.equipment_type_id IS NOT NULL
                          AND scoped_equipment.is_deleted = false
                        """;
        return """
                WITH work_equipment AS (
                SELECT DISTINCT occurrence_base.equipment_id
                FROM (
                """ + work + """
                ) occurrence_base
                WHERE occurrence_base.equipment_id IS NOT NULL
                ),
                candidate_ids AS (
                SELECT equipment_id FROM work_equipment
                """ + targets + """
                )
                """;
    }

    private String occurrenceBase(CalendarQuery query, boolean restrictPlacement) {
        if (query.authoritativeSource()
                == PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT) {
            return snapshotBase(query, restrictPlacement);
        }
        return taskBase(query, restrictPlacement);
    }

    private String taskBase(CalendarQuery query, boolean restrictPlacement) {
        String placement = restrictPlacement
                ? """
                          AND coalesce(linked.planned_date, cast(task.scheduled_start AS date))
                              BETWEEN :windowStart AND :windowEnd
                          AND coalesce(linked.planned_date, cast(task.scheduled_start AS date))
                              BETWEEN :planStart AND :planEnd
                        """
                : "";
        String exactRevision = query.sourceRevision() == null
                ? ""
                : """
                  AND linked.plan_id = :planId
                  AND linked.calculation_revision = :sourceRevision
                """;
        return """
                SELECT task.equipment_id,
                       coalesce(linked.planned_date, cast(task.scheduled_start AS date)) AS effective_date
                FROM ppr_tasks task
                LEFT JOIN maintenance_schedule_calculation_items linked
                       ON linked.id = task.source_calculation_item_id
                      AND linked.is_deleted = false
                LEFT JOIN equipment_maintenance_rules maintenance_rule
                       ON maintenance_rule.id =
                          coalesce(task.equipment_maintenance_rule_id, linked.maintenance_rule_id)
                LEFT JOIN maintenance_regulations regulation
                       ON regulation.id = coalesce(task.regulation_id, linked.regulation_id)
                WHERE task.plan_id = :planId
                  AND task.is_deleted = false
                """ + exactRevision + taskOccurrencePredicate(query) + placement;
    }

    private String snapshotBase(CalendarQuery query, boolean restrictPlacement) {
        String placement = restrictPlacement
                ? """
                          AND item.planned_date BETWEEN :windowStart AND :windowEnd
                          AND item.planned_date BETWEEN :planStart AND :planEnd
                        """
                : "";
        return """
                SELECT item.equipment_id,
                       item.planned_date AS effective_date
                FROM maintenance_schedule_calculation_items item
                WHERE item.plan_id = :planId
                  AND item.calculation_revision = :sourceRevision
                  AND item.is_deleted = false
                """ + snapshotOccurrencePredicate(query.filter()) + placement;
    }

    private String taskOccurrenceSql(CalendarQuery query) {
        boolean exactSnapshotLineage = query.sourceRevision() != null;
        String sourceType = exactSnapshotLineage
                ? "CASE WHEN linked.maintenance_rule_id IS NOT NULL "
                    + "THEN 'MAINTENANCE_RULE' ELSE 'REGULATION' END"
                : "CASE WHEN maintenance_rule.id IS NOT NULL "
                    + "THEN 'MAINTENANCE_RULE' ELSE 'REGULATION' END";
        String sourceCode = exactSnapshotLineage
                ? "linked.source_code_snapshot"
                : "coalesce(maintenance_rule.code, regulation.code)";
        String sourceName = exactSnapshotLineage
                ? "linked.source_name_snapshot"
                : """
                  coalesce(
                      maintenance_rule.name,
                      regulation.name,
                      linked.maintenance_rule_name_snapshot,
                      linked.regulation_name_snapshot
                  )
                  """;
        String maintenanceKind = exactSnapshotLineage
                ? "linked.maintenance_type"
                : """
                  coalesce(
                      maintenance_rule.maintenance_kind,
                      regulation.maintenance_kind,
                      linked.maintenance_type
                  )
                  """;
        String exactRevision = exactSnapshotLineage
                ? """
                  AND linked.plan_id = :planId
                  AND linked.calculation_revision = :sourceRevision
                """
                : "";
        return """
                SELECT task.equipment_id,
                       %1$s AS source_type,
                       task.id AS task_id,
                       linked.id AS calculation_item_id,
                       coalesce(task.regulation_id, linked.regulation_id) AS regulation_id,
                       coalesce(task.equipment_maintenance_rule_id, linked.maintenance_rule_id)
                           AS maintenance_rule_id,
                       %2$s AS source_code,
                       %3$s AS source_name,
                       %4$s AS maintenance_kind,
                       linked.planned_date AS planned_date,
                       task.scheduled_start,
                       task.scheduled_end,
                       task.due_date,
                       task.status,
                       task.priority,
                       cast(task.planned_labor_hours AS numeric) AS planned_labor_hours,
                       task.title
                FROM ppr_tasks task
                LEFT JOIN maintenance_schedule_calculation_items linked
                       ON linked.id = task.source_calculation_item_id
                      AND linked.is_deleted = false
                LEFT JOIN equipment_maintenance_rules maintenance_rule
                       ON maintenance_rule.id =
                          coalesce(task.equipment_maintenance_rule_id, linked.maintenance_rule_id)
                LEFT JOIN maintenance_regulations regulation
                       ON regulation.id = coalesce(task.regulation_id, linked.regulation_id)
                WHERE task.plan_id = :planId
                  AND task.is_deleted = false
                  AND task.equipment_id IN (:equipmentIds)
                  AND coalesce(linked.planned_date, cast(task.scheduled_start AS date))
                      BETWEEN :windowStart AND :windowEnd
                  AND coalesce(linked.planned_date, cast(task.scheduled_start AS date))
                      BETWEEN :planStart AND :planEnd
                %5$s
                """.formatted(
                sourceType,
                sourceCode,
                sourceName,
                maintenanceKind,
                exactRevision) + taskOccurrencePredicate(query);
    }

    private String snapshotOccurrenceSql(CalendarQuery query) {
        return """
                SELECT item.equipment_id,
                       CASE WHEN item.maintenance_rule_id IS NOT NULL
                            THEN 'MAINTENANCE_RULE' ELSE 'REGULATION' END AS source_type,
                       NULL::uuid AS task_id,
                       item.id AS calculation_item_id,
                       item.regulation_id,
                       item.maintenance_rule_id,
                       item.source_code_snapshot AS source_code,
                       item.source_name_snapshot AS source_name,
                       item.maintenance_type AS maintenance_kind,
                       item.planned_date,
                       item.scheduled_start,
                       item.scheduled_end,
                       item.due_date,
                       NULL::varchar AS status,
                       item.priority,
                       item.normative_labor_hours AS planned_labor_hours,
                       item.task_title_snapshot AS title
                FROM maintenance_schedule_calculation_items item
                WHERE item.plan_id = :planId
                  AND item.calculation_revision = :sourceRevision
                  AND item.is_deleted = false
                  AND item.equipment_id IN (:equipmentIds)
                  AND item.planned_date BETWEEN :windowStart AND :windowEnd
                  AND item.planned_date BETWEEN :planStart AND :planEnd
                """ + snapshotOccurrencePredicate(query.filter());
    }

    private String taskOccurrencePredicate(CalendarQuery query) {
        PprEquipmentCalendarFilter filter = query.filter();
        StringBuilder predicate = new StringBuilder();
        if (!filter.includeCancelled()) {
            predicate.append("\n  AND task.status <> 'CANCELLED'");
        }
        if (!filter.taskStatuses().isEmpty()) {
            predicate.append("\n  AND task.status IN (:taskStatuses)");
        }
        if (!filter.maintenanceKinds().isEmpty()) {
            predicate.append(query.sourceRevision() == null
                    ? """

                          AND coalesce(
                              maintenance_rule.maintenance_kind,
                              regulation.maintenance_kind,
                              linked.maintenance_type
                          ) IN (:maintenanceKinds)
                        """
                    : "\n  AND linked.maintenance_type IN (:maintenanceKinds)");
        }
        return predicate.toString();
    }

    private String snapshotOccurrencePredicate(PprEquipmentCalendarFilter filter) {
        StringBuilder predicate = new StringBuilder();
        if (!filter.taskStatuses().isEmpty()) {
            predicate.append("\n  AND 1 = 0");
        }
        if (!filter.maintenanceKinds().isEmpty()) {
            predicate.append("\n  AND item.maintenance_type IN (:maintenanceKinds)");
        }
        return predicate.toString();
    }

    private String equipmentPredicate(CalendarQuery query, String alias) {
        PprEquipmentCalendarFilter filter = query.filter();
        String owner = "coalesce(" + alias + ".responsible_department_id, "
                + alias + ".department_id)";
        StringBuilder predicate = new StringBuilder();
        if (query.planDepartmentId() != null) {
            predicate.append("\n  AND ").append(owner).append(" = :planDepartmentId");
        }
        if (query.scopeDepartmentId() != null) {
            predicate.append("\n  AND ").append(owner).append(" = :scopeDepartmentId");
        }
        if (filter.departmentId() != null) {
            predicate.append("\n  AND ").append(owner).append(" = :departmentId");
        }
        if (filter.locationId() != null) {
            predicate.append("\n  AND ").append(alias).append(".location_id = :locationId");
        }
        if (filter.equipmentId() != null) {
            predicate.append("\n  AND ").append(alias).append(".id = :equipmentId");
        }
        if (filter.equipmentTypeId() != null) {
            predicate.append("\n  AND ").append(alias)
                    .append(".equipment_type_id = :equipmentTypeId");
        }
        if (filter.search() != null) {
            predicate.append("""

                      AND (
                          lower(coalesce(%1$s.name, '')) LIKE :search
                          OR lower(coalesce(%1$s.code, '')) LIKE :search
                          OR lower(coalesce(%1$s.inventory_number, '')) LIKE :search
                          OR lower(coalesce(%1$s.technical_number, '')) LIKE :search
                      )
                    """.formatted(alias));
        }
        return predicate.toString();
    }

    private MapSqlParameterSource parameters(CalendarQuery query) {
        PprEquipmentCalendarFilter filter = query.filter();
        LocalDate yearStart = LocalDate.of(filter.year(), 1, 1);
        LocalDate yearEnd = LocalDate.of(filter.year(), 12, 31);
        LocalDate windowStart = filter.month() == null
                ? yearStart
                : LocalDate.of(filter.year(), filter.month(), 1);
        LocalDate windowEnd = filter.month() == null
                ? yearEnd
                : windowStart.withDayOfMonth(windowStart.lengthOfMonth());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("planId", query.planId())
                .addValue("planDepartmentId", query.planDepartmentId())
                .addValue("scopeDepartmentId", query.scopeDepartmentId())
                .addValue("planStart", query.planStartDate())
                .addValue("planEnd", query.planEndDate())
                .addValue("yearStart", yearStart)
                .addValue("yearEnd", yearEnd)
                .addValue("windowStart", windowStart)
                .addValue("windowEnd", windowEnd)
                .addValue("sourceRevision", query.sourceRevision())
                .addValue("departmentId", filter.departmentId())
                .addValue("locationId", filter.locationId())
                .addValue("equipmentId", filter.equipmentId())
                .addValue("equipmentTypeId", filter.equipmentTypeId())
                .addValue("search", filter.search() == null
                        ? null
                        : "%" + filter.search().toLowerCase(Locale.ROOT) + "%");
        addEnumNames(params, "maintenanceKinds", filter.maintenanceKinds());
        addEnumNames(params, "taskStatuses", filter.taskStatuses());
        return params;
    }

    private void addEnumNames(
            MapSqlParameterSource params,
            String name,
            Collection<? extends Enum<?>> values) {
        params.addValue(name, values.stream().map(Enum::name).toList());
    }

    private boolean hasExplicitEquipmentFilter(PprEquipmentCalendarFilter filter) {
        return filter.departmentId() != null
                || filter.locationId() != null
                || filter.equipmentId() != null
                || filter.equipmentTypeId() != null
                || filter.search() != null;
    }

    private String equipmentOrderBy(PprEquipmentCalendarFilter filter) {
        String expression = switch (filter.sortBy()) {
            case EQUIPMENT_NAME -> "lower(coalesce(e.name, ''))";
            case INVENTORY_NUMBER ->
                    "lower(coalesce(e.inventory_number, ''))";
            case EQUIPMENT_CODE -> "lower(coalesce(e.code, ''))";
        };
        String direction = filter.sortDirection()
                == com.toir.dto.pprplanning.calendar
                        .PprEquipmentCalendarSortDirection.DESC
                ? "DESC"
                : "ASC";
        return expression + " " + direction + ", e.id ASC";
    }

    private CalendarEquipment mapEquipment(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new CalendarEquipment(
                uuid(resultSet, "id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("inventory_number"),
                resultSet.getString("technical_number"),
                enumValue(EquipmentStatus.class, resultSet.getString("status")),
                resultSet.getBoolean("is_deleted"),
                resultSet.getString("criticality_code"),
                uuid(resultSet, "physical_department_id"),
                resultSet.getString("physical_department_code"),
                resultSet.getString("physical_department_name"),
                uuid(resultSet, "responsible_department_id"),
                resultSet.getString("responsible_department_code"),
                resultSet.getString("responsible_department_name"),
                uuid(resultSet, "parent_id"),
                resultSet.getString("parent_code"),
                resultSet.getString("parent_name"),
                uuid(resultSet, "location_id"),
                resultSet.getString("location_code"),
                resultSet.getString("location_name"),
                uuid(resultSet, "equipment_type_id"),
                resultSet.getString("equipment_type_code"),
                resultSet.getString("equipment_type_name"));
    }

    private CalendarOccurrence mapOccurrence(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new CalendarOccurrence(
                uuid(resultSet, "equipment_id"),
                enumValue(
                        PprEquipmentCalendarOccurrenceSourceType.class,
                        resultSet.getString("source_type")),
                uuid(resultSet, "task_id"),
                uuid(resultSet, "calculation_item_id"),
                uuid(resultSet, "regulation_id"),
                uuid(resultSet, "maintenance_rule_id"),
                resultSet.getString("source_code"),
                resultSet.getString("source_name"),
                enumValue(MaintenanceKind.class, resultSet.getString("maintenance_kind")),
                resultSet.getObject("planned_date", LocalDate.class),
                resultSet.getObject("scheduled_start", LocalDateTime.class),
                resultSet.getObject("scheduled_end", LocalDateTime.class),
                resultSet.getObject("due_date", LocalDateTime.class),
                enumValue(PprTaskStatus.class, resultSet.getString("status")),
                enumValue(PriorityLevel.class, resultSet.getString("priority")),
                resultSet.getBigDecimal("planned_labor_hours"),
                resultSet.getString("title"));
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
