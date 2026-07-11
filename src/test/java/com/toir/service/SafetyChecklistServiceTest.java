package com.toir.service;

import com.toir.dto.safetychecklist.SafetyChecklistDecisionRequest;
import com.toir.dto.safetychecklist.SafetyChecklistItemUpdateRequest;
import com.toir.dto.safetychecklist.WorkOrderSafetyChecklistDto;
import com.toir.entity.maintenance.SafetyChecklistTemplate;
import com.toir.entity.maintenance.SafetyChecklistTemplateItem;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSafetyChecklist;
import com.toir.entity.maintenance.WorkOrderSafetyChecklistItem;
import com.toir.enums.SafetyChecklistCategory;
import com.toir.enums.SafetyChecklistItemStatus;
import com.toir.enums.SafetyChecklistStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.SafetyChecklistTemplateItemRepository;
import com.toir.repository.maintenance.SafetyChecklistTemplateRepository;
import com.toir.repository.maintenance.WorkOrderSafetyChecklistItemRepository;
import com.toir.repository.maintenance.WorkOrderSafetyChecklistRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyChecklistServiceTest {

    @Mock
    WorkOrderSafetyChecklistRepository checklistRepository;

    @Mock
    WorkOrderSafetyChecklistItemRepository checklistItemRepository;

    @Mock
    SafetyChecklistTemplateRepository templateRepository;

    @Mock
    SafetyChecklistTemplateItemRepository templateItemRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    SafetyChecklistService service;

    private final List<WorkOrderSafetyChecklistItem> savedItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedItems.clear();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(checklistRepository.save(any(WorkOrderSafetyChecklist.class))).thenAnswer(invocation -> {
            WorkOrderSafetyChecklist checklist = invocation.getArgument(0);
            if (checklist.getId() == null) {
                ReflectionTestUtils.setField(checklist, "id", UUID.randomUUID());
            }
            return checklist;
        });
        lenient().when(checklistItemRepository.saveAll(anyCollection())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterable<WorkOrderSafetyChecklistItem> items = invocation.getArgument(0);
            items.forEach(item -> {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                }
                savedItems.add(item);
            });
            return savedItems;
        });
        lenient().when(checklistItemRepository.findAllByChecklistIdAndIsDeletedFalseOrderBySequenceAsc(any(UUID.class)))
                .thenAnswer(invocation -> savedItems.stream()
                        .filter(item -> item.getChecklist() != null
                                && invocation.getArgument(0).equals(item.getChecklist().getId()))
                        .toList());
    }

    @Test
    void generateChecklistFromTemplate() {
        WorkOrder workOrder = workOrder();
        SafetyChecklistTemplate template = template(workOrder.getType(), null);
        SafetyChecklistTemplateItem item = templateItem(template, true);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrder.getId())).thenReturn(Optional.of(workOrder));
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.empty());
        when(templateRepository.findFirstByWorkOrderTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getType()))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findAllByTemplateIdAndIsDeletedFalseOrderBySequenceAsc(template.getId()))
                .thenReturn(List.of(item));

        WorkOrderSafetyChecklistDto result = service.generate(workOrder.getId());

        assertThat(result.workOrderId()).isEqualTo(workOrder.getId());
        assertThat(result.templateId()).isEqualTo(template.getId());
        assertThat(result.status()).isEqualTo(SafetyChecklistStatus.DRAFT);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().critical()).isTrue();
    }

    @Test
    void generationIsIdempotent() {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist existing = checklist(workOrder, SafetyChecklistStatus.DRAFT);
        savedItems.add(item(existing, true, SafetyChecklistItemStatus.PENDING));
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.of(existing));

        Optional<WorkOrderSafetyChecklistDto> result = service.generateForWorkOrderIfTemplateExists(workOrder);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(existing.getId());
        verify(templateRepository, never()).findFirstByWorkOrderTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(any());
    }

    @Test
    void updateItemPass() {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist checklist = checklist(workOrder, SafetyChecklistStatus.DRAFT);
        WorkOrderSafetyChecklistItem item = item(checklist, true, SafetyChecklistItemStatus.PENDING);
        savedItems.add(item);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrder.getId())).thenReturn(Optional.of(workOrder));
        when(checklistRepository.findByIdAndIsDeletedFalse(checklist.getId())).thenReturn(Optional.of(checklist));
        when(checklistItemRepository.findByIdAndIsDeletedFalse(item.getId())).thenReturn(Optional.of(item));
        when(checklistItemRepository.save(any(WorkOrderSafetyChecklistItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderSafetyChecklistDto result = service.updateItem(
                workOrder.getId(),
                checklist.getId(),
                item.getId(),
                new SafetyChecklistItemUpdateRequest(SafetyChecklistItemStatus.PASSED, null, UUID.randomUUID()));

        assertThat(item.getStatus()).isEqualTo(SafetyChecklistItemStatus.PASSED);
        assertThat(result.status()).isEqualTo(SafetyChecklistStatus.IN_PROGRESS);
    }

    @Test
    void failedCriticalItemBlocksComplete() {
        assertCompleteBlockedBy(SafetyChecklistItemStatus.FAILED);
    }

    @Test
    void pendingCriticalItemBlocksComplete() {
        assertCompleteBlockedBy(SafetyChecklistItemStatus.PENDING);
    }

    @Test
    void completedChecklistAllowsWorkOrderStart() {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist checklist = checklist(workOrder, SafetyChecklistStatus.COMPLETED);
        savedItems.add(item(checklist, true, SafetyChecklistItemStatus.PASSED));
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.of(checklist));

        service.assertCanStart(workOrder);
    }

    @Test
    void startShutdownRequiredWorkOrderWithoutChecklistFailsClosed() {
        WorkOrder workOrder = workOrder();
        workOrder.setRequiresShutdown(true);
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertCanStart(workOrder))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Safety checklist is required");
    }

    @Test
    void ordinaryWorkOrderWithoutChecklistRemainsStartable() {
        WorkOrder workOrder = workOrder();

        service.assertCanStart(workOrder);
    }

    @Test
    void failedChecklistBlocksWorkOrderStart() {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist checklist = checklist(workOrder, SafetyChecklistStatus.FAILED);
        savedItems.add(item(checklist, true, SafetyChecklistItemStatus.FAILED));
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> service.assertCanStart(workOrder))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("critical safety checklist items are not passed");
    }

    @Test
    void closeBlocksIfChecklistNotCompleted() {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist checklist = checklist(workOrder, SafetyChecklistStatus.IN_PROGRESS);
        savedItems.add(item(checklist, true, SafetyChecklistItemStatus.PASSED));
        when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrder.getId(), EnumSet.of(SafetyChecklistStatus.CANCELLED))).thenReturn(Optional.of(checklist));

        Optional<String> blocker = service.closeBlocker(workOrder);

        assertThat(blocker).contains("Safety checklist must be COMPLETED");
    }

    private void assertCompleteBlockedBy(SafetyChecklistItemStatus status) {
        WorkOrder workOrder = workOrder();
        WorkOrderSafetyChecklist checklist = checklist(workOrder, SafetyChecklistStatus.IN_PROGRESS);
        savedItems.add(item(checklist, true, status));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrder.getId())).thenReturn(Optional.of(workOrder));
        when(checklistRepository.findByIdAndIsDeletedFalse(checklist.getId())).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> service.complete(
                workOrder.getId(),
                checklist.getId(),
                new SafetyChecklistDecisionRequest(UUID.randomUUID(), null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("critical items are pending or failed");
    }

    private WorkOrder workOrder() {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
        workOrder.setNumber("WO-SAFE");
        workOrder.setTitle("Safety test");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private SafetyChecklistTemplate template(WorkOrderType type, WorkType workType) {
        SafetyChecklistTemplate template = new SafetyChecklistTemplate();
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        template.setCode("SAFE-REPAIR");
        template.setName("Repair safety");
        template.setWorkOrderType(type);
        template.setWorkType(workType);
        template.setActive(true);
        return template;
    }

    private SafetyChecklistTemplateItem templateItem(SafetyChecklistTemplate template, boolean critical) {
        SafetyChecklistTemplateItem item = new SafetyChecklistTemplateItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setTemplate(template);
        item.setSequence(1);
        item.setLabel("Lockout confirmed");
        item.setCategory(SafetyChecklistCategory.ENERGY_ISOLATION);
        item.setCritical(critical);
        item.setActive(true);
        return item;
    }

    private WorkOrderSafetyChecklist checklist(WorkOrder workOrder, SafetyChecklistStatus status) {
        WorkOrderSafetyChecklist checklist = new WorkOrderSafetyChecklist();
        ReflectionTestUtils.setField(checklist, "id", UUID.randomUUID());
        checklist.setWorkOrderId(workOrder.getId());
        checklist.setStatus(status);
        return checklist;
    }

    private WorkOrderSafetyChecklistItem item(
            WorkOrderSafetyChecklist checklist,
            boolean critical,
            SafetyChecklistItemStatus status) {
        WorkOrderSafetyChecklistItem item = new WorkOrderSafetyChecklistItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setChecklist(checklist);
        item.setSequence(1);
        item.setLabel("Lockout confirmed");
        item.setCategory(SafetyChecklistCategory.ENERGY_ISOLATION);
        item.setCritical(critical);
        item.setStatus(status);
        return item;
    }
}
