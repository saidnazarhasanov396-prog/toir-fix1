package com.toir.repository;

import com.toir.entity.OeeRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface OeeRecordRepository extends JpaRepository<OeeRecord, UUID> {
    @Query(value = "SELECT * FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<OeeRecord> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM oee_records WHERE is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByIsDeletedFalseOrderByShiftStartDesc();

    @Query(value = "SELECT * FROM oee_records WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<OeeRecord> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM oee_records WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentId") UUID equipmentId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentIds") Collection<UUID> equipmentIds, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByShiftStartBetweenAndIsDeletedFalse(@Param("from") Instant from, @Param("to") Instant to);


    @Query("""
            select r
            from OeeRecord r
            where r.isDeleted = false
              and (:equipmentId is null or r.equipmentId = :equipmentId)
              and (:from is null or r.shiftStart >= :from)
              and (:to is null or r.shiftStart <= :to)
              and (:equipmentSearch is null or exists (
                  select 1 from Equipment e
                  where e.id = r.equipmentId
                    and e.isDeleted = false
                    and (
                        lower(coalesce(e.code, '')) like :equipmentSearch
                        or lower(coalesce(e.name, '')) like :equipmentSearch
                        or lower(coalesce(e.inventoryNumber, '')) like :equipmentSearch
                        or lower(coalesce(e.technicalNumber, '')) like :equipmentSearch
                        or lower(coalesce(e.serialNumber, '')) like :equipmentSearch
                    )
              ))
              and (:departmentId is null or exists (
                  select 1 from Equipment e
                  where e.id = r.equipmentId
                    and e.isDeleted = false
                    and coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId
              ))
              and (:equipmentTypeId is null or exists (
                  select 1 from Equipment e
                  where e.id = r.equipmentId
                    and e.isDeleted = false
                    and e.equipmentTypeId = :equipmentTypeId
              ))
            order by r.shiftStart desc
            """)
    List<OeeRecord> search(
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentSearch") String equipmentSearch,
            @Param("departmentId") UUID departmentId,
            @Param("equipmentTypeId") UUID equipmentTypeId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

}
