package com.toir.service;

import com.toir.entity.Counteragent;
import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewEvent;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.CounteragentStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.dto.notification.NotificationDto;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualCostReviewFacadeServiceTest {

    @Mock
    ActualCostRepository actualCostRepository;
    @Mock
    ActualCostService actualCostService;
    @Mock
    FinanceScopeService financeScopeService;
    @Mock
    NotificationService notificationService;
    @Mock
    ActualCostReviewRouteOverrideService routeOverrideService;
    @Mock
    ActualCostReviewRouteOverrideRepository routeOverrideRepository;
    @Mock
    ActualCostReviewEventRepository eventRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    ContractorWorkRepository contractorWorkRepository;
    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    CostCategoryRepository costCategoryRepository;
    @Mock
    FinancialApprovalRuleRepository financialApprovalRuleRepository;
    @Mock
    CounteragentService counteragentService;
    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    ActualCostReviewFacadeService service;

    @Test
    void actualCostRegisterEnrichesDepartmentContractorWorkOrderAndCostCategoryRefs() {
        UUID actualCostId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID counteragentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", actualCostId);
        actualCost.setContractorWorkId(contractorWorkId);
        actualCost.setCostCategoryId(costCategoryId);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(100.0);
        actualCost.setCostDate(Instant.parse("2026-05-26T09:00:00Z"));
        actualCost.setNotes("pump");

        ContractorWork contractorWork = new ContractorWork();
        ReflectionTestUtils.setField(contractorWork, "id", contractorWorkId);
        contractorWork.setCounteragentId(counteragentId);
        contractorWork.setWorkOrderId(workOrderId);
        contractorWork.setDescription("Pump contractor work");
        contractorWork.setStatus(ContractorWorkStatus.IN_PROGRESS);
        contractorWork.setCost(100.0);

        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", workOrderId);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Pump repair");
        workOrder.setDepartmentId(departmentId);

        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", departmentId);
        department.setCode("D-1");
        department.setName("Mechanical");

        CostCategory costCategory = new CostCategory();
        ReflectionTestUtils.setField(costCategory, "id", costCategoryId);
        costCategory.setCode("CC-1");
        costCategory.setName("Service");

        when(actualCostRepository.findAllByFiltersOrderByUpdatedAtDesc(null, "pump"))
                .thenReturn(List.of(actualCost));
        when(financeScopeService.filterActualCosts(List.of(actualCost))).thenReturn(List.of(actualCost));
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId))
                .thenReturn(Optional.empty());
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)).thenReturn(Optional.of(contractorWork));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department));
        when(costCategoryRepository.findByIdAndIsDeletedFalse(costCategoryId)).thenReturn(Optional.of(costCategory));
        when(counteragentService.load(counteragentId)).thenReturn(counteragent(counteragentId, "CA-1", "Counteragent"));

        var item = service.actualCostRegister("pump").getFirst();

        assertThat(item.department()).hasFieldOrPropertyWithValue("id", departmentId);
        assertThat(item.workOrder()).hasFieldOrPropertyWithValue("id", workOrderId);
        assertThat(item.costCategory()).hasFieldOrPropertyWithValue("id", costCategoryId);
        assertThat(item.counteragentWork()).hasFieldOrPropertyWithValue("id", contractorWorkId);
        Object counteragent = ((com.toir.dto.financialreview.ActualCostReviewItem.CounteragentWorkRef) item.counteragentWork()).counteragent();
        assertThat(counteragent).hasFieldOrPropertyWithValue("id", counteragentId);
    }

    private Counteragent counteragent(UUID id, String code, String name) {
        Counteragent counteragent = new Counteragent();
        ReflectionTestUtils.setField(counteragent, "id", id);
        counteragent.setCode(code);
        counteragent.setName(name);
        counteragent.setStatus(CounteragentStatus.ACTIVE);
        return counteragent;
    }

    @Test
    void reviewQueueUsesMatchingFinancialApprovalRuleWhenNoOverrideExists() {
        UUID actualCostId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(actualCostId, workOrderId, 750.0, Instant.now().minusSeconds(3600));
        WorkOrder workOrder = workOrder(workOrderId, departmentId);
        Department department = department(departmentId);
        FinancialApprovalRule rule = new FinancialApprovalRule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setCode("MECH_GT_500");
        rule.setRequiredRoleCode("ECONOMIST");
        rule.setEscalateToRoleCode("FINANCE_MANAGER");
        rule.setThresholdHours(8);

        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(actualCost));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_APPROVE)).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_REJECT)).thenReturn(false);
        when(financeScopeService.filterActualCosts(List.of(actualCost))).thenReturn(List.of(actualCost));
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId))
                .thenReturn(Optional.empty());
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department));
        when(financialApprovalRuleRepository.findFirstMatchingRule(departmentId, 750.0)).thenReturn(Optional.of(rule));

        var item = service.reviewQueue(null).getFirst();

        assertThat(item.approvalRoleCode()).isEqualTo("ECONOMIST");
        assertThat(item.effectiveReviewRoleCode()).isEqualTo("ECONOMIST");
        assertThat(item.escalationRoleCode()).isEqualTo("FINANCE_MANAGER");
        assertThat(item.hoursToOverdue()).isLessThanOrEqualTo(8);
        assertThat(item.approvalRule()).hasFieldOrPropertyWithValue("id", rule.getId());
        assertThat(item.approvalRule()).hasFieldOrPropertyWithValue("code", "MECH_GT_500");
    }

    @Test
    void reviewQueueReturnsAllPendingCostsForApprover() {
        List<ActualCost> pendingCosts = List.of(
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID())
        );
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(pendingCosts);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_APPROVE)).thenReturn(true);
        stubMinimalReviewQueueMapping();

        assertThat(service.reviewQueue(null)).hasSize(3);

        verify(financeScopeService, never()).filterActualCosts(any());
    }

    @Test
    void reviewQueueAppliesScopeFilterForNonApprover() {
        List<ActualCost> pendingCosts = List.of(
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID())
        );
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(pendingCosts);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_APPROVE)).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_REJECT)).thenReturn(false);
        when(financeScopeService.filterActualCosts(pendingCosts)).thenReturn(List.of(pendingCosts.getFirst()));
        stubMinimalReviewQueueMapping();

        assertThat(service.reviewQueue(null)).hasSize(1);

        verify(financeScopeService).filterActualCosts(pendingCosts);
    }

    @Test
    void reviewQueueReturnsAllPendingCostsForRejectRole() {
        List<ActualCost> pendingCosts = List.of(
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID())
        );
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(pendingCosts);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_APPROVE)).thenReturn(false);
        when(scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_REJECT)).thenReturn(true);
        stubMinimalReviewQueueMapping();

        assertThat(service.reviewQueue(null)).hasSize(3);

        verify(financeScopeService, never()).filterActualCosts(any());
    }

    @Test
    void reviewQueueReturnsScopeFilteredResultsForScopeAdmin() {
        List<ActualCost> pendingCosts = List.of(
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID()),
                minimalPendingCost(UUID.randomUUID())
        );
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(pendingCosts);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        stubMinimalReviewQueueMapping();

        assertThat(service.reviewQueue(null)).hasSize(5);

        verify(financeScopeService, never()).filterActualCosts(any());
    }

    @Test
    void evaluateOverdueCreatesDepartmentNotificationsAndCountsDuplicatesSeparately() {
        UUID departmentId = UUID.randomUUID();
        ActualCost overdue = pendingActualCost(UUID.randomUUID(), UUID.randomUUID(), 100.0, Instant.now().minusSeconds(30 * 3600));
        ActualCost dueSoon = pendingActualCost(UUID.randomUUID(), UUID.randomUUID(), 120.0, Instant.now().minusSeconds(22 * 3600));

        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(overdue, dueSoon));
        when(financeScopeService.filterActualCosts(List.of(overdue, dueSoon))).thenReturn(List.of(overdue, dueSoon));
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
        when(workOrderRepository.findByIdAndIsDeletedFalse(overdue.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder(overdue.getWorkOrderId(), departmentId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(dueSoon.getWorkOrderId()))
                .thenReturn(Optional.of(workOrder(dueSoon.getWorkOrderId(), departmentId)));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));
        when(financialApprovalRuleRepository.findFirstMatchingRule(eq(departmentId), any())).thenReturn(Optional.empty());
        when(notificationService.notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.ACTUAL_COST_APPROVE),
                anyString(),
                anyString(),
                eq(NotificationSeverity.WARNING),
                eq("ACTUAL_COST"),
                eq(overdue.getId().toString())
        )).thenReturn(List.of(notification(overdue.getId())));
        when(notificationService.notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.ACTUAL_COST_APPROVE),
                anyString(),
                anyString(),
                eq(NotificationSeverity.INFO),
                eq("ACTUAL_COST"),
                eq(dueSoon.getId().toString())
        )).thenReturn(List.of());

        var response = service.evaluateOverdue(24, 4);

        assertThat(response.scanned()).isEqualTo(2);
        assertThat(response.overdueCount()).isEqualTo(1);
        assertThat(response.dueSoonCount()).isEqualTo(1);
        assertThat(response.createdNotifications()).isEqualTo(1);
        assertThat(response.createdReminderNotifications()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(response.skippedReminders()).isEqualTo(1);
    }

    @Test
    void handoversReadOnlyHandoverEventsAndMapReturnedItems() {
        UUID actualCostId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(actualCostId, workOrderId, 500.0, Instant.parse("2026-06-01T09:00:00Z"));
        ActualCostReviewEvent handover = handoverEvent(actualCostId, notificationId, actorId);

        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(actualCost));
        when(eventRepository.findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc("HANDOVER"))
                .thenReturn(List.of(handover));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));

        var items = service.handovers("reassignment");

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().actualCostId()).isEqualTo(actualCostId);
        assertThat(items.getFirst().notificationId()).isEqualTo(notificationId);
        assertThat(items.getFirst().nextApprovalRoleCode()).isEqualTo("FINANCE_MANAGER");
        assertThat(items.getFirst().handoverComment()).isEqualTo("SLA reassignment");
        assertThat(items.getFirst().department()).hasFieldOrPropertyWithValue("id", departmentId);
        verify(eventRepository).findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc("HANDOVER");
    }

    @Test
    void handoversStayEmptyWhenNoHandoverEventsExist() {
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(eventRepository.findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc("HANDOVER"))
                .thenReturn(List.of());

        var items = service.handovers(null);

        assertThat(items).isEmpty();
        verify(eventRepository).findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc("HANDOVER");
    }

    @Test
    void handoverFromInboxPersistsHandoverEventAfterApplyingOverride() {
        UUID actualCostId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(actualCostId, workOrderId, 900.0, Instant.parse("2026-06-02T09:00:00Z"));
        ActualCostReviewRouteOverrideResponseDto override = new ActualCostReviewRouteOverrideResponseDto(
                overrideId,
                null,
                departmentId,
                "TECHNICAL_DIRECTOR",
                "SYSTEM_ADMIN",
                12,
                "Move overdue review",
                true,
                actorId,
                null,
                null,
                null,
                null,
                null
        );

        when(routeOverrideService.apply(any())).thenReturn(override);
        when(actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)).thenReturn(Optional.of(actualCost));
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId))
                .thenReturn(Optional.empty());

        var response = service.handoverFromInbox(
                actualCostId,
                notificationId,
                departmentId,
                "TECHNICAL_DIRECTOR",
                "SYSTEM_ADMIN",
                12,
                "Move overdue review",
                "Acknowledged",
                actorId,
                true
        );

        ArgumentCaptor<ActualCostReviewEvent> eventCaptor = ArgumentCaptor.forClass(ActualCostReviewEvent.class);
        verify(eventRepository, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anySatisfy(event -> {
                    assertThat(event.getActualCostId()).isEqualTo(actualCostId);
                    assertThat(event.getNotificationId()).isEqualTo(notificationId);
                    assertThat(event.getRouteOverrideId()).isEqualTo(overrideId);
                    assertThat(event.getActorUserId()).isEqualTo(actorId);
                    assertThat(event.getEventGroup()).isEqualTo("ROUTE");
                    assertThat(event.getEventCode()).isEqualTo("HANDOVER");
                    assertThat(event.getNextApprovalRoleCode()).isEqualTo("TECHNICAL_DIRECTOR");
                    assertThat(event.getNextEscalationRoleCode()).isEqualTo("SYSTEM_ADMIN");
                    assertThat(event.getNextThresholdHours()).isEqualTo(12);
                    assertThat(event.getHandoverComment()).isEqualTo("Move overdue review");
                    assertThat(event.getAcknowledgementComment()).isEqualTo("Acknowledged");
                });
        assertThat(response.override().id()).isEqualTo(overrideId);
        assertThat(response.acknowledgedNotificationId()).isEqualTo(notificationId);
        assertThat(response.acknowledgementComment()).isEqualTo("Acknowledged");
    }

    private ActualCost pendingActualCost(UUID actualCostId, UUID workOrderId, double amount, Instant createdAt) {
        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", actualCostId);
        ReflectionTestUtils.setField(actualCost, "createdAt", createdAt);
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(amount);
        actualCost.setCostDate(createdAt);
        return actualCost;
    }

    private ActualCost minimalPendingCost(UUID actualCostId) {
        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", actualCostId);
        ReflectionTestUtils.setField(actualCost, "createdAt", Instant.now());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(100.0);
        actualCost.setCostDate(Instant.parse("2026-05-26T09:00:00Z"));
        return actualCost;
    }

    private void stubMinimalReviewQueueMapping() {
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
    }

    private WorkOrder workOrder(UUID workOrderId, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", workOrderId);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Pump repair");
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private Department department(UUID departmentId) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", departmentId);
        department.setCode("D-1");
        department.setName("Mechanical");
        return department;
    }

    private ActualCostReviewEvent handoverEvent(UUID actualCostId, UUID notificationId, UUID actorId) {
        ActualCostReviewEvent event = new ActualCostReviewEvent();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setActualCostId(actualCostId);
        event.setNotificationId(notificationId);
        event.setActorUserId(actorId);
        event.setSource("SYSTEM");
        event.setEventGroup("ROUTE");
        event.setEventCode("HANDOVER");
        event.setTitle("Actual cost review handed over");
        event.setDescription("SLA signal reassignment");
        event.setStatus("PENDING");
        event.setPreviousApprovalRoleCode("ECONOMIST");
        event.setNextApprovalRoleCode("FINANCE_MANAGER");
        event.setPreviousEscalationRoleCode("CHIEF_MECHANIC");
        event.setNextEscalationRoleCode("SYSTEM_ADMIN");
        event.setPreviousThresholdHours(24);
        event.setNextThresholdHours(12);
        event.setHandoverComment("SLA reassignment");
        event.setAcknowledgementComment("Acknowledged");
        event.setOccurredAt(Instant.parse("2026-06-02T10:00:00Z"));
        return event;
    }

    private NotificationDto notification(UUID actualCostId) {
        return new NotificationDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Finance review",
                "Review actual cost",
                null,
                null,
                NotificationSeverity.INFO,
                "ACTUAL_COST",
                actualCostId.toString(),
                null
        );
    }
}
