package com.toir.repository;

import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.enums.RepairMaterialReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairMaterialReturnRepository extends JpaRepository<RepairMaterialReturn, UUID> {

    Optional<RepairMaterialReturn> findByIdAndIsDeletedFalse(UUID id);

    List<RepairMaterialReturn> findAllByMaterialUsageIdAndStatusAndIsDeletedFalse(
            UUID materialUsageId,
            RepairMaterialReturnStatus status
    );

    List<RepairMaterialReturn> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID workOrderId);
}
