package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzDocumentRepository extends JpaRepository<FakturaUzDocument, UUID> {
    Optional<FakturaUzDocument> findByUniqueIdAndIsDeletedFalse(String uniqueId);

    @Query(value = """
            SELECT *
            FROM faktura_uz_documents d
            WHERE d.is_deleted = false
              AND (:endpointId IS NULL OR d.endpoint_id = cast(:endpointId AS uuid))
              AND (:type IS NULL OR d.type = :type)
              AND (:fromMillis IS NULL OR d.created_date_time >= :fromMillis)
              AND (:toMillis IS NULL OR d.created_date_time <= :toMillis)
              AND (
                    :query IS NULL OR :query = ''
                    OR lower(coalesce(d.title, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.file_name, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.unique_id, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.contractor_name, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.contractor_inn, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.owner_name, '')) LIKE lower(concat('%', :query, '%'))
              )
            ORDER BY d.created_date_time DESC NULLS LAST, d.updated_at DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM faktura_uz_documents d
            WHERE d.is_deleted = false
              AND (:endpointId IS NULL OR d.endpoint_id = cast(:endpointId AS uuid))
              AND (:type IS NULL OR d.type = :type)
              AND (:fromMillis IS NULL OR d.created_date_time >= :fromMillis)
              AND (:toMillis IS NULL OR d.created_date_time <= :toMillis)
              AND (
                    :query IS NULL OR :query = ''
                    OR lower(coalesce(d.title, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.file_name, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.unique_id, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.contractor_name, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.contractor_inn, '')) LIKE lower(concat('%', :query, '%'))
                    OR lower(coalesce(d.owner_name, '')) LIKE lower(concat('%', :query, '%'))
              )
            """,
            nativeQuery = true)
    Page<FakturaUzDocument> search(@Param("endpointId") UUID endpointId,
                                   @Param("type") Integer type,
                                   @Param("fromMillis") Long fromMillis,
                                   @Param("toMillis") Long toMillis,
                                   @Param("query") String query,
                                   Pageable pageable);
}
