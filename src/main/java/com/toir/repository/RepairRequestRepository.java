package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairRequest;
import com.toir.enums.RequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM repair_requests WHERE number = :number AND is_deleted = false", nativeQuery = true)
    boolean existsByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM repair_requests WHERE (cast(:status as varchar) IS NULL OR status = cast(:status as varchar)) AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") RequestStatus status);

    @Query(value = "SELECT * FROM repair_requests WHERE (cast(:status as varchar) IS NULL OR status = cast(:status as varchar)) " +
            "AND (cast(:departmentId as uuid) IS NULL OR department_id = cast(:departmentId as uuid)) " +
            "AND (cast(:equipmentId as uuid) IS NULL OR equipment_id = cast(:equipmentId as uuid)) AND is_deleted = false " +
            "ORDER BY detected_at DESC", nativeQuery = true)
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId);

    @Query("select r from RepairRequest r where (:status is null or r.status = :status) " +
            "and (:departmentId is null or r.departmentId = :departmentId) " +
            "and (:equipmentId is null or r.equipmentId = :equipmentId) " +
            "and (:search is null or lower(r.number) like lower(concat('%', :search, '%')) " +
            "or lower(r.title) like lower(concat('%', :search, '%')) " +
            "or lower(r.description) like lower(concat('%', :search, '%')) " +
            "or lower(r.rejectionReason) like lower(concat('%', :search, '%')) " +
            "or lower(r.closeResult) like lower(concat('%', :search, '%'))) " +
            "order by r.detectedAt desc")
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId,
                               @Param("search") String search);

    @Query(nativeQuery = true, value =
            """ 
            select * from repair_requests r where
            (cast(:status as varchar) is null or r.status = cast(:status as varchar))
            and (cast(:departmentId as uuid) is null or r.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as uuid) is null or r.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(r.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(r.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(r.description) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(r.rejection_reason) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(r.close_result) like lower(concat('%', cast(:search as varchar), '%')))
            order by r.detected_at desc limit :limit offset :offset
                        """)
    List<RepairRequest> searchPaginated(@Param("status") RequestStatus status,
                                        @Param("departmentId") UUID departmentId,
                                        @Param("equipmentId") UUID equipmentId,
                                        @Param("search") String search,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);
}
