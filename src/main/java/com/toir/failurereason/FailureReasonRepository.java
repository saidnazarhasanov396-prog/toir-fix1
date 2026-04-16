package com.toir.failurereason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailureReasonRepository extends JpaRepository<FailureReason, UUID> {
    boolean existsByCode(String code);
}
