package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WorkExecution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;


@Repository
public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    List<WorkExecution> findAllByWorkOrderIdOrderByStartedAtAsc(UUID workOrderId);
    Page<WorkExecution> findAllByOrderByStartedAtDesc(Pageable pageable);
}
