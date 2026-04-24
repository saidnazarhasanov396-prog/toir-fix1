package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.BudgetLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {
}
