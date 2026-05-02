package com.toir.controller;

import com.toir.dto.defect.DefectDictionariesResponse;
import com.toir.dto.defectcategory.DefectCategoryDto;
import com.toir.dto.defectseverity.DefectSeverityDto;
import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.dto.rootcause.RootCauseDto;
import com.toir.repository.DefectCategoryRepository;
import com.toir.repository.DefectSeverityRepository;
import com.toir.repository.FailureReasonRepository;
import com.toir.repository.RootCauseRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/defects/dictionaries")
@Tag(name = "defects-dictionaries")
@RequiredArgsConstructor
public class DefectDictionariesController {

    private final DefectCategoryRepository categoryRepository;
    private final DefectSeverityRepository severityRepository;
    private final FailureReasonRepository failureReasonRepository;
    private final RootCauseRepository rootCauseRepository;



    @GetMapping
    public ResponseEntity<DefectDictionariesResponse> dictionaries() {
        return ResponseEntity.ok(new DefectDictionariesResponse(
                categoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(DefectCategoryDto::from).toList(),
                severityRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(DefectSeverityDto::from).toList(),
                failureReasonRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(FailureReasonDto::from).toList(),
                rootCauseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(RootCauseDto::from).toList()
        ));
    }
}
