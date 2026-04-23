package com.toir.repository;
import com.toir.entity.BudgetLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {
}
