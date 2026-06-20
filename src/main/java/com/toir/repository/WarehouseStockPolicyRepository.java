package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStockPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseStockPolicyRepository extends JpaRepository<WarehouseStockPolicy, UUID> {
    Optional<WarehouseStockPolicy> findByWarehouseIdAndSparePartIdAndIsDeletedFalse(
            UUID warehouseId, UUID sparePartId);

    List<WarehouseStockPolicy> findAllByWarehouseIdAndIsDeletedFalse(UUID warehouseId);

    List<WarehouseStockPolicy> findAllBySparePartIdAndIsDeletedFalse(UUID sparePartId);

    List<WarehouseStockPolicy> findAllBySparePartIdInAndIsDeletedFalse(Collection<UUID> sparePartIds);
}
