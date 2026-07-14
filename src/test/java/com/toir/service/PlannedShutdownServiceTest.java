package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetReplaceRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownCreateRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownUpdateRequest;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.users.Employee;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownReadinessSeverity;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownReadinessItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownIsolationPointRepository;
import com.toir.repository.SafetyPermitRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import com.toir.service.plannedshutdown.PlannedShutdownReadinessPolicy;
import com.toir.service.repair.CanonicalWorkSourceResolver;
import com.toir.security.ScopeAccessService;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownServiceTest {

    @Mock
    private PlannedShutdownRepository repository;

    @Mock private PlannedShutdownAssetRepository assetRepository;
    @Mock private PlannedShutdownWorkItemRepository workItemRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private DefectRepository defectRepository;
    @Mock private PprTaskRepository pprTaskRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private CanonicalWorkSourceResolver canonicalWorkSourceResolver;
    @Mock private WorkOrderService workOrderService;
    @Mock private PlannedShutdownWorkItemPolicy workItemPolicy;
    @Mock private PlannedShutdownReadinessItemRepository readinessItemRepository;
    @Mock private PlannedShutdownIsolationPointRepository isolationPointRepository;
    @Mock private com.toir.repository.plannedshutdown.PlannedShutdownStatusHistoryRepository statusHistoryRepository;
    @Mock private com.toir.repository.ApprovalRequestRepository approvalRequestRepository;
    @Mock private PlannedShutdownReadinessPolicy readinessPolicy;
    @Mock private WorkOrderAssignmentEligibilityService workOrderAssignmentEligibilityService;
    @Mock private WorkOrderMaterialReadinessService workOrderMaterialReadinessService;
    @Mock private com.toir.service.plannedshutdown.PlannedShutdownEvidenceService evidenceService;
    @Mock private com.toir.service.plannedshutdown.PlannedShutdownReportService reportService;
    @Spy private com.toir.service.plannedshutdown.PlannedShutdownReadinessLifecyclePolicy readinessLifecyclePolicy =
            new com.toir.service.plannedshutdown.PlannedShutdownReadinessLifecyclePolicy();
    @Spy private com.toir.service.plannedshutdown.PlannedShutdownTransitionPolicy transitionPolicy =
            new com.toir.service.plannedshutdown.PlannedShutdownTransitionPolicy();
    @Spy private com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher approvalScopeHasher =
            new com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher();
    @Mock private SafetyPermitRepository safetyPermitRepository;
    @Mock private ScopeAccessService scopeAccessService;
    @Mock private AuditBuilderService auditBuilderService;

    @InjectMocks
    private PlannedShutdownService service;

    @BeforeEach
    void allowDefaultDepartmentScope() {
        lenient().when(scopeAccessService.canAccessDepartment(any())).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void departmentPbacRejectsUnrelatedDepartmentReadAndMutation() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 4L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        doThrow(new org.springframework.security.access.AccessDeniedException("scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(
                4L, "PS-100", "Annual", "PLANNED", departmentId, UUID.randomUUID(),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", null, null, null, null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void linkedWorkOrdersUsesAggregatePbacAndCanonicalShutdownQuery() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 4L, PlannedShutdownStatus.TESTING);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(workOrderService.findByPlannedShutdown(id, departmentId)).thenReturn(List.of());

        assertThat(service.linkedWorkOrders(id)).isEmpty();

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        verify(workOrderService).findByPlannedShutdown(id, departmentId);
    }

    @Test
    void createAndListEnforceCurrentDepartmentScopeInsideService() {
        UUID requestedDepartment = UUID.randomUUID();
        UUID currentDepartment = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("scope"))
                .when(scopeAccessService).assertCanAccessDepartment(requestedDepartment);
        when(scopeAccessService.enforceDepartmentScope(requestedDepartment)).thenReturn(currentDepartment);
        when(repository.findAllFiltered(eq(currentDepartment), isNull(), isNull())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new PlannedShutdownCreateRequest(
                "PS-100", "Annual", "PLANNED", requestedDepartment, UUID.randomUUID(),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", null, null, null, null,
                List.of(new PlannedShutdownAssetRequest(UUID.randomUUID(), PlannedShutdownAssetDisposition.STOPPED,
                        "main", 0)))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        service.findAllFiltered(requestedDepartment, null, null);
        verify(repository).findAllFiltered(currentDepartment, null, null);
    }

    @Test
    void completeAndReopenReadinessCaptureServerActorAndEvidence() {
        UUID id = UUID.randomUUID(); UUID itemId = UUID.randomUUID(); UUID actorId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 4L, PlannedShutdownStatus.READINESS_CHECK);
        var item = new com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem();
        item.setId(itemId); item.setPlannedShutdownId(id); item.setReadinessKey("HSE-CHECK");
        item.setTitle("HSE check"); item.setSeverity(com.toir.enums.PlannedShutdownReadinessSeverity.CRITICAL);
        item.setOrderNumber(0);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(readinessItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, id))
                .thenReturn(java.util.Optional.of(item));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(item));

        var completed = service.completeReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessActionRequest(4L, "photo:1", "checked"));

        assertThat(completed.items().get(0).status()).isEqualTo(com.toir.enums.PlannedShutdownItemStatus.PASSED);
        assertThat(item.getCompletedById()).isEqualTo(actorId);
        assertThat(item.getCompletedAt()).isNotNull();
        assertThat(item.getEvidence()).isEqualTo("photo:1");

        assertThatThrownBy(() -> service.updateReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessItemRequest(4L, "HSE-CHECK", null,
                        null, "Changed", com.toir.enums.PlannedShutdownReadinessSeverity.CRITICAL,
                        null, null, "replacement", null, 0)))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.completeReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessActionRequest(4L, "photo:2", "again")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        service.reopenReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessActionRequest(4L, null, "scope changed"));
        assertThat(item.getStatus()).isEqualTo(com.toir.enums.PlannedShutdownItemStatus.PENDING);
        assertThat(item.getCompletedById()).isNull();
        assertThat(item.getCompletedAt()).isNull();
        assertThat(item.getEvidence()).isNull();
    }

    @Test
    void startReadinessMovesPendingItemToInProgress() {
        UUID id = UUID.randomUUID(); UUID itemId = UUID.randomUUID(); UUID actorId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 4L, PlannedShutdownStatus.READINESS_CHECK);
        var item = new com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem();
        item.setId(itemId); item.setPlannedShutdownId(id); item.setReadinessKey("OPS-CHECK");
        item.setTitle("Operations check"); item.setSeverity(com.toir.enums.PlannedShutdownReadinessSeverity.WARNING);
        item.setStatus(com.toir.enums.PlannedShutdownItemStatus.PENDING); item.setOrderNumber(0);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(readinessItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, id))
                .thenReturn(java.util.Optional.of(item));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(item));

        var response = service.startReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessActionRequest(4L, "photo:start", "started"));

        assertThat(response.items().get(0).status()).isEqualTo(com.toir.enums.PlannedShutdownItemStatus.IN_PROGRESS);
        assertThat(item.getStatus()).isEqualTo(com.toir.enums.PlannedShutdownItemStatus.IN_PROGRESS);
        assertThat(item.getEvidence()).isEqualTo("photo:start");
        assertThat(item.getComment()).isEqualTo("started");
        assertThat(item.getCompletedAt()).isNull();
        assertThat(item.getCompletedById()).isNull();

        assertThatThrownBy(() -> service.startReadinessItem(id, itemId,
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessActionRequest(4L, null, "again")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void isolationApplyVerifyReleaseRequiresOrderedServerStampedActions() {
        UUID id = UUID.randomUUID(); UUID pointId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 2L, PlannedShutdownStatus.PREPARATION);
        var point = new com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint();
        point.setId(pointId); point.setPlannedShutdownId(id); point.setEquipmentId(UUID.randomUUID());
        point.setIsolationMethod("ELECTRICAL"); point.setLockTagIdentifier("LT-1");
        point.setResponsibleEmployeeId(UUID.randomUUID()); point.setOrderNumber(0);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(isolationPointRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(pointId, id))
                .thenReturn(java.util.Optional.of(point));
        when(scopeAccessService.currentEmployeeId()).thenReturn(java.util.Optional.of(actor));
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(point));

        service.applyIsolation(id, pointId, new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(2L));
        assertThatThrownBy(() -> service.verifyIsolation(id, pointId,
                new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(2L)))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        shutdown.setStatus(PlannedShutdownStatus.SHUTDOWN_STARTED);
        service.verifyIsolation(id, pointId, new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(2L));
        assertThatThrownBy(() -> service.releaseIsolation(id, pointId,
                new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(2L)))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        service.releaseIsolation(id, pointId, new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(2L));

        assertThat(point.getAppliedById()).isEqualTo(actor);
        assertThat(point.getVerifiedById()).isEqualTo(actor);
        assertThat(point.getReleasedById()).isEqualTo(actor);
        assertThat(point.getStatus()).isEqualTo(com.toir.enums.PlannedShutdownItemStatus.PASSED);
        assertThat(point.getReleasedAt()).isNotNull();
    }


    @Test
    void listIsolationReturnsEquipmentNameFromBackend() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 4L, PlannedShutdownStatus.SAFE_STATE);
        var point = new com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint();
        point.setId(UUID.randomUUID());
        point.setPlannedShutdownId(id);
        point.setEquipmentId(equipmentId);
        point.setIsolationMethod("Electrical disconnect");
        point.setLockTagIdentifier("LOTO-44");
        point.setResponsibleEmployeeId(UUID.randomUUID());
        point.setStatus(com.toir.enums.PlannedShutdownItemStatus.PENDING);
        point.setOrderNumber(0);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setName("Main compressor");

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(point));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of(equipment));

        var response = service.listIsolation(id);

        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).equipmentId()).isEqualTo(equipmentId);
        assertThat(response.points().get(0).equipmentName()).isEqualTo("Main compressor");
    }

    @Test
    void isolationRejectsUnrelatedNotIssuedFutureAndExpiredPermits() {
        UUID id = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        UUID permitId = UUID.randomUUID(); UUID workOrderId = UUID.randomUUID(); UUID workItemId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 2L, PlannedShutdownStatus.DRAFT);
        var asset = new com.toir.entity.plannedshutdown.PlannedShutdownAsset(); asset.setEquipmentId(equipmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setActive(true);
        var permit = new com.toir.entity.SafetyPermit(); permit.setId(permitId); permit.setWorkOrderId(workOrderId);
        var workOrder = new com.toir.entity.maintenance.WorkOrder(); workOrder.setId(workOrderId);
        workOrder.setPlannedShutdownId(id); workOrder.setShutdownWorkItemId(workItemId); workOrder.setEquipmentId(equipmentId);
        var workItem = new com.toir.entity.plannedshutdown.PlannedShutdownWorkItem(); workItem.setId(workItemId);
        workItem.setEquipmentId(equipmentId);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(asset));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(safetyPermitRepository.findByIdAndIsDeletedFalse(permitId)).thenReturn(java.util.Optional.of(permit));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(java.util.Optional.of(workOrder));
        when(workItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(workItemId, id))
                .thenReturn(java.util.Optional.of(workItem));
        var request = new com.toir.dto.plannedshutdown.PlannedShutdownIsolationPointRequest(2L, equipmentId,
                null, "ELECTRICAL", "LT-7", employeeId, permitId, 0);

        permit.setStatus(com.toir.enums.SafetyPermitStatus.DRAFT);
        assertThatThrownBy(() -> service.addIsolationPoint(id, request)).isInstanceOf(RestException.class);
        permit.setStatus(com.toir.enums.SafetyPermitStatus.ISSUED);
        permit.setIssuedAt(Instant.now().plusSeconds(600));
        assertThatThrownBy(() -> service.addIsolationPoint(id, request)).isInstanceOf(RestException.class);
        permit.setIssuedAt(Instant.now().minusSeconds(600)); permit.setValidUntil(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.addIsolationPoint(id, request)).isInstanceOf(RestException.class);
        permit.setValidUntil(Instant.now().plusSeconds(600)); workOrder.setPlannedShutdownId(UUID.randomUUID());
        assertThatThrownBy(() -> service.addIsolationPoint(id, request)).isInstanceOf(RestException.class);

        workOrder.setPlannedShutdownId(id);
        when(isolationPointRepository.saveAndFlush(any())).thenAnswer(inv -> {
            var point = (com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint) inv.getArgument(0);
            point.setId(UUID.randomUUID()); return point;
        });
        when(repository.saveAndFlush(shutdown)).thenReturn(shutdown);
        assertThat(service.addIsolationPoint(id, request).plannedShutdownId()).isEqualTo(id);
    }

    @Test
    void readinessAssessmentRejectsInactiveOwnerAndIneligibleCurrentAssignment() {
        UUID id = UUID.randomUUID(); UUID department = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        UUID equipment = UUID.randomUUID(); UUID workItemId = UUID.randomUUID(); UUID workOrderId = UUID.randomUUID();
        UUID permitId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, department, 3L, PlannedShutdownStatus.APPROVED);
        shutdown.setResponsibleEmployeeId(owner); shutdown.setScopeVersion(4L); shutdown.setApprovalScopeVersion(4L);
        shutdown.setApprovedStartAt(Instant.now().minusSeconds(60)); shutdown.setApprovedEndAt(Instant.now().plusSeconds(600));
        Employee inactive = new Employee(); inactive.setId(owner); inactive.setDepartmentId(department); inactive.setActive(false);
        var asset = new com.toir.entity.plannedshutdown.PlannedShutdownAsset(); asset.setEquipmentId(equipment);
        asset.setDisposition(PlannedShutdownAssetDisposition.STOPPED);
        var item = new com.toir.entity.plannedshutdown.PlannedShutdownWorkItem(); item.setId(workItemId);
        item.setSourceType(com.toir.enums.PlannedShutdownWorkItemSourceType.WORK_ORDER);
        item.setSourceId(workOrderId); item.setEquipmentId(equipment);
        var workOrder = new com.toir.entity.maintenance.WorkOrder(); workOrder.setId(workOrderId);
        workOrder.setEquipmentId(equipment); workOrder.setPlannedShutdownId(UUID.randomUUID());
        workOrder.setShutdownWorkItemId(workItemId);
        var readiness = new com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem();
        readiness.setSeverity(com.toir.enums.PlannedShutdownReadinessSeverity.CRITICAL);
        readiness.setStatus(com.toir.enums.PlannedShutdownItemStatus.PASSED);
        var point = new com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint();
        point.setId(UUID.randomUUID()); point.setEquipmentId(equipment); point.setPermitId(permitId);
        var permit = new com.toir.entity.SafetyPermit(); permit.setId(permitId); permit.setWorkOrderId(workOrderId);
        permit.setStatus(com.toir.enums.SafetyPermitStatus.ISSUED); permit.setIssuedAt(Instant.now().minusSeconds(60));
        permit.setValidUntil(Instant.now().plusSeconds(600));
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(employeeRepository.findByIdAndIsDeletedFalse(owner)).thenReturn(java.util.Optional.of(inactive));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(asset));
        when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(item));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(java.util.Optional.of(workOrder));
        when(workOrderAssignmentEligibilityService.isCurrentlyEligible(workOrder)).thenReturn(false);
        when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(readiness));
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(point));
        when(safetyPermitRepository.findByIdAndIsDeletedFalse(permitId)).thenReturn(java.util.Optional.of(permit));
        when(readinessPolicy.evaluateReadiness(any())).thenAnswer(inv ->
                new com.toir.service.plannedshutdown.PlannedShutdownReadinessPolicy()
                        .evaluateReadiness(inv.getArgument(0)));

        var result = service.assessReadiness(id, Instant.now());

        assertThat(result.blockers()).extracting(com.toir.dto.plannedshutdown.PlannedShutdownBlocker::code)
                .contains("OWNER_MISSING", "PERFORMER_OR_CONTRACTOR_MISSING", "PERMIT_INACTIVE");
    }

    @Test
    void findAllFilteredAppliesFiltersCorrectly() {
        UUID departmentId = UUID.randomUUID();

        PlannedShutdown s1 = new PlannedShutdown();
        s1.setId(UUID.randomUUID());
        s1.setCode("PS-2026-0001");
        s1.setName("Annual Maintenance");
        s1.setShutdownType("FULL_PRODUCTION");
        s1.setDepartmentId(departmentId);
        UUID responsibleEmployeeId = UUID.randomUUID();
        s1.setResponsibleEmployeeId(responsibleEmployeeId);
        s1.setStartAt(Instant.parse("2026-05-19T10:00:00Z"));
        s1.setEndAt(Instant.parse("2026-05-19T18:00:00Z"));
        s1.setReason("Routine check");
        s1.setRiskLevel("HIGH");
        s1.setRiskScore(new java.math.BigDecimal("8.5000"));
        s1.setStatus(PlannedShutdownStatus.DRAFT);

        when(repository.findAllFiltered(departmentId, "DRAFT", "%annual%")).thenReturn(List.of(s1));

        List<PlannedShutdownDto> results = service.findAllFiltered(departmentId, PlannedShutdownStatus.DRAFT, "annual");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Annual Maintenance");
        assertThat(results.get(0).code()).isEqualTo("PS-2026-0001");
        assertThat(results.get(0).shutdownType()).isEqualTo("FULL_PRODUCTION");
        assertThat(results.get(0).responsibleEmployeeId()).isEqualTo(responsibleEmployeeId);
        assertThat(results.get(0).riskLevel()).isEqualTo("HIGH");
        assertThat(results.get(0).riskScore()).isEqualByComparingTo("8.5000");
        assertThat(results.get(0).status()).isEqualTo(PlannedShutdownStatus.DRAFT);
    }

    @Test
    void createValidatesOwnerWindowAndGeneratesUniqueCode() {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment(equipmentId, departmentId)));
        when(repository.existsByCodeAndIsDeletedFalse(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> { PlannedShutdown s = invocation.getArgument(0); if (s.getId() == null) s.setId(UUID.randomUUID()); s.setVersion(s.getScopeVersion()); return s; });
        when(assetRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(new PlannedShutdownCreateRequest(null, "Annual", "PLANNED", departmentId,
                employeeId, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", "Safe overhaul", null, "HIGH", new java.math.BigDecimal("7.5"), List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.STOPPED, "main", 0))));

        assertThat(result.code()).startsWith("PS-");
        assertThat(result.status()).isEqualTo(PlannedShutdownStatus.DRAFT);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.scopeVersion()).isEqualTo(1L);
        assertThat(result.assets()).extracting(a -> a.equipmentId()).containsExactly(equipmentId);
        verify(repository).existsByCodeAndIsDeletedFalse(result.code());
    }

    @Test
    void createRejectsUnknownOrCrossDepartmentResponsibleEmployee() {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(UUID.randomUUID()); employee.setActive(true);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));

        assertThatThrownBy(() -> service.create(request(departmentId, employeeId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("department");
                });
    }

    @Test
    void createRejectsDuplicateCodeAndInvalidWindow() {
        UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new PlannedShutdownCreateRequest("PS-X", "Annual", "PLANNED",
                departmentId, employeeId, Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"), "Maintenance", null, null, null, null, List.of())))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("after start"));

        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(repository.existsByCodeAndIsDeletedFalse("PS-CUSTOM")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(departmentId, employeeId)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void saveClassifiesOnlyTheNamedActiveCodeConstraintAsConflict() {
        UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment(equipmentId, departmentId)));
        when(repository.existsByCodeAndIsDeletedFalse("PS-RACE")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert failed",
                new RuntimeException("duplicate key violates constraint uq_planned_shutdowns_active_code")));

        assertThatThrownBy(() -> service.create(createRequest("PS-RACE", departmentId, employeeId, equipmentId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("Planned shutdown code already exists: PS-RACE");
                });
    }

    @Test
    void saveDoesNotMaskUnrelatedIntegrityFailures() {
        UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("violates chk_planned_shutdown_window");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment(equipmentId, departmentId)));
        when(repository.existsByCodeAndIsDeletedFalse("PS-OTHER")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.create(createRequest("PS-OTHER", departmentId, employeeId, equipmentId)))
                .isSameAs(failure);
    }

    @Test
    void updateClassifiesNamedActiveCodeConstraintAsConflict() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 2L, PlannedShutdownStatus.DRAFT);
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(repository.existsByCodeAndIdNotAndIsDeletedFalse("PS-CHANGED", id)).thenReturn(false);
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of());
        when(repository.saveAndFlush(shutdown)).thenThrow(new DataIntegrityViolationException(
                "constraint uq_planned_shutdowns_active_code"));

        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(2L, "PS-CHANGED", "Annual",
                "PLANNED", departmentId, employeeId, shutdown.getStartAt(), shutdown.getEndAt(), "Maintenance",
                null, null, null, null)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createRejectsMissingAndInactiveResponsibleEmployees() {
        UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        Department department = new Department(); department.setId(departmentId);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.create(request(departmentId, employeeId)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("not found"));

        Employee inactive = new Employee(); inactive.setId(employeeId); inactive.setDepartmentId(departmentId); inactive.setActive(false);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(inactive));
        assertThatThrownBy(() -> service.create(request(departmentId, employeeId)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("active"));
    }

    @Test
    void getReturnsTypedDetailWithPersistedScope() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 5L, PlannedShutdownStatus.SCOPE_FORMATION);
        shutdown.setScopeVersion(3L);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(asset(id, equipmentId, PlannedShutdownAssetDisposition.STOPPED, 0)));

        var response = service.get(id);
        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.SCOPE_FORMATION);
        assertThat(response.version()).isEqualTo(5L);
        assertThat(response.scopeVersion()).isEqualTo(3L);
        assertThat(response.assets()).extracting(a -> a.equipmentId()).containsExactly(equipmentId);
    }

    @Test
    void getReturnsSummaryAndNextActionForDetailHeader() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 5L, PlannedShutdownStatus.SCOPE_FORMATION);
        var workItem = new com.toir.entity.plannedshutdown.PlannedShutdownWorkItem();
        workItem.setId(UUID.randomUUID()); workItem.setPlannedShutdownId(id); workItem.setEquipmentId(equipmentId);
        workItem.setTitle("Replace seal"); workItem.setStatus(com.toir.enums.PlannedShutdownItemStatus.PENDING);
        var readinessPassed = new com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem();
        readinessPassed.setSeverity(PlannedShutdownReadinessSeverity.CRITICAL);
        readinessPassed.setStatus(com.toir.enums.PlannedShutdownItemStatus.PASSED);
        var readinessCritical = new com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem();
        readinessCritical.setSeverity(com.toir.enums.PlannedShutdownReadinessSeverity.CRITICAL);
        readinessCritical.setStatus(com.toir.enums.PlannedShutdownItemStatus.PENDING);
        var isolation = new com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint();
        isolation.setVerifiedAt(Instant.parse("2026-08-01T01:00:00Z"));
        var workOrder = new com.toir.entity.maintenance.WorkOrder();
        workOrder.setId(UUID.randomUUID()); workOrder.setPlannedShutdownId(id);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(asset(id, equipmentId, PlannedShutdownAssetDisposition.STOPPED, 0)));
        when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(workItem));
        when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(readinessPassed, readinessCritical));
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(isolation));
        when(workOrderRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of(workOrder));

        var response = service.get(id);

        assertThat(response.summary().equipmentCount()).isEqualTo(1);
        assertThat(response.summary().workItemCount()).isEqualTo(1);
        assertThat(response.summary().workOrderCount()).isEqualTo(1);
        assertThat(response.summary().readinessItemCount()).isEqualTo(2);
        assertThat(response.summary().readinessPassedCount()).isEqualTo(1);
        assertThat(response.summary().readinessCriticalOpenCount()).isEqualTo(1);
        assertThat(response.summary().isolationPointCount()).isEqualTo(1);
        assertThat(response.summary().isolationVerifiedCount()).isEqualTo(1);
        assertThat(response.nextAction().code()).isEqualTo("BEGIN_READINESS");
        assertThat(response.nextAction().commandEndpoint()).isEqualTo("begin-readiness");
        assertThat(response.nextAction().targetTab()).isEqualTo("readiness");
        assertThat(response.nextAction().blocked()).isFalse();
    }

    @Test
    void updateUsesLockedAggregateAndRejectsStaleVersion() {
        UUID id = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 4L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));

        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(3L, shutdown.getCode(),
                shutdown.getName(), shutdown.getShutdownType(), shutdown.getDepartmentId(), shutdown.getResponsibleEmployeeId(),
                shutdown.getStartAt(), shutdown.getEndAt(), shutdown.getReason(), null, null, null, null)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void metadataUpdateIsPreApprovalOnlyAndInvalidatesScopeSnapshot() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 4L, PlannedShutdownStatus.APPROVED);
        shutdown.setResponsibleEmployeeId(employeeId);
        shutdown.setApprovalScopeVersion(2L); shutdown.setApprovalScopeHash("a".repeat(64));
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        PlannedShutdownUpdateRequest request = new PlannedShutdownUpdateRequest(4L, shutdown.getCode(),
                shutdown.getName(), shutdown.getShutdownType(), departmentId, employeeId, shutdown.getStartAt(),
                shutdown.getEndAt(), shutdown.getReason(), null, null, null, null);

        assertThatThrownBy(() -> service.update(id, request))
                .hasMessageContaining("METADATA_UPDATE_NOT_ALLOWED:APPROVED");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateFlushesAndReturnsTheNewOptimisticVersion() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 4L, PlannedShutdownStatus.DRAFT);
        shutdown.setResponsibleEmployeeId(employeeId);
        Department department = new Department(); department.setId(departmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(departmentId); employee.setActive(true);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(repository.existsByCodeAndIdNotAndIsDeletedFalse("PS-TEST", id)).thenReturn(false);
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of());
        when(repository.saveAndFlush(shutdown)).thenAnswer(inv -> { shutdown.setVersion(5L); return shutdown; });

        var response = service.update(id, new PlannedShutdownUpdateRequest(4L, "PS-TEST", "Annual updated", "PLANNED",
                departmentId, employeeId, shutdown.getStartAt(), shutdown.getEndAt(), "Maintenance", null, null, null, null));

        assertThat(response.version()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Annual updated");
        assertThat(response.scopeVersion()).isEqualTo(1L);
        assertThat(response.approvalScopeVersion()).isNull();
        assertThat(response.approvalScopeHash()).isNull();
        verify(repository).saveAndFlush(shutdown);
    }

    @Test
    void replaceScopeKeepsStableRowsAndSoftDeletesRemovedRows() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID();
        UUID keptEquipmentId = UUID.randomUUID(); UUID removedEquipmentId = UUID.randomUUID(); UUID addedEquipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 2L, PlannedShutdownStatus.SCOPE_FORMATION);
        shutdown.setApprovalScopeVersion(0L);
        shutdown.setApprovalScopeHash("a".repeat(64));
        var kept = asset(id, keptEquipmentId, PlannedShutdownAssetDisposition.STOPPED, 0);
        kept.setInclusionReason("old reason");
        var removed = asset(id, removedEquipmentId, PlannedShutdownAssetDisposition.RESERVE, 1);
        Equipment keptEquipment = equipment(keptEquipmentId, departmentId);
        Equipment addedEquipment = equipment(addedEquipmentId, departmentId);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id)).thenReturn(List.of(kept, removed));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(keptEquipment, addedEquipment));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(keptEquipmentId, PlannedShutdownAssetDisposition.STOPPED, "primary", 0),
                new PlannedShutdownAssetRequest(addedEquipmentId, PlannedShutdownAssetDisposition.RUNNING, "support", 1))));

        assertThat(response.scopeVersion()).isEqualTo(1L);
        assertThat(shutdown.getApprovalScopeVersion()).isNull();
        assertThat(shutdown.getApprovalScopeHash()).isNull();
        assertThat(response.assets()).extracting(a -> a.id()).contains(kept.getId());
        assertThat(removed.isDeleted()).isTrue();
        assertThat(kept.getInclusionReason()).isEqualTo("primary");
        ArgumentCaptor<Object> oldSnapshot = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> newSnapshot = ArgumentCaptor.forClass(Object.class);
        verify(auditBuilderService).log(eq("planned_shutdown_scope"), eq(id.toString()), any(), any(), any(),
                oldSnapshot.capture(), newSnapshot.capture());
        assertThat(oldSnapshot.getValue().toString()).contains("old reason").doesNotContain("primary");
        assertThat(newSnapshot.getValue().toString()).contains("primary").doesNotContain("old reason");
    }

    @Test
    void replaceScopeRejectsDuplicatesMissingBoundaryAndForeignDepartmentEquipment() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 2L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));

        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.RUNNING, null, 0),
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.RUNNING, null, 1)))))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("Duplicate"));

        UUID secondEquipmentId = UUID.randomUUID();
        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.STOPPED, null, 0),
                new PlannedShutdownAssetRequest(secondEquipmentId, PlannedShutdownAssetDisposition.RESERVE, null, 0)))))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("order"));

        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.RUNNING, null, 0)))))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("STOPPED or RESERVE"));

        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment(equipmentId, UUID.randomUUID())));
        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.STOPPED, null, 0)))))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("department"));
    }

    @Test
    void replaceScopeRejectsMissingEquipment() {
        UUID id = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 2L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.STOPPED, null, 0)))))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("not found"));
    }

    @Test
    void updateCannotTransferShutdownToUnauthorizedTargetDepartment() {
        UUID id = UUID.randomUUID(); UUID sourceDepartment = UUID.randomUUID();
        UUID targetDepartment = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, sourceDepartment, 4L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        doAnswer(invocation -> {
            if (targetDepartment.equals(invocation.getArgument(0))) {
                throw new org.springframework.security.access.AccessDeniedException("target scope");
            }
            return null;
        }).when(scopeAccessService).assertCanAccessDepartment(any());

        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(4L, "PS-TEST", "Annual",
                "PLANNED", targetDepartment, employeeId, shutdown.getStartAt(), shutdown.getEndAt(),
                "Maintenance", null, null, null, null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(departmentRepository, employeeRepository, equipmentRepository);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateCannotTransferToUnauthorizedDepartmentEvenWhenAssetsWouldBeCompatible() {
        UUID id = UUID.randomUUID(); UUID sourceDepartment = UUID.randomUUID();
        UUID targetDepartment = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, sourceDepartment, 4L, PlannedShutdownStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        doAnswer(invocation -> {
            if (targetDepartment.equals(invocation.getArgument(0))) {
                throw new org.springframework.security.access.AccessDeniedException("target scope");
            }
            return null;
        }).when(scopeAccessService).assertCanAccessDepartment(any());
        var compatible = asset(id, UUID.randomUUID(), PlannedShutdownAssetDisposition.STOPPED, 0);
        lenient().when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(compatible));

        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(4L, "PS-TEST", "Annual",
                "PLANNED", targetDepartment, employeeId, shutdown.getStartAt(), shutdown.getEndAt(),
                "Maintenance", null, null, null, null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(assetRepository, never()).findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsDepartmentChangeThatConflictsWithExistingScope() {
        UUID id = UUID.randomUUID(); UUID oldDepartmentId = UUID.randomUUID(); UUID newDepartmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, oldDepartmentId, 2L, PlannedShutdownStatus.DRAFT);
        Department department = new Department(); department.setId(newDepartmentId);
        Employee employee = new Employee(); employee.setId(employeeId); employee.setDepartmentId(newDepartmentId); employee.setActive(true);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        when(departmentRepository.findByIdAndIsDeletedFalse(newDepartmentId)).thenReturn(java.util.Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(java.util.Optional.of(employee));
        when(repository.existsByCodeAndIdNotAndIsDeletedFalse("PS-TEST", id)).thenReturn(false);
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(asset(id, equipmentId, PlannedShutdownAssetDisposition.STOPPED, 0)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment(equipmentId, oldDepartmentId)));

        assertThatThrownBy(() -> service.update(id, new PlannedShutdownUpdateRequest(2L, "PS-TEST", "Annual", "PLANNED",
                newDepartmentId, employeeId, shutdown.getStartAt(), shutdown.getEndAt(), "Maintenance", null, null, null, null)))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getMessage()).contains("department"));
    }

    @Test
    void replaceScopeRejectsImmutableLifecycleStatus() {
        UUID id = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 2L, PlannedShutdownStatus.READINESS_CHECK);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(java.util.Optional.of(shutdown));
        assertThatThrownBy(() -> service.replaceAssets(id, new PlannedShutdownAssetReplaceRequest(2L, List.of())))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static PlannedShutdownCreateRequest request(UUID departmentId, UUID employeeId) {
        return new PlannedShutdownCreateRequest("PS-CUSTOM", "Annual", "PLANNED", departmentId, employeeId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", null, null, null, null, List.of(
                new PlannedShutdownAssetRequest(UUID.randomUUID(), PlannedShutdownAssetDisposition.STOPPED, null, 0)));
    }

    @Test
    void assessReadinessEnrichesBlockersWithLabelsUrlsAndActionHints() {
        UUID id = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, UUID.randomUUID(), 4L, PlannedShutdownStatus.READINESS_CHECK);
        shutdown.setResponsibleEmployeeId(null);
        var workItem = new com.toir.entity.plannedshutdown.PlannedShutdownWorkItem();
        workItem.setId(workItemId);
        workItem.setPlannedShutdownId(id);
        workItem.setTitle("Replace compressor seal");

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(java.util.Optional.of(shutdown));
        when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        when(readinessPolicy.evaluateReadiness(any())).thenReturn(new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(
                false, List.of(new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                        "PERFORMER_OR_CONTRACTOR_MISSING", "Work has no eligible performer or contractor",
                        "WORK_ITEM", workItemId))));
        when(workItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(workItemId, id))
                .thenReturn(java.util.Optional.of(workItem));

        var assessment = service.assessReadiness(id, Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(assessment.blockers()).hasSize(1);
        var blocker = assessment.blockers().get(0);
        assertThat(blocker.entityLabel()).isEqualTo("Replace compressor seal");
        assertThat(blocker.entityUrl()).isEqualTo("/planned-shutdowns/" + id + "?tab=work");
        assertThat(blocker.actionHintCode()).isEqualTo("PERFORMER_OR_CONTRACTOR_MISSING");
        assertThat(blocker.entityId()).isEqualTo(workItemId);
    }

    private static PlannedShutdownCreateRequest createRequest(String code, UUID departmentId, UUID employeeId,
            UUID equipmentId) {
        return new PlannedShutdownCreateRequest(code, "Annual", "PLANNED", departmentId, employeeId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", null, null, null, null, List.of(
                new PlannedShutdownAssetRequest(equipmentId, PlannedShutdownAssetDisposition.STOPPED, null, 0)));
    }

    private static PlannedShutdown shutdown(UUID id, UUID departmentId, long version, PlannedShutdownStatus status) {
        PlannedShutdown s = new PlannedShutdown(); s.setId(id); s.setVersion(version); s.setCode("PS-TEST");
        s.setName("Annual"); s.setShutdownType("PLANNED"); s.setDepartmentId(departmentId); s.setResponsibleEmployeeId(UUID.randomUUID());
        s.setStartAt(Instant.parse("2026-08-01T00:00:00Z")); s.setEndAt(Instant.parse("2026-08-02T00:00:00Z"));
        s.setPlannedStartAt(s.getStartAt()); s.setPlannedEndAt(s.getEndAt()); s.setReason("Maintenance"); s.setStatus(status); s.setScopeVersion(0L); return s;
    }

    private static com.toir.entity.plannedshutdown.PlannedShutdownAsset asset(UUID shutdownId, UUID equipmentId,
            PlannedShutdownAssetDisposition disposition, int order) {
        var a = new com.toir.entity.plannedshutdown.PlannedShutdownAsset(); a.setId(UUID.randomUUID()); a.setPlannedShutdownId(shutdownId);
        a.setEquipmentId(equipmentId); a.setDisposition(disposition); a.setOrderNumber(order); return a;
    }

    private static Equipment equipment(UUID id, UUID departmentId) {
        Equipment e = new Equipment(); e.setId(id); e.setDepartmentId(departmentId); e.setResponsibleDepartmentId(departmentId); return e;
    }
}
