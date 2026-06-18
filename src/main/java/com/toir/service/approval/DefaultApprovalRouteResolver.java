package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.repository.ApprovalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultApprovalRouteResolver implements ApprovalRouteResolver {

    private final ApprovalTemplateRepository templateRepository;

    @Override
    public List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request) {
        if (request == null || request.getTargetType() == null) {
            return List.of();
        }
        return templateRepository
                .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(request.getTargetType())
                .map(this::stepsFromTemplate)
                .orElse(List.of());
    }

    private List<CreateApprovalRequest.StepInput> stepsFromTemplate(ApprovalTemplate template) {
        ApprovalRoutePolicy policy = template.getRoutePolicy();
        if (policy == ApprovalRoutePolicy.USER_BASED && template.getApproverId() != null) {
            return List.of(new CreateApprovalRequest.StepInput(template.getApproverId(), null));
        }
        if (template.getApproverId() != null) {
            return List.of(new CreateApprovalRequest.StepInput(template.getApproverId(), template.getApproverRole()));
        }
        String routeRole = switch (policy) {
            case SYSTEM_ADMIN -> "SYSTEM_ADMIN";
            case DEPARTMENT_HEAD -> "DEPARTMENT_HEAD";
            case ROLE_BASED, USER_BASED -> template.getApproverRole();
        };
        if (!StringUtils.hasText(routeRole)) {
            return List.of();
        }
        return List.of(new CreateApprovalRequest.StepInput(null, routeRole.trim()));
    }
}
