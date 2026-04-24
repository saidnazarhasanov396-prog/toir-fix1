package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectListLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface DefectListLineRepository extends JpaRepository<DefectListLine, UUID> {
}
