package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.FailureReason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface FailureReasonRepository extends JpaRepository<FailureReason, UUID> {
    boolean existsByCode(String code);
}
