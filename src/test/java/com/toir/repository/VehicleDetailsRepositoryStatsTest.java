package com.toir.repository;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.VehicleStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class VehicleDetailsRepositoryStatsTest {

    @Autowired
    VehicleDetailsRepository repository;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Test
    void getVehicleStatsWithoutFiltersCountsVehicleEquipmentByStatus() {
        UUID departmentId = UUID.randomUUID();

        Equipment active1 = saveVehicle("VEH-001", "Kamaz 1", "AA001", EquipmentStatus.ACTIVE, departmentId);
        Equipment active2 = saveVehicle("VEH-002", "Kamaz 2", "AA002", EquipmentStatus.ACTIVE, departmentId);
        Equipment inRepair = saveVehicle("VEH-003", "Kamaz 3", "AA003", EquipmentStatus.IN_REPAIR, departmentId);
        Equipment outOfService = saveVehicle("VEH-004", "Kamaz 4", "AA004", EquipmentStatus.OUT_OF_SERVICE, departmentId);

        saveDetails(active1.getId(), "01A001AA", "VIN001", "Kamaz", "65115");
        saveDetails(active2.getId(), "01A002AA", "VIN002", "Kamaz", "65115");
        saveDetails(inRepair.getId(), "01A003AA", "VIN003", "Kamaz", "5511");
        saveDetails(outOfService.getId(), "01A004AA", "VIN004", "Kamaz", "Old");

        saveEquipment(
                "EQ-001",
                "Pump",
                "INV-PUMP-001",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId
        );

        VehicleStatsProjection stats = repository.getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );

        assertThat(stats.getTotal()).isEqualTo(4);
        assertThat(stats.getActive()).isEqualTo(2);
        assertThat(stats.getInRepair()).isEqualTo(1);
        assertThat(stats.getOutOfService()).isEqualTo(1);
    }

    @Test
    void getVehicleStatsWithDepartmentAndSearchCountsOnlyMatchingVehicles() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();

        Equipment targetActive = saveVehicle("VEH-KAMAZ-001", "Kamaz Truck", "INV-KAMAZ-001", EquipmentStatus.ACTIVE, targetDepartmentId);
        Equipment targetRepair = saveVehicle("VEH-KAMAZ-002", "Repair Truck", "INV-KAMAZ-002", EquipmentStatus.IN_REPAIR, targetDepartmentId);

        Equipment otherDepartment = saveVehicle("VEH-KAMAZ-003", "Other Department Kamaz", "INV-KAMAZ-003", EquipmentStatus.ACTIVE, otherDepartmentId);
        Equipment nonMatchingSearch = saveVehicle("VEH-BUS-001", "Bus", "INV-BUS-001", EquipmentStatus.OUT_OF_SERVICE, targetDepartmentId);

        saveDetails(targetActive.getId(), "01K001AA", "VIN-KAMAZ-001", "Kamaz", "65115");
        saveDetails(targetRepair.getId(), "01K002AA", "VIN-KAMAZ-002", "Kamaz", "5511");
        saveDetails(otherDepartment.getId(), "01K003AA", "VIN-KAMAZ-003", "Kamaz", "4310");
        saveDetails(nonMatchingSearch.getId(), "01B001AA", "VIN-BUS-001", "Mercedes", "Sprinter");

        VehicleStatsProjection stats = repository.getVehicleStats(
                targetDepartmentId,
                EquipmentCategory.VEHICLE,
                "%kamaz%",
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );

        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getActive()).isEqualTo(1);
        assertThat(stats.getInRepair()).isEqualTo(1);
        assertThat(stats.getOutOfService()).isZero();
    }

    private Equipment saveVehicle(
            String code,
            String name,
            String inventoryNumber,
            EquipmentStatus status,
            UUID departmentId
    ) {
        return saveEquipment(
                code,
                name,
                inventoryNumber,
                status,
                EquipmentCategory.VEHICLE,
                departmentId
        );
    }

    private Equipment saveEquipment(
            String code,
            String name,
            String inventoryNumber,
            EquipmentStatus status,
            EquipmentCategory category,
            UUID departmentId
    ) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName(name);
        equipment.setInventoryNumber(inventoryNumber);
        equipment.setTechnicalNumber("TN-" + code);
        equipment.setSerialNumber("SN-" + code);
        equipment.setModel("Model " + code);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(status);
        equipment.setCategory(category);
        equipment.setManufacturer("Manufacturer " + code);
        equipment.setDeleted(false);

        return equipmentRepository.save(equipment);
    }

    private VehicleDetails saveDetails(
            UUID equipmentId,
            String plateNumber,
            String vin,
            String brand,
            String model
    ) {
        VehicleDetails details = new VehicleDetails();
        details.setId(UUID.randomUUID());
        details.setEquipmentId(equipmentId);
        details.setPlateNumber(plateNumber);
        details.setVin(vin);
        details.setBrand(brand);
        details.setModel(model);
        details.setVehicleType(VehicleType.values()[0]);
        details.setCurrentOdometerKm(0);
        details.setCurrentEngineHours(0);
        details.setDeleted(false);

        return repository.save(details);
    }
}