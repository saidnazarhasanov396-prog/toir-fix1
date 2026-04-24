package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.FailureReason;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface FailureReasonRepository extends JpaRepository<FailureReason, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM failure_reasons WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
