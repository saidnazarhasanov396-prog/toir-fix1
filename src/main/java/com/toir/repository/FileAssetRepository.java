package com.toir.repository;
import com.toir.entity.FileAsset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
    List<FileAsset> findAllByEntityTypeAndEntityId(String entityType, String entityId);
}
