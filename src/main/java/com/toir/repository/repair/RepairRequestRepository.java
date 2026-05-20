package com.toir.repository.repair;

import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    @Query(value = "SELECT * FROM repair_requests WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairRequest> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM repair_requests WHERE number = :number AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairRequest> findByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT * FROM repair_requests WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairRequest> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM repair_requests WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<RepairRequest> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_requests WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM repair_requests WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM repair_requests WHERE number = :number AND is_deleted = false", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM repair_requests WHERE (:status IS NULL OR status = :status) AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query(value = "SELECT * FROM repair_requests WHERE (cast(:status as varchar) IS NULL OR status = cast(:status as varchar)) " +
            "AND (cast(:departmentId as uuid) IS NULL OR department_id = cast(:departmentId as uuid)) " +
            "AND (cast(:equipmentId as uuid) IS NULL OR equipment_id = cast(:equipmentId as uuid)) AND is_deleted = false " +
            "ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId);

    @Query("select r from RepairRequest r where r.isDeleted = false " +
            "and (:status is null or r.status = :status) " +
            "and (:departmentId is null or r.departmentId = :departmentId) " +
            "and (:equipmentId is null or r.equipmentId = :equipmentId) " +
            "and (:search is null or lower(r.number) like lower(concat('%', :search, '%')) " +
            "or lower(r.title) like lower(concat('%', :search, '%')) " +
            "or lower(r.description) like lower(concat('%', :search, '%')) " +
            "or lower(r.rejectionReason) like lower(concat('%', :search, '%')) " +
            "or lower(r.closeResult) like lower(concat('%', :search, '%'))) " +
            "order by r.updatedAt desc")
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId,
                               @Param("search") String search);

    @Query(nativeQuery = true, value =
            """ 
            select * from repair_requests r where
            r.is_deleted = false
            and (cast(:status as text) is null or r.status = cast(:status as text))
            and (cast(:priority as text) is null or r.priority = cast(:priority as text))
            and (cast(:departmentId as uuid) is null or r.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as uuid) is null or r.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or (
                lower(r.number) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.title) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.description) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.rejection_reason) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.close_result) like lower(concat('%', cast(:search as varchar), '%'))
            ))
            order by r.updated_at desc
                        """, countQuery =
            """
            select count(*) from repair_requests r where
            r.is_deleted = false
            and (cast(:status as text) is null or r.status = cast(:status as text))
            and (cast(:priority as text) is null or r.priority = cast(:priority as text))
            and (cast(:departmentId as uuid) is null or r.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as uuid) is null or r.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or (
                lower(r.number) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.title) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.description) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.rejection_reason) like lower(concat('%', cast(:search as varchar), '%'))
                or lower(r.close_result) like lower(concat('%', cast(:search as varchar), '%'))
            ))
                        """)
    Page<RepairRequest> searchPaginated(@Param("status") String status,
                                        @Param("departmentId") UUID departmentId,
                                        @Param("equipmentId") UUID equipmentId,
                                        @Param("search") String search,
                                        @Param("priority") String priority,
                                        Pageable pageable);


    @Query(nativeQuery = true, value = """
        select
            count(r.id) as totalRequests,

            count(*) filter (
                where r.priority = cast(:emergencyPriority as text)
            ) as emergency,

            count(*) filter (
                where r.status = cast(:openStatus as text)
            ) as open,

            count(*) filter (
                where exists (
                    select 1
                    from work_orders w
                    where w.is_deleted = false
                      and w.repair_request_id = r.id
                )
            ) as withWorkOrder

        from repair_requests r
        where r.is_deleted = false
          and (cast(:departmentId as uuid) is null or r.department_id = cast(:departmentId as uuid))
          and (cast(:equipmentId as uuid) is null or r.equipment_id = cast(:equipmentId as uuid))
          and (
              cast(:searchPattern as varchar) is null
              or lower(coalesce(r.number, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(r.title, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(r.description, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(r.rejection_reason, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(r.close_result, '')) like cast(:searchPattern as varchar)
          )
        """)
    RepairRequestStatsProjection getRepairRequestStats(
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("searchPattern") String searchPattern,
            @Param("emergencyPriority") String emergencyPriority,
            @Param("openStatus") String openStatus
    );
}
