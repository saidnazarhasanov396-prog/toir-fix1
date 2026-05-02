package com.toir.repository;

import com.toir.entity.CompletionAct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CompletionActRepository extends JpaRepository<CompletionAct, UUID> {
    @Query(value = "SELECT * FROM completion_acts WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CompletionAct> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM completion_acts WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CompletionAct> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM completion_acts WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<CompletionAct> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM completion_acts WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM completion_acts WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM completion_acts WHERE work_order_id = :workOrderId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CompletionAct> findByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM completion_acts WHERE act_number = :actNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByActNumberAndIsDeletedFalse(@Param("actNumber") String actNumber);
}
