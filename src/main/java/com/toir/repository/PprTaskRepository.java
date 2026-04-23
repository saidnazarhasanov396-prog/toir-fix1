package com.toir.repository;
import com.toir.entity.PprTask;
import com.toir.entity.PprTaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    List<PprTask> findAllByPlanId(UUID planId);
    long countByStatus(PprTaskStatus status);
}
