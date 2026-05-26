package com.toir.repository;

import com.toir.entity.PprPlanTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PprPlanTargetRepository extends JpaRepository<PprPlanTarget, UUID> {
}
