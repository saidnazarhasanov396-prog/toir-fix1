package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultApprovalActionExecutor implements ApprovalActionExecutor {

    private final List<ApprovalActionHandler> handlers;

    @Override
    public String execute(ApprovalRequest request) {
        if (request == null || request.getActionType() == null) {
            return null;
        }
        ApprovalTargetType targetType = resolveTargetType(request);
        return handlers.stream()
                .filter(handler -> handler.supports(targetType, request.getActionType()))
                .findFirst()
                .map(handler -> handler.execute(request))
                .orElseThrow(() -> RestException.conflict(
                        "No approval action handler registered for "
                                + targetType + " / " + request.getActionType()));
    }

    private ApprovalTargetType resolveTargetType(ApprovalRequest request) {
        ApprovalTargetType documentTargetType = ApprovalTargetType.fromDocumentType(request.getDocumentType());
        if (documentTargetType != null && documentTargetType != ApprovalTargetType.OTHER) {
            return documentTargetType;
        }
        return request.getTargetType();
    }
}
