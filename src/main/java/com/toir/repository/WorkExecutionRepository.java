package com.toir.repository;
import com.toir.entity.WorkExecution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    List<WorkExecution> findAllByWorkOrderIdOrderByStartedAtAsc(UUID workOrderId);
}
