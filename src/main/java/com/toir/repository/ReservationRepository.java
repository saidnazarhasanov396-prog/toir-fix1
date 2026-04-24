package com.toir.repository;
import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findAllByWorkOrderId(UUID workOrderId);
    List<Reservation> findAllByStatus(ReservationStatus status);
}
