package com.toir.repository;

import com.toir.entity.warehouse.WarehouseReservationLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseReservationLedgerRepository extends JpaRepository<WarehouseReservationLedger, UUID> {
    Optional<WarehouseReservationLedger> findByIdempotencyKeyAndIsDeletedFalse(String idempotencyKey);
}
