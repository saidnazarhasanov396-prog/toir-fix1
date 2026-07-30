package com.toir.repository.maintenance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationFilter;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationStatsDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaintenanceRegulationReadRepository {

    Page<GeneralKey> findGeneral(MaintenanceRegulationFilter filter, Pageable pageable);

    MaintenanceRegulationStatsDto stats(MaintenanceRegulationFilter filter);

    Page<UUID> findEquipmentIds(MaintenanceRegulationFilter filter, Pageable pageable);

    List<EquipmentMatch> findEquipmentMatches(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentIds
    );

    Page<UUID> findEquipmentTypeIds(MaintenanceRegulationFilter filter, Pageable pageable);

    List<EquipmentTypeMatch> findEquipmentTypeMatches(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentTypeIds
    );

    Map<UUID, Integer> countMatchingEquipmentByType(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentTypeIds
    );

    enum DisplaySource {
        REGULATION,
        EQUIPMENT_RULE
    }

    record GeneralKey(
            DisplaySource source,
            UUID displayId
    ) {}

    record EquipmentMatch(
            UUID equipmentId,
            DisplaySource source,
            UUID displayId,
            boolean effectiveActive
    ) {}

    record EquipmentTypeMatch(
            UUID equipmentTypeId,
            DisplaySource source,
            UUID displayId,
            boolean effectiveActive
    ) {}

    record EquipmentTypeCount(
            UUID equipmentTypeId,
            int equipmentCount
    ) {}
}
