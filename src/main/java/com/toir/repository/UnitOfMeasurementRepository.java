package com.toir.repository;

import com.toir.entity.UnitOfMeasurement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    @Query(value = "SELECT * FROM units_of_measurement WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<UnitOfMeasurement> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
            SELECT u FROM UnitOfMeasurement u
            WHERE u.isDeleted = false
            AND (cast(:search as string) IS NULL OR
                 lower(u.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                 lower(u.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY u.updatedAt DESC
            """)
    List<UnitOfMeasurement> findAllByIsDeletedFalse(
            @Param("search") String search
    );

    @Query(value = "SELECT * FROM units_of_measurement WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<UnitOfMeasurement> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM units_of_measurement WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM units_of_measurement WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM units_of_measurement WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
