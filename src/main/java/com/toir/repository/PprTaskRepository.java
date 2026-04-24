package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    List<PprTask> findAllByPlanId(UUID planId);

    long countByStatus(PprTaskStatus status);
}
