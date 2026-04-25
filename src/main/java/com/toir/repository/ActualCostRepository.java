package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ActualCost;
import com.toir.enums.ActualCostStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface ActualCostRepository extends JpaRepository<ActualCost, UUID> {
    java.util.Optional<ActualCost> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ActualCost> findAllByIsDeletedFalse();

    java.util.List<ActualCost> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<ActualCost> findAllByWorkOrderIdAndIsDeletedFalse(UUID workOrderId);

    List<ActualCost> findAllByStatusAndIsDeletedFalse(ActualCostStatus status);
}
