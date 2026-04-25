package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    java.util.Optional<PprTask> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<PprTask> findAllByIsDeletedFalse();

    java.util.List<PprTask> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<PprTask> findAllByPlanIdAndIsDeletedFalse(UUID planId);

    long countByStatusAndIsDeletedFalse(PprTaskStatus status);
}
