package com.toir.repository;
import com.toir.entity.CompletionAct;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompletionActRepository extends JpaRepository<CompletionAct, UUID> {
    Optional<CompletionAct> findByWorkOrderId(UUID workOrderId);
    boolean existsByActNumber(String actNumber);
}
