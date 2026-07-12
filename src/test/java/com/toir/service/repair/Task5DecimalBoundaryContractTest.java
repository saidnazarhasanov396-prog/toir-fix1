package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.warehouse.WarehouseQualityTransferHistoryDto;
import com.toir.dto.warehouse.WarehouseStockMoveJournalDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnRequest;
import com.toir.entity.warehouse.RepairMaterialReturn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Task5DecimalBoundaryContractTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @Test void usageRejectsNumericJsonAndEmitsQuantityAndTotalCostAsStrings()throws Exception{
        UUID warehouse=UUID.randomUUID(),spare=UUID.randomUUID();
        String numeric="{\"warehouseId\":\""+warehouse+"\",\"sparePartId\":\""+spare+"\",\"quantity\":1.25,\"unitCost\":2.0}";
        assertThatThrownBy(()->mapper.readValue(numeric,RepairMaterialUsageDto.class)).hasMessageContaining("canonical JSON decimal string");
        var dto=mapper.readValue(numeric.replace("1.25","\"1.2500\""),RepairMaterialUsageDto.class);
        String json=mapper.writeValueAsString(dto);
        assertThat(mapper.readTree(json).get("quantity").isTextual()).isTrue();
        assertThat(mapper.readTree(json).get("totalCost").isTextual()).isTrue();
        assertThat(mapper.readTree(json).get("totalCost").textValue()).isEqualTo("2.50000");
    }

    @Test void returnRejectsNumericJsonAndAllReturnAndJournalOutputsAreStrings()throws Exception{
        String numeric="{\"quantity\":2.5,\"reason\":\"unused\"}";
        assertThatThrownBy(()->mapper.readValue(numeric,WorkOrderMaterialReturnRequest.class)).hasMessageContaining("canonical JSON decimal string");
        assertThat(mapper.readValue(numeric.replace("2.5","\"2.5000\""),WorkOrderMaterialReturnRequest.class).quantity()).isEqualByComparingTo("2.5000");
        RepairMaterialReturn entity=new RepairMaterialReturn();entity.setQuantity(new BigDecimal("2.5000"));
        assertTextQuantity(WorkOrderMaterialReturnDto.from(entity));
        assertTextQuantity(new WarehouseQualityTransferHistoryDto(null,null,null,null,null,null,null,null,null,null,new BigDecimal("2.5000"),null,null,null,null,null,null,null));
        assertTextQuantity(new WarehouseStockMoveJournalDto(null,null,null,null,null,null,null,null,null,null,null,new BigDecimal("2.5000"),null,null,null,null,null,null,null));
    }

    private void assertTextQuantity(Object value)throws Exception{
        assertThat(mapper.readTree(mapper.writeValueAsString(value)).get("quantity").isTextual()).isTrue();
    }
}
