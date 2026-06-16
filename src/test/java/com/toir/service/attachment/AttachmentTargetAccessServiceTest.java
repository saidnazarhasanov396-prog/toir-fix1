package com.toir.service.attachment;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.AttachmentTargetType;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentTargetAccessServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    CompletionActRepository completionActRepository;

    @Mock
    ApprovalRequestRepository approvalRequestRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    private AttachmentTargetAccessService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentTargetAccessService(
                equipmentRepository,
                workOrderRepository,
                repairRequestRepository,
                completionActRepository,
                approvalRequestRepository,
                stockMovementRepository,
                warehouseRepository,
                scopeAccessService
        );
    }

    @Test
    void approvalParticipantCanAccessApprovalAttachmentsWithoutSupportedUnderlyingTarget() {
        UUID approvalId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ApprovalRequest approval = approvalWithTarget(ApprovalTargetType.PPR_PLAN);
        ApprovalStep step = new ApprovalStep();
        step.setApproverId(currentUserId);
        approval.setSteps(List.of(step));
        when(approvalRequestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(currentUserId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        service.assertCanAccess(AttachmentTargetType.APPROVAL, approvalId);

        verify(workOrderRepository, never()).findByIdAndIsDeletedFalse(approval.getTargetId());
        verify(repairRequestRepository, never()).findByIdAndIsDeletedFalse(approval.getTargetId());
    }

    @Test
    void unsupportedApprovalTargetDeniesWhenUserIsNotRequesterOrParticipant() {
        UUID approvalId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ApprovalRequest approval = approvalWithTarget(ApprovalTargetType.PPR_PLAN);
        when(approvalRequestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(currentUserId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanAccess(AttachmentTargetType.APPROVAL, approvalId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("approval scope");
    }

    private ApprovalRequest approvalWithTarget(ApprovalTargetType targetType) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setRequesterId(UUID.randomUUID());
        approval.setTargetType(targetType);
        approval.setTargetId(UUID.randomUUID());
        approval.setDocumentType(targetType.name());
        approval.setDocumentId(approval.getTargetId());
        return approval;
    }
}
