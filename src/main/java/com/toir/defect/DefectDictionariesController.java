package com.toir.defect;

import com.toir.defectcategory.DefectCategoryRepository;
import com.toir.defectcategory.dto.DefectCategoryDto;
import com.toir.defectseverity.DefectSeverityRepository;
import com.toir.defectseverity.dto.DefectSeverityDto;
import com.toir.failurereason.FailureReasonRepository;
import com.toir.failurereason.dto.FailureReasonDto;
import com.toir.rootcause.RootCauseRepository;
import com.toir.rootcause.dto.RootCauseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/defects/dictionaries")
@Tag(name = "defects-dictionaries")
public class DefectDictionariesController {

    private final DefectCategoryRepository categoryRepository;
    private final DefectSeverityRepository severityRepository;
    private final FailureReasonRepository failureReasonRepository;
    private final RootCauseRepository rootCauseRepository;

    public DefectDictionariesController(DefectCategoryRepository categoryRepository,
                                        DefectSeverityRepository severityRepository,
                                        FailureReasonRepository failureReasonRepository,
                                        RootCauseRepository rootCauseRepository) {
        this.categoryRepository = categoryRepository;
        this.severityRepository = severityRepository;
        this.failureReasonRepository = failureReasonRepository;
        this.rootCauseRepository = rootCauseRepository;
    }

    @GetMapping
    public Map<String, Object> dictionaries() {
        return Map.of(
                "categories", categoryRepository.findAll().stream().map(DefectCategoryDto::from).toList(),
                "severities", severityRepository.findAll().stream().map(DefectSeverityDto::from).toList(),
                "failureReasons", failureReasonRepository.findAll().stream().map(FailureReasonDto::from).toList(),
                "rootCauses", rootCauseRepository.findAll().stream().map(RootCauseDto::from).toList()
        );
    }
}
