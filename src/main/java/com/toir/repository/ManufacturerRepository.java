package com.toir.repository;

import com.toir.entity.Manufacturer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
    @Query(value = "SELECT * FROM manufacturers WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Manufacturer> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM manufacturers WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Manufacturer> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT m FROM Manufacturer m
            WHERE m.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(m.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(m.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY m.updatedAt DESC
            """)
    List<Manufacturer> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM manufacturers WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Manufacturer> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM manufacturers WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM manufacturers WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM manufacturers WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM manufacturers
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
