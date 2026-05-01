package com.toir.dto.defect;

import com.toir.dto.defectcategory.DefectCategoryDto;
import com.toir.dto.defectseverity.DefectSeverityDto;
import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.dto.rootcause.RootCauseDto;

import java.util.List;

public record DefectDictionariesResponse(
        List<DefectCategoryDto> categories,
        List<DefectSeverityDto> severities,
        List<FailureReasonDto> failureReasons,
        List<RootCauseDto> rootCauses
) {
}
