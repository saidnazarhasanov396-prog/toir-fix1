package com.toir.repository;

import com.toir.entity.FileAsset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
    @Query(value = "SELECT * FROM file_assets WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<FileAsset> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM file_assets WHERE is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<FileAsset> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM file_assets WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<FileAsset> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM file_assets WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM file_assets WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM file_assets WHERE entity_type = :entityType AND entity_id = :entityId AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<FileAsset> findAllByEntityTypeAndEntityIdAndIsDeletedFalse(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
