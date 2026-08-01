package com.toir.service;

import com.toir.service.ppr.PprCompletionEvidenceService;
import com.toir.config.PprLifecycleProperties;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.attachment.AttachmentPhotoSummary;
import com.toir.dto.equipment.EquipmentPlacementRequest;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.notification.NotificationContent;
import com.toir.dto.workorder.*;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.TriadLinkMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.Counteragent;
import com.toir.entity.Department;
import com.toir.entity.FileAsset;
import com.toir.entity.Location;
import com.toir.entity.UploadedFile;
import com.toir.entity.defects.Defect;
import com.toir.entity.defects.DefectList;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderDocument;
import com.toir.entity.maintenance.WorkOrderTask;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.CertificationType;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.repair.RepairRequestTemplateAction;
import com.toir.entity.users.UserCertification;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.Employee;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.PlanStatus;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.PlacementTargetType;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.CertificationTypeRepository;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SafetyPermitRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderStatsProjection;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.WorkExecutionRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairRequestTemplateActionRepository;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.CloseReadinessGroupStatus;
import com.toir.enums.CloseReadinessSeverity;
import com.toir.enums.DefectStatus;
import com.toir.enums.DefectListStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.SafetyPermitStatus;
import com.toir.enums.TaskExecutionStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.WarrantyHandling;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
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
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.projection.WorkOrderCalendarBucketProjection;
import com.toir.repository.users.UserRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.file_management.FileService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.service.maintanance.WorkOrderSparePartRequirementService;
import com.toir.service.maintenance.WorkOrderCompletionService;
import com.toir.service.ppr.PprWorkOrderEarlyCreationPolicy;
import com.toir.service.repair.RepairMaterialUsageService;
import com.toir.service.integration.ToirErpWorkOrderSnapshotPublisher;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderService {

    private static final String MODULE = "work-order";
    private static final String ENTITY = "WorkOrder";

    public record TemplateTaskSyncResult(int operationsCount, int createdCount) {
    }

    private record ResolvedCompletionMeterSnapshot(CompletionMeterSnapshotRequest snapshot, EquipmentMeter meter) {
    }

    private static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Tashkent");
    private static final String COMPLETED_OR_CLOSED_STATUS_SCOPE = "COMPLETED_OR_CLOSED";
    private static final String UNPLANNED_TYPE_SCOPE = "UNPLANNED";

    private final WorkOrderRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MeterService meterService;
    private final EquipmentNodeRepository equipmentNodeRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditBuilderService auditBuilderService;
    private final PprPlanRepository pprPlanRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final RepairCampaignRepository repairCampaignRepository;
    private final RepairCampaignStageRepository repairCampaignStageRepository;
    private final RepairCampaignDepartmentRepository repairCampaignDepartmentRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final RepairRequestTemplateActionRepository repairRequestTemplateActionRepository;
    private final DefectRepository defectRepository;
    private final DefectListRepository defectListRepository;
    private final CounteragentService counteragentService;
    private final BrigadeMemberRepository brigadeMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkOrderPerformerAssignmentPolicy performerAssignmentPolicy;
    private final UserRepository userRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final CertificationTypeRepository certificationTypeRepository;
    private final WorkExecutionRepository workExecutionRepository;
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final LaborEntryRepository laborEntryRepository;
    private final ReservationRepository reservationRepository;
    private final ActualCostRepository actualCostRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;
    private final EquipmentService equipmentService;
    private final SafetyPermitRepository safetyPermitRepository;
    private final CompletionActRepository completionActRepository;
    private final PprCompletionEvidenceService pprCompletionEvidenceService;
    private final PprLifecycleProperties pprLifecycleProperties;
    private final FileAssetRepository fileAssetRepository;
    private final FileService fileService;
    private final UploadedFileRepository uploadedFileRepository;
    private final WorkOrderDocumentRepository workOrderDocumentRepository;
    private final AttachmentGroupService attachmentGroupService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final RepairMaterialUsageService repairMaterialUsageService;
    private final WorkOrderCompletionService workOrderCompletionService;
    private final MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;
    private final MaintenanceOperationRepository maintenanceOperationRepository;
    private final MaintenanceRegulationRepository maintenanceRegulationRepository;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final RepairAcceptanceRepository repairAcceptanceRepository;
    private final MaintenanceDueEventService maintenanceDueEventService;
    private final WorkOrderSparePartRequirementService workOrderSparePartRequirementService;
    private final SafetyChecklistService safetyChecklistService;
    private final com.toir.service.plannedshutdown.PlannedShutdownWorkOrderStartPolicy plannedShutdownStartPolicy;
    private final ScopeAccessService scopeAccessService;
    private final WorkOrderNumberService workOrderNumberService;
    private final NotificationService notificationService;
    private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
    private final ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;
    private final com.toir.service.sparepartlifecycle.SparePartLifecycleService sparePartLifecycleService;
    private final com.toir.service.sparepartlifecycle.SparePartLifecycleOperationGuard sparePartLifecycleOperationGuard;
    private final ObjectMapper objectMapper;
    private final ToirErpWorkOrderSnapshotPublisher erpWorkOrderDeltas;
    private static final Set<WorkOrderStatus> COMPLETE_ALLOWED_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS);
    private static final Set<WorkOrderStatus> TERMINAL_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);
    private static final Set<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED, WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.SUSPENDED);
    private static final Set<RequestStatus> TERMINAL_REPAIR_REQUEST_STATUSES =
            EnumSet.of(RequestStatus.REJECTED, RequestStatus.COMPLETED, RequestStatus.CLOSED, RequestStatus.CANCELLED);
    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES =
            EnumSet.of(DefectStatus.RESOLVED, DefectStatus.CLOSED, DefectStatus.CANCELLED);
    private static final Set<DefectStatus> RESOLVED_OR_CLOSED_DEFECT_STATUSES =
            EnumSet.of(DefectStatus.RESOLVED, DefectStatus.CLOSED);
    private static final Set<WorkOrderType> DEFECT_LIST_REQUIRED_WORK_ORDER_TYPES =
            EnumSet.of(WorkOrderType.MEDIUM_REPAIR, WorkOrderType.CAPITAL_REPAIR);
    private static final Set<WorkOrderType> CAMPAIGN_WORK_ORDER_TYPES =
            EnumSet.of(WorkOrderType.OVERHAUL, WorkOrderType.MEDIUM_REPAIR, WorkOrderType.CAPITAL_REPAIR);
    private static final Set<RequestStatus> DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE =
            EnumSet.of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED);
    private static final Set<PlanStatus> ALLOWED_PARENT_PLAN_STATUSES_FOR_WORK_ORDER_CREATE = EnumSet
            .of(PlanStatus.APPROVED, PlanStatus.IN_PROGRESS);

    @Transactional(readOnly = true)
    public List<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId) {
        return toDtos(repository.search(status, departmentId, equipmentId));
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDto> findByPlannedShutdown(UUID shutdownId, UUID departmentId) {
        scopeAccessService.assertCanAccessDepartment(departmentId);
        return toDtos(repository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByUpdatedAtDesc(shutdownId));
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page,
                                     int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        String normalizedSearch = normalizeSearch(search);
        String statusStr = status == null ? null : status.name();
        Page<WorkOrder> resultPage = repository.searchPaginated(
                statusStr,
                departmentId,
                equipmentId,
                normalizedSearch,
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page,
                                     int pageSize, String search, Instant plannedFrom, Instant plannedTo, Sort sort) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        var nativeQueryPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String normalizedSearch = normalizeSearch(search);
        String statusStr = status == null ? null : status.name();
        Page<WorkOrder> resultPage = repository.searchPaginated(
                statusStr,
                departmentId,
                equipmentId,
                normalizedSearch,
                plannedFrom,
                plannedTo,
                nativeQueryPageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, String statusScope, UUID departmentId, UUID equipmentId, int page,
                                     int pageSize, String search, Instant plannedFrom, Instant plannedTo, Sort sort) {
        return search(status, statusScope, null, null, departmentId, equipmentId, page, pageSize, search, plannedFrom, plannedTo, sort);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, String statusScope, WorkOrderType type, String typeScope,
                                     UUID departmentId, UUID equipmentId, int page, int pageSize, String search,
                                     Instant plannedFrom, Instant plannedTo, Sort sort) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        var nativeQueryPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String normalizedSearch = normalizeSearch(search);
        String statusStr = status == null ? null : status.name();
        TypeFilter typeFilter = resolveTypeFilter(type, typeScope);
        Page<WorkOrder> resultPage = repository.searchPaginated(
                statusStr,
                completedOrClosedOnly(status, statusScope),
                departmentId,
                equipmentId,
                normalizedSearch,
                plannedFrom,
                plannedTo,
                typeFilter.typeName(),
                typeFilter.unplannedOnly(),
                nativeQueryPageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchSorted(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page,
                                           int pageSize, String search, Instant plannedFrom, Instant plannedTo, Sort sort) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        Page<WorkOrder> resultPage = repository.findAll(
                workOrderListSpecification(status, null, TypeFilter.none(), departmentId, equipmentId, normalizeSearch(search), plannedFrom, plannedTo, null, null),
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchSorted(WorkOrderStatus status, String statusScope, UUID departmentId, UUID equipmentId, int page,
                                           int pageSize, String search, Instant plannedFrom, Instant plannedTo, Sort sort) {
        return searchSorted(status, statusScope, null, null, departmentId, equipmentId, page, pageSize, search, plannedFrom, plannedTo, sort);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchSorted(WorkOrderStatus status, String statusScope, WorkOrderType type, String typeScope,
                                           UUID departmentId, UUID equipmentId, int page, int pageSize, String search,
                                           Instant plannedFrom, Instant plannedTo, Sort sort) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        TypeFilter typeFilter = resolveTypeFilter(type, typeScope);
        Page<WorkOrder> resultPage = repository.findAll(
                workOrderListSpecification(status, statusScope, typeFilter, departmentId, equipmentId, normalizeSearch(search), plannedFrom, plannedTo, null, null),
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchByCampaign(
            WorkOrderStatus status,
            UUID departmentId,
            UUID equipmentId,
            int page,
            int pageSize,
            String search,
            Instant plannedFrom,
            Instant plannedTo,
            Sort sort,
            UUID repairCampaignId,
            UUID repairCampaignStageId
    ) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        Page<WorkOrder> resultPage = repository.findAll(
                workOrderListSpecification(status, null, TypeFilter.none(), departmentId, equipmentId, normalizeSearch(search), plannedFrom,
                        plannedTo, repairCampaignId, repairCampaignStageId),
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchByCampaign(
            WorkOrderStatus status,
            String statusScope,
            UUID departmentId,
            UUID equipmentId,
            int page,
            int pageSize,
            String search,
            Instant plannedFrom,
            Instant plannedTo,
            Sort sort,
            UUID repairCampaignId,
            UUID repairCampaignStageId
    ) {
        return searchByCampaign(status, statusScope, null, null, departmentId, equipmentId, page, pageSize, search,
                plannedFrom, plannedTo, sort, repairCampaignId, repairCampaignStageId);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> searchByCampaign(
            WorkOrderStatus status,
            String statusScope,
            WorkOrderType type,
            String typeScope,
            UUID departmentId,
            UUID equipmentId,
            int page,
            int pageSize,
            String search,
            Instant plannedFrom,
            Instant plannedTo,
            Sort sort,
            UUID repairCampaignId,
            UUID repairCampaignStageId
    ) {
        var pageable = PaginationUtils.pageRequest(page, pageSize, sort);
        TypeFilter typeFilter = resolveTypeFilter(type, typeScope);
        Page<WorkOrder> resultPage = repository.findAll(
                workOrderListSpecification(status, statusScope, typeFilter, departmentId, equipmentId, normalizeSearch(search), plannedFrom,
                        plannedTo, repairCampaignId, repairCampaignStageId),
                pageable);
        return toDtoPage(resultPage);
    }

    private Specification<WorkOrder> workOrderListSpecification(WorkOrderStatus status,
                                                                String statusScope,
                                                                TypeFilter typeFilter,
                                                                UUID departmentId,
                                                                UUID equipmentId,
                                                                String search,
                                                                Instant plannedFrom,
                                                                Instant plannedTo,
                                                                UUID repairCampaignId,
                                                                UUID repairCampaignStageId) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (completedOrClosedOnly(null, statusScope)) {
                predicates.add(root.get("status").in(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED));
            }
            if (typeFilter != null && typeFilter.type() != null) {
                predicates.add(cb.equal(root.get("type"), typeFilter.type()));
            } else if (typeFilter != null && typeFilter.unplannedOnly()) {
                predicates.add(root.get("type").in(WorkOrderType.EMERGENCY, WorkOrderType.DEFECT));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (repairCampaignId != null) {
                predicates.add(cb.equal(root.get("repairCampaignId"), repairCampaignId));
            }
            if (repairCampaignStageId != null) {
                predicates.add(cb.equal(root.get("repairCampaignStageId"), repairCampaignStageId));
            }
            if (plannedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(cb.coalesce(root.get("endPlannedAt"), root.get("startPlannedAt")), plannedFrom));
            }
            if (plannedTo != null) {
                predicates.add(cb.lessThan(cb.coalesce(root.get("endPlannedAt"), root.get("startPlannedAt")), plannedTo));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("number"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("title"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("result"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("closureNotes"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page,
                                     int pageSize, String search, Instant plannedFrom, Instant plannedTo) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        String normalizedSearch = normalizeSearch(search);
        String statusStr = status == null ? null : status.name();
        Page<WorkOrder> resultPage = repository.searchPaginated(
                statusStr,
                departmentId,
                equipmentId,
                normalizedSearch,
                plannedFrom,
                plannedTo,
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public WorkOrderCalendarSummaryResponse calendarSummary(
            WorkOrderStatus status,
            UUID departmentId,
            UUID equipmentId,
            String search,
            int year,
            Integer month) {
        if (year < 1) {
            throw RestException.badRequest("year must be positive");
        }

        String normalizedSearch = normalizeSearch(search);
        String statusStr = status == null ? null : status.name();
        if (month == null) {
            Instant from = LocalDate.of(year, 1, 1).atStartOfDay(CALENDAR_ZONE).toInstant();
            Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(CALENDAR_ZONE).toInstant();
            List<WorkOrderCalendarBucketProjection> rows = repository.getWorkOrderCalendarMonthBuckets(
                    statusStr,
                    departmentId,
                    equipmentId,
                    normalizedSearch,
                    from,
                    to);
            List<WorkOrderCalendarBucketDto> months = buildMonthBuckets(rows);
            return new WorkOrderCalendarSummaryResponse(
                    year,
                    null,
                    totalOrders(months),
                    aggregateStatusCounts(rows),
                    months,
                    List.of());
        }

        if (month < 1 || month > 12) {
            throw RestException.badRequest("month must be between 1 and 12");
        }

        YearMonth targetMonth = YearMonth.of(year, month);
        Instant from = targetMonth.atDay(1).atStartOfDay(CALENDAR_ZONE).toInstant();
        Instant to = targetMonth.plusMonths(1).atDay(1).atStartOfDay(CALENDAR_ZONE).toInstant();
        List<WorkOrderCalendarBucketProjection> rows = repository.getWorkOrderCalendarDayBuckets(
                statusStr,
                departmentId,
                equipmentId,
                normalizedSearch,
                from,
                to);
        List<WorkOrderCalendarBucketDto> days = buildDayBuckets(targetMonth, rows);
        return new WorkOrderCalendarSummaryResponse(
                year,
                month,
                totalOrders(days),
                aggregateStatusCounts(rows),
                List.of(),
                days);
    }

    @Transactional(readOnly = true)
    public WorkOrderStatsResponse getStats(WorkOrderStatus status, UUID departmentId, UUID equipmentId, String search) {
        return getStats(status, null, departmentId, equipmentId, search);
    }

    @Transactional(readOnly = true)
    public WorkOrderStatsResponse getStats(WorkOrderStatus status, String statusScope, UUID departmentId, UUID equipmentId, String search) {
        String statusStr = status == null ? null : status.name();
        String normalizedSearch = normalizeSearch(search);
        var stats = repository.getWorkOrderStats(
                statusStr,
                completedOrClosedOnly(status, statusScope),
                departmentId,
                equipmentId,
                normalizedSearch);
        return toStatsResponse(stats);
    }

    @Transactional(readOnly = true)
    public WorkOrderStatsResponse getStats(WorkOrderStatus status, String statusScope, WorkOrderType type, String typeScope,
                                           UUID departmentId, UUID equipmentId, String search) {
        String statusStr = status == null ? null : status.name();
        String normalizedSearch = normalizeSearch(search);
        TypeFilter typeFilter = resolveTypeFilter(type, typeScope);
        var stats = repository.getWorkOrderStats(
                statusStr,
                completedOrClosedOnly(status, statusScope),
                departmentId,
                equipmentId,
                normalizedSearch,
                typeFilter.typeName(),
                typeFilter.unplannedOnly());
        return toStatsResponse(stats);
    }

    private WorkOrderStatsResponse toStatsResponse(WorkOrderStatsProjection stats) {
        return new WorkOrderStatsResponse(
                stats.getTotalOrders() == null ? 0 : stats.getTotalOrders(),
                stats.getOpenOrders() == null ? 0 : stats.getOpenOrders(),
                stats.getCompletedOrders() == null ? 0 : stats.getCompletedOrders(),
                stats.getOverdueOrders() == null ? 0 : stats.getOverdueOrders());
    }

    private boolean completedOrClosedOnly(WorkOrderStatus status, String statusScope) {
        if (status != null || statusScope == null || statusScope.isBlank()) {
            return false;
        }
        String normalizedScope = statusScope.trim().toUpperCase(Locale.ROOT);
        if (COMPLETED_OR_CLOSED_STATUS_SCOPE.equals(normalizedScope)) {
            return true;
        }
        throw RestException.badRequest("Unsupported work order statusScope: " + statusScope);
    }

    private TypeFilter resolveTypeFilter(WorkOrderType type, String typeScope) {
        if (type != null) {
            return new TypeFilter(type, false);
        }
        if (typeScope == null || typeScope.isBlank()) {
            return TypeFilter.none();
        }
        String normalizedScope = typeScope.trim().toUpperCase(Locale.ROOT);
        if (UNPLANNED_TYPE_SCOPE.equals(normalizedScope)) {
            return new TypeFilter(null, true);
        }
        throw RestException.badRequest("Unsupported work order typeScope: " + typeScope);
    }

    private record TypeFilter(WorkOrderType type, boolean unplannedOnly) {
        static TypeFilter none() {
            return new TypeFilter(null, false);
        }

        String typeName() {
            return type == null ? null : type.name();
        }
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> mobileFeed(UUID departmentId, UUID equipmentId, String search, int page, int pageSize) {
        Page<WorkOrder> resultPage = repository.searchMobileFeed(
                departmentId,
                equipmentId,
                search,
                PaginationUtils.pageRequest(page, pageSize));
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public WorkOrderDto findById(UUID id) {
        WorkOrder entity = getOrThrow(id);
        return toDetailDto(entity);
    }

    private static final Set<String> ALLOWED_WORK_ORDER_DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    @Transactional
    public List<WorkOrderDocumentDto> attachDocuments(
            UUID workOrderId,
            List<MultipartFile> files,
            List<String> documentNames,
            List<String> documentTypes,
            List<String> documentNumbers,
            AuthenticatedUser user
    ) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one work order document file is required");
        }
        List<String> normalizedDocumentNames = normalizeDocumentNames(files, documentNames);
        List<String> normalizedDocumentTypes = normalizeDocumentTypes(files, documentTypes);
        List<String> normalizedDocumentNumbers = normalizeDocumentNumbers(files, documentNumbers);
        List<WorkOrderDocumentDto> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            AttachmentGroupDto group = attachmentGroupService.createGroup(
                    normalizedDocumentNames.get(i),
                    null,
                    "WORK_ORDER",
                    workOrderId,
                    normalizedDocumentTypes.get(i),
                    normalizedDocumentNumbers.get(i),
                    List.of(files.get(i)),
                    null,
                    user
            );
            WorkOrderDocumentDto dto = WorkOrderDocumentDto.fromAttachmentGroup(workOrderId, group);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDocumentDto> getDocuments(UUID workOrderId, AuthenticatedUser user) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        return attachmentGroupService.listGroups("WORK_ORDER", workOrderId, user)
                .stream()
                .map(group -> WorkOrderDocumentDto.fromAttachmentGroup(workOrderId, group))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderDocumentDto getDocument(UUID workOrderId, UUID documentId, AuthenticatedUser user) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        return WorkOrderDocumentDto.fromAttachmentGroup(workOrderId, attachmentGroupService.getGroup(documentId, user));
    }

    @Transactional(readOnly = true)
    public com.toir.dto.file.PresignedUrlResponse getDocumentPresignedUrl(
            UUID workOrderId,
            UUID documentId,
            AuthenticatedUser user
    ) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        return attachmentGroupService.getFilePresignedUrl(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID workOrderId, UUID documentId, AuthenticatedUser user) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        return attachmentGroupService.downloadFile(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional
    public void deleteDocument(UUID workOrderId, UUID documentId, AuthenticatedUser user) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        attachmentGroupService.deleteGroup(documentId, user);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderPerformerOptionDto> performerOptions(UUID departmentId) {
        List<BrigadeMember> performers = brigadeMemberRepository.findActivePerformersByDepartment(departmentId);
        if (performers.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = performers.stream()
                .map(BrigadeMember::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndIsDeletedFalse(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return performers.stream()
                .map(member -> {
                    Brigade brigade = member.getBrigade();
                    UUID performerDepartmentId = brigade == null ? null : brigade.getDepartmentId();
                    String departmentName = performerDepartmentId == null
                            ? null
                            : departmentRepository.findById(performerDepartmentId)
                            .map(Department::getName)
                            .orElse(null);
                    return new WorkOrderPerformerOptionDto(
                            member.getId(),
                            performerDisplayName(member, usersById),
                            performerDepartmentId,
                            departmentName,
                            member.getRoleCode());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderEmployeePerformerOptionDto> employeePerformerOptions(
            UUID departmentId, String search, int page, int size
    ) {
        scopeAccessService.assertCanAccessDepartment(departmentId);
        String normalized = normalizeSearch(search);
        String[] parts = normalized == null ? new String[0] : normalized.split("\\s+", 3);
        Page<Employee> employees = employeeRepository.searchEmployees(
                normalized,
                parts.length > 1 ? parts[0] : null,
                parts.length > 1 ? parts[1] : null,
                true,
                departmentId,
                null,
                PaginationUtils.pageRequest(page, size)
        );
        List<UUID> userIds = employees.getContent().stream()
                .map(Employee::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, List<BrigadeMember>> memberships = userIds.isEmpty()
                ? Map.of()
                : brigadeMemberRepository.findActiveByUserIdIn(userIds).stream()
                .filter(member -> member.getBrigade() != null
                        && Objects.equals(departmentId, member.getBrigade().getDepartmentId()))
                .collect(Collectors.groupingBy(BrigadeMember::getUserId, LinkedHashMap::new, Collectors.toList()));
        return employees.map(employee -> new WorkOrderEmployeePerformerOptionDto(
                employee.getId(),
                employeeFullName(employee),
                employee.getUserId(),
                employee.getDepartmentId(),
                memberships.getOrDefault(employee.getUserId(), List.of()).stream()
                        .map(member -> new WorkOrderEmployeePerformerOptionDto.BrigadeMembership(
                                member.getId(), member.getBrigade().getId(), member.getBrigade().getName(), member.getRoleCode()))
                        .toList()
        ));
    }

    @Transactional
    public WorkOrderDto reassignPerformer(UUID workOrderId, WorkOrderPerformerAssignmentRequest request) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        scopeAccessService.assertCanAccessDepartment(workOrder.getDepartmentId());
        WorkOrderPerformerAssignmentPolicy.ResolvedAssignment resolved = performerAssignmentPolicy.resolve(
                request.performerId(), request.performerEmployeeId(), request.performerBrigadeMemberId(),
                workOrder.getDepartmentId());
        workOrder.setPerformerEmployee(resolved.employee());
        workOrder.setPerformer(resolved.brigadeMember());
        WorkOrder saved = repository.save(workOrder);
        validatePerformerSkillsForWorkOrder(saved);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        notifyAssignedPerformer(saved);
        return toDto(saved);
    }

    @Transactional
    public WorkOrderDto reassignPerformerFromDispatcher(
            UUID workOrderId, UUID legacyOwnerUserId, UUID employeeId, UUID memberId
    ) {
        WorkOrder workOrder = getOrThrow(workOrderId);
        scopeAccessService.assertCanAccessDepartment(workOrder.getDepartmentId());
        WorkOrderPerformerAssignmentPolicy.ResolvedAssignment resolved = performerAssignmentPolicy.resolveLegacyOwner(
                legacyOwnerUserId, employeeId, memberId, workOrder.getDepartmentId());
        workOrder.setPerformerEmployee(resolved.employee());
        workOrder.setPerformer(resolved.brigadeMember());
        WorkOrder saved = repository.save(workOrder);
        validatePerformerSkillsForWorkOrder(saved);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        notifyAssignedPerformer(saved);
        return toDto(saved);
    }

    @Transactional
    public WorkOrderDto create(WorkOrderRequest request) {
        return createPublic(request, null);
    }

    @Transactional
    public WorkOrderDto create(WorkOrderRequest request, UUID createdById) {
        return createPublic(request, createdById);
    }

    @Transactional
    public WorkOrderDto createPublic(WorkOrderRequest request, UUID createdById) {
        assertNoServerOwnedCreateFields(request);
        return createInternal(request, createdById);
    }

    @Transactional
    public WorkOrderDto createGenerated(WorkOrderRequest request) {
        return createGenerated(request, null);
    }

    @Transactional
    public WorkOrderDto createGenerated(WorkOrderRequest request, UUID createdById) {
        if (request.generationKey() == null || request.generationKey().isBlank()) {
            throw RestException.badRequest("Generated work order requires a server generation key");
        }
        WorkOrderDto created = createInternal(request, createdById);
        repository.flush();
        return created;
    }

    @Transactional
    public WorkOrderDto createCampaignLinked(WorkOrderRequest publicRequest, UUID campaignId, UUID stageId) {
        assertNoServerOwnedCreateFields(publicRequest);
        if (campaignId == null || stageId == null) {
            throw RestException.badRequest("Campaign-linked work order requires campaign and stage");
        }
        if (publicRequest.requiresShutdown() || publicRequest.requiresIsolation()) {
            throw RestException.badRequest("CAMPAIGN_WORK_ORDER_SAFETY_FLAGS_ARE_SERVER_OWNED");
        }
        WorkOrderRequest canonical = publicRequest.withSafetyRequirements(false, false)
                .withRepairCampaign(campaignId, stageId);
        return createInternal(canonical, null);
    }

    private void assertNoServerOwnedCreateFields(WorkOrderRequest request) {
        if (request.generationKey() != null || request.plannedShutdownId() != null
                || request.shutdownWorkItemId() != null || request.repairCampaignId() != null
                || request.repairCampaignStageId() != null) {
            throw RestException.badRequest("SERVER_OWNED_WORK_ORDER_FIELDS_NOT_ALLOWED");
        }
    }

    private WorkOrderDto createInternal(WorkOrderRequest request, UUID createdById) {
        if (request.equipmentId() == null) {
            throw RestException.badRequest("Equipment is required to create a work order");
        }
        String effectiveNumber = normalizeWorkOrderNumber(request.number());
        equipmentStatusLifecycleService.assertOperationallyAllowed(request.equipmentId(), "create work order");
        WorkType effectiveWorkType = request.workType() != null ? request.workType() : WorkType.REPAIR;
        validateReplacementFields(request, effectiveWorkType);
        validateTypeRequiredRelations(request);
        if (repository.existsByNumberAndIsDeletedFalse(effectiveNumber)) {
            throw RestException.conflict("Work order number already exists: " + effectiveNumber);
        }
        Defect linkedDefect = validateCreateRelations(request);
        UUID effectiveEquipmentNodeId = resolveEffectiveEquipmentNodeId(request, linkedDefect);
        EquipmentNode equipmentNode = validateEquipmentNodeLink(effectiveEquipmentNodeId, request.equipmentId());
        DefectList linkedDefectList = validateDefectListForWorkOrder(
                request.type(),
                request.equipmentId(),
                request.defectListId(),
                false);
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + request.equipmentId()));
        UUID effectiveDepartmentId = resolveEffectiveDepartmentId(request, equipment);
        UUID effectiveLocationId = resolveEffectiveLocationId(request, equipment, effectiveDepartmentId);
        RepairCampaignStage linkedCampaignStage = validateCampaignLink(request, effectiveDepartmentId);
        WorkOrderPerformerAssignmentPolicy.ResolvedAssignment performer = performerAssignmentPolicy.resolve(
                request.performerId(), request.performerEmployeeId(), request.performerBrigadeMemberId(),
                effectiveDepartmentId);
        UUID effectiveBudgetLineId = resolveWorkOrderBudgetLineId(request.budgetLineId(), linkedCampaignStage);
        reserveReplacementEquipmentOnCreate(request, effectiveWorkType);
        WorkOrder entity = new WorkOrder();
        entity.setNumber(effectiveNumber);
        entity.setTitle(request.title());
        entity.setEquipmentId(request.equipmentId());
        entity.setEquipmentNodeId(effectiveEquipmentNodeId);
        entity.setLocationId(effectiveLocationId);
        entity.setDepartmentId(effectiveDepartmentId);
        entity.setWorkLocationNote(normalizeNote(request.workLocationNote()));
        entity.setRepairRequestId(request.repairRequestId());
        entity.setDefectId(request.defectId());
        entity.setDefectListId(linkedDefectList == null ? null : linkedDefectList.getId());
        entity.setPprTaskId(request.pprTaskId());
        entity.setMaintenanceDueEventId(request.maintenanceDueEventId());
        if (linkedCampaignStage != null) {
            entity.setRepairCampaignId(linkedCampaignStage.getCampaign().getId());
            entity.setRepairCampaignStageId(linkedCampaignStage.getId());
        }
        entity.setBudgetLineId(effectiveBudgetLineId);
        entity.setCycleKey(request.cycleKey());
        entity.setGenerationKey(request.generationKey());
        entity.setPlannedShutdownId(request.plannedShutdownId());
        entity.setShutdownWorkItemId(request.shutdownWorkItemId());
        entity.setCounteragentId(request.counteragentId());
        entity.setPerformer(performer.brigadeMember());
        entity.setPerformerEmployee(performer.employee());
        entity.setType(request.type());
        entity.setWorkType(effectiveWorkType);
        entity.setWarehouseId(request.warehouseId());
        entity.setReplacementEquipmentId(request.replacementEquipmentId());
        if (request.priority() != null)
            entity.setPriority(request.priority());
        entity.setStartPlannedAt(request.startPlannedAt());
        entity.setEndPlannedAt(request.endPlannedAt());
        if (createdById != null) {
            entity.setCreatedById(createdById);
        }
        entity.setSummary(request.summary());
        entity.setRepairActRequired(Boolean.TRUE.equals(request.repairActRequired()));
        entity.setStoppageActRequired(Boolean.TRUE.equals(request.stoppageActRequired()));
        entity.setRequiresShutdown(Boolean.TRUE.equals(request.requiresShutdown()));
        entity.setRequiresIsolation(Boolean.TRUE.equals(request.requiresIsolation()));
        WorkOrder saved = repository.save(entity);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        generateTemplateTasksFromWorkOrderContext(saved);
        validatePerformerSkillsForWorkOrder(saved);
        safetyChecklistService.generateForWorkOrderIfTemplateExists(saved);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.WORK_ORDER,
                "Создан наряд " + saved.getNumber(),
                null,
                saved);

        workOrderSparePartRequirementService.syncFromWorkOrderContext(saved);
        notifyAssignedPerformer(saved, equipment);

        return toDetailDto(saved);
    }

    private void notifyAssignedPerformer(WorkOrder workOrder, Equipment equipment) {
        if (workOrder == null || workOrder.getId() == null) {
            return;
        }
        UUID performerUserId = performerUserId(workOrder);
        if (performerUserId == null) {
            return;
        }
        notificationService.notifyUser(
                performerUserId,
                performerNotificationContent(workOrder, equipment),
                NotificationSeverity.INFO,
                NotificationEventType.WORK_ORDER_ASSIGNED,
                NotificationEntityTypes.WORK_ORDER,
                workOrder.getId().toString()
        );
    }

    private void notifyAssignedPerformer(WorkOrder workOrder) {
        notifyAssignedPerformer(workOrder, null);
    }

    private NotificationContent performerNotificationContent(WorkOrder workOrder, Equipment equipment) {
        String equipmentName = equipment != null
                ? formatEquipmentName(equipment)
                : equipmentRepository.findByIdAndIsDeletedFalse(workOrder.getEquipmentId())
                .map(this::formatEquipmentName)
                .orElse(null);
        String ruEquipment = equipmentName != null ? equipmentName : "Это оборудование";
        String uzEquipment = equipmentName != null ? equipmentName : "Ushbu qurilma";
        String enEquipment = equipmentName != null ? equipmentName : "This equipment";
        return new NotificationContent(
                "Назначен заказ-наряд: " + workOrder.getNumber(),
                ruEquipment + ": технический осмотр или ремонт необходимо выполнить "
                        + plannedTimingText(workOrder.getStartPlannedAt(), "ru") + ".",
                "Ish buyurtmasi tayinlandi: " + workOrder.getNumber(),
                uzEquipment + " bo‘yicha texnik ko‘rik yoki ta’mirlashni "
                        + plannedTimingText(workOrder.getStartPlannedAt(), "uz") + ".",
                "Work order assigned: " + workOrder.getNumber(),
                enEquipment + ": the inspection or repair must be completed "
                        + plannedTimingText(workOrder.getStartPlannedAt(), "en") + "."
        );
    }

    private String formatEquipmentName(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        String code = equipment.getCode();
        String name = equipment.getName();
        if (code != null && !code.isBlank() && name != null && !name.isBlank()) {
            return "%s - %s".formatted(code, name);
        }
        if (name != null && !name.isBlank()) {
            return name;
        }
        return null;
    }

    @Transactional
    public TemplateTaskSyncResult syncTemplateTasks(UUID workOrderId, UUID templateId) {
        if (workOrderId == null || templateId == null) {
            return new TemplateTaskSyncResult(0, 0);
        }
        WorkOrder workOrder = getOrThrow(workOrderId);
        return syncTemplateTasks(workOrder, templateId);
    }

    @Transactional
    public WorkOrderDto finalizeApprovalFromApprovalRequest(UUID id, UUID approverId) {
        WorkOrder entity = getOrThrow(id);
        validateCanApprove(entity);
        entity.setStatus(WorkOrderStatus.APPROVED);
        entity.setApprovedById(approverId);
        ensureReplacementEquipmentReservedOnStart(entity);

        WorkOrder saved = repository.save(entity);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        safetyChecklistService.generateForWorkOrderIfTemplateExists(saved);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.APPROVE,
                AuditModule.WORK_ORDER,
                "Утверждён наряд " + entity.getNumber(),
                entity,
                saved);
        notifyAssignedPerformer(saved);

        return toDto(saved);
    }

    /**
     * @deprecated Approval decisions must go through ApprovalService. This wrapper remains for tests and
     * compatibility with older internal callers; approval handlers should call
     * {@link #finalizeApprovalFromApprovalRequest(UUID, UUID)}.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public WorkOrderDto approve(UUID id, UUID approverId) {
        return finalizeApprovalFromApprovalRequest(id, approverId);
    }

    @Transactional(readOnly = true)
    public WorkOrderDto validateCanApprove(UUID id) {
        WorkOrder entity = getOrThrow(id);
        validateCanApprove(entity);
        return toDto(entity);
    }

    private void validateCanApprove(WorkOrder entity) {
        if (entity.getStatus() != WorkOrderStatus.DRAFT && entity.getStatus() != WorkOrderStatus.PLANNED) {
            throw RestException.badRequest("Only DRAFT/PLANNED work orders can be approved");
        }
        assertDefectListGate(entity);
        validatePerformerSkillsForWorkOrder(entity);
    }

    @Transactional
    public WorkOrderDto start(UUID id) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.APPROVED) {
            throw RestException.badRequest("Only approved work orders can be started");
        }
        assertDefectListGate(entity);
        validatePerformerSkillsForWorkOrder(entity);
        plannedShutdownStartPolicy.assertCanStart(entity);
        safetyChecklistService.assertCanStart(entity);
        sparePartLifecycleOperationGuard.assertAllowed(
                entity.getEquipmentId(),
                com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation.WORK_ORDER_START);
        entity.setStatus(WorkOrderStatus.IN_PROGRESS);
        entity.setStartedAt(Instant.now());
        ensureReplacementEquipmentReservedOnStart(entity);

        WorkOrder saved = repository.save(entity);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        syncLinkedOnStart(saved);
        equipmentStatusLifecycleService.recordWorkOrderTransition(
                saved.getId(),
                saved.getEquipmentId(),
                EquipmentStatus.IN_REPAIR,
                "Work order started: " + saved.getNumber()
        );

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Начато выполнение наряда " + saved.getNumber(),
                entity,
                saved);

        return toDto(saved);
    }

    @Transactional
    public WorkOrderTaskDto updateTaskStatus(UUID workOrderId, UUID taskId, WorkOrderTaskStatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw RestException.badRequest("Task status is required");
        }
        if (request.actualHours() != null && request.actualHours() < 0) {
            throw RestException.badRequest("actualHours must be zero or greater");
        }

        WorkOrder entity = getOrThrow(workOrderId);
        assertCanAccessWorkOrder(entity);
        WorkOrderTask task = taskOrThrow(entity, taskId);
        validateTaskStatusTransition(task.getStatus(), request.status());

        Instant now = Instant.now();
        task.setStatus(request.status());
        if (request.actualHours() != null) {
            task.setActualHours(request.actualHours());
        }
        if (request.status() == TaskExecutionStatus.IN_PROGRESS && task.getStartedAt() == null) {
            task.setStartedAt(now);
        }
        if (request.status() == TaskExecutionStatus.DONE) {
            if (task.getStartedAt() == null) {
                task.setStartedAt(now);
            }
            task.setCompletedAt(now);
        }
        if (request.status() == TaskExecutionStatus.CANCELLED) {
            task.setCompletedAt(now);
        }

        repository.save(entity);
        return taskDtos(List.of(task)).getFirst();
    }

    @Transactional
    public WorkOrderDto complete(UUID id, CompleteWorkOrderRequest request) {
        WorkOrder entity = getOrThrow(id);
        assertCanComplete(entity, request);
        sparePartLifecycleOperationGuard.assertAllowed(
                entity.getEquipmentId(),
                com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation.WORK_ORDER_COMPLETE);
        validateAndBindCompletionActFiles(entity, request);
        if (pprLifecycleProperties.isStrictClosureEnabled()) {
            pprCompletionEvidenceService.validateAndStore(
                    entity, request, scopeAccessService.currentUserIdOrNull());
        }
        assertDefectListGate(entity);
        MaintenanceDueEvent dueEvent = loadMaintenanceDueEvent(entity);
        entity.setResult(request.result());
        if (request.summary() != null && !request.summary().isBlank()) {
            entity.setSummary(request.summary());
        }
        validateCompleteRequestForReplacement(entity, request);
        List<ResolvedCompletionMeterSnapshot> completionMeterSnapshots =
                resolveCompletionMeterSnapshots(entity, request);
        issueCompletionMaterials(entity, request);
        executeSparePartLifecycleOperations(entity, request);
        workOrderCompletionService.createActualCostsOnCompletion(entity);
        entity.setStatus(WorkOrderStatus.COMPLETED);
        entity.setCompletedAt(Instant.now());
        if (isReplacementWorkOrder(entity)) {
            completeReplacementPlacement(entity, request);
        }
        markLinkedPprTaskInProgress(entity);

        WorkOrder saved = repository.save(entity);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        MaintenanceCompletionAnchor completionAnchor = createMaintenanceCompletionAnchor(saved, request, dueEvent);
        completeLinkedMaintenanceDueEvent(dueEvent);
        persistCompletionMeterReadings(saved, completionMeterSnapshots, request);
        syncLinkedOnComplete(saved);
        if (!isReplacementWorkOrder(saved)) {
            equipmentStatusLifecycleService.recordWorkOrderReturn(
                    saved.getId(),
                    saved.getEquipmentId(),
                    "Work order completed: " + saved.getNumber()
            );
        }
        triggerMaintenanceRecalculation(saved, completionAnchor);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Завершён наряд " + saved.getNumber(),
                entity,
                saved);
        return toDetailDto(saved);
    }

    private void executeSparePartLifecycleOperations(WorkOrder workOrder, CompleteWorkOrderRequest request) {
        List<com.toir.dto.workorder.WorkOrderSparePartLifecycleOperation> operations =
                request.sparePartLifecycleOperations();
        if (operations == null || operations.isEmpty()) {
            return;
        }
        UUID actorId = scopeAccessService.currentUserIdOrNull();
        Set<String> operationKeys = new HashSet<>();
        for (com.toir.dto.workorder.WorkOrderSparePartLifecycleOperation operation : operations) {
            if (!operationKeys.add(operation.clientOperationKey())) {
                throw RestException.conflict(
                        "LIFECYCLE_OPERATION_KEY_DUPLICATE: clientOperationKey must be unique within completion");
            }
            String idempotencyKey = "work-order:" + workOrder.getId() + ":" + operation.clientOperationKey();
            switch (operation.operationType()) {
                case INSTALL -> {
                    if (operation.install() == null) {
                        throw RestException.badRequest("INSTALL_OPERATION_REQUIRED: install payload is required");
                    }
                    var install = operation.install();
                    sparePartLifecycleService.install(
                            workOrder.getEquipmentId(),
                            idempotencyKey,
                            actorId,
                            new com.toir.dto.sparepartlifecycle.InstallSparePartCommand(
                                    install.equipmentNodeId(), install.slotCode(), install.positionLabel(),
                                    install.sparePartId(), install.quantity(), install.serialNumber(), install.lotNumber(),
                                    install.installedAt(), workOrder.getId(), install.sourceMaterialUsageId(),
                                    install.externalSourceReason(), install.notes())
                    );
                }
                case REMOVE -> {
                    if (operation.remove() == null) {
                        throw RestException.badRequest("REMOVE_OPERATION_REQUIRED: remove payload is required");
                    }
                    var remove = operation.remove();
                    sparePartLifecycleService.remove(
                            idempotencyKey,
                            actorId,
                            new com.toir.dto.sparepartlifecycle.RemoveSparePartCommand(
                                    remove.installationId(), remove.removedAt(), workOrder.getId(),
                                    remove.disposition(), remove.reason(), remove.notes())
                    );
                }
                case REPLACE -> {
                    if (operation.replace() == null) {
                        throw RestException.badRequest("REPLACE_OPERATION_REQUIRED: replace payload is required");
                    }
                    var replace = operation.replace();
                    sparePartLifecycleService.replace(
                            idempotencyKey,
                            actorId,
                            new com.toir.dto.sparepartlifecycle.ReplaceSparePartCommand(
                                    replace.oldInstallationId(), replace.newPart(), replace.replacedAt(), workOrder.getId(),
                                    replace.oldPartDisposition(), replace.reason(), replace.notes())
                    );
                }
            }
        }
    }

    @Transactional
    public WorkOrderDto close(UUID id, CloseWorkOrderRequest request) {
        WorkOrder entity = getOrThrow(id);
        assertCanClose(entity, request);
        assertClosureEvidenceReady(entity);
        sparePartLifecycleOperationGuard.assertAllowed(
                entity.getEquipmentId(),
                com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation.WORK_ORDER_CLOSE);
        entity.setResult(request.result());
        entity.setClosureNotes(request.closureNotes());
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setCompletedAt(Instant.now());
        updateReplacementEquipmentStatus(entity, WarehouseEquipmentStatus.INSTALLED);
        completeLinkedPprTask(entity);

        WorkOrder saved = repository.save(entity);
        erpWorkOrderDeltas.queueDelta(saved.getId());
        syncLinkedOnClose(saved);
        if (!isReplacementWorkOrder(saved)) {
            equipmentStatusLifecycleService.recordWorkOrderReturn(
                    saved.getId(),
                    saved.getEquipmentId(),
                    "Work order closed: " + saved.getNumber()
            );
        }

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.CLOSE,
                AuditModule.WORK_ORDER,
                "Закрыт наряд " + saved.getNumber(),
                saved,
                null);

        return toDto(saved);
    }

    private void assertCanComplete(WorkOrder entity, CompleteWorkOrderRequest request) {
        if (!COMPLETE_ALLOWED_WORK_ORDER_STATUSES.contains(entity.getStatus())) {
            throw RestException.badRequest("Only APPROVED or IN_PROGRESS work orders can be completed");
        }
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to complete a work order");
        }
        List<String> incompleteTasks = incompleteTaskNames(entity);
        if (!incompleteTasks.isEmpty()) {
            throw RestException.badRequest("Cannot complete work order; incomplete mandatory tasks/checklist items: "
                    + String.join(", ", incompleteTasks));
        }
    }

    private void validateAndBindCompletionActFiles(WorkOrder entity, CompleteWorkOrderRequest request) {
        UUID repairActFileId = request.repairActFileId();
        UUID stoppageActFileId = request.stoppageActFileId();
        boolean repairActRequired = Boolean.TRUE.equals(entity.getRepairActRequired());
        boolean stoppageActRequired = Boolean.TRUE.equals(entity.getStoppageActRequired());
        boolean hasRepairActFile = entity.getRepairActFileAssetId() != null || repairActFileId != null;
        boolean hasStoppageActFile = entity.getStoppageActFileAssetId() != null || stoppageActFileId != null;

        if (repairActRequired && !hasRepairActFile) {
            throw RestException.badRequest("Bu WorkOrder uchun ta'mirlash akti fayli talab qilinadi.");
        }
        if (stoppageActRequired && !hasStoppageActFile) {
            throw RestException.badRequest("Bu WorkOrder uchun to'xtash akti fayli talab qilinadi.");
        }

        if (repairActFileId != null) {
            assertFileAssetBelongsToWorkOrder(repairActFileId, entity.getId());
            entity.setRepairActFileAssetId(repairActFileId);
        }
        if (stoppageActFileId != null) {
            assertFileAssetBelongsToWorkOrder(stoppageActFileId, entity.getId());
            entity.setStoppageActFileAssetId(stoppageActFileId);
        }
    }

    private void assertFileAssetBelongsToWorkOrder(UUID fileAssetId, UUID workOrderId) {
        FileAsset file = fileAssetRepository.findByIdAndIsDeletedFalse(fileAssetId)
                .orElseThrow(() -> RestException.notFound("File not found: " + fileAssetId));
        if (!"WORK_ORDER".equalsIgnoreCase(file.getEntityType())
                || !workOrderId.toString().equals(file.getEntityId())) {
            throw RestException.badRequest(
                    "Completion act file does not belong to work order " + workOrderId);
        }
    }

    private MaintenanceDueEvent loadMaintenanceDueEvent(WorkOrder workOrder) {
        if (workOrder.getMaintenanceDueEventId() == null) {
            return null;
        }
        return maintenanceDueEventService.getOrThrow(workOrder.getMaintenanceDueEventId());
    }

    private MaintenanceCompletionAnchor createMaintenanceCompletionAnchor(WorkOrder workOrder,
                                                                         CompleteWorkOrderRequest request,
                                                                         MaintenanceDueEvent dueEvent) {
        UUID regulationId = request.regulationId();
        UUID equipmentMaintenanceRuleId = request.equipmentMaintenanceRuleId();
        Instant plannedDueAt = request.plannedDueAt();

        PprTask task = null;
        if (workOrder.getPprTaskId() != null) {
            task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId()).orElse(null);
        }
        if ((regulationId == null && equipmentMaintenanceRuleId == null) && task != null) {
            regulationId = task.getRegulationId();
            equipmentMaintenanceRuleId = task.getEquipmentMaintenanceRuleId();
        }
        if ((regulationId == null && equipmentMaintenanceRuleId == null) && dueEvent != null) {
            regulationId = dueEvent.getRegulationId();
            equipmentMaintenanceRuleId = dueEvent.getEquipmentMaintenanceRuleId();
        }
        if (plannedDueAt == null && task != null && task.getDueDate() != null) {
            plannedDueAt = task.getDueDate().atZone(ZoneId.systemDefault()).toInstant();
        }
        if (plannedDueAt == null && dueEvent != null) {
            plannedDueAt = dueEvent.getDueAt();
        }
        List<CompletionMeterSnapshotRequest> meterSnapshots = request.meterSnapshots();
        if ((meterSnapshots == null || meterSnapshots.isEmpty()) && dueEvent != null
                && dueEvent.getMeterType() != null && dueEvent.getMeterCurrentValue() != null) {
            meterSnapshots = List.of(new CompletionMeterSnapshotRequest(
                    null,
                    dueEvent.getMeterType(),
                    dueEvent.getMeterCurrentValue(),
                    dueEvent.getDetectedAt() == null ? workOrder.getCompletedAt() : dueEvent.getDetectedAt()
            ));
        }
        if (regulationId == null && equipmentMaintenanceRuleId == null) {
            return null;
        }
        MaintenanceCompletionAnchor anchor = findExistingMaintenanceCompletionAnchor(workOrder, dueEvent)
                .orElseGet(MaintenanceCompletionAnchor::new);
        anchor.setEquipmentId(workOrder.getEquipmentId());
        anchor.setRegulationId(regulationId);
        anchor.setEquipmentMaintenanceRuleId(equipmentMaintenanceRuleId);
        anchor.setWorkOrderId(workOrder.getId());
        anchor.setPprTaskId(workOrder.getPprTaskId());
        anchor.setMaintenanceDueEventId(dueEvent == null ? null : dueEvent.getId());
        anchor.setPerformedAt(request.performedAt() == null ? workOrder.getCompletedAt() : request.performedAt());
        anchor.setPlannedDueAt(plannedDueAt);
        anchor.setPlannedMeterValue(plannedMeterValue(dueEvent));
        anchor.setRecalculationPolicy(request.recalculationPolicy() == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : request.recalculationPolicy());
        anchor.setMeterSnapshots(toMeterSnapshotsJson(meterSnapshots));
        anchor.setSource("WORK_ORDER");
        anchor.setNote(request.summary());
        return maintenanceCompletionAnchorRepository.save(anchor);
    }

    private List<ResolvedCompletionMeterSnapshot> resolveCompletionMeterSnapshots(WorkOrder workOrder,
                                                                                  CompleteWorkOrderRequest request) {
        List<CompletionMeterSnapshotRequest> snapshots = request.meterSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<ResolvedCompletionMeterSnapshot> resolved = new ArrayList<>(snapshots.size());
        for (CompletionMeterSnapshotRequest snapshot : snapshots) {
            if (snapshot == null) {
                throw RestException.badRequest("Meter snapshot is required");
            }
            if (snapshot.value() == null) {
                throw RestException.badRequest("Meter snapshot value is required");
            }
            if (snapshot.value() < 0) {
                throw RestException.badRequest("Meter snapshot value must be non-negative");
            }
            resolved.add(new ResolvedCompletionMeterSnapshot(snapshot, resolveCompletionSnapshotMeter(workOrder, snapshot)));
        }
        return List.copyOf(resolved);
    }

    private EquipmentMeter resolveCompletionSnapshotMeter(WorkOrder workOrder,
                                                          CompletionMeterSnapshotRequest snapshot) {
        if (snapshot.meterId() != null) {
            EquipmentMeter meter = equipmentMeterRepository.findByIdAndIsDeletedFalse(snapshot.meterId())
                    .orElseThrow(() -> RestException.badRequest("Active meter not found: " + snapshot.meterId()));
            assertCompletionSnapshotMeterMatchesWorkOrder(workOrder, snapshot, meter);
            return meter;
        }
        if (snapshot.meterType() == null) {
            throw RestException.badRequest("Meter snapshot meterId or meterType is required");
        }
        return equipmentMeterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(workOrder.getEquipmentId())
                .stream()
                .filter(meter -> meter.getMeterType() == snapshot.meterType())
                .findFirst()
                .orElseThrow(() -> RestException.badRequest(
                        "Active meter not found for work order equipment and meter type " + snapshot.meterType()));
    }

    private void assertCompletionSnapshotMeterMatchesWorkOrder(WorkOrder workOrder,
                                                               CompletionMeterSnapshotRequest snapshot,
                                                               EquipmentMeter meter) {
        if (!Objects.equals(meter.getEquipmentId(), workOrder.getEquipmentId())) {
            throw RestException.badRequest("Meter " + meter.getId() + " does not belong to work order equipment");
        }
        if (!meter.isActive()) {
            throw RestException.badRequest("Meter " + meter.getId() + " is not active");
        }
        MeterType snapshotMeterType = snapshot.meterType();
        if (snapshotMeterType != null && meter.getMeterType() != snapshotMeterType) {
            throw RestException.badRequest("Meter snapshot type does not match meter " + meter.getId());
        }
    }

    private void persistCompletionMeterReadings(WorkOrder workOrder,
                                                List<ResolvedCompletionMeterSnapshot> snapshots,
                                                CompleteWorkOrderRequest request) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        Instant fallbackReadAt = request.performedAt() == null ? workOrder.getCompletedAt() : request.performedAt();
        for (ResolvedCompletionMeterSnapshot resolved : snapshots) {
            CompletionMeterSnapshotRequest snapshot = resolved.snapshot();
            EquipmentMeter meter = resolved.meter();
            meterService.addReading(
                    new MeterReadingRequest(
                            meter.getId(),
                            snapshot.value(),
                            snapshot.readAt() == null ? fallbackReadAt : snapshot.readAt(),
                            MeterSource.MANUAL,
                            null,
                            null,
                            "Work order completed: " + workOrder.getNumber()
                    ),
                    MeterReadingContext.WORK_COMPLETED,
                    workOrder.getRepairRequestId(),
                    workOrder.getId(),
                    workOrder.getDefectId()
            );
        }
    }

    private Optional<MaintenanceCompletionAnchor> findExistingMaintenanceCompletionAnchor(WorkOrder workOrder,
                                                                                          MaintenanceDueEvent dueEvent) {
        if (dueEvent != null && dueEvent.getId() != null) {
            Optional<MaintenanceCompletionAnchor> byEvent =
                    maintenanceCompletionAnchorRepository.findByMaintenanceDueEventIdAndIsDeletedFalse(dueEvent.getId());
            if (byEvent.isPresent()) {
                return byEvent;
            }
        }
        if (workOrder.getId() == null) {
            return Optional.empty();
        }
        return maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrder.getId());
    }

    private BigDecimal plannedMeterValue(MaintenanceDueEvent dueEvent) {
        if (dueEvent == null) {
            return null;
        }
        Double plannedValue = null;
        if (dueEvent.getMeterAnchorValue() != null && dueEvent.getMeterInterval() != null) {
            plannedValue = dueEvent.getMeterAnchorValue() + dueEvent.getMeterInterval();
        } else if (dueEvent.getMeterCurrentValue() != null && dueEvent.getMeterRemaining() != null) {
            plannedValue = dueEvent.getMeterCurrentValue() + Math.max(0.0, dueEvent.getMeterRemaining());
        }
        if (plannedValue == null || !Double.isFinite(plannedValue)) {
            return null;
        }
        return BigDecimal.valueOf(plannedValue);
    }

    private void completeLinkedMaintenanceDueEvent(MaintenanceDueEvent dueEvent) {
        if (dueEvent == null) {
            return;
        }
        maintenanceDueEventService.completeFromWorkOrder(dueEvent, "Work order completed");
    }

    private void triggerMaintenanceRecalculation(WorkOrder workOrder, MaintenanceCompletionAnchor completionAnchor) {
        if (!hasPlannedMaintenanceContext(workOrder, completionAnchor)) {
            return;
        }
        if (workOrder.getEquipmentId() == null) {
            log.warn("Skipping maintenance recalculation for workOrderId={} because equipmentId is missing", workOrder.getId());
            return;
        }
        MaintenanceAutomationService automationService = maintenanceAutomationServiceProvider.getIfAvailable();
        if (automationService == null) {
            log.warn("Skipping maintenance recalculation for workOrderId={} equipmentId={} because MaintenanceAutomationService is unavailable",
                    workOrder.getId(), workOrder.getEquipmentId());
            return;
        }
        automationService.evaluateEquipment(workOrder.getEquipmentId(), MaintenanceTriggerSource.WORK_ORDER_COMPLETED);
    }

    private boolean hasPlannedMaintenanceContext(WorkOrder workOrder, MaintenanceCompletionAnchor completionAnchor) {
        if (workOrder.getMaintenanceDueEventId() != null || workOrder.getPprTaskId() != null) {
            return true;
        }
        if (completionAnchor != null && (completionAnchor.getMaintenanceDueEventId() != null
                || completionAnchor.getPprTaskId() != null
                || completionAnchor.getRegulationId() != null
                || completionAnchor.getEquipmentMaintenanceRuleId() != null)) {
            return true;
        }
        return hasTemplateTaskContext(workOrder);
    }

    private boolean hasTemplateTaskContext(WorkOrder workOrder) {
        return workOrder.getTasks() != null && workOrder.getTasks().stream().anyMatch(task ->
                task.getSourceTemplateId() != null || task.getSourceOperationId() != null);
    }

    private String toMeterSnapshotsJson(List<CompletionMeterSnapshotRequest> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw RestException.badRequest("Invalid meter snapshots");
        }
    }

    private void assertCanClose(WorkOrder entity, CloseWorkOrderRequest request) {
        if (entity.getStatus() != WorkOrderStatus.COMPLETED) {
            throw RestException.badRequest("Only completed work orders can be closed");
        }
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to close a work order");
        }
    }

    @Transactional(readOnly = true)
    public WorkOrderCloseReadinessDto getCloseReadiness(UUID id) {
        WorkOrder entity = getOrThrow(id);
        ClosureReadiness readiness = buildCloseReadiness(entity, true);
        return new WorkOrderCloseReadinessDto(
                entity.getId(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                readiness.ready(),
                Instant.now(),
                readiness.blockers(),
                readiness.warnings(),
                readiness.groups()
        );
    }

    private void assertClosureEvidenceReady(WorkOrder entity) {
        ClosureReadiness readiness = buildCloseReadiness(entity, false);
        if (!readiness.ready()) {
            throw RestException.badRequest("Cannot close work order; missing evidence: "
                    + readiness.blockers().stream()
                    .map(WorkOrderCloseReadinessItemDto::message)
                    .collect(Collectors.joining("; ")));
        }
    }

    private ClosureReadiness buildCloseReadiness(WorkOrder entity, boolean includePreCloseChecks) {
        List<WorkOrderCloseReadinessItemDto> blockers = new ArrayList<>();
        List<WorkOrderCloseReadinessItemDto> warnings = new ArrayList<>();
        Map<String, CloseReadinessGroupStatus> groups = closeReadinessGroups();

        if (includePreCloseChecks) {
            if (entity.getStatus() == WorkOrderStatus.CLOSED) {
                addBlocker(blockers, groups, "ALREADY_CLOSED",
                        "Work order is already closed.", "equipment", "review-status", "overview");
            } else if (entity.getStatus() == WorkOrderStatus.CANCELLED) {
                addBlocker(blockers, groups, "INVALID_STATUS_FOR_CLOSE",
                        "Cancelled work orders cannot be closed.", "equipment", "review-status", "overview");
            } else if (entity.getStatus() != WorkOrderStatus.COMPLETED) {
                addBlocker(blockers, groups, "WORK_ORDER_NOT_CLOSEABLE",
                        "Work order can be closed only after it reaches Completed status.",
                        "equipment", "review-status", "overview");
            }
            if (entity.getResult() == null || entity.getResult().isBlank()) {
                addBlocker(blockers, groups, "MISSING_RESULT",
                        "Result is required to close a work order.", "acts", "enter-result", "closure");
            }
        }

        List<String> incompleteTasks = incompleteTaskNames(entity);
        if (!incompleteTasks.isEmpty()) {
            addBlocker(blockers, groups, "INCOMPLETE_TASKS",
                    "Incomplete tasks/checklist items: " + String.join(", ", incompleteTasks),
                    "tasks", "review-tasks", "tasks");
        }

        safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(entity.getId())
                .filter(permit -> permit.getStatus() != SafetyPermitStatus.CLOSED)
                .ifPresent(permit -> addBlocker(blockers, groups, "OPEN_SAFETY_PERMIT",
                        "Safety permit must be CLOSED"
                                + (permit.getStatus() == null ? "" : " (current: " + permit.getStatus() + ")"),
                        "safety", "close-safety-permit", "safety"));

        Optional<com.toir.entity.CompletionAct> completionAct = completionActRepository
                .findByWorkOrderIdAndIsDeletedFalse(entity.getId());
        if (pprLifecycleProperties.isStrictClosureEnabled()
                && entity.getPprTaskId() != null && completionAct.isEmpty()) {
            addBlocker(blockers, groups, "COMPLETION_ACT_MISSING",
                    "Completion act is required for a PPR work order.",
                    "acts", "create-completion-act", "closure");
        } else {
            completionAct
                    .filter(act -> act.getSignedAt() == null || act.getSignedById() == null)
                    .ifPresent(act -> addBlocker(blockers, groups, "COMPLETION_ACT_NOT_SIGNED",
                            "Completion act must be signed.",
                            "acts", "sign-completion-act", "closure"));
        }

        if (!repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(entity.getId())) {
            addBlocker(blockers, groups, "FINAL_ACCEPTANCE_NOT_ACCEPTED",
                    "Final repair acceptance must be ACCEPTED.", "acts", "review-acceptance", "closure");
        }

        if (pprLifecycleProperties.isStrictClosureEnabled() && entity.getPprTaskId() != null) {
            Set<com.toir.enums.CompletionEvidenceType> missingEvidence =
                    pprCompletionEvidenceService.missingEvidence(entity);
            if (missingEvidence != null && !missingEvidence.isEmpty()) {
                addBlocker(blockers, groups, "MISSING_PPR_EVIDENCE",
                        "Missing required PPR completion evidence: " + missingEvidence,
                        "acts", "add-completion-evidence", "closure");
            }
        }

        Optional<String> safetyChecklistBlocker = safetyChecklistService.closeBlocker(entity);
        if (safetyChecklistBlocker != null) {
            safetyChecklistBlocker.ifPresent(message -> addBlocker(blockers, groups, "SAFETY_CHECKLIST_BLOCKER",
                    message, "safety", "review-safety-checklist", "safety"));
        }

        if (requiresLaborEvidence(entity) && safeList(laborEntryRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(entity.getId())).isEmpty()) {
            addBlocker(blockers, groups, "MISSING_LABOR_ENTRIES",
                    "At least one labor entry is required for " + entity.getWorkType() + " work order.",
                    "labor", "review-labor", "labor");
        }

        List<String> activeReservations = safeList(reservationRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId()))
                .stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE)
                .map(reservation -> reservation.getId() == null ? "active reservation" : reservation.getId().toString())
                .toList();
        if (!activeReservations.isEmpty()) {
            addBlocker(blockers, groups, "ACTIVE_MATERIAL_RESERVATIONS",
                    "Material reservations must be issued, released or cancelled: "
                            + String.join(", ", activeReservations),
                    "materials", "review-materials", "resources");
        }

        List<ActualCost> actualCosts = safeList(actualCostRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId()));
        long pendingCosts = actualCosts.stream()
                .filter(cost -> cost.getStatus() == ActualCostStatus.PENDING)
                .count();
        if (pendingCosts > 0) {
            addWarning(warnings, groups, "PENDING_ACTUAL_COSTS",
                    "Pending actual costs require finance review.", "finance", "review-costs", "finance");
        } else if (actualCosts.stream().anyMatch(cost -> cost.getStatus() != ActualCostStatus.APPROVED)) {
            addWarning(warnings, groups, "UNAPPROVED_ACTUAL_COSTS",
                    "Unapproved actual costs require finance review.", "finance", "review-costs", "finance");
        }

        return new ClosureReadiness(blockers.isEmpty(), List.copyOf(blockers), List.copyOf(warnings), Map.copyOf(groups));
    }

    private Map<String, CloseReadinessGroupStatus> closeReadinessGroups() {
        Map<String, CloseReadinessGroupStatus> groups = new LinkedHashMap<>();
        groups.put("tasks", CloseReadinessGroupStatus.READY);
        groups.put("acts", CloseReadinessGroupStatus.READY);
        groups.put("materials", CloseReadinessGroupStatus.READY);
        groups.put("labor", CloseReadinessGroupStatus.READY);
        groups.put("safety", CloseReadinessGroupStatus.READY);
        groups.put("finance", CloseReadinessGroupStatus.READY);
        groups.put("equipment", CloseReadinessGroupStatus.READY);
        groups.put("ppr", CloseReadinessGroupStatus.READY);
        return groups;
    }

    private void addBlocker(List<WorkOrderCloseReadinessItemDto> blockers,
                            Map<String, CloseReadinessGroupStatus> groups,
                            String code,
                            String message,
                            String group,
                            String targetAction,
                            String targetTab) {
        blockers.add(new WorkOrderCloseReadinessItemDto(
                code,
                message,
                CloseReadinessSeverity.BLOCKING,
                group,
                targetAction,
                targetTab
        ));
        groups.put(group, CloseReadinessGroupStatus.BLOCKED);
    }

    private void addWarning(List<WorkOrderCloseReadinessItemDto> warnings,
                            Map<String, CloseReadinessGroupStatus> groups,
                            String code,
                            String message,
                            String group,
                            String targetAction,
                            String targetTab) {
        warnings.add(new WorkOrderCloseReadinessItemDto(
                code,
                message,
                CloseReadinessSeverity.WARNING,
                group,
                targetAction,
                targetTab
        ));
        groups.computeIfPresent(group, (key, current) -> current == CloseReadinessGroupStatus.BLOCKED
                ? current
                : CloseReadinessGroupStatus.WARNING);
    }

    private boolean requiresLaborEvidence(WorkOrder workOrder) {
        return workOrder.getWorkType() == WorkType.REPAIR || workOrder.getWorkType() == WorkType.REPLACEMENT;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String taskEvidenceName(WorkOrderTask task) {
        if (task.getTitle() != null && !task.getTitle().isBlank()) {
            return task.getTitle();
        }
        return task.getId() == null ? "unnamed task" : task.getId().toString();
    }

    private WorkOrderTask taskOrThrow(WorkOrder entity, UUID taskId) {
        if (taskId == null || entity.getTasks() == null) {
            throw RestException.notFound("Work order task not found: " + taskId);
        }
        return entity.getTasks().stream()
                .filter(task -> taskId.equals(task.getId()) && !task.isDeleted())
                .findFirst()
                .orElseThrow(() -> RestException.notFound("Work order task not found: " + taskId));
    }

    private void validateTaskStatusTransition(TaskExecutionStatus current, TaskExecutionStatus requested) {
        if (current == requested) {
            return;
        }
        if (current == TaskExecutionStatus.DONE || current == TaskExecutionStatus.CANCELLED) {
            throw RestException.badRequest("DONE or CANCELLED work order tasks cannot be changed");
        }
        if (current == TaskExecutionStatus.TODO) {
            if (requested == TaskExecutionStatus.IN_PROGRESS
                    || requested == TaskExecutionStatus.DONE
                    || requested == TaskExecutionStatus.CANCELLED) {
                return;
            }
        }
        if (current == TaskExecutionStatus.IN_PROGRESS) {
            if (requested == TaskExecutionStatus.DONE || requested == TaskExecutionStatus.CANCELLED) {
                return;
            }
        }
        throw RestException.badRequest("Invalid work order task status transition: " + current + " -> " + requested);
    }

    private List<String> incompleteTaskNames(WorkOrder entity) {
        return entity.getTasks() == null
                ? List.of()
                : entity.getTasks().stream()
                .filter(task -> task.getStatus() != TaskExecutionStatus.DONE)
                .map(this::taskEvidenceName)
                .toList();
    }

    private record ClosureReadiness(
            boolean ready,
            List<WorkOrderCloseReadinessItemDto> blockers,
            List<WorkOrderCloseReadinessItemDto> warnings,
            Map<String, CloseReadinessGroupStatus> groups
    ) {
    }

    @Transactional
    public WorkOrderDto recalculateLinkedPprPlanForWorkOrder(UUID id) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getPprTaskId() == null) {
            return toDto(entity);
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(entity.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + entity.getPprTaskId()));
        recalculatePlanStatus(task.getPlan());
        return toDto(entity);
    }

    private void markLinkedPprTaskInProgress(WorkOrder workOrder) {
        if (workOrder.getPprTaskId() == null) {
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + workOrder.getPprTaskId()));
        if (task.getStatus() == PprTaskStatus.COMPLETED || task.getStatus() == PprTaskStatus.CANCELLED) {
            recalculatePlanStatus(task.getPlan());
            return;
        }
        task.setStatus(PprTaskStatus.IN_PROGRESS);
        PprTask saved = pprTaskRepository.save(task);
        auditBuilderService.log(
                "ppr_task",
                task.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_TASK,
                "PPR task awaits acceptance for linked work order " + workOrder.getNumber(),
                task,
                saved);
        recalculatePlanStatus(task.getPlan());
    }

    private void completeLinkedPprTask(WorkOrder workOrder) {
        if (workOrder.getPprTaskId() == null) {
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + workOrder.getPprTaskId()));
        if (task.getStatus() == PprTaskStatus.COMPLETED || task.getStatus() == PprTaskStatus.CANCELLED) {
            recalculatePlanStatus(task.getPlan());
            return;
        }
        task.setStatus(PprTaskStatus.COMPLETED);

        PprTask saved = pprTaskRepository.save(task);
        auditBuilderService.log(
                "ppr_task",
                task.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_TASK,
                "PPR task completed from linked work order " + workOrder.getNumber(),
                task,
                saved);
        recalculatePlanStatus(task.getPlan());
    }

    private void recalculatePlanStatus(PprPlan plan) {
        if (plan == null || plan.getId() == null) {
            return;
        }
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            return;
        }
        List<PprTask> planTasks = pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(plan.getId());
        if (planTasks.isEmpty()) {
            return;
        }

        boolean allCompleted = planTasks.stream()
                .allMatch(t -> t.getStatus() == PprTaskStatus.COMPLETED);
        boolean hasOperationalProgress = planTasks.stream()
                .anyMatch(t -> t.getStatus() == PprTaskStatus.IN_PROGRESS || t.getStatus() == PprTaskStatus.COMPLETED);

        PlanStatus newStatus = null;
        if (allCompleted) {
            newStatus = PlanStatus.CLOSED;
        } else if (hasOperationalProgress) {
            newStatus = PlanStatus.IN_PROGRESS;
        }

        if (newStatus == null || plan.getStatus() == newStatus) {
            return;
        }

        plan.setStatus(newStatus);
        PprPlan savedPlan = pprPlanRepository.save(plan);
        auditBuilderService.log(
                "ppr_plan",
                savedPlan.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_PLAN,
                "PPR plan status recalculated from child task progress",
                plan,
                savedPlan);
    }

    private void syncLinkedOnStart(WorkOrder workOrder) {
        syncRepairRequestOnStart(workOrder);
        syncDefectOnStart(workOrder);
    }

    private void syncLinkedOnComplete(WorkOrder workOrder) {
        syncDefectOnComplete(workOrder);
        syncRepairRequestOnComplete(workOrder);
    }

    private void syncLinkedOnClose(WorkOrder workOrder) {
        syncDefectOnClose(workOrder);
        syncRepairRequestOnComplete(workOrder);
    }

    private void syncRepairRequestOnStart(WorkOrder workOrder) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .ifPresent(request -> {
                    if (TERMINAL_REPAIR_REQUEST_STATUSES.contains(request.getStatus())) {
                        return;
                    }
                    if (request.getStatus() == RequestStatus.IN_PROGRESS) {
                        return;
                    }
                    request.setStatus(RequestStatus.IN_PROGRESS);
                    RepairRequest saved = repairRequestRepository.save(request);
                    auditBuilderService.log(
                            "repair_request",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.REPAIR_REQUEST,
                            "Repair request moved to IN_PROGRESS from linked work order start",
                            request,
                            saved);
                });
    }

    private void syncDefectOnStart(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
                        return;
                    }
                    if (defect.getStatus() == DefectStatus.IN_PROGRESS) {
                        return;
                    }
                    defect.setStatus(DefectStatus.IN_PROGRESS);
                    Defect saved = defectRepository.save(defect);
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.DEFECT,
                            "Defect moved to IN_PROGRESS from linked work order start",
                            defect,
                            saved);
                });
    }

    private void syncDefectOnComplete(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
                        return;
                    }
                    if (hasActiveWorkOrderForDefect(defect.getId())) {
                        return;
                    }
                    defect.setStatus(DefectStatus.RESOLVED);
                    defect.setResolvedAt(Instant.now());
                    Defect saved = defectRepository.save(defect);
                    operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(
                            saved,
                            "Defect resolved from linked work order completion.");
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.DEFECT,
                            "Defect resolved after linked work orders became non-active",
                            defect,
                            saved);
                });
    }

    private void syncRepairRequestOnComplete(WorkOrder workOrder) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .ifPresent(request -> {
                    if (!allWorkOrdersTerminalForRepairRequest(request.getId())) {
                        return;
                    }
                    if (!allDefectsResolvedOrClosedForRepairRequest(request.getId())) {
                        return;
                    }
                    if (TERMINAL_REPAIR_REQUEST_STATUSES.contains(request.getStatus())) {
                        return;
                    }
                    request.setStatus(RequestStatus.COMPLETED);
                    RepairRequest saved = repairRequestRepository.save(request);
                    operationalIssueLifecycleSyncService.sweepRepairRequest(
                            saved.getId(),
                            "Repair request completed after linked work orders and defects reached terminal state.");
                    auditBuilderService.log(
                            "repair_request",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.REPAIR_REQUEST,
                            "Repair request moved to COMPLETED after linked work order completion",
                            request,
                            saved);
                });
    }

    private void syncDefectOnClose(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (!allWorkOrdersTerminalForDefect(defect.getId())) {
                        return;
                    }
                    if (defect.getStatus() == DefectStatus.CLOSED) {
                        return;
                    }
                    if (defect.getStatus() != DefectStatus.RESOLVED) {
                        return;
                    }
                    defect.setStatus(DefectStatus.CLOSED);
                    Defect saved = defectRepository.save(defect);
                    operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(
                            saved,
                            "Defect closed after linked work orders reached terminal state.");
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.CLOSE,
                            AuditModule.DEFECT,
                            "Defect closed after linked work orders reached terminal state",
                            defect,
                            saved);
                });
    }

    private boolean hasActiveWorkOrderForDefect(UUID defectId) {
        List<WorkOrder> linkedWorkOrders = repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId);
        return linkedWorkOrders.stream()
                .anyMatch(linkedWorkOrder -> ACTIVE_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allWorkOrdersTerminalForRepairRequest(UUID repairRequestId) {
        List<WorkOrder> linkedWorkOrders = repository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId);
        if (linkedWorkOrders.isEmpty()) {
            return false;
        }
        return linkedWorkOrders.stream()
                .allMatch(linkedWorkOrder -> TERMINAL_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allWorkOrdersTerminalForDefect(UUID defectId) {
        List<WorkOrder> linkedWorkOrders = repository
                .findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId);
        if (linkedWorkOrders.isEmpty()) {
            return false;
        }
        return linkedWorkOrders.stream()
                .allMatch(linkedWorkOrder -> TERMINAL_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allDefectsResolvedOrClosedForRepairRequest(UUID repairRequestId) {
        List<Defect> linkedDefects = defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId);
        return linkedDefects.stream()
                .allMatch(defect -> RESOLVED_OR_CLOSED_DEFECT_STATUSES.contains(defect.getStatus()));
    }

    private WorkOrder getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private WorkOrderDocument findWorkOrderDocument(UUID workOrderId, UUID documentId) {
        return workOrderDocumentRepository.findByIdAndWorkOrderId(documentId, workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order document not found: " + documentId));
    }

    private WorkOrderDocumentDto toDocumentDtoWithMetadata(
            UUID workOrderId,
            WorkOrderDocument document,
            UUID currentUserId
    ) {
        fileService.getMetadata(document.getFile().getId(), currentUserId);
        return WorkOrderDocumentDto.from(workOrderId, document);
    }

    private AttachmentGroupDto.FileItem primaryFile(AttachmentGroupDto group) {
        if (group == null || group.files() == null || group.files().isEmpty()) {
            throw RestException.notFound("Attachment group file not found");
        }
        return group.files().getFirst();
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            deleteDocumentQuietly(fileId, currentUserId);
        }
    }

    private void deleteDocumentQuietly(UUID fileId, UUID currentUserId) {
        try {
            fileService.delete(fileId, currentUserId);
        } catch (RuntimeException e) {
            // Cleanup must not hide the original attach failure.
        }
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }

    private String normalizeDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return null;
        }
        String trimmed = documentType.trim();
        if (trimmed.length() > 64) {
            throw RestException.badRequest("documentType must be 64 characters or fewer");
        }
        return trimmed;
    }

    private String normalizeDocumentNumber(String documentNumber, int index) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return null;
        }
        String trimmed = documentNumber.trim();
        if (trimmed.length() > 128) {
            throw RestException.badRequest("documentNumbers[" + index + "] must be 128 characters or fewer");
        }
        return trimmed;
    }

    private List<String> normalizeDocumentTypes(List<MultipartFile> files, List<String> documentTypes) {
        if (documentTypes == null || documentTypes.isEmpty()) {
            return Collections.nCopies(files.size(), null);
        }
        if (documentTypes.size() != files.size()) {
            throw RestException.badRequest("files and documentTypes must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentTypes.size());
        for (int i = 0; i < documentTypes.size(); i++) {
            String normalizedType = normalizeDocumentType(documentTypes.get(i));
            if (normalizedType == null) {
                throw RestException.badRequest("documentTypes[" + i + "] must not be blank");
            }
            normalized.add(normalizedType);
        }
        return normalized;
    }

    private List<String> normalizeDocumentNumbers(List<MultipartFile> files, List<String> documentNumbers) {
        if (documentNumbers == null || documentNumbers.isEmpty()) {
            return Collections.nCopies(files.size(), null);
        }
        if (documentNumbers.size() != files.size()) {
            throw RestException.badRequest("files and documentNumbers must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentNumbers.size());
        for (int i = 0; i < documentNumbers.size(); i++) {
            normalized.add(normalizeDocumentNumber(documentNumbers.get(i), i));
        }
        return normalized;
    }

    private List<String> normalizeDocumentNames(List<MultipartFile> files, List<String> documentNames) {
        if (documentNames == null || documentNames.isEmpty()) {
            throw RestException.badRequest("documentNames are required for work order document uploads");
        }
        if (documentNames.size() != files.size()) {
            throw RestException.badRequest("files and documentNames must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentNames.size());
        for (int i = 0; i < documentNames.size(); i++) {
            String documentName = documentNames.get(i);
            if (documentName == null || documentName.isBlank()) {
                throw RestException.badRequest("documentNames[" + i + "] must not be blank");
            }
            String trimmed = documentName.trim();
            if (trimmed.length() > 255) {
                throw RestException.badRequest("documentNames[" + i + "] must be 255 characters or fewer");
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private void assertCanAccessWorkOrder(WorkOrder workOrder) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        UUID departmentId = workOrder.getDepartmentId();
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied by work order department scope");
        }
    }

    private Defect validateCreateRelations(WorkOrderRequest request) {
        if (request.repairRequestId() != null) {
            RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(request.repairRequestId())
                    .orElseThrow(
                            () -> RestException.notFound("Repair request not found: " + request.repairRequestId()));
            if (DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE.contains(repairRequest.getStatus())) {
                throw RestException.badRequest(
                        "Cannot create work order for repair request in status " + repairRequest.getStatus());
            }
            assertWarrantyAllowsWorkOrder(repairRequest);
            if (request.equipmentId() != null
                    && repairRequest.getEquipmentId() != null
                    && !request.equipmentId().equals(repairRequest.getEquipmentId())) {
                throw RestException.badRequest("Repair request belongs to a different equipment");
            }
        }

        validatePprTaskRelationForCreate(request.pprTaskId(), request.equipmentId());

        if (request.defectId() == null) {
            return null;
        }
        Defect defect = defectRepository.findByIdAndIsDeletedFalse(request.defectId())
                .orElseThrow(() -> RestException.notFound("Defect not found: " + request.defectId()));

        if (request.equipmentId() != null
                && defect.getEquipmentId() != null
                && !request.equipmentId().equals(defect.getEquipmentId())) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a different equipment");
        }

        UUID defectRepairRequestId = defect.getRepairRequestId();
        if (defectRepairRequestId != null && request.repairRequestId() == null) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a repair request; repairRequestId is required");
        }
        if (defectRepairRequestId != null && !defectRepairRequestId.equals(request.repairRequestId())) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a different repair request");
        }
        return defect;
    }

    private RepairCampaignStage validateCampaignLink(WorkOrderRequest request, UUID effectiveDepartmentId) {
        UUID campaignId = request.repairCampaignId();
        UUID stageId = request.repairCampaignStageId();
        if (campaignId == null && stageId == null) {
            return null;
        }
        if (campaignId == null || stageId == null) {
            throw RestException.badRequest("repairCampaignId and repairCampaignStageId are required together");
        }
        RepairCampaign campaign = repairCampaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + campaignId));
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED
                || campaign.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Cannot link work order to closed/cancelled campaign");
        }
        RepairCampaignStage stage = repairCampaignStageRepository.findByIdAndIsDeletedFalse(stageId)
                .orElseThrow(() -> RestException.notFound("Repair campaign stage not found: " + stageId));
        if (stage.getCampaign() == null || !campaignId.equals(stage.getCampaign().getId())) {
            throw RestException.badRequest("Stage does not belong to repair campaign");
        }
        if (!CAMPAIGN_WORK_ORDER_TYPES.contains(request.type())) {
            throw RestException.badRequest("Campaign work order type must be OVERHAUL, MEDIUM_REPAIR, or CAPITAL_REPAIR");
        }
        validateCampaignDepartment(campaign, effectiveDepartmentId);
        validateCampaignPlannedDates(stage, request.startPlannedAt(), request.endPlannedAt());
        return stage;
    }

    private UUID resolveWorkOrderBudgetLineId(UUID requestedBudgetLineId, RepairCampaignStage linkedCampaignStage) {
        if (requestedBudgetLineId != null) {
            BudgetLine line = budgetLineRepository.findByIdAndIsDeletedFalse(requestedBudgetLineId)
                    .orElseThrow(() -> RestException.notFound("Budget line not found: " + requestedBudgetLineId));
            validateCampaignBudgetLine(line, linkedCampaignStage);
            return requestedBudgetLineId;
        }
        return linkedCampaignStage == null ? null : linkedCampaignStage.getBudgetLineId();
    }

    private void validateCampaignBudgetLine(BudgetLine line, RepairCampaignStage linkedCampaignStage) {
        if (linkedCampaignStage == null || linkedCampaignStage.getCampaign() == null) {
            return;
        }
        RepairCampaign campaign = linkedCampaignStage.getCampaign();
        UUID maintenanceBudgetId = campaign.getMaintenanceBudgetId();
        if (maintenanceBudgetId == null) {
            throw RestException.badRequest("Campaign must be linked to a maintenance budget before assigning a budget line");
        }
        if (line.getBudget() == null || !maintenanceBudgetId.equals(line.getBudget().getId())) {
            throw RestException.badRequest("Budget line must belong to the repair campaign maintenance budget");
        }
    }

    private void validateCampaignDepartment(RepairCampaign campaign, UUID effectiveDepartmentId) {
        if (effectiveDepartmentId == null) {
            return;
        }
        if (campaign.getScopeType() == RepairCampaignScopeType.CROSS_DEPARTMENT) {
            if (effectiveDepartmentId.equals(campaign.getDepartmentId())
                    || repairCampaignDepartmentRepository.existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
                    campaign.getId(), effectiveDepartmentId)) {
                return;
            }
            throw RestException.badRequest("Work order department is not a campaign participant");
        }
        if (campaign.getDepartmentId() != null && !campaign.getDepartmentId().equals(effectiveDepartmentId)) {
            throw RestException.badRequest("Work order department must match repair campaign department");
        }
    }

    private void validateCampaignPlannedDates(RepairCampaignStage stage, Instant startPlannedAt, Instant endPlannedAt) {
        if (stage.getStartDate() == null || stage.getEndDate() == null) {
            return;
        }
        validateCampaignPlannedDate(stage, startPlannedAt, "startPlannedAt");
        validateCampaignPlannedDate(stage, endPlannedAt, "endPlannedAt");
    }

    private void validateCampaignPlannedDate(RepairCampaignStage stage, Instant plannedAt, String fieldName) {
        if (plannedAt == null) {
            return;
        }
        LocalDate plannedDate = plannedAt.atZone(CALENDAR_ZONE).toLocalDate();
        if (plannedDate.isBefore(stage.getStartDate()) || plannedDate.isAfter(stage.getEndDate())) {
            throw RestException.badRequest(fieldName + " must fit repair campaign stage dates");
        }
    }

    private static final Set<WarrantyHandling> WARRANTY_HANDLING_BLOCKS_WORK_ORDER =
            EnumSet.of(WarrantyHandling.CONTACT_SUPPLIER, WarrantyHandling.WAITING_FOR_SUPPLIER);

    private void assertWarrantyAllowsWorkOrder(RepairRequest repairRequest) {
        if (!Boolean.TRUE.equals(repairRequest.getWarrantyActiveAtCreation())) {
            return;
        }
        WarrantyHandling handling = repairRequest.getWarrantyHandling();
        if (handling == null || WARRANTY_HANDLING_BLOCKS_WORK_ORDER.contains(handling)) {
            throw RestException.badRequest(
                    "Work order cannot be created: warranty decision required or pending supplier response for repair request "
                            + repairRequest.getId());
        }
    }

    private void assertDefectListGate(WorkOrder workOrder) {
        validateDefectListForWorkOrder(
                workOrder.getType(),
                workOrder.getEquipmentId(),
                workOrder.getDefectListId());
    }

    private DefectList validateDefectListForWorkOrder(WorkOrderType type, UUID equipmentId, UUID defectListId) {
        return validateDefectListForWorkOrder(type, equipmentId, defectListId, true);
    }

    private DefectList validateDefectListForWorkOrder(
            WorkOrderType type, UUID equipmentId, UUID defectListId, boolean enforceTypeRequirement) {
        boolean required = enforceTypeRequirement && requiresApprovedDefectList(type);
        if (defectListId == null) {
            if (required) {
                throw RestException.badRequest(approvedDefectListRequiredMessage(type));
            }
            return null;
        }

        DefectList defectList = defectListRepository.findByIdAndIsDeletedFalse(defectListId)
                .orElseThrow(() -> required
                        ? RestException.badRequest(approvedDefectListRequiredMessage(type))
                        : RestException.notFound("DefectList not found: " + defectListId));
        if (equipmentId != null && defectList.getEquipmentId() != null && !equipmentId.equals(defectList.getEquipmentId())) {
            throw RestException.badRequest("DefectList belongs to a different equipment");
        }
        if (defectList.getStatus() != DefectListStatus.APPROVED) {
            if (required) {
                throw RestException.badRequest(approvedDefectListRequiredMessage(type));
            }
            throw RestException.badRequest("DefectList must be APPROVED");
        }
        return defectList;
    }

    private boolean requiresApprovedDefectList(WorkOrderType type) {
        return type != null && DEFECT_LIST_REQUIRED_WORK_ORDER_TYPES.contains(type);
    }

    private String approvedDefectListRequiredMessage(WorkOrderType type) {
        return "Approved DefectList is required for " + type;
    }

    private void validatePerformerSkillsForWorkOrder(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getPerformer() == null) {
            return;
        }
        Set<String> requiredSkills = requiredSkillsForWorkOrder(workOrder);
        if (requiredSkills.isEmpty()) {
            return;
        }
        BrigadeMember performer = workOrder.getPerformer();
        for (String requiredSkill : requiredSkills) {
            SkillMatchResult result = performerHasRequiredSkill(performer, requiredSkill);
            if (result == SkillMatchResult.EXPIRED) {
                throw RestException.badRequest("Assigned performer certification is expired: " + requiredSkill);
            }
            if (result == SkillMatchResult.MISSING) {
                throw RestException.badRequest("Assigned performer does not have required skill: " + requiredSkill);
            }
        }
    }

    /** Canonical reusable skill eligibility used by readiness policies without duplicating certification rules. */
    @Transactional(readOnly = true)
    public boolean hasEligiblePerformerSkills(WorkOrder workOrder) {
        try {
            validatePerformerSkillsForWorkOrder(workOrder);
            return true;
        } catch (RestException ex) {
            return false;
        }
    }

    private Set<String> requiredSkillsForWorkOrder(WorkOrder workOrder) {
        if (workOrder.getTasks() == null || workOrder.getTasks().isEmpty()) {
            return Set.of();
        }
        return workOrder.getTasks().stream()
                .map(this::requiredSkillForTask)
                .filter(skill -> skill != null && !skill.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String requiredSkillForTask(WorkOrderTask task) {
        if (task == null) {
            return null;
        }
        if (task.getSourceOperationId() != null) {
            Optional<String> fromOperation = maintenanceOperationRepository.findByIdAndIsDeletedFalse(task.getSourceOperationId())
                    .map(MaintenanceOperation::getRequiredSkill)
                    .filter(skill -> skill != null && !skill.isBlank());
            if (fromOperation.isPresent()) {
                return fromOperation.get();
            }
        }
        return requiredSkillFromTaskDescription(task.getDescription());
    }

    private String requiredSkillFromTaskDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String prefix = "Required skill:";
        return description.lines()
                .map(String::trim)
                .filter(line -> line.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(line -> line.substring(prefix.length()).trim())
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private SkillMatchResult performerHasRequiredSkill(BrigadeMember performer, String requiredSkill) {
        String normalizedRequiredSkill = normalizeSkill(requiredSkill);
        if (normalizedRequiredSkill == null) {
            return SkillMatchResult.OK;
        }
        if (performer.getQualifications() != null && performer.getQualifications().stream()
                .map(this::normalizeSkill)
                .anyMatch(normalizedRequiredSkill::equals)) {
            return SkillMatchResult.OK;
        }
        if (performer.getUserId() == null) {
            return SkillMatchResult.MISSING;
        }
        boolean expiredMatch = false;
        for (UserCertification certification : userCertificationRepository.findAllByUserIdAndIsDeletedFalse(performer.getUserId())) {
            if (!certificationMatchesRequiredSkill(certification, normalizedRequiredSkill)) {
                continue;
            }
            if (certificationExpired(certification)) {
                expiredMatch = true;
                continue;
            }
            if ("ACTIVE".equalsIgnoreCase(certification.getStatus())) {
                return SkillMatchResult.OK;
            }
        }
        return expiredMatch ? SkillMatchResult.EXPIRED : SkillMatchResult.MISSING;
    }

    private boolean certificationMatchesRequiredSkill(UserCertification certification, String normalizedRequiredSkill) {
        if (certification == null || normalizedRequiredSkill == null) {
            return false;
        }
        String typeCode = certification.getTypeCode();
        if (normalizedRequiredSkill.equals(normalizeSkill(typeCode))) {
            return true;
        }
        CertificationType type = typeCode == null ? null : certificationTypeRepository.findByCodeAndIsDeletedFalse(typeCode);
        return type != null && (
                normalizedRequiredSkill.equals(normalizeSkill(type.getCode()))
                        || normalizedRequiredSkill.equals(normalizeSkill(type.getName()))
                        || normalizedRequiredSkill.equals(normalizeSkill(type.getNameEn()))
                        || normalizedRequiredSkill.equals(normalizeSkill(type.getNameUz())));
    }

    private boolean certificationExpired(UserCertification certification) {
        return "EXPIRED".equalsIgnoreCase(certification.getStatus())
                || (certification.getExpiresAt() != null && certification.getExpiresAt().isBefore(LocalDate.now()));
    }

    private String normalizeSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            return null;
        }
        return skill.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }

    private enum SkillMatchResult {
        OK,
        MISSING,
        EXPIRED
    }

    private UUID resolveEquipmentDepartmentId(UUID equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(this::resolveEquipmentOwnerDepartmentId)
                .orElse(null);
    }

    private UUID resolveEffectiveDepartmentId(WorkOrderRequest request, Equipment equipment) {
        UUID equipmentDepartmentId = resolveEquipmentOwnerDepartmentId(equipment);
        UUID effectiveDepartmentId = request.departmentId() == null ? equipmentDepartmentId : request.departmentId();
        if (effectiveDepartmentId == null) {
            throw RestException.badRequest(
                    "departmentId is required because selected equipment has no responsible or physical department"
            );
        }
        if (equipmentDepartmentId != null && request.departmentId() != null
                && !equipmentDepartmentId.equals(request.departmentId())) {
            throw RestException.badRequest("departmentId must match selected equipment responsible department");
        }
        departmentRepository.findById(effectiveDepartmentId)
                .orElseThrow(() -> RestException.notFound("Department not found: " + effectiveDepartmentId));
        if (!scopeAccessService.isScopeAdmin() && !scopeAccessService.canAccessDepartment(effectiveDepartmentId)) {
            throw RestException.forbidden("Access denied by work order department scope");
        }
        return effectiveDepartmentId;
    }

    private UUID resolveEquipmentOwnerDepartmentId(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private UUID resolveEffectiveLocationId(WorkOrderRequest request, Equipment equipment, UUID effectiveDepartmentId) {
        UUID equipmentLocationId = equipment.getLocationId();
        UUID effectiveLocationId = request.locationId() == null ? equipmentLocationId : request.locationId();
        if (equipmentLocationId != null && request.locationId() != null
                && !equipmentLocationId.equals(request.locationId())) {
            throw RestException.badRequest("locationId must match selected equipment location");
        }
        if (effectiveLocationId == null) {
            return null;
        }
        Location location = locationRepository.findByIdAndIsDeletedFalse(effectiveLocationId)
                .orElseThrow(() -> RestException.notFound("Location not found: " + effectiveLocationId));
        if (location.getDepartmentId() != null && effectiveDepartmentId != null
                && !location.getDepartmentId().equals(effectiveDepartmentId)) {
            throw RestException.badRequest("locationId belongs to a different department");
        }
        return effectiveLocationId;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID resolveEffectiveEquipmentNodeId(WorkOrderRequest request, Defect linkedDefect) {
        if (request.equipmentNodeId() != null) {
            return request.equipmentNodeId();
        }
        return linkedDefect == null ? null : linkedDefect.getEquipmentNodeId();
    }

    private EquipmentNode validateEquipmentNodeLink(UUID equipmentNodeId, UUID equipmentId) {
        if (equipmentNodeId == null) {
            return null;
        }
        EquipmentNode node = equipmentNodeRepository.findByIdAndIsDeletedFalse(equipmentNodeId)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + equipmentNodeId));
        if (!node.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("Equipment node belongs to a different equipment");
        }
        return node;
    }

    private void validatePprTaskRelationForCreate(UUID pprTaskId, UUID equipmentId) {
        if (pprTaskId == null) {
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(pprTaskId)
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + pprTaskId));
        if (task.getEquipmentId() != null && equipmentId != null && !task.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("PPR task belongs to a different equipment");
        }
        if (task.getStatus() != PprTaskStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED PPR tasks can generate work orders");
        }
        PprWorkOrderEarlyCreationPolicy.requireDue(
                task, java.time.LocalDateTime.now(CALENDAR_ZONE));
        PprPlan plan = task.getPlan();
        if (plan == null || plan.getId() == null) {
            throw RestException.notFound("Parent PPR plan not found for task: " + pprTaskId);
        }
        if (!ALLOWED_PARENT_PLAN_STATUSES_FOR_WORK_ORDER_CREATE.contains(plan.getStatus())) {
            throw RestException.badRequest("Work order can be created only after the parent PPR plan is approved");
        }
    }

    void ensureReplacementEquipmentReservedOnStart(WorkOrder workOrder) {
        if (!isReplacementWorkOrder(workOrder)) {
            return;
        }
        WarehouseEquipmentItem item = getReplacementWarehouseEquipmentItemOrThrow(workOrder);
        WarehouseEquipmentStatus currentStatus = item.getStatus();
        if (currentStatus == WarehouseEquipmentStatus.AVAILABLE) {
            item.setStatus(WarehouseEquipmentStatus.RESERVED);
            warehouseEquipmentItemRepository.save(item);
            return;
        }
        if (currentStatus == WarehouseEquipmentStatus.RESERVED) {
            return;
        }
        throw RestException.badRequest("Replacement equipment must be AVAILABLE or RESERVED to start work order");
    }

    private boolean isReplacementWorkOrder(WorkOrder workOrder) {
        return workOrder.getWorkType() == WorkType.REPLACEMENT;
    }

    private void updateReplacementEquipmentStatus(WorkOrder workOrder, WarehouseEquipmentStatus status) {
        if (!isReplacementWorkOrder(workOrder)) {
            return;
        }
        WarehouseEquipmentItem item = getReplacementWarehouseEquipmentItemOrThrow(workOrder);
        if (item.getStatus() == status) {
            return;
        }
        item.setStatus(status);
        warehouseEquipmentItemRepository.save(item);
    }

    private WarehouseEquipmentItem getReplacementWarehouseEquipmentItemOrThrow(WorkOrder workOrder) {
        if (workOrder.getWarehouseId() == null || workOrder.getReplacementEquipmentId() == null) {
            throw RestException
                    .badRequest("warehouseId and replacementEquipmentId are required for replacement work orders");
        }
        return warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        workOrder.getWarehouseId(),
                        workOrder.getReplacementEquipmentId())
                .orElseThrow(
                        () -> RestException.badRequest("Replacement equipment item not found in selected warehouse"));
    }

    private void completeReplacementPlacement(WorkOrder workOrder, CompleteWorkOrderRequest request) {
        equipmentService.updatePlacement(
                workOrder.getReplacementEquipmentId(),
                new EquipmentPlacementRequest(
                        PlacementTargetType.DEPARTMENT,
                        null,
                        workOrder.getDepartmentId(),
                        null,
                        null,
                        "Replacement work order completed: " + workOrder.getNumber()
                )
        );
        equipmentService.updatePlacement(
                workOrder.getEquipmentId(),
                new EquipmentPlacementRequest(
                        PlacementTargetType.WAREHOUSE,
                        request.oldEquipmentReturnWarehouseId(),
                        null,
                        WarehouseEquipmentStatus.OUT_OF_SERVICE,
                        null,
                        "Replaced by work order: " + workOrder.getNumber()
                )
        );
    }

    private void validateTypeRequiredRelations(WorkOrderRequest request) {
        if (request.type() == WorkOrderType.EMERGENCY && request.repairRequestId() == null) {
            throw RestException.badRequest("repairRequestId is required when work order type is EMERGENCY");
        }
        if (request.type() == WorkOrderType.DEFECT && request.defectId() == null) {
            throw RestException.badRequest("defectId is required when work order type is DEFECT");
        }
    }

    private TemplateTaskSyncResult generateTemplateTasksFromWorkOrderContext(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getId() == null) {
            return new TemplateTaskSyncResult(0, 0);
        }
        if (workOrder.getRepairRequestId() != null) {
            TemplateTaskSyncResult selectedActions = syncRepairRequestTemplateActions(workOrder, workOrder.getRepairRequestId());
            if (selectedActions.operationsCount() > 0) {
                return selectedActions;
            }
        }
        return resolveTemplateId(workOrder)
                .map(templateId -> syncTemplateTasks(workOrder, templateId))
                .orElseGet(() -> new TemplateTaskSyncResult(0, 0));
    }

    private TemplateTaskSyncResult syncRepairRequestTemplateActions(WorkOrder workOrder, UUID repairRequestId) {
        List<RepairRequestTemplateAction> actions = repairRequestTemplateActionRepository
                .findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(repairRequestId);
        if (actions.isEmpty()) {
            return new TemplateTaskSyncResult(0, 0);
        }
        if (workOrder.getTasks() == null) {
            workOrder.setTasks(new ArrayList<>());
        }
        int created = 0;
        for (RepairRequestTemplateAction action : actions) {
            if (hasTaskForRepairRequestAction(workOrder, action)) {
                continue;
            }
            WorkOrderTask task = new WorkOrderTask();
            task.setWorkOrder(workOrder);
            task.setTitle(action.getNameSnapshot());
            task.setDescription(prefixed("Required skill", action.getRequiredSkill()));
            task.setStatus(TaskExecutionStatus.TODO);
            task.setAssignedToId(action.getSpecialistId());
            if (action.getDurationHours() != null && action.getDurationHours() > 0) {
                task.setPlannedHours(action.getDurationHours());
            }
            task.setSourceTemplateId(action.getTemplateId());
            task.setSourceOperationId(action.getOperationId());
            workOrder.getTasks().add(task);
            created++;
        }
        if (created > 0) {
            repository.save(workOrder);
        }
        return new TemplateTaskSyncResult(actions.size(), created);
    }

    private TemplateTaskSyncResult syncTemplateTasks(WorkOrder workOrder, UUID templateId) {
        if (workOrder == null || workOrder.getId() == null || templateId == null) {
            return new TemplateTaskSyncResult(0, 0);
        }
        if (workOrder.getTasks() == null) {
            workOrder.setTasks(new ArrayList<>());
        }
        List<MaintenanceOperation> operations = maintenanceOperationRepository
                .findAllByTemplateIdInAndIsDeletedFalse(List.of(templateId))
                .stream()
                .filter(operation -> !operation.isDeleted())
                .sorted(java.util.Comparator.comparingInt(MaintenanceOperation::getSequence))
                .toList();
        int created = 0;
        for (MaintenanceOperation operation : operations) {
            if (hasTaskForOperation(workOrder, operation)) {
                continue;
            }
            WorkOrderTask task = new WorkOrderTask();
            task.setWorkOrder(workOrder);
            task.setTitle(operation.getName());
            task.setDescription(operationDescription(operation));
            task.setStatus(TaskExecutionStatus.TODO);
            if (operation.getDurationHours() != null && operation.getDurationHours() > 0) {
                task.setPlannedHours(operation.getDurationHours());
            }
            task.setAssignedToId(operation.getSpecialistId());
            task.setSourceTemplateId(templateId);
            task.setSourceOperationId(operation.getId());
            workOrder.getTasks().add(task);
            created++;
        }
        if (created > 0) {
            repository.save(workOrder);
        }
        return new TemplateTaskSyncResult(operations.size(), created);
    }

    private boolean hasTaskForOperation(WorkOrder workOrder, MaintenanceOperation operation) {
        if (workOrder.getTasks() == null || operation == null) {
            return false;
        }
        return workOrder.getTasks().stream().anyMatch(task ->
                (operation.getId() != null && operation.getId().equals(task.getSourceOperationId()))
                        || (task.getSourceOperationId() == null
                        && task.getTitle() != null
                        && task.getTitle().equals(operation.getName())));
    }

    private boolean hasTaskForRepairRequestAction(WorkOrder workOrder, RepairRequestTemplateAction action) {
        if (workOrder.getTasks() == null || action == null) {
            return false;
        }
        return workOrder.getTasks().stream().anyMatch(task ->
                (action.getOperationId() != null && action.getOperationId().equals(task.getSourceOperationId()))
                        || (action.getOperationId() == null
                        && Objects.equals(task.getSourceTemplateId(), action.getTemplateId())
                        && Objects.equals(task.getTitle(), action.getNameSnapshot())));
    }

    private Optional<UUID> resolveTemplateId(WorkOrder workOrder) {
        if (workOrder.getMaintenanceDueEventId() != null) {
            MaintenanceDueEvent dueEvent = loadMaintenanceDueEvent(workOrder);
            Optional<UUID> fromDueEvent = Optional.ofNullable(dueEvent == null ? null : dueEvent.getTemplateId());
            if (fromDueEvent.isPresent()) {
                return fromDueEvent;
            }
            Optional<UUID> fromRegulation = resolveTemplateIdFromRegulation(dueEvent == null ? null : dueEvent.getRegulationId());
            if (fromRegulation.isPresent()) {
                return fromRegulation;
            }
        }
        if (workOrder.getPprTaskId() != null) {
            Optional<PprTask> task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId());
            Optional<UUID> fromDueEvent = task
                    .map(PprTask::getMaintenanceDueEventId)
                    .flatMap(dueEventId -> {
                        WorkOrder probe = new WorkOrder();
                        probe.setMaintenanceDueEventId(dueEventId);
                        MaintenanceDueEvent dueEvent = loadMaintenanceDueEvent(probe);
                        return dueEvent == null
                                ? Optional.<UUID>empty()
                                : Optional.ofNullable(dueEvent.getTemplateId())
                                .or(() -> resolveTemplateIdFromRegulation(dueEvent.getRegulationId()));
                    });
            if (fromDueEvent.isPresent()) {
                return fromDueEvent;
            }
            return task.map(PprTask::getRegulationId).flatMap(this::resolveTemplateIdFromRegulation);
        }
        return Optional.empty();
    }

    private Optional<UUID> resolveTemplateIdFromRegulation(UUID regulationId) {
        if (regulationId == null) {
            return Optional.empty();
        }
        return maintenanceRegulationRepository.findByIdAndIsDeletedFalse(regulationId)
                .map(MaintenanceRegulation::getTemplateId)
                .filter(id -> id != null);
    }

    private String operationDescription(MaintenanceOperation operation) {
        StringBuilder description = new StringBuilder();
        appendLine(description, operation.getDescription());
        appendLine(description, prefixed("Required skill", operation.getRequiredSkill()));
        appendLine(description, prefixed("Safety", operation.getSafetyNotes()));
        appendLine(description, prefixed("Tools", operation.getToolsRequired()));
        appendLine(description, prefixed("Spare parts", operation.getSparePartsRequired()));
        appendLine(description, prefixed("Consumables", operation.getConsumablesRequired()));
        appendLine(description, prefixed("Control parameter", operation.getControlParameter()));
        appendLine(description, prefixed("Instruction", operation.getInstructionUrl()));
        return description.isEmpty() ? null : description.toString();
    }

    private String prefixed(String label, String value) {
        return value == null || value.isBlank() ? null : label + ": " + value;
    }

    private void appendLine(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private void validateReplacementFields(WorkOrderRequest request, WorkType effectiveWorkType) {
        if (effectiveWorkType == WorkType.REPLACEMENT) {
            if (request.warehouseId() == null) {
                throw RestException.badRequest("warehouseId is required when workType is REPLACEMENT");
            }
            if (request.replacementEquipmentId() == null) {
                throw RestException.badRequest("replacementEquipmentId is required when workType is REPLACEMENT");
            }
            if (request.replacementEquipmentId().equals(request.equipmentId())) {
                throw RestException.badRequest("replacementEquipmentId must be different from equipmentId");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(request.warehouseId())
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + request.warehouseId()));
            equipmentRepository.findByIdAndIsDeletedFalse(request.replacementEquipmentId())
                    .orElseThrow(() -> RestException
                            .notFound("Replacement equipment not found: " + request.replacementEquipmentId()));
            WarehouseEquipmentItem warehouseEquipmentItem = warehouseEquipmentItemRepository
                    .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                            request.warehouseId(),
                            request.replacementEquipmentId())
                    .orElseThrow(() -> RestException
                            .badRequest("Replacement equipment does not belong to selected warehouse"));
            if (warehouseEquipmentItem.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
                throw RestException.badRequest("Replacement equipment must be AVAILABLE");
            }
            if (repository.existsActiveReplacementAssignment(
                    request.replacementEquipmentId(),
                    WorkType.REPLACEMENT,
                    TERMINAL_WORK_ORDER_STATUSES)) {
                throw RestException.conflict("Replacement equipment is already assigned to another active work order");
            }
            return;
        }
        if (request.warehouseId() != null || request.replacementEquipmentId() != null) {
            throw RestException
                    .badRequest("warehouseId and replacementEquipmentId must be null when workType is not REPLACEMENT");
        }
    }

    private void reserveReplacementEquipmentOnCreate(WorkOrderRequest request, WorkType effectiveWorkType) {
        if (effectiveWorkType != WorkType.REPLACEMENT) {
            return;
        }
        WarehouseEquipmentItem item = warehouseEquipmentItemRepository
                .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        request.warehouseId(),
                        request.replacementEquipmentId())
                .orElseThrow(
                        () -> RestException.badRequest("Replacement equipment does not belong to selected warehouse"));
        if (item.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
            throw RestException.badRequest("Replacement equipment must be AVAILABLE");
        }
        item.setStatus(WarehouseEquipmentStatus.RESERVED);
        warehouseEquipmentItemRepository.save(item);
    }

    private void validateCompleteRequestForReplacement(WorkOrder entity, CompleteWorkOrderRequest request) {
        if (isReplacementWorkOrder(entity)) {
            if (request.oldEquipmentReturnWarehouseId() == null) {
                throw RestException
                        .badRequest("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(request.oldEquipmentReturnWarehouseId())
                    .orElseThrow(() -> RestException
                            .notFound("Warehouse not found: " + request.oldEquipmentReturnWarehouseId()));
            return;
        }
        if (request.oldEquipmentReturnWarehouseId() != null) {
            throw RestException
                    .badRequest("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT");
        }
    }

    private void assignReplacementEquipmentToWorkOrderDepartment(WorkOrder workOrder) {
        Equipment replacementEquipment = equipmentRepository
                .findByIdAndIsDeletedFalse(workOrder.getReplacementEquipmentId())
                .orElseThrow(() -> RestException
                        .notFound("Replacement equipment not found: " + workOrder.getReplacementEquipmentId()));
        replacementEquipment.setDepartmentId(workOrder.getDepartmentId());
        equipmentRepository.save(replacementEquipment);
    }

    private void issueCompletionMaterials(WorkOrder workOrder, CompleteWorkOrderRequest request) {
        if (request.materialUsages() == null || request.materialUsages().isEmpty()) {
            return;
        }
        request.materialUsages().forEach(usage -> repairMaterialUsageService.register(workOrder.getId(), usage));
    }

    private WorkOrderDto toDetailDto(WorkOrder entity) {
        EquipmentNode equipmentNode = entity.getEquipmentNodeId() == null
                ? null
                : equipmentNodeRepository.findByIdAndIsDeletedFalse(entity.getEquipmentNodeId()).orElse(null);
        return toDto(entity, equipmentNode, materialUsagesFor(entity));
    }

    private WorkOrderDto toDto(WorkOrder entity) {
        EquipmentNode equipmentNode = entity.getEquipmentNodeId() == null
                ? null
                : equipmentNodeRepository.findByIdAndIsDeletedFalse(entity.getEquipmentNodeId()).orElse(null);
        return toDto(entity, equipmentNode, List.of());
    }

    private WorkOrderDto toDto(WorkOrder entity, EquipmentNode equipmentNode) {
        return toDto(entity, equipmentNode, List.of());
    }

    private WorkOrderDto toDto(WorkOrder entity, EquipmentNode equipmentNode, List<com.toir.dto.materialusage.RepairMaterialUsageDto> materialUsages) {
        RepairRequest linkedRepairRequest = entity.getRepairRequestId() == null
                ? null
                : repairRequestRepository.findByIdAndIsDeletedFalse(entity.getRepairRequestId()).orElse(null);
        Defect linkedDefect = entity.getDefectId() == null
                ? null
                : defectRepository.findByIdAndIsDeletedFalse(entity.getDefectId()).orElse(null);
        Map<UUID, Integer> operationsCountByWorkOrderId = loadOperationsCountMap(entity.getId() == null
                ? List.of()
                : List.of(entity.getId()));
        Map<UUID, Integer> materialsCountByWorkOrderId = loadMaterialsCountMap(entity.getId() == null
                ? List.of()
                : List.of(entity.getId()));
        return toDto(
                entity,
                equipmentNode,
                linkedRepairRequest,
                linkedDefect,
                resolveCount(entity.getId(), operationsCountByWorkOrderId),
                resolveCount(entity.getId(), materialsCountByWorkOrderId),
                materialUsages);
    }

    private WorkOrderDto toDto(WorkOrder entity,
                               EquipmentNode equipmentNode,
                               RepairRequest linkedRepairRequest,
                               Defect linkedDefect,
                               int operationsCount,
                               int materialsCount) {
        return toDto(entity, equipmentNode, linkedRepairRequest, linkedDefect, operationsCount, materialsCount, List.of());
    }

    private WorkOrderDto toDto(WorkOrder entity,
                               EquipmentNode equipmentNode,
                               RepairRequest linkedRepairRequest,
                               Defect linkedDefect,
                               int operationsCount,
                               int materialsCount,
                               List<com.toir.dto.materialusage.RepairMaterialUsageDto> materialUsages) {
        String equipmentName = equipmentRepository.findById(entity.getEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        String departmentName = departmentRepository.findById(entity.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        String locationName = entity.getLocationId() == null
                ? null
                : locationRepository.findByIdAndIsDeletedFalse(entity.getLocationId())
                .map(Location::getName)
                .orElse(null);
        String replacementEquipmentName = entity.getReplacementEquipmentId() == null
                ? null
                : equipmentRepository.findById(entity.getReplacementEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        DefectList linkedDefectList = entity.getDefectListId() == null
                ? null
                : defectListRepository.findByIdAndIsDeletedFalse(entity.getDefectListId()).orElse(null);
        return new WorkOrderDto(
                entity.getId(), entity.getNumber(), entity.getTitle(), entity.getEquipmentId(),
                entity.getEquipmentNodeId(),
                equipmentNode == null ? null : equipmentNode.getCode(),
                equipmentNode == null ? null : equipmentNode.getName(),
                equipmentNode == null ? null : equipmentNode.getNodeType(),
                entity.getDepartmentId(),
                equipmentName, departmentName,
                entity.getLocationId(), locationName, entity.getWorkLocationNote(),
                entity.getRepairRequestId(), entity.getDefectId(),
                entity.getDefectListId(),
                linkedDefectList == null ? null : linkedDefectList.getCode(),
                linkedDefectList == null ? null : linkedDefectList.getStatus(),
                entity.getPprTaskId(), entity.getCounteragentId(),
                counteragentRef(entity.getCounteragentId()),
                performerId(entity), performerName(entity),
                entity.getStatus(), entity.getType(), entity.getWorkType(), entity.getPriority(),
                entity.getStartPlannedAt(), entity.getEndPlannedAt(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getSummary(), entity.getResult(), entity.getClosureNotes(),
                entity.getCreatedById(), entity.getApprovedById(),
                entity.getWarehouseId(), entity.getReplacementEquipmentId(), replacementEquipmentName,
                taskDtos(entity.getTasks()),
                repairRequestBrief(linkedRepairRequest),
                defectBrief(linkedDefect),
                operationsCount,
                materialsCount,
                entity.getRepairActRequired(),
                entity.getStoppageActRequired(),
                entity.getRepairActFileAssetId(),
                entity.getStoppageActFileAssetId(),
                materialUsages,
                entity.getUpdatedAt(),
                entity.getRepairCampaignId(),
                entity.getRepairCampaignStageId(),
                null,
                null,
                entity.getBudgetLineId(),
                entity.isRequiresShutdown(),
                entity.isRequiresIsolation(),
                entity.getPlannedShutdownId(),
                entity.getShutdownWorkItemId(),
                entity.getPerformerEmployee() == null ? null : entity.getPerformerEmployee().getId(),
                performerName(entity),
                performerUserId(entity),
                performerId(entity),
                entity.getPerformer() == null || entity.getPerformer().getBrigade() == null ? null : entity.getPerformer().getBrigade().getId(),
                entity.getPerformer() == null || entity.getPerformer().getBrigade() == null ? null : entity.getPerformer().getBrigade().getName());
    }

    private WorkOrderDto.CounteragentRef counteragentRef(UUID counteragentId) {
        if (counteragentId == null) {
            return null;
        }
        return toCounteragentRef(counteragentService.load(counteragentId));
    }

    private WorkOrderDto.CounteragentRef toCounteragentRef(Counteragent counteragent) {
        return new WorkOrderDto.CounteragentRef(
                counteragent.getId(),
                counteragent.getCode(),
                counteragent.getName()
        );
    }

    private List<WorkOrderTaskDto> taskDtos(List<WorkOrderTask> tasks) {
        List<WorkOrderTask> safeTasks = safeList(tasks);
        if (safeTasks.isEmpty()) {
            return List.of();
        }
        Set<UUID> operationIds = safeTasks.stream()
                .map(WorkOrderTask::getSourceOperationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceOperation> operationsById = operationIds.isEmpty()
                ? Map.of()
                : safeList(maintenanceOperationRepository.findAllByIdInAndIsDeletedFalse(operationIds))
                .stream()
                .collect(Collectors.toMap(MaintenanceOperation::getId, Function.identity(), (left, ignored) -> left));
        Set<UUID> templateIds = safeTasks.stream()
                .map(WorkOrderTask::getSourceTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceTemplate> templatesById = templateIds.isEmpty()
                ? Map.of()
                : safeList(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(templateIds))
                .stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, Function.identity(), (left, ignored) -> left));
        return safeTasks.stream()
                .map(task -> WorkOrderTaskDto.from(
                        task,
                        task.getSourceTemplateId() == null ? null : templatesById.get(task.getSourceTemplateId()),
                        task.getSourceOperationId() == null ? null : operationsById.get(task.getSourceOperationId())))
                .toList();
    }

    private List<com.toir.dto.materialusage.RepairMaterialUsageDto> materialUsagesFor(WorkOrder entity) {
        if (entity.getId() == null) {
            return List.of();
        }
        return repairMaterialUsageService.findByWorkOrder(entity.getId());
    }

    private List<WorkOrderDto> toDtos(List<WorkOrder> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> workOrderIds = entities.stream()
                .map(WorkOrder::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, Integer> operationsCountByWorkOrderId = loadOperationsCountMap(workOrderIds);
        Map<UUID, Integer> materialsCountByWorkOrderId = loadMaterialsCountMap(workOrderIds);

        List<UUID> repairRequestIds = entities.stream()
                .map(WorkOrder::getRepairRequestId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, RepairRequest> repairRequestById = repairRequestIds.isEmpty()
                ? Map.of()
                : repairRequestRepository.findAllByIdInAndIsDeletedFalse(repairRequestIds)
                .stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));

        List<UUID> defectIds = entities.stream()
                .map(WorkOrder::getDefectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, Defect> defectById = defectIds.isEmpty()
                ? Map.of()
                : defectRepository.findAllByIdInAndIsDeletedFalse(defectIds)
                .stream()
                .collect(Collectors.toMap(Defect::getId, Function.identity()));

        List<UUID> equipmentNodeIds = entities.stream()
                .map(WorkOrder::getEquipmentNodeId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, EquipmentNode> equipmentNodeById = equipmentNodeIds.isEmpty()
                ? Map.of()
                : equipmentNodeRepository.findAllByIdInAndIsDeletedFalse(equipmentNodeIds)
                .stream()
                .collect(Collectors.toMap(EquipmentNode::getId, Function.identity()));

        return entities.stream()
                .map(entity -> toDto(
                        entity,
                        resolveEquipmentNode(entity.getEquipmentNodeId(), equipmentNodeById),
                        resolveRepairRequestBrief(entity.getRepairRequestId(), repairRequestById),
                        resolveDefectBrief(entity.getDefectId(), defectById),
                        resolveCount(entity.getId(), operationsCountByWorkOrderId),
                        resolveCount(entity.getId(), materialsCountByWorkOrderId)))
                .toList();
    }

    private Map<UUID, Integer> loadOperationsCountMap(List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return workExecutionRepository.countByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getWorkOrderId(),
                        projection -> safeCount(projection.getCount())));
    }

    private Map<UUID, Integer> loadMaterialsCountMap(List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return repairMaterialUsageRepository.countByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getWorkOrderId(),
                        projection -> safeCount(projection.getCount())));
    }

    private int resolveCount(UUID workOrderId, Map<UUID, Integer> countByWorkOrderId) {
        if (workOrderId == null) {
            return 0;
        }
        return countByWorkOrderId.getOrDefault(workOrderId, 0);
    }

    private int safeCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private RepairRequest resolveRepairRequestBrief(UUID repairRequestId, Map<UUID, RepairRequest> repairRequestById) {
        if (repairRequestId == null) {
            return null;
        }
        return repairRequestById.get(repairRequestId);
    }

    private Defect resolveDefectBrief(UUID defectId, Map<UUID, Defect> defectById) {
        if (defectId == null) {
            return null;
        }
        return defectById.get(defectId);
    }

    private UUID performerId(WorkOrder entity) {
        return entity.getPerformer() == null ? null : entity.getPerformer().getId();
    }

    private String performerName(WorkOrder entity) {
        if (entity.getPerformerEmployee() != null) {
            return employeeFullName(entity.getPerformerEmployee());
        }
        BrigadeMember performer = entity.getPerformer();
        if (performer == null || performer.getUserId() == null) return null;
        return userRepository.findByIdAndIsDeletedFalse(performer.getUserId()).map(User::getFullName).orElse(null);
    }

    private String employeeFullName(Employee employee) {
        return java.util.stream.Stream.of(employee.getLastName(), employee.getFirstName(), employee.getMiddleName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private UUID performerUserId(WorkOrder entity) {
        return entity.getPerformerEmployee() != null
                ? entity.getPerformerEmployee().getUserId()
                : entity.getPerformer() == null ? null : entity.getPerformer().getUserId();
    }


    private RepairRequestBriefDto repairRequestBrief(RepairRequest repairRequest) {
        if (repairRequest == null) {
            return null;
        }
        String assigneeName = repairRequest.getAssignedToId() == null
                ? null
                : userRepository.findByIdAndIsDeletedFalse(repairRequest.getAssignedToId())
                .map(User::getFullName)
                .orElse(null);
        String departmentName = repairRequest.getDepartmentId() == null
                ? null
                : departmentRepository.findByIdAndIsDeletedFalse(repairRequest.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        String locationName = repairRequest.getLocationId() == null
                ? null
                : locationRepository.findByIdAndIsDeletedFalse(repairRequest.getLocationId())
                .map(Location::getName)
                .orElse(null);
        return TriadLinkMapper.toRepairRequestBrief(repairRequest, assigneeName, departmentName, locationName);
    }

    private DefectBriefDto defectBrief(Defect defect) {
        if (defect == null) {
            return null;
        }
        AttachmentPhotoSummary photoSummary = attachmentGroupService
                .getPhotoSummaries(AttachmentTargetType.DEFECT, List.of(defect.getId()))
                .get(defect.getId());
        return TriadLinkMapper.toDefectBrief(defect, photoSummary);
    }

    private String performerDisplayName(BrigadeMember member, Map<UUID, User> usersById) {
        if (member.getUserId() == null) {
            return null;
        }
        User user = usersById.get(member.getUserId());
        return user == null ? member.getUserId().toString() : user.getFullName();
    }

    private String plannedTimingText(Instant startPlannedAt, String language) {
        if (startPlannedAt == null) {
            return switch (language) {
                case "ru" -> "в запланированное время";
                case "en" -> "at the planned time";
                default -> "rejalashtirilgan vaqtda bajarishingiz kerak";
            };
        }
        LocalDate plannedDate = startPlannedAt.atZone(CALENDAR_ZONE).toLocalDate();
        LocalDate today = LocalDate.now(CALENDAR_ZONE);
        if (plannedDate.isEqual(today)) {
            return switch (language) {
                case "ru" -> "сегодня";
                case "en" -> "today";
                default -> "bugun bajarishingiz kerak";
            };
        }
        if (plannedDate.isEqual(today.plusDays(1))) {
            return switch (language) {
                case "ru" -> "завтра";
                case "en" -> "tomorrow";
                default -> "ertaga bajarishingiz kerak";
            };
        }
        return switch (language) {
            case "ru" -> plannedDate + "";
            case "en" -> "on " + plannedDate;
            default -> plannedDate + " sanasida bajarishingiz kerak";
        };
    }

    private EquipmentNode resolveEquipmentNode(UUID equipmentNodeId, Map<UUID, EquipmentNode> equipmentNodeById) {
        if (equipmentNodeId == null) {
            return null;
        }
        return equipmentNodeById.get(equipmentNodeId);
    }

    private Page<WorkOrderDto> toDtoPage(Page<WorkOrder> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        return new PageImpl<>(toDtos(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private List<WorkOrderCalendarBucketDto> buildMonthBuckets(List<WorkOrderCalendarBucketProjection> rows) {
        List<WorkOrderCalendarBucketDto> buckets = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int currentMonth = month;
            List<WorkOrderCalendarBucketProjection> monthRows = rows.stream()
                    .filter(row -> row.getBucketNumber() != null && row.getBucketNumber() == currentMonth)
                    .toList();
            buckets.add(new WorkOrderCalendarBucketDto(
                    currentMonth,
                    null,
                    totalCount(monthRows),
                    aggregateStatusCounts(monthRows)));
        }
        return buckets;
    }

    private List<WorkOrderCalendarBucketDto> buildDayBuckets(
            YearMonth month,
            List<WorkOrderCalendarBucketProjection> rows) {
        List<WorkOrderCalendarBucketDto> buckets = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            List<WorkOrderCalendarBucketProjection> dayRows = rows.stream()
                    .filter(row -> date.equals(row.getBucketDate()))
                    .toList();
            buckets.add(new WorkOrderCalendarBucketDto(
                    null,
                    date,
                    totalCount(dayRows),
                    aggregateStatusCounts(dayRows)));
        }
        return buckets;
    }

    private long totalOrders(List<WorkOrderCalendarBucketDto> buckets) {
        return buckets.stream().mapToLong(WorkOrderCalendarBucketDto::totalOrders).sum();
    }

    private long totalCount(List<WorkOrderCalendarBucketProjection> rows) {
        return rows.stream().mapToLong(row -> row.getCount() == null ? 0L : row.getCount()).sum();
    }

    private List<WorkOrderStatusCountDto> aggregateStatusCounts(List<WorkOrderCalendarBucketProjection> rows) {
        Map<WorkOrderStatus, Long> counts = new LinkedHashMap<>();
        for (WorkOrderCalendarBucketProjection row : rows) {
            if (row.getStatus() == null) {
                continue;
            }
            WorkOrderStatus status = WorkOrderStatus.valueOf(row.getStatus());
            counts.merge(status, row.getCount() == null ? 0L : row.getCount(), Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new WorkOrderStatusCountDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeWorkOrderNumber(String number) {
        if (number == null || number.isBlank()) {
            return workOrderNumberService.nextManualNumber();
        }
        return number.trim();
    }
}
