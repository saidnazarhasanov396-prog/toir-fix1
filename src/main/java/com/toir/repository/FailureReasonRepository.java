package com.toir.repository;
import com.toir.entity.FailureReason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailureReasonRepository extends JpaRepository<FailureReason, UUID> {
    boolean existsByCode(String code);
}
