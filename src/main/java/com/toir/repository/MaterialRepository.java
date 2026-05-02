package com.toir.repository;

import com.toir.entity.Material;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    @Query(value = "SELECT * FROM materials WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Material> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM materials WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Material> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT m FROM Material m
            WHERE m.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(m.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(m.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY m.updatedAt DESC
            """)
    List<Material> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM materials WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Material> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM materials WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM materials WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM materials WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
