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
    java.util.Optional<CompletionAct> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<CompletionAct> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<CompletionAct> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM completion_acts WHERE work_order_id = :workOrderId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CompletionAct> findByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM completion_acts WHERE act_number = :actNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByActNumberAndIsDeletedFalse(@Param("actNumber") String actNumber);
}
