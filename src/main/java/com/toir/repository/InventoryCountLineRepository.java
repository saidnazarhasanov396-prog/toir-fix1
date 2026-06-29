package com.toir.repository;

import com.toir.entity.warehouse.InventoryCountLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCountLineRepository extends JpaRepository<InventoryCountLine, UUID> {

    List<InventoryCountLine> findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID sessionId);

    Optional<InventoryCountLine> findByIdAndSessionIdAndIsDeletedFalse(UUID id, UUID sessionId);
}
