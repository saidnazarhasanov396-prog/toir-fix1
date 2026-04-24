package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CompletionAct;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface CompletionActRepository extends JpaRepository<CompletionAct, UUID> {
    Optional<CompletionAct> findByWorkOrderId(UUID workOrderId);
    boolean existsByActNumber(String actNumber);
}
