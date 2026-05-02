package com.toir.repository;

import com.toir.entity.Warehouse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    @Query(value = "SELECT * FROM warehouses WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Warehouse> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM warehouses WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Warehouse> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM warehouses WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Warehouse> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM warehouses WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM warehouses WHERE is_deleted = false", nativeQuery = true)
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
    List<Warehouse> search(@Param("search") String search,
                                      @Param("departmentId") UUID departmentId,
                                      @Param("locationId") UUID locationId,
                                      @Param("responsibleId") UUID responsibleId,
                                      @Param("active") Boolean active);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM warehouses
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
