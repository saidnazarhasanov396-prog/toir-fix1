package com.toir.service.repair;

import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestClarificationRequest;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.enums.UserStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestStatsProjection;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.NotificationService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairRequestServiceTest {

    @Mock
    RepairRequestRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    NotificationService notificationService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @InjectMocks
    RepairRequestService service;

    @Test
    void createLinksRepairRequestToDefect() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(defectId, equipmentId);
        Defect defect = defect(defectId, equipmentId);

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of(defect));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.id()).isEqualTo(savedRequestId);
        assertThat(result.linkedDefects()).hasSize(1);
        assertThat(result.linkedDefects().getFirst().id()).isEqualTo(defectId);
        assertThat(defect.getRepairRequestId()).isEqualTo(savedRequestId);
        verify(defectRepository).save(defect);
    }

    @Test
    void createWithDefectIdDoesNotLinkAnotherDefect() {
        UUID requestedDefectId = UUID.randomUUID();
        UUID otherDefectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(requestedDefectId, equipmentId);
        Defect requestedDefect = defect(requestedDefectId, equipmentId);
        Defect otherDefect = defect(otherDefectId, equipmentId);

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(requestedDefectId)).thenReturn(Optional.of(requestedDefect));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of(requestedDefect));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.linkedDefects())
                .extracting(DefectBriefDto::id)
                .containsExactly(requestedDefectId)
                .doesNotContain(otherDefectId);
        assertThat(requestedDefect.getRepairRequestId()).isEqualTo(savedRequestId);
        assertThat(otherDefect.getRepairRequestId()).isNull();
        verify(defectRepository).findByIdAndIsDeletedFalse(requestedDefectId);
        verify(defectRepository, never()).findByIdAndIsDeletedFalse(otherDefectId);
    }

    @Test
    void createWithoutDefectIdSucceedsWithoutDefectAssociation() {
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(null, equipmentId);

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.id()).isEqualTo(savedRequestId);
        assertThat(result.linkedDefects()).isEmpty();
        verify(defectRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void createWithInlineDefectCreatesAndLinksDefect() {
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequestWithInlineDefect(equipmentId, "Bearing wear", "Noise from bearing");

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        when(defectRepository.maxSequenceByCodePrefix("DEF-2026-")).thenReturn(0L);
        when(defectRepository.existsByCode("DEF-2026-0001")).thenReturn(false);
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenAnswer(invocation -> List.of(defect(savedRequestId)));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.id()).isEqualTo(savedRequestId);
        verify(defectRepository).save(org.mockito.ArgumentMatchers.argThat(defect ->
                savedRequestId.equals(defect.getRepairRequestId())
                        && equipmentId.equals(defect.getEquipmentId())
                        && "Bearing wear".equals(defect.getTitle())
                        && "Noise from bearing".equals(defect.getDescription())
                        && "DEF-2026-0001".equals(defect.getCode())
        ));
    }

    @Test
    void createIgnoresEmptyInlineDefectObject() {
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequestWithInlineDefect(equipmentId, "   ", " ", null, null);

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.linkedDefects()).isEmpty();
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void createWithEmptyInlineDefectsArraySucceedsWithoutDefectAssociation() {
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequestWithInlineDefects(equipmentId, List.of());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        stubNameLookups(repairRequestForCreate(savedRequestId, request));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(savedRequestId))
                .thenReturn(List.of());

        RepairRequestDto result = service.create(request);

        assertThat(result.linkedDefects()).isEmpty();
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void createRejectsStartedInlineDefectWithoutDescription() {
        UUID equipmentId = UUID.randomUUID();
        RepairRequestRequest request = createRequestWithInlineDefect(equipmentId, "Bearing wear", " ");

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("Defect description is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDefectIdWithInlineDefects() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        RepairRequestRequest request = new RepairRequestRequest(
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration on pump",
                defectId,
                null,
                List.of(new RepairRequestRequest.InlineDefectRequest(
                        "Bearing wear",
                        "Noise from bearing",
                        "Mechanical",
                        "HIGH",
                        null,
                        null
                )),
                equipmentId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestSource.MANUAL,
                null
        );

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("Use either defectId or inline defects, not both");
        verify(repository, never()).save(any());
        verify(defectRepository, never()).findByIdAndIsDeletedFalse(defectId);
    }

    @Test
    void createPropagatesInlineDefectSaveFailureForTransactionalRollback() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID savedRequestId = UUID.randomUUID();
        RepairRequestRequest request = createRequestWithInlineDefect(equipmentId, "Bearing wear", "Noise from bearing");

        Method createMethod = RepairRequestService.class.getMethod("create", RepairRequestRequest.class);
        assertThat(createMethod.getAnnotation(Transactional.class)).isNotNull();

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", savedRequestId);
            return saved;
        });
        when(defectRepository.maxSequenceByCodePrefix("DEF-2026-")).thenReturn(0L);
        when(defectRepository.existsByCode("DEF-2026-0001")).thenReturn(false);
        when(defectRepository.save(any(Defect.class))).thenThrow(new RuntimeException("defect save failed"));

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("defect save failed");
    }

    @Test
    void createRejectsUnknownDefect() {
        UUID defectId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(defectId, UUID.randomUUID());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("Defect not found");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDefectForDifferentEquipment() {
        UUID defectId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(defectId, UUID.randomUUID());
        Defect defect = defect(defectId, UUID.randomUUID());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("different equipment");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsTerminalDefect() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(defectId, equipmentId);
        Defect defect = defect(defectId, equipmentId);
        defect.setStatus(DefectStatus.CLOSED);

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("terminal defect");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsAlreadyLinkedDefect() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequestRequest request = createRequest(defectId, equipmentId);
        Defect defect = defect(defectId, equipmentId);
        defect.setRepairRequestId(UUID.randomUUID());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("already belongs to a repair request");
        verify(repository, never()).save(any());
    }

    @Test
    void findAllFiltersByEquipmentId() {
        UUID equipmentId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, null, equipmentId, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        Page<RepairRequestDto> result = service.search(null, null, equipmentId, null, 0, 20, null);

        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(null), eq(equipmentId), eq(null), eq(null), eq(pageRequest));
    }

    @Test
    void findAllFiltersByEquipmentIdAndStatus() {
        UUID equipmentId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(RequestStatus.APPROVED.name(), null, equipmentId, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        Page<RepairRequestDto> result = service.search(RequestStatus.APPROVED, null, equipmentId, null, 0, 20, null);

        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(
                eq(RequestStatus.APPROVED.name()),
                eq(null),
                eq(equipmentId),
                eq(null),
                eq(null),
                eq(pageRequest)
        );
    }

    @Test
    void findAllWithoutEquipmentIdKeepsExistingBehavior() {
        UUID departmentId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, departmentId, null, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        Page<RepairRequestDto> result = service.search(null, departmentId, null, null, 0, 20, null);

        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(departmentId), eq(null), eq(null), eq(null), eq(pageRequest));
    }

    @Test
    void findAllNormalizesBlankSearchToNull() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, null, null, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        Page<RepairRequestDto> result = service.search(null, null, null, null, 0, 20, "   ");

        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(null), eq(null), eq(null), eq(null), eq(pageRequest));
    }

    @Test
    void requestClarificationSetsNeedsClarificationAndClarificationReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("old rejection");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.requestClarification(id, "Need serial number");

        assertThat(result.status()).isEqualTo(RequestStatus.NEEDS_CLARIFICATION);
        assertThat(result.clarificationReason()).isEqualTo("Need serial number");
    }

    @Test
    void requestClarificationNotifiesReporterWhenRecipientIsNotSelected() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);

        stubFindSaveAndDtoLookups(id, entity);

        service.requestClarification(id, "Need serial number");

        verify(notificationService).notifyUser(
                eq(entity.getReporterId()),
                org.mockito.ArgumentMatchers.contains("Clarification requested"),
                org.mockito.ArgumentMatchers.contains("Need serial number"),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("RepairRequest"),
                eq(id.toString())
        );
    }

    @Test
    void requestClarificationUsesSelectedRecipientFromPayload() {
        UUID id = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        User recipient = new User();
        recipient.setId(recipientId);

        stubFindSaveAndDtoLookups(id, entity);
        when(userRepository.findByIdAndIsDeletedFalse(recipientId)).thenReturn(Optional.of(recipient));

        RepairRequestDto result = service.requestClarification(
                id,
                new RepairRequestClarificationRequest(recipientId, "Need oil pressure trend", "APPROVAL")
        );

        assertThat(result.status()).isEqualTo(RequestStatus.NEEDS_CLARIFICATION);
        assertThat(result.clarificationReason()).isEqualTo("Need oil pressure trend");
        verify(notificationService).notifyUser(
                eq(recipientId),
                org.mockito.ArgumentMatchers.contains("Clarification requested"),
                org.mockito.ArgumentMatchers.contains("Need oil pressure trend"),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("RepairRequest"),
                eq(id.toString())
        );
    }

    @Test
    void requestClarificationRejectsSelectedRecipientThatDoesNotExist() {
        UUID id = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(userRepository.findByIdAndIsDeletedFalse(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestClarification(
                id,
                new RepairRequestClarificationRequest(recipientId, "Need oil pressure trend", "APPROVAL")
        )).hasMessageContaining("Clarification recipient not found");

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestClarificationClearsRejectionReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("must be cleared");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.requestClarification(id, "Need more photos");

        assertThat(result.rejectionReason()).isNull();
        assertThat(entity.getRejectionReason()).isNull();
    }

    @Test
    void requestClarificationBlocksTerminalStatuses() {
        for (RequestStatus status : List.of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED, RequestStatus.COMPLETED)) {
            UUID id = UUID.randomUUID();
            RepairRequest entity = repairRequest(id);
            entity.setStatus(status);
            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.requestClarification(id, "Need more photos"))
                    .hasMessageContaining("Cannot request clarification");
        }
    }

    @Test
    void approveAllowsReviewableStatusesOnly() {
        for (RequestStatus status : List.of(
                RequestStatus.OPEN,
                RequestStatus.REGISTERED,
                RequestStatus.IN_REVIEW,
                RequestStatus.NEEDS_CLARIFICATION
        )) {
            UUID id = UUID.randomUUID();
            RepairRequest entity = repairRequest(id);
            entity.setStatus(status);
            stubFindSaveAndDtoLookups(id, entity);

            RepairRequestDto result = service.approve(id);

            assertThat(result.status()).isEqualTo(RequestStatus.APPROVED);
        }
    }

    @Test
    void approveBlocksInvalidAndTerminalStatuses() {
        for (RequestStatus status : List.of(
                RequestStatus.DRAFT,
                RequestStatus.APPROVED,
                RequestStatus.ASSIGNED,
                RequestStatus.IN_PROGRESS,
                RequestStatus.COMPLETED,
                RequestStatus.REJECTED,
                RequestStatus.CLOSED,
                RequestStatus.CANCELLED
        )) {
            UUID id = UUID.randomUUID();
            RepairRequest entity = repairRequest(id);
            entity.setStatus(status);
            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.approve(id))
                    .hasMessageContaining("Cannot approve");
        }
    }

    @Test
    void rejectSetsRejectedAndRejectionReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.reject(id, "Safety violation");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Safety violation");
    }

    @Test
    void rejectClearsClarificationReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setClarificationReason("must be cleared");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.reject(id, "Invalid request");

        assertThat(result.clarificationReason()).isNull();
        assertThat(entity.getClarificationReason()).isNull();
    }

    @Test
    void rejectBlocksInvalidAndTerminalStatuses() {
        for (RequestStatus status : List.of(
                RequestStatus.DRAFT,
                RequestStatus.APPROVED,
                RequestStatus.ASSIGNED,
                RequestStatus.IN_PROGRESS,
                RequestStatus.COMPLETED,
                RequestStatus.REJECTED,
                RequestStatus.CLOSED,
                RequestStatus.CANCELLED
        )) {
            UUID id = UUID.randomUUID();
            RepairRequest entity = repairRequest(id);
            entity.setStatus(status);
            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.reject(id, "Invalid request"))
                    .hasMessageContaining("Cannot reject");
        }
    }

    @Test
    void assignRequiresApprovedStatus() {
        UUID id = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.APPROVED);
        User assignee = new User();
        assignee.setId(assigneeId);
        assignee.setStatus(UserStatus.ACTIVE);

        stubFindSaveAndDtoLookups(id, entity);
        when(userRepository.findByIdAndIsDeletedFalse(assigneeId)).thenReturn(Optional.of(assignee));

        RepairRequestDto result = service.assign(id, assigneeId);

        assertThat(result.status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(result.assignedToId()).isEqualTo(assigneeId);
        verify(notificationService).notifyUser(
                eq(assigneeId),
                org.mockito.ArgumentMatchers.contains("Repair request assigned"),
                eq("Sizga ushbu qurilma bo'yicha ta'mirlash vazifasi biriktirildi."),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("RepairRequest"),
                eq(id.toString())
        );
    }

    @Test
    void assignRejectsUnknownAssignee() {
        UUID id = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.APPROVED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(userRepository.findByIdAndIsDeletedFalse(assigneeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(id, assigneeId))
                .hasMessageContaining("Assignee not found");

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignRejectsInactiveAssignee() {
        UUID id = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.APPROVED);
        User assignee = new User();
        assignee.setId(assigneeId);
        assignee.setStatus(UserStatus.INACTIVE);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(userRepository.findByIdAndIsDeletedFalse(assigneeId)).thenReturn(Optional.of(assignee));

        assertThatThrownBy(() -> service.assign(id, assigneeId))
                .hasMessageContaining("Assignee is inactive");

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignBlocksAllNonApprovedStatuses() {
        for (RequestStatus status : List.of(
                RequestStatus.DRAFT,
                RequestStatus.OPEN,
                RequestStatus.REGISTERED,
                RequestStatus.IN_REVIEW,
                RequestStatus.NEEDS_CLARIFICATION,
                RequestStatus.ASSIGNED,
                RequestStatus.IN_PROGRESS,
                RequestStatus.COMPLETED,
                RequestStatus.REJECTED,
                RequestStatus.CLOSED,
                RequestStatus.CANCELLED
        )) {
            UUID id = UUID.randomUUID();
            RepairRequest entity = repairRequest(id);
            entity.setStatus(status);
            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.assign(id, UUID.randomUUID()))
                    .hasMessageContaining("Cannot assign");
        }
    }

    @Test
    void genericStatusChangeRequiresAdminOverride() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.changeStatus(id, RequestStatus.CLOSED, "manual correction"))
                .hasMessageContaining("Only SYSTEM_ADMIN");

        verify(repository, never()).save(any());
    }

    @Test
    void genericStatusChangeRequiresOverrideReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(id, RequestStatus.CLOSED, "   "))
                .hasMessageContaining("Override reason is required");

        verify(repository, never()).save(any());
    }

    @Test
    void genericStatusChangeAllowsAdminOverrideWithReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.changeStatus(id, RequestStatus.CLOSED, "data correction");

        assertThat(result.status()).isEqualTo(RequestStatus.CLOSED);
    }

    @Test
    void closeBlocksWhenLinkedWorkOrderIsActive() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.COMPLETED);
        WorkOrder activeWorkOrder = workOrder(id);
        activeWorkOrder.setStatus(WorkOrderStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(activeWorkOrder));

        assertThatThrownBy(() -> service.close(id, new CloseRequestRequest("Resolved")))
                .hasMessageContaining("active linked work orders");
    }

    @Test
    void closeBlocksWhenLinkedDefectIsOpen() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.COMPLETED);
        WorkOrder closedWorkOrder = workOrder(id);
        closedWorkOrder.setStatus(WorkOrderStatus.CLOSED);
        Defect openDefect = defect(id);
        openDefect.setStatus(DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(closedWorkOrder));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(openDefect));

        assertThatThrownBy(() -> service.close(id, new CloseRequestRequest("Resolved")))
                .hasMessageContaining("open linked defects");
    }

    @Test
    void closeBlocksWithoutLinkedWorkOrderEvidence() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.COMPLETED);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.close(id, new CloseRequestRequest("Resolved")))
                .hasMessageContaining("execution evidence");
    }

    @Test
    void closeAllowsTerminalWorkOrdersAndResolvedDefects() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setStatus(RequestStatus.COMPLETED);
        WorkOrder closedWorkOrder = workOrder(id);
        closedWorkOrder.setStatus(WorkOrderStatus.CLOSED);
        Defect resolvedDefect = defect(id);
        resolvedDefect.setStatus(DefectStatus.RESOLVED);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubNameLookups(entity);
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(closedWorkOrder));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(resolvedDefect));

        RepairRequestDto result = service.close(id, new CloseRequestRequest("Resolved"));

        assertThat(result.status()).isEqualTo(RequestStatus.CLOSED);
        assertThat(result.closeResult()).isEqualTo("Resolved");
    }

    @Test
    void closePreservesNotFoundForMissingRequest() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(id, new CloseRequestRequest("Resolved")))
                .hasMessageContaining("Repair request not found");
    }

    @Test
    void findByIdMapsBothReasonFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("Rejection reason");
        entity.setClarificationReason("Clarification reason");

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        stubDtoLookups(entity);

        RepairRequestDto result = service.findById(id);

        assertThat(result.rejectionReason()).isEqualTo("Rejection reason");
        assertThat(result.clarificationReason()).isEqualTo("Clarification reason");
    }

    @Test
    void detailIncludesLinkedDefects() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        stubDtoLookups(entity);
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(defect(id)));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        RepairRequestDto result = service.findById(id);

        assertThat(result.linkedDefects()).hasSize(1);
        assertThat(result.linkedDefects().getFirst().code()).isEqualTo("DEF-2026-1001");
    }

    @Test
    void detailIncludesLinkedWorkOrders() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        stubDtoLookups(entity);
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(workOrder(id)));

        RepairRequestDto result = service.findById(id);

        assertThat(result.linkedWorkOrders()).hasSize(1);
        assertThat(result.linkedWorkOrders().getFirst().number()).isEqualTo("WO-2026-1001");
    }

    @Test
    void detailWithNoLinksReturnsEmptyArrays() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        stubDtoLookups(entity);

        RepairRequestDto result = service.findById(id);

        assertThat(result.linkedDefects()).isEmpty();
        assertThat(result.linkedWorkOrders()).isEmpty();
    }

    @Test
    void findByIdMapsEquipmentDepartmentReporterIds() {
        UUID id = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);
        entity.setDepartmentId(departmentId);
        entity.setReporterId(reporterId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        stubDtoLookups(entity);

        RepairRequestDto result = service.findById(id);

        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.reporterId()).isEqualTo(reporterId);
    }

    @Test
    void findAllMapsEquipmentDepartmentReporterIds() {
        UUID id = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);
        entity.setDepartmentId(departmentId);
        entity.setReporterId(reporterId);
        when(repository.searchPaginated(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsDeletedFalse(reporterId)).thenReturn(Optional.empty());
        when(defectRepository.findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(id)))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(id)))
                .thenReturn(List.of());

        Page<RepairRequestDto> result = service.search(null, null, null, null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().equipmentId()).isEqualTo(equipmentId);
        assertThat(result.getContent().getFirst().departmentId()).isEqualTo(departmentId);
        assertThat(result.getContent().getFirst().reporterId()).isEqualTo(reporterId);
    }

    @Test
    void findByIdWithNullableIdsReturnsNulls() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(null);
        entity.setDepartmentId(null);
        entity.setReporterId(null);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        RepairRequestDto result = service.findById(id);

        assertThat(result.equipmentId()).isNull();
        assertThat(result.departmentId()).isNull();
        assertThat(result.reporterId()).isNull();
        assertThat(result.equipmentName()).isNull();
        assertThat(result.departmentName()).isNull();
        assertThat(result.reporterName()).isNull();
    }
    @Test
    void getStatsWithoutFiltersReturnsRepairRequestStats() {
        RepairRequestStatsProjection projection = statsProjection(24L, 3L, 8L, 12L);

        when(repository.getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        )).thenReturn(projection);

        RepairRequestStatsResponse result = service.getStats(null, null, null);

        assertThat(result.totalRequests()).isEqualTo(24);
        assertThat(result.emergency()).isEqualTo(3);
        assertThat(result.open()).isEqualTo(8);
        assertThat(result.withWorkOrder()).isEqualTo(12);

        verify(repository).getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );
    }

    @Test
    void getStatsWithFiltersPassesDepartmentEquipmentAndNormalizedSearchPattern() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        RepairRequestStatsProjection projection = statsProjection(10L, 2L, 4L, 5L);

        when(repository.getRepairRequestStats(
                departmentId,
                equipmentId,
                "%pump%",
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        )).thenReturn(projection);

        RepairRequestStatsResponse result = service.getStats(
                departmentId,
                equipmentId,
                "  PuMp  "
        );

        assertThat(result.totalRequests()).isEqualTo(10);
        assertThat(result.emergency()).isEqualTo(2);
        assertThat(result.open()).isEqualTo(4);
        assertThat(result.withWorkOrder()).isEqualTo(5);

        verify(repository).getRepairRequestStats(
                departmentId,
                equipmentId,
                "%pump%",
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );
    }

    @Test
    void getStatsWithBlankSearchPassesNullSearchPattern() {
        RepairRequestStatsProjection projection = statsProjection(7L, 1L, 3L, 2L);

        when(repository.getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        )).thenReturn(projection);

        RepairRequestStatsResponse result = service.getStats(null, null, "   ");

        assertThat(result.totalRequests()).isEqualTo(7);
        assertThat(result.emergency()).isEqualTo(1);
        assertThat(result.open()).isEqualTo(3);
        assertThat(result.withWorkOrder()).isEqualTo(2);

        verify(repository).getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );
    }

    @Test
    void getStatsMapsNullProjectionValuesToZero() {
        RepairRequestStatsProjection projection = statsProjection(null, null, null, null);

        when(repository.getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        )).thenReturn(projection);

        RepairRequestStatsResponse result = service.getStats(null, null, null);

        assertThat(result.totalRequests()).isZero();
        assertThat(result.emergency()).isZero();
        assertThat(result.open()).isZero();
        assertThat(result.withWorkOrder()).isZero();
    }

    private RepairRequestStatsProjection statsProjection(
            Long totalRequests,
            Long emergency,
            Long open,
            Long withWorkOrder
    ) {
        return new RepairRequestStatsProjection() {
            @Override
            public Long getTotalRequests() {
                return totalRequests;
            }

            @Override
            public Long getEmergency() {
                return emergency;
            }

            @Override
            public Long getOpen() {
                return open;
            }

            @Override
            public Long getWithWorkOrder() {
                return withWorkOrder;
            }
        };
    }

    private void stubFindSaveAndDtoLookups(UUID id, RepairRequest entity) {
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubDtoLookups(entity);
    }

    private void stubDtoLookups(RepairRequest entity) {
        stubNameLookups(entity);
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId()))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId()))
                .thenReturn(List.of());
    }

    private void stubNameLookups(RepairRequest entity) {
        when(equipmentRepository.findByIdAndIsDeletedFalse(entity.getEquipmentId())).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndIsDeletedFalse(entity.getDepartmentId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsDeletedFalse(entity.getReporterId())).thenReturn(Optional.empty());
    }

    private RepairRequest repairRequest(UUID id) {
        RepairRequest entity = new RepairRequest();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setNumber("RR-001");
        entity.setTitle("Repair request");
        entity.setDescription("Initial description");
        entity.setEquipmentId(UUID.randomUUID());
        entity.setDepartmentId(UUID.randomUUID());
        entity.setReporterId(UUID.randomUUID());
        entity.setStatus(RequestStatus.OPEN);
        return entity;
    }

    private RepairRequest repairRequestForCreate(UUID id, RepairRequestRequest request) {
        RepairRequest entity = new RepairRequest();
        entity.setId(id);
        entity.setEquipmentId(request.equipmentId());
        entity.setDepartmentId(request.departmentId());
        entity.setReporterId(request.reporterId());
        return entity;
    }

    private RepairRequestRequest createRequest(UUID defectId, UUID equipmentId) {
        return new RepairRequestRequest(
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration on pump",
                defectId,
                null,
                null,
                equipmentId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestSource.MANUAL,
                null
        );
    }

    private RepairRequestRequest createRequestWithInlineDefect(UUID equipmentId, String defectTitle, String defectDescription) {
        return createRequestWithInlineDefect(equipmentId, defectTitle, defectDescription, "Mechanical", "HIGH");
    }

    private RepairRequestRequest createRequestWithInlineDefect(
            UUID equipmentId,
            String defectTitle,
            String defectDescription,
            String category,
            String severity
    ) {
        return createRequestWithInlineDefects(
                equipmentId,
                List.of(new RepairRequestRequest.InlineDefectRequest(
                        defectTitle,
                        defectDescription,
                        category,
                        severity,
                        null,
                        null
                ))
        );
    }

    private RepairRequestRequest createRequestWithInlineDefects(
            UUID equipmentId,
            List<RepairRequestRequest.InlineDefectRequest> defects
    ) {
        return new RepairRequestRequest(
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration on pump",
                null,
                null,
                defects,
                equipmentId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestSource.MANUAL,
                null
        );
    }

    private Defect defect(UUID defectId, UUID equipmentId) {
        Defect defect = new Defect();
        defect.setId(defectId);
        defect.setCode("DEF-2026-1001");
        defect.setTitle("Leak");
        defect.setDescription("Pump leak");
        defect.setEquipmentId(equipmentId);
        defect.setStatus(DefectStatus.OPEN);
        defect.setSeverity("HIGH");
        return defect;
    }

    private Defect defect(UUID repairRequestId) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode("DEF-2026-1001");
        defect.setTitle("Leak");
        defect.setRepairRequestId(repairRequestId);
        defect.setStatus(DefectStatus.OPEN);
        defect.setSeverity("HIGH");
        return defect;
    }

    private WorkOrder workOrder(UUID repairRequestId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-2026-1001");
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }
}
