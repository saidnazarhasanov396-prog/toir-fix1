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
    @Query(value = "SELECT * FROM file_assets WHERE entity_type = :entityType AND entity_id = :entityId", nativeQuery = true)
    List<FileAsset> findAllByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
