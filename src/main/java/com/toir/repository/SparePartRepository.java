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

    @Query(value = "SELECT COUNT(*) > 0 FROM spare_parts WHERE type_id = :typeId AND is_deleted = false", nativeQuery = true)
    boolean existsByTypeIdAndIsDeletedFalse(@Param("typeId") UUID typeId);

    @Query(value = """
            select sp.type.id as typeId, count(sp.id) as sparePartCount
            from SparePart sp
            where sp.isDeleted = false
              and sp.kind = :kind
              and sp.type.id in :typeIds
            group by sp.type.id
            """)
    List<SparePartTypeCountProjection> countActiveByTypeIdsAndKind(
            @Param("typeIds") Collection<UUID> typeIds,
            @Param("kind") InventoryItemKind kind
    );

    @Query(value = """
            SELECT sp.type_id                    AS typeId,
                   COALESCE(SUM(wsb.qty_on_hand), 0) AS sparePartStockCount
            FROM spare_parts sp
            LEFT JOIN warehouse_stock_balances wsb
                   ON wsb.spare_part_id = sp.id
                  AND wsb.is_deleted = false
            WHERE sp.type_id IN :typeIds
              AND sp.is_deleted = false
            GROUP BY sp.type_id
            """, nativeQuery = true)
    List<SparePartTypeCountProjection> countStockByTypeIds(
            @Param("typeIds") Collection<UUID> typeIds
    );

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM spare_parts
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = """
        select sp from SparePart sp where (:itemType is null or sp.kind = :itemType)
        and (:typeId is null or sp.type.id = :typeId)
        and (:unitId is null or exists (
            select 1 from UnitOfMeasurement uom
            where uom.id = :unitId
              and uom.isDeleted = false
              and (
                lower(sp.unit) = lower(uom.code)
                or lower(sp.unit) = lower(uom.name)
                or (uom.nameEn is not null and lower(sp.unit) = lower(uom.nameEn))
                or (uom.nameUz is not null and lower(sp.unit) = lower(uom.nameUz))
              )
        ))
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
                                    @Param("typeId") UUID typeId,
                                    @Param("unitId") UUID unitId,
                                    @Param("searchPattern") String searchPattern,
                                    Pageable pageable);

    @Query(value = """
        select sp from SparePart sp where (:itemType is null or sp.kind = :itemType)
        and (:typeId is null or sp.type.id = :typeId)
        and (:unitId is null or exists (
            select 1 from UnitOfMeasurement uom
            where uom.id = :unitId
              and uom.isDeleted = false
              and (
                lower(sp.unit) = lower(uom.code)
                or lower(sp.unit) = lower(uom.name)
                or (uom.nameEn is not null and lower(sp.unit) = lower(uom.nameEn))
                or (uom.nameUz is not null and lower(sp.unit) = lower(uom.nameUz))
              )
        ))
        and (:searchPattern is null
            or lower(sp.code) like :searchPattern
            or lower(sp.name) like :searchPattern
            or lower(sp.manufacturer) like :searchPattern
            or lower(sp.sku) like :searchPattern
            or lower(sp.specification) like :searchPattern)
        and sp.isDeleted = false
        and exists (
            select 1
            from WarehouseStock ws
            where ws.sparePartId = sp.id
              and ws.warehouseId = :warehouseId
              and ws.isDeleted = false
        )
        order by sp.updatedAt desc
""")
    Page<SparePart> findAllByFilterAndWarehouseId(@Param("itemType") InventoryItemKind itemType,
                                                  @Param("typeId") UUID typeId,
                                                  @Param("unitId") UUID unitId,
                                                  @Param("searchPattern") String searchPattern,
                                                  @Param("warehouseId") UUID warehouseId,
                                                  Pageable pageable);

    @Query(value = """
        select sp from SparePart sp where (:itemType is null or sp.kind = :itemType)
        and (:typeId is null or sp.type.id = :typeId)
        and (:unitId is null or exists (
            select 1 from UnitOfMeasurement uom
            where uom.id = :unitId
              and uom.isDeleted = false
              and (
                lower(sp.unit) = lower(uom.code)
                or lower(sp.unit) = lower(uom.name)
                or (uom.nameEn is not null and lower(sp.unit) = lower(uom.nameEn))
                or (uom.nameUz is not null and lower(sp.unit) = lower(uom.nameUz))
              )
        ))
        and (:searchPattern is null
            or lower(sp.code) like :searchPattern
            or lower(sp.name) like :searchPattern
            or lower(sp.manufacturer) like :searchPattern
            or lower(sp.sku) like :searchPattern
            or lower(sp.specification) like :searchPattern)
        and sp.isDeleted = false
        and exists (
            select 1
            from WarehouseStock ws
            where ws.sparePartId = sp.id
              and ws.warehouseId in :warehouseIds
              and ws.isDeleted = false
        )
        order by sp.updatedAt desc
""")
    Page<SparePart> findAllByFilterAndWarehouseIds(@Param("itemType") InventoryItemKind itemType,
                                                   @Param("typeId") UUID typeId,
                                                   @Param("unitId") UUID unitId,
                                                   @Param("searchPattern") String searchPattern,
                                                   @Param("warehouseIds") Collection<UUID> warehouseIds,
                                                   Pageable pageable);
}
