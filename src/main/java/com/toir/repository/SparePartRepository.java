package com.toir.repository;

import com.toir.entity.SparePart;
import com.toir.enums.InventoryItemKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartRepository extends JpaRepository<SparePart, UUID> {
    @Query(value = "SELECT * FROM spare_parts WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<SparePart> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM spare_parts WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<SparePart> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM spare_parts WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<SparePart> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM spare_parts WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM spare_parts WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM spare_parts WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
        select sp from SparePart sp where (:itemType is null or sp.kind = :itemType)
        and (:searchPattern is null
            or lower(sp.code) like :searchPattern
            or lower(sp.name) like :searchPattern
            or lower(sp.manufacturer) like :searchPattern
            or lower(sp.sku) like :searchPattern
            or lower(sp.specification) like :searchPattern)
        and sp.isDeleted = false
        order by sp.updatedAt desc
""")
    Page<SparePart> findAllByFilter(@Param("itemType") InventoryItemKind itemType,
                                    @Param("searchPattern") String searchPattern,
                                    Pageable pageable);
}
