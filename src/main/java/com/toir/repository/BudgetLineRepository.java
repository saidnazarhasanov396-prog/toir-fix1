package com.toir.repository;

import com.toir.entity.BudgetLine;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {
    @Query(value = "SELECT * FROM budget_lines WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<BudgetLine> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM budget_lines WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BudgetLine> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM budget_lines WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<BudgetLine> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM budget_lines WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM budget_lines WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

}
