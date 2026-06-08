package com.toir.service;

import com.toir.dto.safetychecklist.SafetyChecklistDecisionRequest;
import com.toir.dto.safetychecklist.SafetyChecklistItemUpdateRequest;
import com.toir.dto.safetychecklist.WorkOrderSafetyChecklistDto;
import com.toir.entity.maintenance.SafetyChecklistTemplate;
import com.toir.entity.maintenance.SafetyChecklistTemplateItem;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSafetyChecklist;
import com.toir.entity.maintenance.WorkOrderSafetyChecklistItem;
import com.toir.entity.maintenance.WorkOrderTask;
import com.toir.enums.SafetyChecklistItemStatus;
import com.toir.enums.SafetyChecklistStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.SafetyChecklistTemplateItemRepository;
import com.toir.repository.maintenance.SafetyChecklistTemplateRepository;
import com.toir.repository.maintenance.WorkOrderSafetyChecklistItemRepository;
import com.toir.repository.maintenance.WorkOrderSafetyChecklistRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SafetyChecklistService {

    private static final EnumSet<SafetyChecklistStatus> DUPLICATE_EXCLUDED_STATUSES =
            EnumSet.of(SafetyChecklistStatus.CANCELLED);

    private final WorkOrderSafetyChecklistRepository checklistRepository;
    private final WorkOrderSafetyChecklistItemRepository checklistItemRepository;
    private final SafetyChecklistTemplateRepository templateRepository;
    private final SafetyChecklistTemplateItemRepository templateItemRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public List<WorkOrderSafetyChecklistDto> list(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        return checklistRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderSafetyChecklistDto get(UUID workOrderId, UUID id) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        return toDto(checklistOrThrow(workOrderId, id));
    }

    @Transactional
    public Optional<WorkOrderSafetyChecklistDto> generateForWorkOrderIfTemplateExists(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getId() == null) {
            return Optional.empty();
        }
        Optional<WorkOrderSafetyChecklist> existing = activeChecklist(workOrder.getId());
        if (existing.isPresent()) {
            return Optional.of(toDto(existing.get()));
        }
        Optional<SafetyChecklistTemplate> template = matchingTemplate(workOrder);
        return template.map(value -> toDto(createFromTemplate(workOrder, value)));
    }

    @Transactional
    public WorkOrderSafetyChecklistDto generate(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        if (activeChecklist(workOrderId).isPresent()) {
            throw RestException.badRequest("Safety checklist already exists for work order");
        }
        SafetyChecklistTemplate template = matchingTemplate(workOrder)
                .orElseThrow(() -> RestException.badRequest("No active safety checklist template matches work order"));
        return toDto(createFromTemplate(workOrder, template));
    }

    @Transactional
    public WorkOrderSafetyChecklistDto updateItem(UUID workOrderId, UUID checklistId, UUID itemId, SafetyChecklistItemUpdateRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        WorkOrderSafetyChecklist checklist = checklistOrThrow(workOrderId, checklistId);
        if (checklist.getStatus() == SafetyChecklistStatus.CANCELLED) {
            throw RestException.badRequest("Cannot update items of CANCELLED checklist");
        }
        WorkOrderSafetyChecklistItem item = checklistItemRepository.findByIdAndIsDeletedFalse(itemId)
                .filter(candidate -> candidate.getChecklist() != null && checklistId.equals(candidate.getChecklist().getId()))
                .orElseThrow(() -> RestException.notFound("Safety checklist item not found: " + itemId));
        if (item.isRequiresComment()
                && (request.status() == SafetyChecklistItemStatus.FAILED || request.status() == SafetyChecklistItemStatus.NOT_APPLICABLE)
                && isBlank(request.comment())) {
            throw RestException.badRequest("Comment is required for this safety checklist item");
        }
        item.setStatus(request.status());
        item.setComment(blankToNull(request.comment()));
        item.setCheckedById(request.checkedById());
        item.setCheckedAt(Instant.now());
        checklistItemRepository.save(item);
        if (checklist.getStatus() == SafetyChecklistStatus.DRAFT) {
            checklist.setStatus(SafetyChecklistStatus.IN_PROGRESS);
            checklistRepository.save(checklist);
        }
        return toDto(checklist);
    }

    @Transactional
    public WorkOrderSafetyChecklistDto complete(UUID workOrderId, UUID checklistId, SafetyChecklistDecisionRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        WorkOrderSafetyChecklist checklist = checklistOrThrow(workOrderId, checklistId);
        if (checklist.getStatus() == SafetyChecklistStatus.CANCELLED) {
            throw RestException.badRequest("Cannot complete CANCELLED safety checklist");
        }
        List<WorkOrderSafetyChecklistItem> items = checklistItems(checklist.getId());
        boolean blocked = items.stream().anyMatch(item -> item.isCritical()
                && (item.getStatus() == SafetyChecklistItemStatus.PENDING || item.getStatus() == SafetyChecklistItemStatus.FAILED));
        if (blocked) {
            throw RestException.badRequest("Cannot complete safety checklist; critical items are pending or failed");
        }
        checklist.setStatus(SafetyChecklistStatus.COMPLETED);
        checklist.setCheckedById(request == null ? null : request.checkedById());
        checklist.setCheckedAt(Instant.now());
        checklist.setRemarks(mergeRemarks(checklist.getRemarks(), request == null ? null : request.remarks()));
        return toDto(checklistRepository.save(checklist));
    }

    @Transactional
    public WorkOrderSafetyChecklistDto fail(UUID workOrderId, UUID checklistId, SafetyChecklistDecisionRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        WorkOrderSafetyChecklist checklist = checklistOrThrow(workOrderId, checklistId);
        if (checklist.getStatus() == SafetyChecklistStatus.CANCELLED) {
            throw RestException.badRequest("Cannot fail CANCELLED safety checklist");
        }
        checklist.setStatus(SafetyChecklistStatus.FAILED);
        checklist.setCheckedById(request == null ? null : request.checkedById());
        checklist.setCheckedAt(Instant.now());
        checklist.setRemarks(mergeRemarks(checklist.getRemarks(), request == null ? null : request.remarks()));
        return toDto(checklistRepository.save(checklist));
    }

    @Transactional
    public WorkOrderSafetyChecklistDto cancel(UUID workOrderId, UUID checklistId, SafetyChecklistDecisionRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccess(workOrder);
        WorkOrderSafetyChecklist checklist = checklistOrThrow(workOrderId, checklistId);
        checklist.setStatus(SafetyChecklistStatus.CANCELLED);
        checklist.setCheckedById(request == null ? null : request.checkedById());
        checklist.setCheckedAt(Instant.now());
        checklist.setRemarks(mergeRemarks(checklist.getRemarks(), request == null ? null : request.remarks()));
        return toDto(checklistRepository.save(checklist));
    }

    @Transactional(readOnly = true)
    public void assertCanStart(WorkOrder workOrder) {
        Optional<WorkOrderSafetyChecklist> checklist = activeChecklist(workOrder.getId());
        if (checklist.isEmpty()) {
            return;
        }
        WorkOrderSafetyChecklist value = checklist.get();
        if (value.getStatus() == SafetyChecklistStatus.FAILED) {
            throw RestException.badRequest("Cannot start work order; critical safety checklist items are not passed");
        }
        boolean blocked = checklistItems(value.getId()).stream()
                .anyMatch(item -> item.isCritical()
                        && item.getStatus() != SafetyChecklistItemStatus.PASSED
                        && item.getStatus() != SafetyChecklistItemStatus.NOT_APPLICABLE);
        if (blocked) {
            throw RestException.badRequest("Cannot start work order; critical safety checklist items are not passed");
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> closeBlocker(WorkOrder workOrder) {
        Optional<WorkOrderSafetyChecklist> checklist = activeChecklist(workOrder.getId());
        if (checklist.isEmpty()) {
            return Optional.empty();
        }
        WorkOrderSafetyChecklist value = checklist.get();
        if (value.getStatus() != SafetyChecklistStatus.COMPLETED) {
            return Optional.of("Safety checklist must be COMPLETED");
        }
        boolean blockedCritical = checklistItems(value.getId()).stream()
                .anyMatch(item -> item.isCritical()
                        && (item.getStatus() == SafetyChecklistItemStatus.FAILED
                        || item.getStatus() == SafetyChecklistItemStatus.PENDING));
        return blockedCritical ? Optional.of("Critical safety checklist items must be passed") : Optional.empty();
    }

    private WorkOrderSafetyChecklist createFromTemplate(WorkOrder workOrder, SafetyChecklistTemplate template) {
        WorkOrderSafetyChecklist checklist = new WorkOrderSafetyChecklist();
        checklist.setWorkOrderId(workOrder.getId());
        checklist.setTemplateId(template.getId());
        checklist.setStatus(SafetyChecklistStatus.DRAFT);
        checklist.setRemarks(operationSafetyRemarks(workOrder));
        WorkOrderSafetyChecklist saved = checklistRepository.save(checklist);

        List<SafetyChecklistTemplateItem> templateItems = templateItemRepository
                .findAllByTemplateIdAndIsDeletedFalseOrderBySequenceAsc(template.getId())
                .stream()
                .filter(SafetyChecklistTemplateItem::isActive)
                .toList();
        for (SafetyChecklistTemplateItem templateItem : templateItems) {
            WorkOrderSafetyChecklistItem item = new WorkOrderSafetyChecklistItem();
            item.setChecklist(saved);
            item.setTemplateItemId(templateItem.getId());
            item.setSequence(templateItem.getSequence());
            item.setLabel(templateItem.getLabel());
            item.setCategory(templateItem.getCategory());
            item.setCritical(templateItem.isCritical());
            item.setRequiresComment(templateItem.isRequiresComment());
            item.setStatus(SafetyChecklistItemStatus.PENDING);
            saved.getItems().add(item);
        }
        if (!saved.getItems().isEmpty()) {
            checklistItemRepository.saveAll(saved.getItems());
        }
        return saved;
    }

    private Optional<SafetyChecklistTemplate> matchingTemplate(WorkOrder workOrder) {
        if (workOrder.getType() != null) {
            Optional<SafetyChecklistTemplate> byType =
                    templateRepository.findFirstByWorkOrderTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getType());
            if (byType.isPresent()) {
                return byType;
            }
        }
        if (workOrder.getWorkType() != null) {
            Optional<SafetyChecklistTemplate> byWorkType =
                    templateRepository.findFirstByWorkOrderTypeIsNullAndWorkTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getWorkType());
            if (byWorkType.isPresent()) {
                return byWorkType;
            }
        }
        return templateRepository.findFirstByWorkOrderTypeIsNullAndWorkTypeIsNullAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc();
    }

    private Optional<WorkOrderSafetyChecklist> activeChecklist(UUID workOrderId) {
        return checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
                workOrderId,
                DUPLICATE_EXCLUDED_STATUSES);
    }

    private WorkOrder workOrderOrThrow(UUID workOrderId) {
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private WorkOrderSafetyChecklist checklistOrThrow(UUID workOrderId, UUID checklistId) {
        return checklistRepository.findByIdAndIsDeletedFalse(checklistId)
                .filter(checklist -> workOrderId.equals(checklist.getWorkOrderId()))
                .orElseThrow(() -> RestException.notFound("Safety checklist not found: " + checklistId));
    }

    private WorkOrderSafetyChecklistDto toDto(WorkOrderSafetyChecklist checklist) {
        return WorkOrderSafetyChecklistDto.from(checklist, checklistItems(checklist.getId()));
    }

    private List<WorkOrderSafetyChecklistItem> checklistItems(UUID checklistId) {
        return checklistItemRepository.findAllByChecklistIdAndIsDeletedFalseOrderBySequenceAsc(checklistId);
    }

    private void assertCanAccess(WorkOrder workOrder) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (workOrder.getDepartmentId() == null || !scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            throw RestException.forbidden("Access denied by work order department scope");
        }
    }

    private String operationSafetyRemarks(WorkOrder workOrder) {
        if (workOrder.getTasks() == null || workOrder.getTasks().isEmpty()) {
            return null;
        }
        String remarks = workOrder.getTasks().stream()
                .map(WorkOrderTask::getDescription)
                .filter(description -> description != null && (description.contains("Safety:") || description.contains("Tools:")))
                .distinct()
                .reduce(null, this::mergeRemarks);
        return blankToNull(remarks);
    }

    private String mergeRemarks(String current, String next) {
        if (isBlank(next)) {
            return blankToNull(current);
        }
        if (isBlank(current)) {
            return next.trim();
        }
        if (current.contains(next.trim())) {
            return current;
        }
        return current + "\n" + next.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
