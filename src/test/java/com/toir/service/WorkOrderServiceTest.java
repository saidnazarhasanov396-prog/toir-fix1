package com.toir.service;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.CompletionMeterSnapshotRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.dto.workorder.WorkOrderCloseReadinessDto;
import com.toir.dto.workorder.WorkOrderTaskDto;
import com.toir.dto.workorder.WorkOrderTaskStatusUpdateRequest;
import com.toir.dto.workorder.WorkOrderSparePartLifecycleOperation;
import com.toir.dto.sparepartlifecycle.InstallSparePartCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.CompletionAct;
import com.toir.entity.CertificationType;
import com.toir.entity.Counteragent;
import com.toir.entity.Department;
import com.toir.entity.FileAsset;
import com.toir.entity.LaborEntry;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.Reservation;
import com.toir.entity.SafetyPermit;
import com.toir.entity.UploadedFile;
import com.toir.entity.defects.Defect;
import com.toir.entity.defects.DefectList;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderDocument;
import com.toir.entity.maintenance.WorkOrderTask;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.repair.RepairRequestTemplateAction;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.User;
import com.toir.entity.users.UserCertification;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.EquipmentNodeType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.CounteragentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PlanStatus;
import com.toir.enums.DefectListStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.SafetyPermitStatus;
import com.toir.enums.TaskExecutionStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.enums.WarrantyHandling;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.CertificationTypeRepository;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SafetyPermitRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.WorkExecutionRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.repository.maintenance.WorkOrderDocumentRepository;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projection.WorkOrderCountProjection;
import com.toir.repository.projection.WorkOrderCalendarBucketProjection;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestTemplateActionRepository;
import com.toir.repository.users.UserRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.dto.file.UploadFileResponse;
import com.toir.security.AuthenticatedUser;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.service.maintanance.WorkOrderSparePartRequirementService;
import com.toir.service.integration.ToirErpWorkOrderSnapshotPublisher;
import com.toir.service.sparepartlifecycle.SparePartLifecycleService;
import com.toir.service.repair.RepairMaterialUsageService;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    com.toir.service.plannedshutdown.PlannedShutdownWorkOrderStartPolicy plannedShutdownStartPolicy;

    @Test
    void campaignCreateRejectsForgedShutdownIdentityAndKeyBeforeMutation() {
        WorkOrderRequest poisoned = request(WorkOrderType.OVERHAUL, null, null, null)
                .withGenerationKey("PS:forged")
                .withSafetyRequirements(false, false)
                .withPlannedShutdown(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.createCampaignLinked(poisoned, UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("SERVER_OWNED_WORK_ORDER_FIELDS_NOT_ALLOWED");
        verifyNoInteractions(repository);
    }

    @Test
    void campaignCreateRejectsClientSuppliedCampaignIdentityInsteadOfOverwritingIt() {
        WorkOrderRequest poisoned = request(WorkOrderType.OVERHAUL, null, null, null)
                .withRepairCampaign(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.createCampaignLinked(poisoned, UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("SERVER_OWNED_WORK_ORDER_FIELDS_NOT_ALLOWED");
        verifyNoInteractions(repository);
    }

    @Mock
    WorkOrderRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentMeterRepository equipmentMeterRepository;

    @Mock
    MeterService meterService;

    @Mock
    EquipmentNodeRepository equipmentNodeRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    PprPlanRepository pprPlanRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    RepairRequestTemplateActionRepository repairRequestTemplateActionRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    DefectListRepository defectListRepository;

    @Mock
    BrigadeMemberRepository brigadeMemberRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    UserCertificationRepository userCertificationRepository;

    @Mock
    CertificationTypeRepository certificationTypeRepository;

    @Mock
    WorkExecutionRepository workExecutionRepository;

    @Mock
    CounteragentService counteragentService;

    @Mock
    RepairMaterialUsageRepository repairMaterialUsageRepository;

    @Mock
    LaborEntryRepository laborEntryRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @Mock
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @Mock
    EquipmentService equipmentService;

    @Mock
    SafetyPermitRepository safetyPermitRepository;

    @Mock
    CompletionActRepository completionActRepository;

    @Mock
    FileAssetRepository fileAssetRepository;

    @Mock
    FileService fileService;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    WorkOrderDocumentRepository workOrderDocumentRepository;

    @Mock
    AttachmentGroupService attachmentGroupService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @Mock
    RepairMaterialUsageService repairMaterialUsageService;

    @Mock
    com.toir.service.maintenance.WorkOrderCompletionService workOrderCompletionService;

    @Mock
    MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;

    @Mock
    MaintenanceOperationRepository maintenanceOperationRepository;

    @Mock
    MaintenanceRegulationRepository maintenanceRegulationRepository;

    @Mock
    MaintenanceTemplateRepository maintenanceTemplateRepository;

    @Mock
    RepairAcceptanceRepository repairAcceptanceRepository;

    @Mock
    MaintenanceDueEventService maintenanceDueEventService;

    @Mock
    WorkOrderSparePartRequirementService workOrderSparePartRequirementService;

    @Mock
    SafetyChecklistService safetyChecklistService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    WorkOrderNumberService workOrderNumberService;

    @Mock
    NotificationService notificationService;

    @Mock
    ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;

    @Mock
    MaintenanceAutomationService maintenanceAutomationService;

    @Mock
    SparePartLifecycleService sparePartLifecycleService;

    @Mock
    com.toir.service.sparepartlifecycle.SparePartLifecycleOperationGuard sparePartLifecycleOperationGuard;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;

    @Mock
    ToirErpWorkOrderSnapshotPublisher erpWorkOrderDeltas;

    @InjectMocks
    WorkOrderService service;

    @BeforeEach
    void setUpTemplateActionSelectionDefaults() {
        lenient().when(repairRequestTemplateActionRepository.findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        lenient().when(attachmentGroupService.getPhotoSummaries(any(), any()))
                .thenReturn(java.util.Map.of());
    }

    @Test
    void attachDocumentsUploadsFilesWithJwtUserAndPersistsDocumentRows() {
        UUID workOrderId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        MockMultipartFile file = new MockMultipartFile("files", "act.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(attachmentGroupService.createGroup(eq("Completion act"), any(), eq("WORK_ORDER"), eq(workOrderId),
                eq("ACT"), eq("ACT-2024-015"), eq(List.of(file)), any(), any()))
                .thenReturn(attachmentGroup(workOrderId, fileId, "Completion act", "ACT", "ACT-2024-015", "act.pdf", currentUserId));

        var result = service.attachDocuments(
                workOrderId,
                List.of(file),
                List.of(" Completion act "),
                List.of(" ACT "),
                List.of(" ACT-2024-015 "),
                authenticatedUser(currentUserId));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workOrderId()).isEqualTo(workOrderId);
        assertThat(result.getFirst().uploadedById()).isEqualTo(currentUserId);
        assertThat(result.getFirst().documentName()).isEqualTo("Completion act");
        assertThat(result.getFirst().documentType()).isEqualTo("ACT");
        assertThat(result.getFirst().documentNumber()).isEqualTo("ACT-2024-015");
        verify(attachmentGroupService).createGroup(eq("Completion act"), any(), eq("WORK_ORDER"), eq(workOrderId),
                eq("ACT"), eq("ACT-2024-015"), eq(List.of(file)), any(), any());
    }

    @Test
    void attachDocumentsRejectsMismatchedDocumentNamesBeforeUpload() {
        UUID workOrderId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        MockMultipartFile file = new MockMultipartFile("files", "act.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.attachDocuments(
                workOrderId,
                List.of(file),
                List.of("One", "Two"),
                null,
                null,
                authenticatedUser(currentUserId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("files and documentNames must have the same length");

        verifyNoInteractions(fileService);
    }

    @Test
    void attachDocuments_eachFileGetsItsOwnTypeAndNumber() {
        UUID workOrderId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        MockMultipartFile file1 = new MockMultipartFile("files", "passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "drawing.png", "image/png", new byte[]{1, 2, 3});

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(attachmentGroupService.createGroup(eq("Technical Passport"), any(), eq("WORK_ORDER"), eq(workOrderId),
                eq("PASSPORT"), any(), eq(List.of(file1)), any(), any()))
                .thenReturn(attachmentGroup(workOrderId, fileId1, "Technical Passport", "PASSPORT", "AKT-2024-001", "passport.pdf", currentUserId));
        when(attachmentGroupService.createGroup(eq("Drawing"), any(), eq("WORK_ORDER"), eq(workOrderId),
                eq("DRAWING"), eq("DRW-2024-001"), eq(List.of(file2)), any(), any()))
                .thenReturn(attachmentGroup(workOrderId, fileId2, "Drawing", "DRAWING", "DRW-2024-001", "drawing.png", currentUserId));

        var result = service.attachDocuments(
                workOrderId,
                List.of(file1, file2),
                List.of("Technical Passport", "Drawing"),
                List.of("PASSPORT", "DRAWING"),
                List.of("АКТ-2024-001", "DRW-2024-001"),
                authenticatedUser(currentUserId)
        );

        assertThat(result).hasSize(2);
        assertThat(result).extracting(item -> item.documentType()).containsExactly("PASSPORT", "DRAWING");
        assertThat(result).extracting(item -> item.documentNumber()).containsExactly("AKT-2024-001", "DRW-2024-001");
        assertThat(result).extracting(item -> item.documentName()).containsExactly("Technical Passport", "Drawing");
    }

    @Test
    void getDocumentsLoadsMetadataAndReturnsDownloadUrls() {
        UUID workOrderId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(attachmentGroupService.listGroups(eq("WORK_ORDER"), eq(workOrderId), any()))
                .thenReturn(List.of(attachmentGroup(workOrderId, fileId, "Completion act", "ACT", null, "act.pdf", currentUserId)));

        var result = service.getDocuments(workOrderId, authenticatedUser(currentUserId));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().downloadUrl()).contains("/api/v1/work-orders/" + workOrderId + "/documents/");
    }

    @Test
    void downloadDocumentDelegatesToFileServiceForLinkedFile() {
        UUID workOrderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        ByteArrayResource resource = new ByteArrayResource("content".getBytes());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenReturn(attachmentGroup(documentId, workOrderId, fileId, "Completion act", "ACT", null, "act.pdf", currentUserId));
        when(attachmentGroupService.downloadFile(eq(documentId), eq(fileId), any())).thenReturn(resource);

        var result = service.downloadDocument(workOrderId, documentId, authenticatedUser(currentUserId));

        assertThat(result).isSameAs(resource);
        verify(attachmentGroupService).downloadFile(eq(documentId), eq(fileId), any());
    }

    @Test
    void deleteDocumentDeletesLinkAndStoredFile() {
        UUID workOrderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        service.deleteDocument(workOrderId, documentId, authenticatedUser(currentUserId));

        verify(attachmentGroupService).deleteGroup(eq(documentId), any());
    }

    @Test
    void getDocumentsRejectsWorkOrderOutsideDepartmentScope() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, UUID.randomUUID(), departmentId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.getDocuments(workOrderId, authenticatedUser(UUID.randomUUID())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("work order department scope");
    }

    @Test
    void calendarSummaryForMonthUsesTashkentBoundariesAndBuildsDayBuckets() {
        Instant expectedFrom = Instant.parse("2026-05-31T19:00:00Z");
        Instant expectedTo = Instant.parse("2026-06-30T19:00:00Z");
        when(repository.getWorkOrderCalendarDayBuckets(
                null,
                null,
                null,
                "pump",
                expectedFrom,
                expectedTo))
                .thenReturn(List.of(new CalendarBucketProjectionStub(
                        null,
                        LocalDate.of(2026, 6, 10),
                        WorkOrderStatus.APPROVED.name(),
                        2L)));

        var result = service.calendarSummary(null, null, null, " pump ", 2026, 6);

        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.month()).isEqualTo(6);
        assertThat(result.days()).hasSize(30);
        assertThat(result.totalOrders()).isEqualTo(2);
        assertThat(result.statusCounts()).extracting("status").containsExactly(WorkOrderStatus.APPROVED);
        assertThat(result.days().stream()
                .filter(day -> LocalDate.of(2026, 6, 10).equals(day.date()))
                .findFirst()
                .orElseThrow()
                .totalOrders()).isEqualTo(2);
        verify(repository).getWorkOrderCalendarDayBuckets(
                null,
                null,
                null,
                "pump",
                expectedFrom,
                expectedTo);
    }

    @Test
    void calendarSummaryForYearUsesTashkentYearBoundariesAndBuildsMonthBuckets() {
        Instant expectedFrom = Instant.parse("2025-12-31T19:00:00Z");
        Instant expectedTo = Instant.parse("2026-12-31T19:00:00Z");
        when(repository.getWorkOrderCalendarMonthBuckets(
                WorkOrderStatus.PLANNED.name(),
                null,
                null,
                null,
                expectedFrom,
                expectedTo))
                .thenReturn(List.of(new CalendarBucketProjectionStub(
                        6,
                        null,
                        WorkOrderStatus.PLANNED.name(),
                        3L)));

        var result = service.calendarSummary(WorkOrderStatus.PLANNED, null, null, "", 2026, null);

        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.month()).isNull();
        assertThat(result.months()).hasSize(12);
        assertThat(result.totalOrders()).isEqualTo(3);
        assertThat(result.months().get(5).month()).isEqualTo(6);
        assertThat(result.months().get(5).totalOrders()).isEqualTo(3);
        verify(repository).getWorkOrderCalendarMonthBuckets(
                WorkOrderStatus.PLANNED.name(),
                null,
                null,
                null,
                expectedFrom,
                expectedTo);
    }

    private record CalendarBucketProjectionStub(
            Integer bucketNumber,
            LocalDate bucketDate,
            String status,
            Long count) implements WorkOrderCalendarBucketProjection {
        @Override
        public Integer getBucketNumber() {
            return bucketNumber;
        }

        @Override
        public LocalDate getBucketDate() {
            return bucketDate;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public Long getCount() {
            return count;
        }
    }

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeShouldSucceedAndPersistRepair() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWorkType()).isEqualTo(WorkType.REPAIR);
        assertThat(captor.getValue().getRepairRequestId()).isNull();
        assertThat(captor.getValue().getDefectId()).isNull();
        assertThat(captor.getValue().isRequiresShutdown()).isFalse();
        assertThat(captor.getValue().isRequiresIsolation()).isFalse();
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defectId()).isNull();
        assertThat(result.requiresShutdown()).isFalse();
        assertThat(result.requiresIsolation()).isFalse();
        verify(workOrderSparePartRequirementService).syncFromWorkOrderContext(captor.getValue());
    }

    @Test
    void createWithoutNumberGeneratesManualNumber() {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        WorkOrderRequest request = new WorkOrderRequest(
                null,
                base.title(),
                base.equipmentId(),
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.counteragentId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary());
        WorkOrderRequest generatedRequest = new WorkOrderRequest(
                "WO-MANUAL-2026-0001",
                request.title(),
                request.equipmentId(),
                request.departmentId(),
                request.repairRequestId(),
                request.defectId(),
                request.pprTaskId(),
                request.counteragentId(),
                request.type(),
                request.workType(),
                request.warehouseId(),
                request.replacementEquipmentId(),
                request.priority(),
                request.startPlannedAt(),
                request.endPlannedAt(),
                request.createdById(),
                request.summary());
        when(workOrderNumberService.nextManualNumber()).thenReturn("WO-MANUAL-2026-0001");
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(generatedRequest);

        WorkOrderDto result = service.create(request);

        assertThat(result.number()).isEqualTo("WO-MANUAL-2026-0001");
    }

    @Test
    void createWithAuthenticatedUserOverridesRequestCreatedById() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        UUID authenticatedUserId = UUID.randomUUID();
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);

        service.create(request, authenticatedUserId);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedById()).isEqualTo(authenticatedUserId);
    }

    @Test
    void createWithoutAuthenticatedUserDoesNotTrustRequestCreatedById() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);

        service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedById()).isNull();
    }

    @Test
    void createPersistsActRequirementsAsStructuredFields() {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        WorkOrderRequest request = new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.equipmentNodeId(),
                base.locationId(),
                base.departmentId(),
                base.workLocationNote(),
                base.repairRequestId(),
                base.defectId(),
                base.defectListId(),
                base.pprTaskId(),
                base.counteragentId(),
                base.performerId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary(),
                base.maintenanceDueEventId(),
                base.cycleKey(),
                true,
                true
        );
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRepairActRequired()).isTrue();
        assertThat(captor.getValue().getStoppageActRequired()).isTrue();
        assertThat(result.repairActRequired()).isTrue();
        assertThat(result.stoppageActRequired()).isTrue();
    }

    @Test
    void createPersistsAndReturnsShutdownAndIsolationRequirements() throws Exception {
        UUID plannedShutdownId = UUID.randomUUID();
        UUID shutdownWorkItemId = UUID.randomUUID();
        WorkOrderRequest request = new ObjectMapper().readValue("""
                {
                  "number": "WO-SAFETY-FLAGS",
                  "title": "Shutdown and isolate",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                  "type": "PLANNED",
                  "workType": "REPAIR",
                  "priority": "MEDIUM",
                  "requiresShutdown": true,
                  "requiresIsolation": true,
                  "plannedShutdownId": "%s",
                  "shutdownWorkItemId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), plannedShutdownId, shutdownWorkItemId), WorkOrderRequest.class);
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.createGenerated(request.withGenerationKey(
                "PS:" + plannedShutdownId + ":" + shutdownWorkItemId + ":1"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isRequiresShutdown()).isTrue();
        assertThat(captor.getValue().isRequiresIsolation()).isTrue();
        assertThat(captor.getValue().getPlannedShutdownId()).isEqualTo(plannedShutdownId);
        assertThat(captor.getValue().getShutdownWorkItemId()).isEqualTo(shutdownWorkItemId);
        assertThat(result.requiresShutdown()).isTrue();
        assertThat(result.requiresIsolation()).isTrue();
        assertThat(result.plannedShutdownId()).isEqualTo(plannedShutdownId);
        assertThat(result.shutdownWorkItemId()).isEqualTo(shutdownWorkItemId);
    }

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeAndWithoutReplacementFieldsShouldSucceed() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.warehouseId()).isNull();
        assertThat(result.replacementEquipmentId()).isNull();
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
    }

    @Test
    void createWithoutRepairRequestAndDefectKeepsExistingBehavior() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        WorkOrderRequest request = requestWithLinks(null, null);
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defectId()).isNull();
        verifyNoInteractions(repairRequestRepository, defectRepository);
    }

    @Test
    void createEmergencyWorkOrderWithoutRepairRequestReturns400() {
        WorkOrderRequest request = request(WorkOrderType.EMERGENCY, WorkType.REPAIR, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("repairRequestId is required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createDefectWorkOrderWithoutDefectReturns400() {
        WorkOrderRequest request = request(WorkOrderType.DEFECT, WorkType.REPAIR, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("defectId is required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createMediumRepairWithoutDefectListSucceeds() {
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.MEDIUM_REPAIR, null, UUID.randomUUID());
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.type()).isEqualTo(WorkOrderType.MEDIUM_REPAIR);
        assertThat(result.defectListId()).isNull();
    }

    @Test
    void createCapitalRepairWithoutDefectListSucceeds() {
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.CAPITAL_REPAIR, null, UUID.randomUUID());
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.type()).isEqualTo(WorkOrderType.CAPITAL_REPAIR);
        assertThat(result.defectListId()).isNull();
    }

    @Test
    void createMediumRepairWithApprovedDefectListSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectListId = UUID.randomUUID();
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.MEDIUM_REPAIR, defectListId, equipmentId);
        DefectList defectList = defectList(defectListId, equipmentId, DefectListStatus.APPROVED);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(defectListRepository.findByIdAndIsDeletedFalse(defectListId)).thenReturn(Optional.of(defectList));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getDefectListId()).isEqualTo(defectListId);
        assertThat(result.defectListId()).isEqualTo(defectListId);
        assertThat(result.defectListNumber()).isEqualTo("DL-2026-0001");
        assertThat(result.defectListStatus()).isEqualTo(DefectListStatus.APPROVED);
    }

    @Test
    void createMediumRepairWithDefectListFromAnotherEquipmentReturns400() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectListId = UUID.randomUUID();
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.MEDIUM_REPAIR, defectListId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectListRepository.findByIdAndIsDeletedFalse(defectListId))
                .thenReturn(Optional.of(defectList(defectListId, UUID.randomUUID(), DefectListStatus.APPROVED)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("DefectList belongs to a different equipment");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createCapitalRepairWithNonApprovedDefectListReturns400() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectListId = UUID.randomUUID();
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.CAPITAL_REPAIR, defectListId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectListRepository.findByIdAndIsDeletedFalse(defectListId))
                .thenReturn(Optional.of(defectList(defectListId, equipmentId, DefectListStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("DefectList must be APPROVED");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createInspectionWorkOrderWithoutDefectListKeepsExistingFlowUnaffected() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        WorkOrderRequest request = requestWithDefectList(WorkOrderType.INSPECTION, null, UUID.randomUUID());
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.type()).isEqualTo(WorkOrderType.INSPECTION);
        assertThat(result.defectListId()).isNull();
        verify(defectListRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void createWithValidRepairRequestSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        mockSuccessfulCreateDependencies(request);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.repairRequest()).isNotNull();
        assertThat(result.repairRequest().id()).isEqualTo(repairRequestId);
        verify(repairRequestRepository, atLeastOnce()).findByIdAndIsDeletedFalse(repairRequestId);
    }

    @Test
    void createWithUnknownRepairRequestReturns404() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Repair request not found");
                });
    }

    @Test
    void createWithRejectedRepairRequestReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.REJECTED)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Cannot create work order for repair request");
                });
    }

    @Test
    void createWorkOrderBlockedWhenWarrantyActiveAndHandlingNotRecorded() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        repairRequest.setWarrantyActiveAtCreation(true);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("warranty decision required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrderBlockedWhenWaitingForSupplierResponse() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        repairRequest.setWarrantyActiveAtCreation(true);
        repairRequest.setWarrantyHandling(WarrantyHandling.WAITING_FOR_SUPPLIER);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("warranty decision required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrderBlockedWhenSupplierNotYetContacted() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        repairRequest.setWarrantyActiveAtCreation(true);
        repairRequest.setWarrantyHandling(WarrantyHandling.CONTACT_SUPPLIER);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("warranty decision required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrderAllowedWhenWarrantyRejectedBySupplier() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        mockSuccessfulCreateDependencies(request);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        repairRequest.setWarrantyActiveAtCreation(true);
        repairRequest.setWarrantyHandling(WarrantyHandling.WARRANTY_REJECTED);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.repairRequest()).isNotNull();
        assertThat(result.repairRequest().id()).isEqualTo(repairRequestId);
        verify(repairRequestRepository, atLeastOnce()).findByIdAndIsDeletedFalse(repairRequestId);
    }

    @Test
    void createWorkOrderAllowedWhenInternalRepairExplicitlyAllowed() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        mockSuccessfulCreateDependencies(request);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        repairRequest.setWarrantyActiveAtCreation(true);
        repairRequest.setWarrantyHandling(WarrantyHandling.INTERNAL_REPAIR_ALLOWED);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.repairRequest()).isNotNull();
        assertThat(result.repairRequest().id()).isEqualTo(repairRequestId);
        verify(repairRequestRepository, atLeastOnce()).findByIdAndIsDeletedFalse(repairRequestId);
    }

    @Test
    void createWorkOrderAllowedWhenWarrantyNotActiveAtCreation() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        mockSuccessfulCreateDependencies(request);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.repairRequest()).isNotNull();
        assertThat(result.repairRequest().id()).isEqualTo(repairRequestId);
        verify(repairRequestRepository, atLeastOnce()).findByIdAndIsDeletedFalse(repairRequestId);
    }

    @Test
    void createWithValidDefectSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID defectId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId);
        mockSuccessfulCreateDependencies(request);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, null)));

        WorkOrderDto result = service.create(request);

        assertThat(result.defectId()).isEqualTo(defectId);
        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defect()).isNotNull();
        assertThat(result.defect().id()).isEqualTo(defectId);
        verify(defectRepository, atLeastOnce()).findByIdAndIsDeletedFalse(defectId);
    }

    @Test
    void createWorkOrder_withEquipmentNode_setsNodeTarget() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithNode(equipmentId, nodeId);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing")));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeCode()).isEqualTo("BRG-01");
        assertThat(result.equipmentNodeName()).isEqualTo("Bearing");
        assertThat(result.equipmentNodeType()).isEqualTo(EquipmentNodeType.COMPONENT);
    }

    @Test
    void createWorkOrder_withNodeFromDifferentEquipment_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithNode(equipmentId, nodeId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, UUID.randomUUID(), "BRG-01", "Bearing")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("different equipment");
                });
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrder_withoutNode_stillWorks() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.equipmentNodeId()).isNull();
        verifyNoInteractions(equipmentNodeRepository);
    }

    @Test
    void createWorkOrderWithPerformerIdSavesBrigadeMemberRelation() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId, "Ivan Petrov")));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPerformer()).isSameAs(performer);
        assertThat(result.performerId()).isEqualTo(performerId);
        assertThat(result.performerName()).isEqualTo("Ivan Petrov");
        verify(notificationService).notifyUser(
                eq(userId),
                contains(request.number()),
                contains("rejalashtirilgan vaqtda bajarishingiz kerak"),
                eq(NotificationSeverity.INFO),
                eq(NotificationEventType.WORK_ORDER_ASSIGNED),
                eq("WORK_ORDER"),
                eq(result.id().toString())
        );
    }

    @Test
    void createWorkOrderWithTodayPerformerPlanSendsTodayTaskMessage() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant todayInTashkent = LocalDate.now(java.time.ZoneId.of("Asia/Tashkent"))
                .atTime(9, 0)
                .atZone(java.time.ZoneId.of("Asia/Tashkent"))
                .toInstant();
        WorkOrderRequest request = requestWithPerformerAndStart(performerId, todayInTashkent);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId, "Ivan Petrov")));

        WorkOrderDto result = service.create(request);

        verify(notificationService).notifyUser(
                eq(userId),
                contains(result.number()),
                contains("bugun bajarishingiz kerak"),
                eq(NotificationSeverity.INFO),
                eq(NotificationEventType.WORK_ORDER_ASSIGNED),
                eq("WORK_ORDER"),
                eq(result.id().toString())
        );
    }

    @Test
    void createWorkOrderWithTodayStartNotifiesAssignedPerformerUser() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant todayInTashkent = LocalDate.now(ZoneId.of("Asia/Tashkent"))
                .atStartOfDay(ZoneId.of("Asia/Tashkent"))
                .plusHours(9)
                .toInstant();
        WorkOrderRequest request = requestWithPerformerAndStart(performerId, todayInTashkent);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId, "Ivan Petrov")));

        WorkOrderDto result = service.create(request);

        verify(notificationService).notifyUser(
                eq(userId),
                contains(result.number()),
                contains("bugun bajarishingiz kerak"),
                eq(NotificationSeverity.INFO),
                eq(NotificationEventType.WORK_ORDER_ASSIGNED),
                eq("WORK_ORDER"),
                eq(result.id().toString())
        );
    }

    @Test
    void createWorkOrderWithoutPerformerIdStillWorks() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPerformer()).isNull();
        assertThat(result.performerId()).isNull();
        assertThat(result.performerName()).isNull();
        verify(brigadeMemberRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void createFromDueEventCopiesTemplateOperationsToTasks() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID inspectOperationId = UUID.randomUUID();
        UUID lubricateOperationId = UUID.randomUUID();
        WorkOrderRequest request = requestWithDueEvent(eventId, equipmentId, departmentId);
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setTemplateId(templateId);
        MaintenanceOperation inspect = operation(inspectOperationId, null);
        inspect.setName("Inspect coupling");
        inspect.setSequence(2);
        inspect.setDurationHours(1.25);
        MaintenanceOperation lubricate = operation(lubricateOperationId, null);
        lubricate.setName("Lubricate bearings");
        lubricate.setSequence(1);
        lubricate.setDurationHours(0.75);
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            if (workOrder.getId() == null) {
                ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            }
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);
        when(maintenanceDueEventService.getOrThrow(eventId)).thenReturn(event);
        when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(List.of(templateId)))
                .thenReturn(List.of(lubricate, inspect));

        WorkOrderDto result = service.create(request);

        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(0).title()).isEqualTo("Lubricate bearings");
        assertThat(result.tasks().get(0).plannedHours()).isEqualTo(0.75);
        assertThat(result.tasks().get(0).sourceTemplateId()).isEqualTo(templateId);
        assertThat(result.tasks().get(0).sourceOperationId()).isEqualTo(lubricateOperationId);
        assertThat(result.tasks().get(1).title()).isEqualTo("Inspect coupling");
        assertThat(result.tasks().get(1).plannedHours()).isEqualTo(1.25);
        assertThat(result.tasks().get(1).sourceTemplateId()).isEqualTo(templateId);
        assertThat(result.tasks().get(1).sourceOperationId()).isEqualTo(inspectOperationId);
    }

    @Test
    void createWorkOrderWithUnknownPerformerReturns404() {
        UUID performerId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Performer not found");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrderWithInactivePerformerReturns400() {
        UUID performerId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, UUID.randomUUID(), request.departmentId(), false, true);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Performer is inactive");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrderWithPerformerFromAnotherDepartmentReturns400() {
        UUID performerId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, UUID.randomUUID(), UUID.randomUUID(), true, true);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("selected department");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithPerformerHavingRequiredSkillSucceeds() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        MaintenanceOperation operation = operation(operationId, "Mechanic");
        UserCertification certification = certification(userId, "MECH", "ACTIVE", LocalDate.now().plusDays(10));
        CertificationType certificationType = certificationType("MECH", "Mechanic");
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            workOrder.getTasks().add(task(workOrder, operationId));
            return workOrder;
        });
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation));
        when(userCertificationRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(List.of(certification));
        when(certificationTypeRepository.findByCodeAndIsDeletedFalse("MECH")).thenReturn(certificationType);

        WorkOrderDto result = service.create(request);

        assertThat(result.performerId()).isEqualTo(performerId);
    }

    @Test
    void createWithPerformerMissingRequiredSkillReturns400() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            workOrder.getTasks().add(task(workOrder, operationId));
            return workOrder;
        });
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation(operationId, "Welder")));
        when(userCertificationRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Assigned performer does not have required skill: Welder");
                });
    }

    @Test
    void createWithExpiredPerformerCertificationReturns400() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        UserCertification certification = certification(userId, "WELD", "ACTIVE", LocalDate.now().minusDays(1));
        CertificationType certificationType = certificationType("WELD", "Welder");
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            workOrder.getTasks().add(task(workOrder, operationId));
            return workOrder;
        });
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        mockCreateEquipmentScope(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation(operationId, "Welder")));
        when(userCertificationRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(List.of(certification));
        when(certificationTypeRepository.findByCodeAndIsDeletedFalse("WELD")).thenReturn(certificationType);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Assigned performer certification is expired: Welder");
                });
    }

    @Test
    void createWithPerformerAndNoRequiredSkillDoesNotBlock() {
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPerformer(performerId);
        BrigadeMember performer = brigadeMember(performerId, userId, request.departmentId(), true, true);
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder workOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
            workOrder.getTasks().add(task(workOrder, operationId));
            return workOrder;
        });
        mockSuccessfulCreateDependencies(request);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(performerId)).thenReturn(Optional.of(performer));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation(operationId, null)));

        WorkOrderDto result = service.create(request);

        assertThat(result.performerId()).isEqualTo(performerId);
        verify(userCertificationRepository, never()).findAllByUserIdAndIsDeletedFalse(any());
    }

    @Test
    void findByIdReturnsPerformerIdAndName() {
        UUID workOrderId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setPerformer(brigadeMember(performerId, userId, workOrder.getDepartmentId(), true, true));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId, "Ivan Petrov")));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.performerId()).isEqualTo(performerId);
        assertThat(response.performerName()).isEqualTo("Ivan Petrov");
    }

    @Test
    void findByIdReturnsAssignedCounteragentReference() {
        UUID workOrderId = UUID.randomUUID();
        UUID counteragentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setCounteragentId(counteragentId);
        Counteragent counteragent = counteragent(counteragentId, "CA-2026-0007", "Tashkent Service LLC");
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(counteragentService.load(counteragentId)).thenReturn(counteragent);
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.counteragent()).isNotNull();
        assertThat(response.counteragent().id()).isEqualTo(counteragentId);
        assertThat(response.counteragent().code()).isEqualTo("CA-2026-0007");
        assertThat(response.counteragent().name()).isEqualTo("Tashkent Service LLC");
    }

    @Test
    void performerOptionsReturnsActiveBrigadeMembersByDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BrigadeMember performer = brigadeMember(performerId, userId, departmentId, true, true);
        Department department = new Department();
        department.setId(departmentId);
        department.setName("Maintenance");
        when(brigadeMemberRepository.findActivePerformersByDepartment(departmentId)).thenReturn(List.of(performer));
        when(userRepository.findAllByIdInAndIsDeletedFalse(List.of(userId))).thenReturn(List.of(user(userId, "Ivan Petrov")));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        var result = service.performerOptions(departmentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(performerId);
        assertThat(result.get(0).name()).isEqualTo("Ivan Petrov");
        assertThat(result.get(0).departmentId()).isEqualTo(departmentId);
        assertThat(result.get(0).departmentName()).isEqualTo("Maintenance");
        assertThat(result.get(0).role()).isEqualTo("MECHANIC");
    }

    @Test
    void createWorkOrder_fromDefectWithNode_inheritsNodeIfImplemented() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId, equipmentId);
        Defect defect = defect(defectId, null, equipmentId);
        defect.setEquipmentNodeId(nodeId);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing")));

        WorkOrderDto result = service.create(request);

        assertThat(result.equipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeCode()).isEqualTo("BRG-01");
    }

    @Test
    void createWithUnknownDefectReturns404() {
        UUID defectId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Defect not found");
                });
    }

    @Test
    void createWithPprTaskFromDraftPlanReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.DRAFT);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(request.equipmentId());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("parent PPR plan is approved");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithPlannedPprTaskReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.PLANNED);
        task.setEquipmentId(request.equipmentId());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only APPROVED PPR tasks can generate work orders");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithApprovedPprPlanAndTaskSucceeds() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(request.equipmentId());

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        WorkOrderDto result = service.create(request);

        assertThat(result.pprTaskId()).isEqualTo(taskId);
    }

    @Test
    void createWithPprTaskForDifferentEquipmentReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(UUID.randomUUID());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("PPR task belongs to a different equipment");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithRepairRequestAndMatchingDefectSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        mockSuccessfulCreateDependencies(request);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, equipmentId)));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.defectId()).isEqualTo(defectId);
    }

    @Test
    void createWithRepairRequestAndDifferentDefectRequestReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID otherRepairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, otherRepairRequestId, equipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("belongs to a different repair request");
                });
    }

    @Test
    void createWithDefectFromDifferentEquipmentReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, otherEquipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("belongs to a different equipment");
                });
    }

    @Test
    void createWithRepairRequestFromDifferentEquipmentReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, otherEquipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Repair request belongs to a different equipment");
                });
    }

    @Test
    void createWithDefectLinkedToRepairRequestRequiresRepairRequestId() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, equipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("repairRequestId is required");
                });
    }

    @Test
    void responseIncludesRepairRequestObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().id()).isEqualTo(repairRequestId);
        assertThat(response.repairRequest().number()).isEqualTo("RR-2026-1001");
    }

    @Test
    void responseIncludesDefectObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setDefectId(defectId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, null)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().id()).isEqualTo(defectId);
        assertThat(response.defect().code()).isEqualTo("DEF-2026-1001");
    }

    @Test
    void detailEnrichesTemplateTaskSourceLabels() {
        UUID workOrderId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        WorkOrderTask task = workOrderTask(workOrder, "Inspect coupling", TaskExecutionStatus.TODO);
        task.setSourceTemplateId(templateId);
        task.setSourceOperationId(operationId);
        workOrder.getTasks().add(task);
        MaintenanceTemplate template = maintenanceTemplate(templateId, "MT-2026-0001", "Pump PM template");
        MaintenanceAction action = maintenanceAction("ACT-INSPECT", "Visual inspection");
        MaintenanceOperation operation = operation(operationId, null);
        operation.setName("Inspect coupling");
        operation.setTemplate(template);
        operation.setAction(action);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(maintenanceOperationRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(operation));
        when(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(template));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().getFirst().sourceTemplateCode()).isEqualTo("MT-2026-0001");
        assertThat(response.tasks().getFirst().sourceTemplateName()).isEqualTo("Pump PM template");
        assertThat(response.tasks().getFirst().sourceOperationCode()).isEqualTo("ACT-INSPECT");
        assertThat(response.tasks().getFirst().sourceOperationName()).isEqualTo("Inspect coupling");
    }

    @Test
    void responseWithoutLinksReturnsNullObjects() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.repairRequest()).isNull();
        assertThat(response.defect()).isNull();
    }

    @Test
    void detailIncludesOperationsAndMaterialsCounts() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId)))
                .thenReturn(List.of(countProjection(workOrderId, 2)));
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId)))
                .thenReturn(List.of(countProjection(workOrderId, 4)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.operationsCount()).isEqualTo(2);
        assertThat(response.materialsCount()).isEqualTo(4);
    }

    @Test
    void detailIncludesMaterialUsageLines() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                UUID.randomUUID(),
                workOrderId,
                "WO-100",
                "Repair pump",
                warehouseId,
                "Main warehouse",
                sparePartId,
                "Bearing",
                "BRG-1",
                null,
                java.math.BigDecimal.valueOf(2),
                15.0,
                java.math.BigDecimal.valueOf(30.0),
                java.time.Instant.parse("2026-06-04T09:00:00Z"),
                UUID.randomUUID(),
                "Technician",
                UUID.randomUUID(),
                "Installed"
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of(countProjection(workOrderId, 1)));
        when(repairMaterialUsageService.findByWorkOrder(workOrderId)).thenReturn(List.of(usage));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.materialsCount()).isEqualTo(1);
        assertThat(response.materialUsages()).containsExactly(usage);
    }

    @Test
    void detailIncludesUpdatedAt() {
        UUID workOrderId = UUID.randomUUID();
        java.time.Instant updatedAt = java.time.Instant.parse("2026-05-31T12:00:00Z");
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setUpdatedAt(updatedAt);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void listBatchEnrichmentDoesNotNPlusOne() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder first = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        first.setRepairRequestId(repairRequestId);
        first.setDefectId(defectId);
        WorkOrder second = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        second.setRepairRequestId(repairRequestId);
        second.setDefectId(defectId);

        when(repository.search(null, null, null)).thenReturn(java.util.List.of(first, second));
        when(workExecutionRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId)))
                .thenReturn(java.util.List.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(defectRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId)))
                .thenReturn(java.util.List.of(defect(defectId, repairRequestId)));
        stubLifecycleDtoLookups(first);
        stubLifecycleDtoLookups(second);

        java.util.List<WorkOrderDto> results = service.search(null, null, null);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(dto -> {
            assertThat(dto.repairRequest()).isNotNull();
            assertThat(dto.defect()).isNotNull();
        });
        verify(repairRequestRepository).findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId));
        verify(defectRepository).findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId));
        verify(repairRequestRepository, never()).findByIdAndIsDeletedFalse(repairRequestId);
        verify(defectRepository, never()).findByIdAndIsDeletedFalse(defectId);
    }

    @Test
    void listIncludesOperationsAndMaterialsCounts() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of(countProjection(workOrder.getId(), 3)));
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of(countProjection(workOrder.getId(), 5)));
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().operationsCount()).isEqualTo(3);
        assertThat(result.getContent().getFirst().materialsCount()).isEqualTo(5);
    }

    @Test
    void listReturnsZeroCountsWhenNoOperationsOrMaterials() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().operationsCount()).isZero();
        assertThat(result.getContent().getFirst().materialsCount()).isZero();
    }

    @Test
    void listBatchLoadsOperationAndMaterialCountsOnce() {
        WorkOrder first = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        WorkOrder second = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        when(repository.search(null, null, null)).thenReturn(List.of(first, second));
        when(workExecutionRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(first);
        stubLifecycleDtoLookups(second);

        List<WorkOrderDto> result = service.search(null, null, null);

        assertThat(result).hasSize(2);
        verify(workExecutionRepository).countByWorkOrderIds(List.of(first.getId(), second.getId()));
        verify(repairMaterialUsageRepository).countByWorkOrderIds(List.of(first.getId(), second.getId()));
    }

    @Test
    void listEnrichmentNullSafeForMissingLinks() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);

        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId)))
                .thenReturn(java.util.List.of());
        when(defectRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId)))
                .thenReturn(java.util.List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        WorkOrderDto dto = result.getContent().get(0);
        assertThat(dto.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(dto.defectId()).isEqualTo(defectId);
        assertThat(dto.repairRequest()).isNull();
        assertThat(dto.defect()).isNull();
    }

    @Test
    void workOrderWithoutIdDefaultsCountsToZero() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber("WO-NO-ID");
        workOrder.setTitle("No id");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        when(repository.search(null, null, null)).thenReturn(List.of(workOrder));
        stubLifecycleDtoLookups(workOrder);

        List<WorkOrderDto> result = service.search(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operationsCount()).isZero();
        assertThat(result.getFirst().materialsCount()).isZero();
        verifyNoInteractions(workExecutionRepository, repairMaterialUsageRepository);
    }

    @Test
    void listBlankSearchDoesNotFail() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(eq(null), eq(null), eq(null), eq(null), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(java.util.List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, "   ");

        assertThat(result.getContent()).hasSize(1);
        verify(repository).searchPaginated(eq(null), eq(null), eq(null), eq(null), eq(PageRequest.of(0, 10)));
    }

    @Test
    void createReplacementWorkOrderWithoutWarehouseIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required");
    }

    @Test
    void createReplacementWorkOrderWithoutReplacementEquipmentIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("replacementEquipmentId is required");
    }

    @Test
    void createReplacementWorkOrderWithEquipmentNotBelongingToWarehouseShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("does not belong to selected warehouse");
    }

    @Test
    void createReplacementWorkOrderWithNonAvailableEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.RESERVED);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE");
    }

    @Test
    void createReplacementWorkOrderWithAlreadyAssignedReplacementEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already assigned to another active work order");
    }

    @Test
    void createValidReplacementWorkOrderShouldPersistSuccessfully() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");
        sourceEquipment.setDepartmentId(request.departmentId());

        Equipment replacementEquipment = new Equipment();
        replacementEquipment.setId(replacementEquipmentId);
        replacementEquipment.setName("Replacement Equipment");

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())).thenReturn(Optional.of(sourceEquipment));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(false);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findById(request.equipmentId())).thenReturn(Optional.of(sourceEquipment));
        when(equipmentRepository.findById(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(departmentRepository.findById(request.departmentId())).thenReturn(Optional.of(department));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(warehouseId);
        assertThat(captor.getValue().getReplacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.replacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.replacementEquipmentName()).isEqualTo("Replacement Equipment");
        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void createNonReplacementWorkOrderWithReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    @Test
    void createWorkOrderWithNullWorkTypeAndReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    @Test
    void approveReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void approveWorkOrderWithTomorrowStartNotifiesAssignedPerformerUser() {
        UUID workOrderId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.PLANNED, null, null);
        workOrder.setPerformer(brigadeMember(performerId, userId, workOrder.getDepartmentId(), true, true));
        workOrder.setStartPlannedAt(LocalDate.now(ZoneId.of("Asia/Tashkent"))
                .plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Tashkent"))
                .plusHours(10)
                .toInstant());

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId, "Ivan Petrov")));

        service.approve(workOrderId, UUID.randomUUID());

        verify(notificationService).notifyUser(
                eq(userId),
                contains(workOrder.getNumber()),
                contains("ertaga bajarishingiz kerak"),
                eq(NotificationSeverity.INFO),
                eq(NotificationEventType.WORK_ORDER_ASSIGNED),
                eq("WORK_ORDER"),
                eq(workOrderId.toString())
        );
    }

    @Test
    void approveReplacementWorkOrderWithReservedItemShouldSucceedWithoutStatusChange() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        verify(warehouseEquipmentItemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    @Test
    void approveReplacementWorkOrderWithInstalledItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void approveReplacementWorkOrderWithOutOfServiceItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.OUT_OF_SERVICE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startApprovedReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.start(workOrderId);

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void startWithLinkedRepairRequestMovesRequestToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.IN_PROGRESS);
        verify(repairRequestRepository).save(repairRequest);
    }

    @Test
    void startWithLinkedDefectMovesDefectToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.IN_PROGRESS);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.IN_PROGRESS);
        verify(defectRepository).save(defect);
    }

    @Test
    void startDoesNotReopenClosedRepairRequest() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.CLOSED);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void startDoesNotReopenClosedDefect() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(defect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.CLOSED);
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void startBlockedByPendingCriticalSafetyChecklist() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        org.mockito.Mockito.doThrow(RestException.badRequest(
                        "Cannot start work order; critical safety checklist items are not passed"))
                .when(safetyChecklistService).assertCanStart(workOrder);

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("critical safety checklist items are not passed");

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void startAllowedWhenAllCriticalSafetyChecklistItemsPassed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(safetyChecklistService).assertCanStart(workOrder);
    }

    @Test
    void startFailsIfCertificationExpiredAfterApproval() {
        UUID workOrderId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setPerformer(brigadeMember(performerId, userId, workOrder.getDepartmentId(), true, true));
        workOrder.getTasks().add(task(workOrder, operationId));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation(operationId, "Electrician")));
        when(userCertificationRepository.findAllByUserIdAndIsDeletedFalse(userId))
                .thenReturn(List.of(certification(userId, "ELEC", "ACTIVE", LocalDate.now().minusDays(1))));
        when(certificationTypeRepository.findByCodeAndIsDeletedFalse("ELEC"))
                .thenReturn(certificationType("ELEC", "Electrician"));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Assigned performer certification is expired: Electrician");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void startSucceedsWhenRequiredSkillsAreSatisfied() {
        UUID workOrderId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setPerformer(brigadeMember(performerId, userId, workOrder.getDepartmentId(), true, true));
        workOrder.getTasks().add(task(workOrder, operationId));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceOperationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation(operationId, "Mechanic")));
        when(userCertificationRepository.findAllByUserIdAndIsDeletedFalse(userId))
                .thenReturn(List.of(certification(userId, "MECH", "ACTIVE", LocalDate.now().plusDays(30))));
        when(certificationTypeRepository.findByCodeAndIsDeletedFalse("MECH"))
                .thenReturn(certificationType("MECH", "Mechanic"));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void startFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void startFromClosedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void completeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        UUID oldEquipmentReturnWarehouseId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        UUID workOrderDepartmentId = UUID.randomUUID();
        workOrder.setDepartmentId(workOrderDepartmentId);
        Warehouse returnWarehouse = new Warehouse();
        returnWarehouse.setId(oldEquipmentReturnWarehouseId);
        returnWarehouse.setActive(true);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseRepository.findByIdAndIsDeletedFalse(oldEquipmentReturnWarehouseId)).thenReturn(Optional.of(returnWarehouse));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", oldEquipmentReturnWarehouseId));

        verify(equipmentService).updatePlacement(eq(replacementEquipmentId), argThat(request ->
                request.targetType() == PlacementTargetType.DEPARTMENT
                        && workOrderDepartmentId.equals(request.departmentId())
                        && request.note().contains(workOrder.getNumber())
        ));
        verify(equipmentService).updatePlacement(eq(workOrder.getEquipmentId()), argThat(request ->
                request.targetType() == PlacementTargetType.WAREHOUSE
                        && oldEquipmentReturnWarehouseId.equals(request.warehouseId())
                        && request.warehouseStatus() == WarehouseEquipmentStatus.OUT_OF_SERVICE
                        && request.note().contains(workOrder.getNumber())
        ));
        verify(warehouseEquipmentItemService, never()).transferEquipmentToWarehouse(any(), any(), any());
    }

    @Test
    void completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(defect.getResolvedAt()).isNotNull();
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.RESOLVED);
        verify(defectRepository).save(defect);
        verify(operationalIssueLifecycleSyncService)
                .resolveDefectIssueIfTerminal(defect, "Defect resolved from linked work order completion.");
    }

    @Test
    void completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setDefectId(defectId);
        WorkOrder anotherActiveWorkOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        anotherActiveWorkOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder, anotherActiveWorkOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.IN_PROGRESS);
        assertThat(defect.getResolvedAt()).isNull();
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.IN_PROGRESS);
        verify(defectRepository, never()).save(any(Defect.class));
        verify(operationalIssueLifecycleSyncService, never())
                .resolveDefectIssueIfTerminal(any(Defect.class), any());
    }

    @Test
    void completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect defect = defect(defectId, repairRequestId, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(defect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.COMPLETED);
        verify(repairRequestRepository).save(repairRequest);
        verify(operationalIssueLifecycleSyncService)
                .sweepRepairRequest(repairRequestId,
                        "Repair request completed after linked work orders and defects reached terminal state.");
    }

    @Test
    void completeDoesNotCompleteRepairRequestWhenAnyDefectStillOpen() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.IN_PROGRESS);
        Defect stillOpenDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, stillOpenDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.OPEN);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void completeWithLinkedPprTaskShouldCompleteTaskAndMovePlanToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.IN_PROGRESS);
        PprTask plannedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.PLANNED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, plannedTask));
        when(pprTaskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(linkedTask.getStatus()).isEqualTo(com.toir.enums.PprTaskStatus.COMPLETED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.DRAFT);
        verify(pprTaskRepository).save(any(PprTask.class));
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void completeWithLinkedPprTaskShouldClosePlanWhenAllTasksCompleted() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.IN_PROGRESS);
        PprTask completedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.COMPLETED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, completedTask));
        when(pprTaskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(linkedTask.getStatus()).isEqualTo(com.toir.enums.PprTaskStatus.COMPLETED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CLOSED);
    }

    @Test
    void closeWithLinkedAlreadyCompletedTaskShouldRollupPlanStatus() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.COMPLETED);
        PprTask plannedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.PLANNED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, plannedTask));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        verify(pprTaskRepository, never()).save(any(PprTask.class));
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void recalculateLinkedPprPlanForHistoricalClosedWorkOrderShouldMoveDraftPlanOutOfDraft() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.COMPLETED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.recalculateLinkedPprPlanForWorkOrder(workOrderId);

        assertThat(result.id()).isEqualTo(workOrderId);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CLOSED);
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.DRAFT);
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void completeWithoutLinkedPprTaskShouldKeepExistingBehavior() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verifyNoInteractions(pprTaskRepository, pprPlanRepository);
    }

    @Test
    void completeAutoWorkOrderCreatesAnchorFromDueEventAndTriggersRecalculationOnce() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        workOrder.setCycleKey("EQ:RULE:METER:ENGINE_HOURS:500");
        MaintenanceDueEvent event = dueEvent(dueEventId, equipmentId, regulationId, ruleId);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceDueEventService.getOrThrow(dueEventId)).thenReturn(event);
        when(maintenanceCompletionAnchorRepository.findByMaintenanceDueEventIdAndIsDeletedFalse(dueEventId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("[{\"meterType\":\"ENGINE_HOURS\",\"value\":520.0}]");
        when(maintenanceDueEventService.completeFromWorkOrder(event, "Work order completed"))
                .thenReturn(event);
        when(maintenanceAutomationServiceProvider.getIfAvailable()).thenReturn(maintenanceAutomationService);
        when(maintenanceAutomationService.evaluateEquipment(equipmentId, MaintenanceTriggerSource.WORK_ORDER_COMPLETED))
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(1, 0, 0, 0, 0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository).save(anchorCaptor.capture());
        MaintenanceCompletionAnchor anchor = anchorCaptor.getValue();
        assertThat(anchor.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(anchor.getRegulationId()).isEqualTo(regulationId);
        assertThat(anchor.getEquipmentMaintenanceRuleId()).isEqualTo(ruleId);
        assertThat(anchor.getSource()).isEqualTo("WORK_ORDER");
        assertThat(anchor.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(anchor.getMaintenanceDueEventId()).isEqualTo(dueEventId);
        assertThat(anchor.getPerformedAt()).isEqualTo(workOrder.getCompletedAt());
        assertThat(anchor.getPlannedDueAt()).isEqualTo(event.getDueAt());
        assertThat(anchor.getPlannedMeterValue()).isEqualByComparingTo("500.0");
        assertThat(anchor.getMeterSnapshots()).contains("ENGINE_HOURS").contains("520.0");
        verify(maintenanceDueEventService).completeFromWorkOrder(event, "Work order completed");
        verify(maintenanceAutomationService, times(1))
                .evaluateEquipment(equipmentId, MaintenanceTriggerSource.WORK_ORDER_COMPLETED);
    }

    @Test
    void completeAutoWorkOrderReusesExistingDueEventAnchor() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent event = dueEvent(dueEventId, equipmentId, UUID.randomUUID(), UUID.randomUUID());
        MaintenanceCompletionAnchor existing = new MaintenanceCompletionAnchor();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceDueEventService.getOrThrow(dueEventId)).thenReturn(event);
        when(maintenanceCompletionAnchorRepository.findByMaintenanceDueEventIdAndIsDeletedFalse(dueEventId))
                .thenReturn(Optional.of(existing));
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("[{\"meterType\":\"ENGINE_HOURS\",\"value\":520.0}]");
        when(maintenanceDueEventService.completeFromWorkOrder(event, "Work order completed"))
                .thenReturn(event);
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository, times(1)).save(anchorCaptor.capture());
        assertThat(anchorCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getMaintenanceDueEventId()).isEqualTo(dueEventId);
        assertThat(existing.getWorkOrderId()).isEqualTo(workOrderId);
    }


    @Test
    void completePprTaskWithoutDueEventStillTriggersMaintenanceRecalculation() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.APPROVED);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask));
        when(pprTaskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationServiceProvider.getIfAvailable()).thenReturn(maintenanceAutomationService);
        when(maintenanceAutomationService.evaluateEquipment(workOrder.getEquipmentId(), MaintenanceTriggerSource.WORK_ORDER_COMPLETED))
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(1, 0, 0, 0, 0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(maintenanceCompletionAnchorRepository).save(any(MaintenanceCompletionAnchor.class));
        verify(maintenanceAutomationService).evaluateEquipment(
                workOrder.getEquipmentId(),
                MaintenanceTriggerSource.WORK_ORDER_COMPLETED
        );
    }

    @Test
    void completeWithRequestRegulationWithoutDueEventTriggersMaintenanceRecalculation() {
        UUID workOrderId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationServiceProvider.getIfAvailable()).thenReturn(maintenanceAutomationService);
        when(maintenanceAutomationService.evaluateEquipment(workOrder.getEquipmentId(), MaintenanceTriggerSource.WORK_ORDER_COMPLETED))
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(1, 0, 0, 0, 0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                regulationId,
                null,
                null,
                null,
                null,
                null
        ));

        verify(maintenanceAutomationService).evaluateEquipment(
                workOrder.getEquipmentId(),
                MaintenanceTriggerSource.WORK_ORDER_COMPLETED
        );
    }

    @Test
    void manualWorkOrderWithoutEventCompletesWithoutMaintenanceAnchor() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(maintenanceCompletionAnchorRepository, never()).save(any(MaintenanceCompletionAnchor.class));
        verifyNoInteractions(maintenanceDueEventService, maintenanceAutomationServiceProvider);
    }

    @Test
    void manualRegulatedWorkOrderStillCreatesCompletionAnchor() {
        UUID workOrderId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                regulationId,
                null,
                null,
                null,
                null,
                null
        ));

        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository).save(anchorCaptor.capture());
        assertThat(anchorCaptor.getValue().getRegulationId()).isEqualTo(regulationId);
        assertThat(anchorCaptor.getValue().getMaintenanceDueEventId()).isNull();
        verifyNoInteractions(maintenanceDueEventService);
        verify(maintenanceAutomationServiceProvider).getIfAvailable();
    }

    @Test
    void completeWithMeterSnapshotPersistsWorkCompletedMeterReading() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-06-17T08:30:00Z");
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(workOrder.getEquipmentId());
        meter.setMeterType(MeterType.MILEAGE_KM);
        meter.setName("Odometer");
        meter.setUnit("km");
        meter.setCurrentValue(4_000.0);
        meter.setActive(true);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(equipmentMeterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(Optional.of(meter));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                Instant.parse("2026-06-17T08:45:00Z"),
                null,
                null,
                List.of(new CompletionMeterSnapshotRequest(meterId, MeterType.MILEAGE_KM, 8_000.0, readAt))
        ));

        verify(meterService).addReading(
                argThat((MeterReadingRequest request) ->
                        meterId.equals(request.meterId())
                                && request.value().equals(8_000.0)
                                && readAt.equals(request.readAt())
                                && request.source() == MeterSource.MANUAL
                                && request.note().contains("Work order completed")),
                eq(MeterReadingContext.WORK_COMPLETED),
                eq(repairRequestId),
                eq(workOrderId),
                eq(defectId)
        );
    }

    @Test
    void completeRejectsMeterSnapshotForAnotherEquipmentBeforeSavingCompletedStatus() {
        UUID workOrderId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(UUID.randomUUID());
        meter.setMeterType(MeterType.MILEAGE_KM);
        meter.setName("Odometer");
        meter.setUnit("km");
        meter.setActive(true);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(equipmentMeterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(Optional.of(meter));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new CompletionMeterSnapshotRequest(meterId, MeterType.MILEAGE_KM, 8_000.0, null))
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("does not belong to work order equipment");

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(repository, never()).save(any(WorkOrder.class));
        verifyNoInteractions(meterService);
    }

    @Test
    void completeFromApprovedShouldSucceed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(result.result()).isEqualTo("done");
    }

    @Test
    void updateTaskStatusToDoneSetsActualHoursAndTimestamps() {
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        WorkOrderTask task = workOrderTask(workOrder, "Inspect coupling", TaskExecutionStatus.TODO);
        ReflectionTestUtils.setField(task, "id", taskId);
        workOrder.getTasks().add(task);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.save(workOrder)).thenReturn(workOrder);

        WorkOrderTaskDto result = service.updateTaskStatus(
                workOrderId,
                taskId,
                new WorkOrderTaskStatusUpdateRequest(TaskExecutionStatus.DONE, 1.5)
        );

        assertThat(result.id()).isEqualTo(taskId);
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.DONE);
        assertThat(result.actualHours()).isEqualTo(1.5);
        assertThat(task.getStatus()).isEqualTo(TaskExecutionStatus.DONE);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getCompletedAt()).isNotNull();
        verify(repository).save(workOrder);
    }

    @Test
    void completeWithRequiredRepairActAndNoFileReturns400WithoutSavingCompletedStatus() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairActRequired(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("ta'mirlash akti fayli talab qilinadi");
                });

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void completeWithRequiredActFilesStoresFileAssetIdsAndCompletes() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairFileId = UUID.randomUUID();
        UUID stoppageFileId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairActRequired(true);
        workOrder.setStoppageActRequired(true);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(fileAssetRepository.findByIdAndIsDeletedFalse(repairFileId)).thenReturn(Optional.of(fileAsset(repairFileId)));
        when(fileAssetRepository.findByIdAndIsDeletedFalse(stoppageFileId)).thenReturn(Optional.of(fileAsset(stoppageFileId)));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                repairFileId,
                stoppageFileId
        ));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(workOrder.getRepairActFileAssetId()).isEqualTo(repairFileId);
        assertThat(workOrder.getStoppageActFileAssetId()).isEqualTo(stoppageFileId);
        assertThat(result.repairActFileId()).isEqualTo(repairFileId);
        assertThat(result.stoppageActFileId()).isEqualTo(stoppageFileId);
    }

    @Test
    void completeWithMaterialUsagesIssuesMaterialsBeforeCompletion() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                warehouseId,
                sparePartId,
                java.math.BigDecimal.valueOf(2),
                12.5
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairMaterialUsageService.register(workOrderId, usage)).thenReturn(usage);
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(usage)
        ));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(repairMaterialUsageService).register(workOrderId, usage);
        verify(repository).save(workOrder);
    }

    @Test
    void explicitLifecycleOperationUsesWorkOrderScopedIdempotencyAndSameDomainService() {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setEquipmentId(equipmentId);
        InstallSparePartCommand install = new InstallSparePartCommand(
                null, "front left", "Front left", partId, java.math.BigDecimal.ONE,
                null, null, null, null, null, "external test source", null);
        WorkOrderSparePartLifecycleOperation operation = new WorkOrderSparePartLifecycleOperation(
                "install-front-left", SparePartLifecycleCommandType.INSTALL, install, null, null);
        CompleteWorkOrderRequest request = new CompleteWorkOrderRequest(
                "done", "summary", null, null, null, null, null, null,
                null, null, null, null, List.of(operation));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);

        ReflectionTestUtils.invokeMethod(service, "executeSparePartLifecycleOperations", workOrder, request);

        verify(sparePartLifecycleService).install(
                eq(equipmentId),
                eq("work-order:" + workOrderId + ":install-front-left"),
                eq(actorId),
                argThat(command -> workOrderId.equals(command.workOrderId())
                        && partId.equals(command.sparePartId())
                        && "front left".equals(command.slotCode()))
        );
    }

    @Test
    void completeWithMaterialUsageFailureDoesNotSaveCompletedWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                warehouseId,
                sparePartId,
                java.math.BigDecimal.valueOf(99),
                12.5
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repairMaterialUsageService.register(workOrderId, usage))
                .thenThrow(RestException.badRequest("Cannot write off more than available"));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(usage)
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot write off more than available");

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void completeFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromPlannedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.PLANNED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromClosedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromCancelledShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CANCELLED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeReplacementWorkOrderWithoutReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("oldEquipmentReturnWarehouseId is required");
    }

    @Test
    void completeReplacementWorkOrderWithUnknownReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        UUID oldEquipmentReturnWarehouseId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseRepository.findByIdAndIsDeletedFalse(oldEquipmentReturnWarehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", oldEquipmentReturnWarehouseId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void completeNonReplacementWithReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", UUID.randomUUID())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("oldEquipmentReturnWarehouseId must be null");
    }

    @Test
    void closeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.COMPLETED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
    }

    @Test
    void closeFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromApprovedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromCompletedShouldSucceed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    void closeBlockedByIncompleteSafetyChecklist() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(true);
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(laborEntry(workOrderId)));
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());
        when(safetyChecklistService.closeBlocker(workOrder)).thenReturn(Optional.of("Safety checklist must be COMPLETED"));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Safety checklist must be COMPLETED");

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeAllowedAfterSafetyChecklistCompletedPlusExistingGates() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(safetyChecklistService.closeBlocker(workOrder)).thenReturn(Optional.empty());
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.CLOSED);
        verify(safetyChecklistService).closeBlocker(workOrder);
    }

    @Test
    void closeWithBlankResultShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest(" ", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Result is required to close a work order");
    }

    @Test
    void closeMissingWorkOrderShouldRemainNotFound() {
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Work order not found");
                });
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithIncompleteTaskShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.getTasks().add(workOrderTask(workOrder, "Lockout checklist", TaskExecutionStatus.IN_PROGRESS));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Incomplete tasks/checklist items: Lockout checklist");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithExistingNonClosedSafetyPermitShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        SafetyPermit permit = safetyPermit(workOrderId, SafetyPermitStatus.ISSUED);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(permit));
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Safety permit must be CLOSED");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithExistingUnsignedCompletionActShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        CompletionAct act = completionAct(workOrderId, false);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(act));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Completion act must be signed");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeMissingRequiredLaborShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of());
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("At least one labor entry is required");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithActiveReservationShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setWorkOrderId(workOrderId);
        reservation.setStatus(ReservationStatus.ACTIVE);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(laborEntry(workOrderId)));
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(reservation));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Material reservations must be issued, released or cancelled");
        verify(repository, never()).save(any(WorkOrder.class));
    }



    @Test
    void closeReadinessReadyWhenCloseEvidenceIsSatisfied() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setResult("completed");
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(true);
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(laborEntry(workOrderId)));
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());

        WorkOrderCloseReadinessDto readiness = service.getCloseReadiness(workOrderId);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
        assertThat(readiness.warnings()).isEmpty();
        assertThat(readiness.groups()).containsEntry("tasks", com.toir.enums.CloseReadinessGroupStatus.READY);
        assertThat(readiness.groups()).containsEntry("materials", com.toir.enums.CloseReadinessGroupStatus.READY);
    }

    @Test
    void closeReadinessReportsInvalidStatusMissingResultAndIncompleteTasks() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.getTasks().add(workOrderTask(workOrder, "Inspect coupling", TaskExecutionStatus.IN_PROGRESS));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of());
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());

        WorkOrderCloseReadinessDto readiness = service.getCloseReadiness(workOrderId);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers()).extracting("code")
                .contains("WORK_ORDER_NOT_CLOSEABLE", "MISSING_RESULT", "INCOMPLETE_TASKS");
        assertThat(readiness.groups()).containsEntry("equipment", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
        assertThat(readiness.groups()).containsEntry("acts", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
        assertThat(readiness.groups()).containsEntry("tasks", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
    }

    @Test
    void closeReadinessReportsSafetyActLaborAndMaterialBlockersConsistentWithClose() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setResult("completed");
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setWorkOrderId(workOrderId);
        reservation.setStatus(ReservationStatus.ACTIVE);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(safetyPermit(workOrderId, SafetyPermitStatus.ISSUED)));
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(completionAct(workOrderId, false)));
        when(repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(false);
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of());
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(reservation));
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());

        WorkOrderCloseReadinessDto readiness = service.getCloseReadiness(workOrderId);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers()).extracting("code")
                .contains(
                        "OPEN_SAFETY_PERMIT",
                        "COMPLETION_ACT_NOT_SIGNED",
                        "FINAL_ACCEPTANCE_NOT_ACCEPTED",
                        "MISSING_LABOR_ENTRIES",
                        "ACTIVE_MATERIAL_RESERVATIONS"
                );
        assertThat(readiness.groups()).containsEntry("safety", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
        assertThat(readiness.groups()).containsEntry("acts", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
        assertThat(readiness.groups()).containsEntry("labor", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
        assertThat(readiness.groups()).containsEntry("materials", com.toir.enums.CloseReadinessGroupStatus.BLOCKED);
    }

    @Test
    void closeReadinessReportsPendingActualCostsAsWarningOnly() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.DIAGNOSTICS, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setResult("completed");
        ActualCost pendingCost = new ActualCost();
        pendingCost.setId(UUID.randomUUID());
        pendingCost.setWorkOrderId(workOrderId);
        pendingCost.setStatus(ActualCostStatus.PENDING);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(true);
        when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(pendingCost));

        WorkOrderCloseReadinessDto readiness = service.getCloseReadiness(workOrderId);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
        assertThat(readiness.warnings()).extracting("code").containsExactly("PENDING_ACTUAL_COSTS");
        assertThat(readiness.groups()).containsEntry("finance", com.toir.enums.CloseReadinessGroupStatus.WARNING);
    }


    @Test
    void closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.RESOLVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.CLOSED);
        verify(defectRepository).save(defect);
        verify(operationalIssueLifecycleSyncService)
                .resolveDefectIssueIfTerminal(defect,
                        "Defect closed after linked work orders reached terminal state.");
    }

    @Test
    void closeDoesNotCloseRepairRequestWhenAnyDefectStillOpen() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.RESOLVED);
        Defect stillOpenDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, stillOpenDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.OPEN);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void closeDoesNotCloseRepairRequestWhenAllWorkOrdersTerminalAndAllDefectsResolvedOrClosed() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        WorkOrder completedSibling = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        completedSibling.setRepairRequestId(repairRequestId);
        WorkOrder cancelledSibling = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.CANCELLED, null, null);
        cancelledSibling.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.COMPLETED);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.RESOLVED);
        Defect alreadyClosedDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder, completedSibling, cancelledSibling));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, alreadyClosedDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(repairRequest.getCloseResult()).isNull();
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.COMPLETED);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void workOrderWithoutLinksStillWorks() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(response.repairRequest()).isNull();
        assertThat(response.defect()).isNull();
    }

    @Test
    void nonReplacementLifecycleShouldNotChangeWarehouseEquipmentItemStatus() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        verifyNoInteractions(warehouseEquipmentItemRepository);
    }

    @Test
    void approveMediumRepairWithoutDefectListReturns400() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setType(WorkOrderType.MEDIUM_REPAIR);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Approved DefectList is required for MEDIUM_REPAIR");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void startCapitalRepairWithoutDefectListReturns400() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setType(WorkOrderType.CAPITAL_REPAIR);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Approved DefectList is required for CAPITAL_REPAIR");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void startReplacementWorkOrderWithInvalidWarehouseEquipmentStateShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startReplacementWorkOrderWithMissingWarehouseEquipmentItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Replacement equipment item not found in selected warehouse");
    }

    private WorkOrderCountProjection countProjection(UUID workOrderId, long count) {
        return new WorkOrderCountProjection() {
            @Override
            public UUID getWorkOrderId() {
                return workOrderId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private PprPlan pprPlan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("Monthly PPR plan");
        plan.setStartDate(java.time.LocalDate.of(2026, 5, 1));
        plan.setEndDate(java.time.LocalDate.of(2026, 5, 31));
        plan.setStatus(status);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }

    private PprTask pprTask(UUID id, PprPlan plan, com.toir.enums.PprTaskStatus status) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PT-" + id.toString().substring(0, 8));
        task.setPlan(plan);
        task.setStatus(status);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("PPR task");
        task.setScheduledStart(java.time.LocalDateTime.now().minusHours(1));
        task.setScheduledEnd(java.time.LocalDateTime.now().plusHours(1));
        task.setDueDate(java.time.LocalDateTime.now().plusDays(1));
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private WorkOrderRequest request(WorkOrderType type, WorkType workType, UUID warehouseId, UUID replacementEquipmentId) {
        return new WorkOrderRequest(
                "WO-2026-REPL-1",
                "Replacement job",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                type,
                workType,
                warehouseId,
                replacementEquipmentId,
                PriorityLevel.MEDIUM,
                null,
                null,
                UUID.randomUUID(),
                "summary"
        );
    }

    private WorkOrderRequest requestWithLinks(UUID repairRequestId, UUID defectId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.departmentId(),
                repairRequestId,
                defectId,
                base.pprTaskId(),
                base.counteragentId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithLinks(UUID repairRequestId, UUID defectId, UUID equipmentId) {
        WorkOrderRequest base = requestWithLinks(repairRequestId, defectId);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.counteragentId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithPprTask(UUID pprTaskId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                pprTaskId,
                base.counteragentId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithDueEvent(UUID dueEventId, UUID equipmentId, UUID departmentId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                base.equipmentNodeId(),
                null,
                departmentId,
                base.workLocationNote(),
                base.repairRequestId(),
                base.defectId(),
                base.defectListId(),
                base.pprTaskId(),
                base.counteragentId(),
                base.performerId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary(),
                dueEventId,
                "cycle-template-work-order"
        );
    }

    private WorkOrderRequest requestWithNode(UUID equipmentId, UUID equipmentNodeId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                equipmentNodeId,
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.counteragentId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithPerformer(UUID performerId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return requestWithPerformerAndStart(base, performerId, base.startPlannedAt());
    }

    private WorkOrderRequest requestWithPerformerAndStart(UUID performerId, Instant startPlannedAt) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return requestWithPerformerAndStart(base, performerId, startPlannedAt);
    }

    private WorkOrderRequest requestWithPerformerAndStart(WorkOrderRequest base, UUID performerId, Instant startPlannedAt) {
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.equipmentNodeId(),
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.counteragentId(),
                performerId,
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                startPlannedAt,
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithDefectList(WorkOrderType type, UUID defectListId, UUID equipmentId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                null,
                null,
                base.departmentId(),
                base.workLocationNote(),
                base.repairRequestId(),
                base.defectId(),
                defectListId,
                base.pprTaskId(),
                base.counteragentId(),
                base.performerId(),
                type,
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary(),
                base.maintenanceDueEventId(),
                base.cycleKey()
        );
    }

    private DefectList defectList(UUID id, UUID equipmentId, DefectListStatus status) {
        DefectList defectList = new DefectList();
        defectList.setId(id);
        defectList.setCode("DL-2026-0001");
        defectList.setTitle("Approved defect list");
        defectList.setEquipmentId(equipmentId);
        defectList.setCreatedById(UUID.randomUUID());
        defectList.setStatus(status);
        return defectList;
    }

    private AuthenticatedUser authenticatedUser(UUID userId) {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }

    private WorkOrder workOrder(UUID id, UUID equipmentId, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Planned repair");
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.PLANNED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private UploadedFile uploadedFile(UUID id, UUID uploadedById, String originalName) {
        return UploadedFile.builder()
                .id(id)
                .originalName(originalName)
                .storedName(originalName)
                .objectName("work-order-documents/2026/06/" + id + "-" + originalName)
                .contentType("application/pdf")
                .extension("pdf")
                .size(128L)
                .uploadedBy(uploadedById)
                .category(FileCategory.WORK_ORDER_DOCUMENT)
                .deleted(false)
                .createdAt(java.time.LocalDateTime.parse("2026-06-09T10:00:00"))
                .build();
    }

    private WorkOrderDocument workOrderDocument(WorkOrder workOrder, UploadedFile file) {
        WorkOrderDocument document = WorkOrderDocument.builder()
                .workOrder(workOrder)
                .file(file)
                .documentType("ACT")
                .documentName("Completion act")
                .build();
        document.setId(UUID.randomUUID());
        return document;
    }

    private AttachmentGroupDto attachmentGroup(
            UUID targetId,
            UUID fileId,
            String title,
            String documentType,
            String documentNumber,
            String originalName,
            UUID uploadedBy
    ) {
        return attachmentGroup(UUID.randomUUID(), targetId, fileId, title, documentType, documentNumber, originalName, uploadedBy);
    }

    private AttachmentGroupDto attachmentGroup(
            UUID groupId,
            UUID targetId,
            UUID fileId,
            String title,
            String documentType,
            String documentNumber,
            String originalName,
            UUID uploadedBy
    ) {
        return new AttachmentGroupDto(
                groupId,
                title,
                null,
                AttachmentTargetType.WORK_ORDER,
                targetId,
                documentType,
                documentNumber,
                uploadedBy,
                java.time.LocalDateTime.parse("2026-06-09T10:00:00"),
                List.of(new AttachmentGroupDto.FileItem(
                        UUID.randomUUID(),
                        fileId,
                        originalName,
                        originalName,
                        originalName.endsWith(".png") ? "image/png" : "application/pdf",
                        128L,
                        0,
                        null,
                        uploadedBy,
                        java.time.LocalDateTime.parse("2026-06-09T10:00:00"),
                        "/download",
                        "/presigned"
                ))
        );
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status) {
        return repairRequest(id, status, null);
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status, UUID equipmentId) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setNumber("RR-2026-1001");
        repairRequest.setEquipmentId(equipmentId);
        repairRequest.setPriority(PriorityLevel.MEDIUM);
        repairRequest.setTitle("Repair request");
        repairRequest.setDescription("Short description");
        repairRequest.setStatus(status);
        return repairRequest;
    }

    private Defect defect(UUID id, UUID repairRequestId) {
        return defect(id, repairRequestId, DefectStatus.OPEN);
    }

    private Defect defect(UUID id, UUID repairRequestId, UUID equipmentId) {
        Defect defect = defect(id, repairRequestId, DefectStatus.OPEN);
        defect.setEquipmentId(equipmentId);
        return defect;
    }

    private Defect defect(UUID id, UUID repairRequestId, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setCode("DEF-2026-1001");
        defect.setTitle("Leak");
        defect.setStatus(status);
        defect.setSeverity("HIGH");
        defect.setRepairRequestId(repairRequestId);
        return defect;
    }

    private EquipmentNode equipmentNode(UUID id, UUID equipmentId, String code, String name) {
        EquipmentNode node = new EquipmentNode();
        node.setId(id);
        node.setEquipmentId(equipmentId);
        node.setCode(code);
        node.setName(name);
        node.setNodeType(EquipmentNodeType.COMPONENT);
        return node;
    }

    private BrigadeMember brigadeMember(UUID id, UUID userId, UUID departmentId, boolean memberActive, boolean brigadeActive) {
        Brigade brigade = new Brigade();
        brigade.setId(UUID.randomUUID());
        brigade.setCode("BRG-1");
        brigade.setName("Repair brigade");
        brigade.setDepartmentId(departmentId);
        brigade.setActive(brigadeActive);

        BrigadeMember member = new BrigadeMember();
        member.setId(id);
        member.setBrigade(brigade);
        member.setUserId(userId);
        member.setRoleCode("MECHANIC");
        member.setActive(memberActive);
        return member;
    }

    private User user(UUID id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id.toString().substring(0, 8));
        user.setEmail(id.toString().substring(0, 8) + "@example.com");
        user.setFullName(fullName);
        user.setPasswordHash("hash");
        return user;
    }

    private Counteragent counteragent(UUID id, String code, String name) {
        Counteragent counteragent = new Counteragent();
        counteragent.setId(id);
        counteragent.setCode(code);
        counteragent.setName(name);
        counteragent.setStatus(CounteragentStatus.ACTIVE);
        return counteragent;
    }

    private void mockSuccessfulCreateDependencies(WorkOrderRequest request) {
        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");
        sourceEquipment.setDepartmentId(request.departmentId());

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findByIdAndIsDeletedFalse(eq(request.equipmentId()))).thenReturn(Optional.of(sourceEquipment));
        when(equipmentRepository.findById(eq(request.equipmentId()))).thenReturn(Optional.of(sourceEquipment));
        when(departmentRepository.findById(eq(request.departmentId()))).thenReturn(Optional.of(department));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    private void mockCreateEquipmentScope(WorkOrderRequest request) {
        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setDepartmentId(request.departmentId());

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(equipmentRepository.findByIdAndIsDeletedFalse(eq(request.equipmentId()))).thenReturn(Optional.of(sourceEquipment));
        when(departmentRepository.findById(eq(request.departmentId()))).thenReturn(Optional.of(department));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    private WorkOrder lifecycleWorkOrder(UUID id,
                                         WorkType workType,
                                         WorkOrderStatus status,
                                         UUID warehouseId,
                                         UUID replacementEquipmentId) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setNumber("WO-LIFE-" + id);
        workOrder.setTitle("Lifecycle test");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(workType);
        workOrder.setStatus(status);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setWarehouseId(warehouseId);
        workOrder.setReplacementEquipmentId(replacementEquipmentId);
        return workOrder;
    }

    private MaintenanceDueEvent dueEvent(UUID id, UUID equipmentId, UUID regulationId, UUID ruleId) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", id);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setEquipmentMaintenanceRuleId(ruleId);
        event.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        event.setCycleKey("EQ:RULE:METER:ENGINE_HOURS:500");
        event.setDueAt(java.time.Instant.parse("2026-06-03T10:00:00Z"));
        event.setDetectedAt(java.time.Instant.parse("2026-06-03T09:55:00Z"));
        event.setMeterType(MeterType.ENGINE_HOURS);
        event.setMeterAnchorValue(0.0);
        event.setMeterInterval(500.0);
        event.setMeterCurrentValue(520.0);
        event.setMeterRemaining(0.0);
        return event;
    }

    private WarehouseEquipmentItem warehouseItem(UUID warehouseId, UUID equipmentId, WarehouseEquipmentStatus status) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(status);
        item.setActive(true);
        item.setDeleted(false);
        return item;
    }

    private WorkOrderTask workOrderTask(WorkOrder workOrder, String title, TaskExecutionStatus status) {
        WorkOrderTask task = new WorkOrderTask();
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        task.setWorkOrder(workOrder);
        task.setTitle(title);
        task.setStatus(status);
        return task;
    }

    private WorkOrderTask task(WorkOrder workOrder, UUID sourceOperationId) {
        WorkOrderTask task = workOrderTask(workOrder, "Generated operation", TaskExecutionStatus.TODO);
        task.setSourceOperationId(sourceOperationId);
        return task;
    }

    private FileAsset fileAsset(UUID id) {
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(id);
        fileAsset.setFileName(id + ".pdf");
        fileAsset.setOriginalName("act.pdf");
        fileAsset.setMimeType("application/pdf");
        fileAsset.setSizeBytes(128);
        fileAsset.setStoragePath("/tmp/" + id + ".pdf");
        return fileAsset;
    }

    private MaintenanceOperation operation(UUID id, String requiredSkill) {
        MaintenanceOperation operation = new MaintenanceOperation();
        ReflectionTestUtils.setField(operation, "id", id);
        operation.setSequence(1);
        operation.setName("Generated operation");
        operation.setRequiredSkill(requiredSkill);
        return operation;
    }

    private MaintenanceTemplate maintenanceTemplate(UUID id, String code, String name) {
        MaintenanceTemplate template = new MaintenanceTemplate();
        ReflectionTestUtils.setField(template, "id", id);
        template.setCode(code);
        template.setName(name);
        return template;
    }

    private MaintenanceAction maintenanceAction(String code, String name) {
        MaintenanceAction action = new MaintenanceAction();
        ReflectionTestUtils.setField(action, "id", UUID.randomUUID());
        action.setCode(code);
        action.setName(name);
        action.setActive(true);
        return action;
    }

    private UserCertification certification(UUID userId, String typeCode, String status, LocalDate expiresAt) {
        UserCertification certification = new UserCertification();
        ReflectionTestUtils.setField(certification, "id", UUID.randomUUID());
        certification.setUserId(userId);
        certification.setTypeCode(typeCode);
        certification.setCertificateNumber("CERT-" + typeCode);
        certification.setIssuedAt(LocalDate.now().minusYears(1));
        certification.setExpiresAt(expiresAt);
        certification.setStatus(status);
        return certification;
    }

    private CertificationType certificationType(String code, String name) {
        CertificationType type = new CertificationType();
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        type.setCode(code);
        type.setName(name);
        return type;
    }

    private SafetyPermit safetyPermit(UUID workOrderId, SafetyPermitStatus status) {
        SafetyPermit permit = new SafetyPermit();
        permit.setId(UUID.randomUUID());
        permit.setWorkOrderId(workOrderId);
        permit.setPermitNumber("SP-" + workOrderId.toString().substring(0, 8));
        permit.setStatus(status);
        return permit;
    }

    private CompletionAct completionAct(UUID workOrderId, boolean signed) {
        CompletionAct act = new CompletionAct();
        act.setId(UUID.randomUUID());
        act.setWorkOrderId(workOrderId);
        act.setActNumber("CA-" + workOrderId.toString().substring(0, 8));
        if (signed) {
            act.setSignedById(UUID.randomUUID());
            act.setSignedAt(java.time.Instant.now());
        }
        return act;
    }

    private void stubLifecycleDtoLookups(WorkOrder workOrder) {
        when(equipmentRepository.findById(workOrder.getEquipmentId())).thenReturn(Optional.empty());
        when(departmentRepository.findById(workOrder.getDepartmentId())).thenReturn(Optional.empty());
        if (workOrder.getStatus() == WorkOrderStatus.COMPLETED
                && (workOrder.getWorkType() == WorkType.REPAIR || workOrder.getWorkType() == WorkType.REPLACEMENT)) {
            when(repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrder.getId())).thenReturn(true);
            when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                    .thenReturn(List.of(laborEntry(workOrder.getId())));
            when(reservationRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                    .thenReturn(List.of());
        }
        if (workOrder.getReplacementEquipmentId() != null) {
            when(equipmentRepository.findById(workOrder.getReplacementEquipmentId())).thenReturn(Optional.empty());
        }
    }

    private LaborEntry laborEntry(UUID workOrderId) {
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(UUID.randomUUID());
        laborEntry.setWorkOrderId(workOrderId);
        laborEntry.setWorkDate(java.time.LocalDate.of(2026, 6, 6));
        laborEntry.setHours(1);
        return laborEntry;
    }
}
