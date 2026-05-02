package com.toir.repository;

import com.toir.entity.FailureReason;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface FailureReasonRepository extends JpaRepository<FailureReason, UUID> {
    @Query(value = "SELECT * FROM failure_reasons WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<FailureReason> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM failure_reasons WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<FailureReason> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT fr FROM FailureReason fr
            WHERE fr.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(fr.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(fr.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY fr.updatedAt DESC
            """)
    List<FailureReason> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM failure_reasons WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<FailureReason> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM failure_reasons WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM failure_reasons WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM failure_reasons WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM failure_reasons
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
