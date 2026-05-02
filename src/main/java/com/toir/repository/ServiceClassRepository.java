package com.toir.repository;

import com.toir.entity.ServiceClass;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    @Query(value = "SELECT * FROM service_classes WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ServiceClass> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM service_classes WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ServiceClass> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT sc FROM ServiceClass sc
            WHERE sc.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(sc.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(sc.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY sc.updatedAt DESC
            """)
    List<ServiceClass> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM service_classes WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ServiceClass> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM service_classes WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM service_classes WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM service_classes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM service_classes
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
