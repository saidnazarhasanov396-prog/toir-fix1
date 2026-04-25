package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.FileAsset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
    java.util.Optional<FileAsset> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<FileAsset> findAllByIsDeletedFalse();

    java.util.List<FileAsset> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM file_assets WHERE entity_type = :entityType AND entity_id = :entityId AND is_deleted = false", nativeQuery = true)
    List<FileAsset> findAllByEntityTypeAndEntityIdAndIsDeletedFalse(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
