package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    java.util.Optional<Warehouse> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Warehouse> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Warehouse> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM warehouses WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query("""
            select w
            from Warehouse w
            where w.isDeleted = false
              and (:departmentId is null or w.departmentId = :departmentId)
              and (:locationId is null or w.locationId = :locationId)
              and (:responsibleId is null or w.responsibleId = :responsibleId)
              and (:active is null or w.active = :active)
              and (
                    :search is null
                    or :search = ''
                    or lower(w.code) like lower(concat('%', :search, '%'))
                    or lower(w.name) like lower(concat('%', :search, '%'))
                  )
            order by w.updatedAt desc
            """)
    java.util.List<Warehouse> search(@Param("search") String search,
                                      @Param("departmentId") java.util.UUID departmentId,
                                      @Param("locationId") java.util.UUID locationId,
                                      @Param("responsibleId") java.util.UUID responsibleId,
                                      @Param("active") Boolean active);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM warehouses
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
