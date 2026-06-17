package com.toir.repository;

import com.toir.dto.approval.ApprovableDocumentDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApprovableDocumentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<ApprovableDocumentDto> searchAll(
            String searchPattern,
            String type,
            String status
    ) {
        String sql = """
                SELECT doc.id,
                       doc.type,
                       doc.code,
                       doc.name,
                       ar.status     AS approval_status,
                       ar.id         AS approval_id,
                       doc.created_at
                FROM (
                    SELECT id, 'WORK_ORDER'              AS type, number  AS code, title                         AS name, created_at FROM work_orders            WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'REPAIR_REQUEST'          AS type, number  AS code, title                         AS name, created_at FROM repair_requests          WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'PROCUREMENT_REQUEST'     AS type, number  AS code, title                         AS name, created_at FROM procurement_requests     WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'PPR_PLAN'                AS type, code    AS code, name                          AS name, created_at FROM ppr_plans                WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'DEFECT_LIST'             AS type, code    AS code, title                         AS name, created_at FROM defect_lists             WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'PLANNED_SHUTDOWN'        AS type, NULL    AS code, name                          AS name, created_at FROM planned_shutdowns        WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'REPAIR_CAMPAIGN'         AS type, code    AS code, name                          AS name, created_at FROM repair_campaigns         WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'ACTUAL_COST'             AS type, NULL    AS code, CONCAT('Amount: ', amount)    AS name, created_at FROM actual_costs             WHERE is_deleted = false
                    UNION ALL
                    SELECT id, 'MAINTENANCE_REGULATION'  AS type, code    AS code, name                          AS name, created_at FROM maintenance_regulations  WHERE is_deleted = false
                ) doc
                LEFT JOIN LATERAL (
                    SELECT id, status
                    FROM approval_requests
                    WHERE COALESCE(target_id, document_id) = doc.id::uuid
                      AND is_deleted = false
                    ORDER BY created_at DESC
                    LIMIT 1
                ) ar ON true
                WHERE (:type   IS NULL OR doc.type   = :type)
                  AND (:status IS NULL OR ar.status  = :status)
                  AND (:search IS NULL
                       OR LOWER(COALESCE(doc.code, '')) LIKE :search
                       OR LOWER(COALESCE(doc.name, '')) LIKE :search)
                ORDER BY doc.created_at DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type)
                .addValue("status", status)
                .addValue("search", searchPattern);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ApprovableDocumentDto(
                UUID.fromString(rs.getString("id")),
                ApprovalTargetType.valueOf(rs.getString("type")),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("approval_status") != null
                        ? ApprovalStatus.valueOf(rs.getString("approval_status"))
                        : null,
                rs.getString("approval_id") != null
                        ? UUID.fromString(rs.getString("approval_id"))
                        : null,
                rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant()
                        : null
        ));
    }
}
