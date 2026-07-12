package com.toir.service.repair;

import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.dto.workorder.WorkOrderSparePartRequirementRequest;
import com.toir.entity.Reservation;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignMaterialPrecisionContractTest {
    @Test
    void reservationAndWorkOrderRequirementUseBigDecimalWithoutBinaryConversion() throws Exception {
        assertThat(Reservation.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(WorkOrderSparePartRequirement.class.getDeclaredField("requiredQty").getType()).isEqualTo(BigDecimal.class);
        assertThat(ReservationRequest.class.getRecordComponents()[12].getType()).isEqualTo(BigDecimal.class);
        assertThat(ReservationDto.class.getRecordComponents()[13].getType()).isEqualTo(BigDecimal.class);
        assertThat(WorkOrderSparePartRequirementRequest.class.getRecordComponents()[1].getType()).isEqualTo(BigDecimal.class);
        assertThat(java.util.Arrays.stream(WorkOrderSparePartRequirementDto.class.getRecordComponents())
                .filter(component -> component.getName().equals("requiredQty")).findFirst().orElseThrow().getType())
                .isEqualTo(BigDecimal.class);
        assertThat(WorkOrderSparePartRequirement.class.getDeclaredField("campaignRequirementId").getType())
                .isEqualTo(java.util.UUID.class);
        assertThat(WorkOrderSparePartRequirementSourceType.valueOf("REPAIR_CAMPAIGN_WORK_ITEM")).isNotNull();
    }
}
