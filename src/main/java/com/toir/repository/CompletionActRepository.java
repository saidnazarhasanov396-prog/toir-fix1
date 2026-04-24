package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CompletionAct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface CompletionActRepository extends JpaRepository<CompletionAct, UUID> {
    @Query(value = "SELECT * FROM completion_acts WHERE work_order_id = :workOrderId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CompletionAct> findByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM completion_acts WHERE act_number = :actNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByActNumber(@Param("actNumber") String actNumber);
}
