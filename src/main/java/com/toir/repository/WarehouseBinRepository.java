package com.toir.repository;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {
    Optional<WarehouseBin> findByIdAndIsDeletedFalse(UUID id);

    List<WarehouseBin> findAllByWarehouseIdAndIsDeletedFalseOrderByTravelSequenceAscCodeAsc(UUID warehouseId);

    @Query("""
            select b
            from WarehouseBin b
            where b.warehouseId = :warehouseId
              and b.isDeleted = false
              and (cast(:search as string) is null
                   or lower(coalesce(b.code, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.zone, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.aisle, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.rack, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.shelfLevel, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.binType, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(b.barcode, '')) like lower(concat('%', cast(:search as string), '%')))
              and (cast(:zone as string) is null or lower(coalesce(b.zone, '')) = lower(cast(:zone as string)))
              and (cast(:aisle as string) is null or lower(coalesce(b.aisle, '')) = lower(cast(:aisle as string)))
              and (cast(:rack as string) is null or lower(coalesce(b.rack, '')) = lower(cast(:rack as string)))
              and (cast(:shelfLevel as string) is null or lower(coalesce(b.shelfLevel, '')) = lower(cast(:shelfLevel as string)))
              and (cast(:binType as string) is null or lower(coalesce(b.binType, '')) = lower(cast(:binType as string)))
              and (:qualityZoneType is null or b.qualityZoneType = :qualityZoneType)
              and (cast(:temperatureZone as string) is null or lower(coalesce(b.temperatureZone, '')) = lower(cast(:temperatureZone as string)))
              and (cast(:hazardClass as string) is null or lower(coalesce(b.hazardClass, '')) = lower(cast(:hazardClass as string)))
              and (:active is null or b.active = :active)
              and (:blocked is null or b.blocked = :blocked)
              and (:frozen is null or b.frozen = :frozen)
              and (:binLevel is null or b.binLevel = :binLevel)
            order by b.travelSequence asc, b.code asc
            """)
    Page<WarehouseBin> search(@Param("warehouseId") UUID warehouseId,
                              @Param("search") String search,
                              @Param("zone") String zone,
                              @Param("aisle") String aisle,
                              @Param("rack") String rack,
                              @Param("shelfLevel") String shelfLevel,
                              @Param("binType") String binType,
                              @Param("qualityZoneType") WarehouseQualityZoneType qualityZoneType,
                              @Param("temperatureZone") String temperatureZone,
                              @Param("hazardClass") String hazardClass,
                              @Param("active") Boolean active,
                              @Param("blocked") Boolean blocked,
                              @Param("frozen") Boolean frozen,
                              @Param("binLevel") Integer binLevel,
                              Pageable pageable);

    boolean existsByWarehouseIdAndCodeIgnoreCaseAndIsDeletedFalse(UUID warehouseId, String code);

    boolean existsByBarcodeIgnoreCaseAndIsDeletedFalse(String barcode);
}
