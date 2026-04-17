package com.toir.workexecution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    List<WorkExecution> findAllByWorkOrderIdOrderByStartedAtAsc(UUID workOrderId);
    Page<WorkExecution> findAllByOrderByStartedAtDesc(Pageable pageable);
}
