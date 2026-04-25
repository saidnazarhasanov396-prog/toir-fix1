package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    java.util.Optional<Reservation> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Reservation> findAllByIsDeletedFalse();

    java.util.List<Reservation> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<Reservation> findAllByWorkOrderIdAndIsDeletedFalse(UUID workOrderId);

    List<Reservation> findAllByStatusAndIsDeletedFalse(ReservationStatus status);
}
