package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceOperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface MaintenanceOperationRepository extends JpaRepository<MaintenanceOperation, UUID> {
    java.util.Optional<MaintenanceOperation> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<MaintenanceOperation> findAllByIsDeletedFalse();

    java.util.List<MaintenanceOperation> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

}
