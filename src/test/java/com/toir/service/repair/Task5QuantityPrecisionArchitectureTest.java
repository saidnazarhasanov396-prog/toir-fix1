package com.toir.service.repair;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.dto.workorder.WorkOrderMaterialReadinessRowDto;
import com.toir.entity.StockMovement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.dto.warehouseanalytics.WarehouseReservationRowDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class Task5QuantityPrecisionArchitectureTest {
    @Test void materialQuantityAccessorsAndCalculationsNeverCrossBinaryFloatingPoint() throws Exception {
        assertThat(StockMovement.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(component(StockMovementDto.class,"quantity")).isEqualTo(BigDecimal.class);
        assertThat(component(StockMovementRequest.class,"quantity")).isEqualTo(BigDecimal.class);
        assertThat(component(WorkOrderMaterialReadinessRowDto.class,"requiredQty")).isEqualTo(BigDecimal.class);
        assertThat(component(WarehouseReservationRowDto.class,"quantity")).isEqualTo(BigDecimal.class);
        assertThat(RepairMaterialUsage.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(RepairMaterialReturn.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(MaintenanceTemplateSparePartRequirement.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(MaintenanceRegulationSparePartRequirement.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        for(String file:java.util.List.of("src/main/java/com/toir/service/StockMovementService.java","src/main/java/com/toir/service/ReservationService.java","src/main/java/com/toir/service/WorkOrderMaterialReadinessService.java")){
            String source=Files.readString(Path.of(file));
            assertThat(source).as(file).doesNotContain("doubleValue()","mapToDouble","BigDecimal.valueOf(");
        }
        String warehouse=Files.readString(Path.of("src/main/java/com/toir/service/WarehouseAnalyticsService.java"));
        assertThat(warehouse).doesNotContain("getQuantity().doubleValue()","mapToDouble(StockMovement::getQuantity)");
    }
    private Class<?> component(Class<?> type,String name){return Arrays.stream(type.getRecordComponents()).filter(c->c.getName().equals(name)).findFirst().orElseThrow().getType();}
}
