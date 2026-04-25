package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.BudgetLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {
    java.util.Optional<BudgetLine> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<BudgetLine> findAllByIsDeletedFalse();

    java.util.List<BudgetLine> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

}
