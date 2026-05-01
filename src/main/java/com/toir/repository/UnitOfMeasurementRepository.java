package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UnitOfMeasurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    java.util.Optional<UnitOfMeasurement> findByIdAndIsDeletedFalse(java.util.UUID id);

    @Query("""
            SELECT u FROM UnitOfMeasurement u WHERE
                        u.isDeleted = false AND
                        u.code is null or  u.code = :code AND
                        u.name is null or u.name = :name AND
                        (cast(:search as string) is null or
                        lower(u.code) like lower(concat('%', cast(:search as string), '%')) or
                        lower(u.name) like lower(concat('%', cast(:search as string), '%')))
            """
    )

    java.util.List<UnitOfMeasurement> findAllByIsDeletedFalse(
            @Param("code") String code,
            @Param("name") String name,
            @Param("search") String search
    );

    java.util.List<UnitOfMeasurement> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM units_of_measurement WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
