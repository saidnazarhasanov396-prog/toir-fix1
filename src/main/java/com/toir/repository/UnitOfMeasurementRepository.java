package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UnitOfMeasurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    java.util.Optional<UnitOfMeasurement> findByIdAndIsDeletedFalse(java.util.UUID id);

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

    java.util.List<UnitOfMeasurement> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM units_of_measurement WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
