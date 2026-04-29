package com.toir.repository;

import com.toir.enums.InventoryItemKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import com.toir.entity.SparePart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

@Repository
public interface SparePartRepository extends JpaRepository<SparePart, UUID> {
    java.util.Optional<SparePart> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<SparePart> findAllByIsDeletedFalse();

    java.util.List<SparePart> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

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
""")
    Page<SparePart> findAllByFilter(@Param("itemType") InventoryItemKind itemType,
                                    @Param("searchPattern") String searchPattern,
                                    Pageable pageable);
}
