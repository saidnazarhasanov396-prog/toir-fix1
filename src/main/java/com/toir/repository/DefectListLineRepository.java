package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectListLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface DefectListLineRepository extends JpaRepository<DefectListLine, UUID> {
    java.util.Optional<DefectListLine> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<DefectListLine> findAllByIsDeletedFalse();

    java.util.List<DefectListLine> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

}
