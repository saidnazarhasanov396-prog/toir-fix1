package com.toir.repository.maintenance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationFilter;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationStatsDto;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceRegulationJdbcRepository
        implements MaintenanceRegulationReadRepository {

    private static final String GENERAL_DATASET_CTE = """
            WITH canonical_regulations AS (
                SELECT 'REGULATION'::text AS source_type,
                       mr.id AS display_id,
                       mr.equipment_type_id,
                       mr.code,
                       mr.name,
                       mr.description,
                       mr.maintenance_kind,
                       mr.is_active AS effective_active,
                       mr.updated_at
                FROM maintenance_regulations mr
                WHERE mr.is_deleted = false

                UNION ALL

                SELECT 'EQUIPMENT_RULE'::text AS source_type,
                       emr.id AS display_id,
                       e.equipment_type_id,
                       emr.code,
                       emr.name,
                       emr.description,
                       emr.maintenance_kind,
                       emr.is_active AS effective_active,
                       emr.updated_at
                FROM equipment_maintenance_rules emr
                JOIN equipment e
                  ON e.id = emr.equipment_id
                 AND e.is_deleted = false
                WHERE emr.is_deleted = false
                  AND emr.base_regulation_id IS NULL
            )
            """;

    private static final String EFFECTIVE_EQUIPMENT_ITEMS_CTE = """
            WITH effective_equipment_items AS (
                SELECT e.id AS equipment_id,
                       e.equipment_type_id,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN 'EQUIPMENT_RULE'::text
                            ELSE 'REGULATION'::text
                       END AS source_type,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.id
                            ELSE mr.id
                       END AS display_id,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.code
                            ELSE mr.code
                       END AS code,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.name
                            ELSE mr.name
                       END AS name,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.description
                            ELSE mr.description
                       END AS description,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.maintenance_kind
                            ELSE mr.maintenance_kind
                       END AS maintenance_kind,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.is_active
                            ELSE emr.is_active AND mr.is_active
                       END AS effective_active,
                       greatest(emr.updated_at, mr.updated_at) AS updated_at
                FROM equipment_maintenance_rules emr
                JOIN equipment e
                  ON e.id = emr.equipment_id
                 AND e.is_deleted = false
                LEFT JOIN maintenance_regulations mr
                  ON mr.id = emr.base_regulation_id
                 AND mr.is_deleted = false
                WHERE emr.is_deleted = false
                  AND (emr.base_regulation_id IS NULL OR mr.id IS NOT NULL)
            )
            """;

    private static final String TYPE_ITEMS_CTE = """
            WITH effective_equipment_items AS (
                SELECT e.id AS equipment_id,
                       e.equipment_type_id,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN 'EQUIPMENT_RULE'::text
                            ELSE 'REGULATION'::text
                       END AS source_type,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.id
                            ELSE mr.id
                       END AS display_id,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.code
                            ELSE mr.code
                       END AS code,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.name
                            ELSE mr.name
                       END AS name,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.description
                            ELSE mr.description
                       END AS description,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.maintenance_kind
                            ELSE mr.maintenance_kind
                       END AS maintenance_kind,
                       CASE WHEN emr.base_regulation_id IS NULL
                            THEN emr.is_active
                            ELSE emr.is_active AND mr.is_active
                       END AS effective_active,
                       greatest(emr.updated_at, mr.updated_at) AS updated_at
                FROM equipment_maintenance_rules emr
                JOIN equipment e
                  ON e.id = emr.equipment_id
                 AND e.is_deleted = false
                LEFT JOIN maintenance_regulations mr
                  ON mr.id = emr.base_regulation_id
                 AND mr.is_deleted = false
                WHERE emr.is_deleted = false
                  AND (emr.base_regulation_id IS NULL OR mr.id IS NOT NULL)
            ),
            type_items AS (
                SELECT mr.equipment_type_id,
                       NULL::uuid AS equipment_id,
                       'TYPE_REGULATION'::text AS source_origin,
                       0 AS origin_priority,
                       'REGULATION'::text AS source_type,
                       mr.id AS display_id,
                       mr.code,
                       mr.name,
                       mr.description,
                       mr.maintenance_kind,
                       mr.is_active AS effective_active,
                       mr.updated_at
                FROM maintenance_regulations mr
                WHERE mr.is_deleted = false

                UNION ALL

                SELECT item.equipment_type_id,
                       item.equipment_id,
                       'EQUIPMENT_ASSIGNMENT'::text AS source_origin,
                       1 AS origin_priority,
                       item.source_type,
                       item.display_id,
                       item.code,
                       item.name,
                       item.description,
                       item.maintenance_kind,
                       item.effective_active,
                       item.updated_at
                FROM effective_equipment_items item
                WHERE item.equipment_type_id IS NOT NULL
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MaintenanceRegulationJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<GeneralKey> findGeneral(
            MaintenanceRegulationFilter filter,
            Pageable pageable
    ) {
        MapSqlParameterSource params = pagedParameters(filter, pageable);
        String filtered = " FROM canonical_regulations item WHERE 1 = 1 "
                + filterPredicate("item");
        Long total = jdbc.queryForObject(
                GENERAL_DATASET_CTE + " SELECT count(*) " + filtered,
                params,
                Long.class
        );
        List<GeneralKey> content = jdbc.query(
                GENERAL_DATASET_CTE
                        + " SELECT item.source_type, item.display_id "
                        + filtered
                        + " ORDER BY item.updated_at DESC, item.source_type, item.display_id "
                        + " LIMIT :limit OFFSET :offset",
                params,
                (resultSet, rowNumber) -> new GeneralKey(
                        DisplaySource.valueOf(resultSet.getString("source_type")),
                        resultSet.getObject("display_id", UUID.class)
                )
        );
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public MaintenanceRegulationStatsDto stats(MaintenanceRegulationFilter filter) {
        MaintenanceRegulationStatsDto result = jdbc.queryForObject(
                GENERAL_DATASET_CTE
                        + """
                          SELECT count(*) AS total,
                                 count(*) FILTER (WHERE item.effective_active) AS active,
                                 count(*) FILTER (
                                     WHERE item.maintenance_kind = 'PREVENTIVE'
                                 ) AS preventive,
                                 count(*) FILTER (
                                     WHERE item.maintenance_kind = 'OVERHAUL'
                                 ) AS overhaul
                          FROM canonical_regulations item
                          WHERE 1 = 1
                          """
                        + filterPredicate("item"),
                parameters(filter),
                (resultSet, rowNumber) -> new MaintenanceRegulationStatsDto(
                        resultSet.getLong("total"),
                        resultSet.getLong("active"),
                        resultSet.getLong("preventive"),
                        resultSet.getLong("overhaul")
                )
        );
        return result == null ? new MaintenanceRegulationStatsDto(0, 0, 0, 0) : result;
    }

    @Override
    public Page<UUID> findEquipmentIds(
            MaintenanceRegulationFilter filter,
            Pageable pageable
    ) {
        MapSqlParameterSource params = pagedParameters(filter, pageable);
        String filtered = """
                , filtered_items AS (
                    SELECT item.*
                    FROM effective_equipment_items item
                    WHERE 1 = 1
                """
                + filterPredicate("item")
                + "\n)";
        Long total = jdbc.queryForObject(
                EFFECTIVE_EQUIPMENT_ITEMS_CTE
                        + filtered
                        + " SELECT count(DISTINCT equipment_id) FROM filtered_items",
                params,
                Long.class
        );
        List<UUID> ids = jdbc.query(
                EFFECTIVE_EQUIPMENT_ITEMS_CTE
                        + filtered
                        + """
                          SELECT e.id
                          FROM filtered_items item
                          JOIN equipment e
                            ON e.id = item.equipment_id
                           AND e.is_deleted = false
                          GROUP BY e.id, e.updated_at
                          ORDER BY e.updated_at DESC, e.id
                          LIMIT :limit OFFSET :offset
                          """,
                params,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );
        return new PageImpl<>(ids, pageable, total == null ? 0 : total);
    }

    @Override
    public List<EquipmentMatch> findEquipmentMatches(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentIds
    ) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = parameters(filter)
                .addValue("equipmentIds", equipmentIds);
        return jdbc.query(
                EFFECTIVE_EQUIPMENT_ITEMS_CTE
                        + """
                          SELECT matched.equipment_id,
                                 matched.source_type,
                                 matched.display_id,
                                 matched.effective_active
                          FROM (
                              SELECT DISTINCT ON (
                                         item.equipment_id,
                                         item.source_type,
                                         item.display_id
                                     )
                                     item.*
                              FROM effective_equipment_items item
                              WHERE item.equipment_id IN (:equipmentIds)
                          """
                        + filterPredicate("item")
                        + """
                              ORDER BY item.equipment_id,
                                       item.source_type,
                                       item.display_id,
                                       item.updated_at DESC
                          ) matched
                          ORDER BY matched.equipment_id,
                                   matched.updated_at DESC,
                                   matched.source_type,
                                   matched.display_id
                          """,
                params,
                (resultSet, rowNumber) -> new EquipmentMatch(
                        resultSet.getObject("equipment_id", UUID.class),
                        DisplaySource.valueOf(resultSet.getString("source_type")),
                        resultSet.getObject("display_id", UUID.class),
                        resultSet.getBoolean("effective_active")
                )
        );
    }

    @Override
    public Page<UUID> findEquipmentTypeIds(
            MaintenanceRegulationFilter filter,
            Pageable pageable
    ) {
        MapSqlParameterSource params = pagedParameters(filter, pageable);
        String filtered = filteredTypeItems();
        Long total = jdbc.queryForObject(
                TYPE_ITEMS_CTE
                        + filtered
                        + " SELECT count(DISTINCT equipment_type_id) FROM filtered_items",
                params,
                Long.class
        );
        List<UUID> ids = jdbc.query(
                TYPE_ITEMS_CTE
                        + filtered
                        + """
                          SELECT et.id
                          FROM (
                              SELECT DISTINCT equipment_type_id
                              FROM filtered_items
                          ) matched
                          JOIN equipment_types et
                            ON et.id = matched.equipment_type_id
                           AND et.is_deleted = false
                          ORDER BY et.updated_at DESC, et.id
                          LIMIT :limit OFFSET :offset
                          """,
                params,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );
        return new PageImpl<>(ids, pageable, total == null ? 0 : total);
    }

    @Override
    public List<EquipmentTypeMatch> findEquipmentTypeMatches(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentTypeIds
    ) {
        if (equipmentTypeIds == null || equipmentTypeIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = parameters(filter)
                .addValue("equipmentTypeIds", equipmentTypeIds);
        return jdbc.query(
                TYPE_ITEMS_CTE
                        + """
                          SELECT matched.equipment_type_id,
                                 matched.source_type,
                                 matched.display_id,
                                 matched.effective_active
                          FROM (
                              SELECT DISTINCT ON (
                                         item.equipment_type_id,
                                         item.source_type,
                                         item.display_id
                                     )
                                     item.*
                              FROM type_items item
                              WHERE item.equipment_type_id IN (:equipmentTypeIds)
                          """
                        + filterPredicate("item")
                        + """
                              ORDER BY item.equipment_type_id,
                                       item.source_type,
                                       item.display_id,
                                       item.origin_priority,
                                       item.updated_at DESC
                          ) matched
                          ORDER BY matched.equipment_type_id,
                                   matched.updated_at DESC,
                                   matched.source_type,
                                   matched.display_id
                          """,
                params,
                (resultSet, rowNumber) -> new EquipmentTypeMatch(
                        resultSet.getObject("equipment_type_id", UUID.class),
                        DisplaySource.valueOf(resultSet.getString("source_type")),
                        resultSet.getObject("display_id", UUID.class),
                        resultSet.getBoolean("effective_active")
                )
        );
    }

    @Override
    public Map<UUID, Integer> countMatchingEquipmentByType(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentTypeIds
    ) {
        if (equipmentTypeIds == null || equipmentTypeIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = parameters(filter)
                .addValue("equipmentTypeIds", equipmentTypeIds);
        List<EquipmentTypeCount> counts = jdbc.query(
                TYPE_ITEMS_CTE
                        + filteredTypeItems()
                        + """
                          , eligible_equipment AS (
                              SELECT e.id AS equipment_id,
                                     e.equipment_type_id
                              FROM filtered_items item
                              JOIN equipment e
                                ON item.source_origin = 'TYPE_REGULATION'
                               AND e.equipment_type_id = item.equipment_type_id
                               AND e.is_deleted = false
                              WHERE item.equipment_type_id IN (:equipmentTypeIds)

                              UNION

                              SELECT e.id AS equipment_id,
                                     e.equipment_type_id
                              FROM filtered_items item
                              JOIN equipment e
                                ON e.id = item.equipment_id
                               AND e.is_deleted = false
                              WHERE item.equipment_id IS NOT NULL
                                AND item.equipment_type_id IN (:equipmentTypeIds)
                          )
                          SELECT equipment_type_id,
                                 count(DISTINCT equipment_id) AS equipment_count
                          FROM eligible_equipment
                          GROUP BY equipment_type_id
                          """,
                params,
                (resultSet, rowNumber) -> new EquipmentTypeCount(
                        resultSet.getObject("equipment_type_id", UUID.class),
                        Math.toIntExact(resultSet.getLong("equipment_count"))
                )
        );
        return counts.stream().collect(Collectors.toMap(
                EquipmentTypeCount::equipmentTypeId,
                EquipmentTypeCount::equipmentCount,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private String filteredTypeItems() {
        return """
                , filtered_items AS (
                    SELECT item.*
                    FROM type_items item
                    WHERE item.equipment_type_id IS NOT NULL
                """
                + filterPredicate("item")
                + "\n)";
    }

    private String filterPredicate(String alias) {
        return """
                 AND (
                     cast(:searchPattern AS text) IS NULL
                     OR lower(%1$s.code) LIKE cast(:searchPattern AS text)
                     OR lower(%1$s.name) LIKE cast(:searchPattern AS text)
                     OR lower(coalesce(%1$s.description, '')) LIKE cast(:searchPattern AS text)
                 )
                 AND (
                     cast(:maintenanceType AS text) IS NULL
                     OR %1$s.maintenance_kind = cast(:maintenanceType AS text)
                 )
                 AND (
                     cast(:equipmentTypeId AS uuid) IS NULL
                     OR %1$s.equipment_type_id = cast(:equipmentTypeId AS uuid)
                 )
                 AND (
                     cast(:active AS boolean) IS NULL
                     OR %1$s.effective_active = cast(:active AS boolean)
                 )
                """.formatted(alias);
    }

    private MapSqlParameterSource pagedParameters(
            MaintenanceRegulationFilter filter,
            Pageable pageable
    ) {
        return parameters(filter)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", Math.multiplyExact(
                        (long) pageable.getPageNumber(),
                        pageable.getPageSize()
                ));
    }

    private MapSqlParameterSource parameters(MaintenanceRegulationFilter filter) {
        MaintenanceRegulationFilter safeFilter = filter == null
                ? new MaintenanceRegulationFilter(null, null, null, null)
                : filter;
        String searchPattern = safeFilter.search() == null
                ? null
                : "%" + safeFilter.search().toLowerCase(Locale.ROOT) + "%";
        return new MapSqlParameterSource()
                .addValue("searchPattern", searchPattern, Types.VARCHAR)
                .addValue(
                        "maintenanceType",
                        safeFilter.maintenanceType() == null
                                ? null
                                : safeFilter.maintenanceType().name(),
                        Types.VARCHAR
                )
                .addValue("equipmentTypeId", safeFilter.equipmentTypeId(), Types.OTHER)
                .addValue("active", safeFilter.active(), Types.BOOLEAN);
    }
}
