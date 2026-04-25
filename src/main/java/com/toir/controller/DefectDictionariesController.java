package com.toir.controller;

import com.toir.repository.DefectCategoryRepository;
import com.toir.dto.defectcategory.DefectCategoryDto;
import com.toir.repository.DefectSeverityRepository;
import com.toir.dto.defectseverity.DefectSeverityDto;
import com.toir.repository.FailureReasonRepository;
import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.repository.RootCauseRepository;
import com.toir.dto.rootcause.RootCauseDto;
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
                "categories", categoryRepository.findAllByIsDeletedFalse().stream().map(DefectCategoryDto::from).toList(),
                "severities", severityRepository.findAllByIsDeletedFalse().stream().map(DefectSeverityDto::from).toList(),
                "failureReasons", failureReasonRepository.findAllByIsDeletedFalse().stream().map(FailureReasonDto::from).toList(),
                "rootCauses", rootCauseRepository.findAllByIsDeletedFalse().stream().map(RootCauseDto::from).toList()
        );
    }
}
