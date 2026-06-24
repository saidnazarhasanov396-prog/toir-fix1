package com.toir.repository.contarctor;

import com.toir.entity.contractors.Contractor;
import com.toir.enums.ContractorStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ContractorRepository extends JpaRepository<Contractor, UUID> {
    @Query(value = "SELECT * FROM contractors WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Contractor> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM contractors WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Contractor> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT c FROM Contractor c
            WHERE c.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(c.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(c.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY c.updatedAt DESC
            """)
    List<Contractor> findAllBySearch(@Param("search") String search);

    @Query("""
            SELECT c FROM Contractor c
            WHERE c.isDeleted = false
                AND (:status IS NULL OR c.status = :status)
                AND (cast(:specialization as string) IS NULL OR :specialization = '' OR
                     lower(coalesce(c.specialization, '')) LIKE lower(concat('%', cast(:specialization as string), '%')))
                AND (cast(:search as string) IS NULL OR :search = '' OR
                     lower(c.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(c.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY c.updatedAt DESC
            """)
    List<Contractor> findAllByFilters(
            @Param("search") String search,
            @Param("status") ContractorStatus status,
            @Param("specialization") String specialization
    );

    @Query(value = "SELECT * FROM contractors WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Contractor> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractors WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM contractors WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM contractors WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM contractors
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
