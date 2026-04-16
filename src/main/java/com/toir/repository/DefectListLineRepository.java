package com.toir.repository;
import com.toir.entity.DefectListLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DefectListLineRepository extends JpaRepository<DefectListLine, UUID> {
}
