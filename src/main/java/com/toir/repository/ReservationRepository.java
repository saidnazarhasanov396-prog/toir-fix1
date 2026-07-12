package com.toir.repository;

import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = "SELECT * FROM reservations WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Reservation> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM reservations WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Reservation> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM reservations WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Reservation> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM reservations WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM reservations WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM reservations WHERE work_order_id = cast(:workOrderId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Reservation> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT * FROM reservations WHERE work_order_id = cast(:workOrderId as uuid) AND status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Reservation> findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId,
                                                                                         @Param("status") ReservationStatus status);

    @Query(value = "SELECT * FROM reservations WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Reservation> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") ReservationStatus status);

    List<Reservation> findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(
            UUID workOrderId, UUID requirementId, UUID sparePartId, ReservationStatus status);
}
