package com.toir.dto.pprplanning;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import jakarta.validation.Valid;

import java.util.List;

public record CompletePprTaskRequest(
        Double actualLaborHours,
        List<@Valid RepairMaterialUsageDto> materialUsages
) {}
