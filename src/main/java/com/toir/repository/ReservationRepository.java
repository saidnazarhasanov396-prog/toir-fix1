package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = "SELECT * FROM reservations WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<Reservation> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT * FROM reservations WHERE status = :status AND is_deleted = false", nativeQuery = true)
    List<Reservation> findAllByStatus(@Param("status") ReservationStatus status);
}
