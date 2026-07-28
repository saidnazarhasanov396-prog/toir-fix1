package com.toir.repository;

import com.toir.dto.repairrequest.RepairRequestFilterRequest;
import com.toir.exception.RestException;
import com.toir.test.RepositorySliceTest;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.*;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestStatsFilter;
import com.toir.repository.repair.RepairRequestStatsProjection;
import com.toir.repository.specification.RepairRequestSpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositorySliceTest
class RepairRequestRepositoryStatsTest {

    @Autowired
    RepairRequestRepository repository;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Autowired
    DefectRepository defectRepository;

    @Test
    void getRepairRequestStatsWithoutFiltersCountsAllNonDeletedRequests() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        RepairRequest openEmergencyWithWo = saveRepairRequest(
                "RR-001",
                "Pump emergency",
                "Emergency pump failure",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest openMedium = saveRepairRequest(
                "RR-002",
                "Pump leak",
                "Small leak",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.MEDIUM
        );

        RepairRequest approvedEmergency = saveRepairRequest(
                "RR-003",
                "Motor issue",
                "Critical motor issue",
                departmentId,
                equipmentId,
                RequestStatus.APPROVED,
                PriorityLevel.EMERGENCY
        );

        saveRepairRequest(
                "RR-004",
                "Closed request",
                "Already closed",
                departmentId,
                equipmentId,
                RequestStatus.CLOSED,
                PriorityLevel.LOW
        );
        saveRepairRequest(
                "RR-005",
                "In progress request",
                "Already being repaired",
                departmentId,
                equipmentId,
                RequestStatus.IN_PROGRESS,
                PriorityLevel.MEDIUM
        );
        RepairRequest deletedOpenEmergency = saveRepairRequest(
                "RR-006",
                "Deleted open request",
                "Soft-deleted open request",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY,
                true
        );

        saveWorkOrder(openEmergencyWithWo.getId(), departmentId, equipmentId);
        saveWorkOrder(approvedEmergency.getId(), departmentId, equipmentId);
        saveWorkOrder(deletedOpenEmergency.getId(), departmentId, equipmentId);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                RepairRequestStatsFilter.empty(),
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(5);
        assertThat(stats.getEmergency()).isEqualTo(2);
        assertThat(stats.getOpen()).isEqualTo(2);
        assertThat(stats.getWithWorkOrder()).isEqualTo(2);
    }

    @Test
    void getRepairRequestStatsWithDepartmentEquipmentAndSearchCountsOnlyMatchingRequests() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID targetEquipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();

        RepairRequest targetOpen = saveRepairRequest(
                "RR-PUMP-001",
                "Pump overheating",
                "Pump is overheating",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest targetApproved = saveRepairRequest(
                "RR-PUMP-002",
                "Pump vibration",
                "Pump vibration issue",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.APPROVED,
                PriorityLevel.HIGH
        );

        RepairRequest otherDepartment = saveRepairRequest(
                "RR-PUMP-003",
                "Other department pump",
                "Pump issue in another department",
                otherDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest otherEquipment = saveRepairRequest(
                "RR-PUMP-004",
                "Other equipment pump",
                "Pump issue on another equipment",
                targetDepartmentId,
                otherEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest nonMatchingSearch = saveRepairRequest(
                "RR-MOTOR-001",
                "Motor issue",
                "Motor issue",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        saveWorkOrder(targetOpen.getId(), targetDepartmentId, targetEquipmentId);
        saveWorkOrder(otherDepartment.getId(), otherDepartmentId, targetEquipmentId);
        saveWorkOrder(otherEquipment.getId(), targetDepartmentId, otherEquipmentId);
        saveWorkOrder(nonMatchingSearch.getId(), targetDepartmentId, targetEquipmentId);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                new RepairRequestStatsFilter(
                        targetDepartmentId, targetEquipmentId, "%pump%",
                        null, null, null, null, null,
                        null, null, null, null, null, null
                ),
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(2);
        assertThat(stats.getEmergency()).isEqualTo(1);
        assertThat(stats.getOpen()).isEqualTo(1);
        assertThat(stats.getWithWorkOrder()).isEqualTo(1);
    }

    @Test
    void getRepairRequestStatsCountsRequestOnceForMultipleActiveWorkOrdersAndIgnoresDeletedWorkOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest active = saveRepairRequest(
                "RR-WO-ACTIVE", "Active work orders", "Active links",
                departmentId, equipmentId, RequestStatus.OPEN, PriorityLevel.MEDIUM
        );
        RepairRequest deletedOnly = saveRepairRequest(
                "RR-WO-DELETED", "Deleted work order", "Deleted link only",
                departmentId, equipmentId, RequestStatus.APPROVED, PriorityLevel.MEDIUM
        );

        saveWorkOrder(active.getId(), departmentId, equipmentId);
        saveWorkOrder(active.getId(), departmentId, equipmentId);
        WorkOrder deleted = saveWorkOrder(deletedOnly.getId(), departmentId, equipmentId);
        deleted.setDeleted(true);
        workOrderRepository.save(deleted);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                RepairRequestStatsFilter.empty(),
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(2);
        assertThat(stats.getWithWorkOrder()).isEqualTo(1);
    }

    @Test
    void getRepairRequestStatsAppliesContextAndManualFiltersBeforeAllFormulas() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant detectedAt = Instant.parse("2026-07-10T10:00:00Z");
        Instant targetAt = Instant.parse("2026-07-20T10:00:00Z");
        RepairRequest matching = saveStatsCandidate(
                "MATCH", departmentId, equipmentId, RequestStatus.OPEN, PriorityLevel.EMERGENCY,
                CriticalityLevel.CRITICAL, RequestSource.MOBILE, detectedAt, targetAt
        );
        linkStatsCandidate(matching, departmentId, equipmentId, true, true);

        RepairRequest wrongDepartment = saveStatsCandidate(
                "DEPARTMENT", UUID.randomUUID(), equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(wrongDepartment, wrongDepartment.getDepartmentId(), equipmentId, true, true);
        RepairRequest wrongEquipment = saveStatsCandidate(
                "EQUIPMENT", departmentId, UUID.randomUUID(), RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(wrongEquipment, departmentId, wrongEquipment.getEquipmentId(), true, true);
        RepairRequest wrongPriority = saveStatsCandidate(
                "PRIORITY", departmentId, equipmentId, RequestStatus.OPEN, PriorityLevel.HIGH,
                CriticalityLevel.CRITICAL, RequestSource.MOBILE, detectedAt, targetAt
        );
        linkStatsCandidate(wrongPriority, departmentId, equipmentId, true, true);
        RepairRequest wrongStatus = saveStatsCandidate(
                "STATUS", departmentId, equipmentId, RequestStatus.APPROVED,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(wrongStatus, departmentId, equipmentId, true, true);
        RepairRequest wrongCriticality = saveStatsCandidate(
                "CRITICALITY", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.HIGH, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(wrongCriticality, departmentId, equipmentId, true, true);
        RepairRequest wrongSource = saveStatsCandidate(
                "SOURCE", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MANUAL,
                detectedAt, targetAt
        );
        linkStatsCandidate(wrongSource, departmentId, equipmentId, true, true);
        RepairRequest wrongDetectedDate = saveStatsCandidate(
                "DETECTED", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt.minusSeconds(60), targetAt
        );
        linkStatsCandidate(wrongDetectedDate, departmentId, equipmentId, true, true);
        RepairRequest wrongTargetDate = saveStatsCandidate(
                "TARGET", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt.plusSeconds(60)
        );
        linkStatsCandidate(wrongTargetDate, departmentId, equipmentId, true, true);
        RepairRequest withoutDefect = saveStatsCandidate(
                "NO-DEFECT", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(withoutDefect, departmentId, equipmentId, false, true);
        RepairRequest withoutWorkOrder = saveStatsCandidate(
                "NO-WO", departmentId, equipmentId, RequestStatus.OPEN,
                PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, RequestSource.MOBILE,
                detectedAt, targetAt
        );
        linkStatsCandidate(withoutWorkOrder, departmentId, equipmentId, true, false);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                new RepairRequestStatsFilter(
                        departmentId,
                        equipmentId,
                        "%filter-pump%",
                        PriorityLevel.EMERGENCY.name(),
                        RequestStatus.OPEN.name(),
                        null,
                        CriticalityLevel.CRITICAL.name(),
                        RequestSource.MOBILE.name(),
                        detectedAt.minusSeconds(1),
                        detectedAt.plusSeconds(1),
                        targetAt.minusSeconds(1),
                        targetAt.plusSeconds(1),
                        true,
                        true
                ),
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(1);
        assertThat(stats.getEmergency()).isEqualTo(1);
        assertThat(stats.getOpen()).isEqualTo(1);
        assertThat(stats.getWithWorkOrder()).isEqualTo(1);
    }

    @Test
    void getRepairRequestStatsSupportsCompletedOrClosedScopeAndReturnsZeroForNoMatches() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        saveRepairRequest("RR-SCOPE-C", "Completed", "Completed", departmentId, equipmentId,
                RequestStatus.COMPLETED, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-SCOPE-X", "Closed", "Closed", departmentId, equipmentId,
                RequestStatus.CLOSED, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-SCOPE-O", "Open", "Open", departmentId, equipmentId,
                RequestStatus.OPEN, PriorityLevel.EMERGENCY);

        RepairRequestStatsProjection scoped = repository.getRepairRequestStats(
                new RepairRequestStatsFilter(
                        departmentId, equipmentId, null, null, null, "COMPLETED_OR_CLOSED",
                        null, null, null, null, null, null, null, null
                ),
                PriorityLevel.EMERGENCY.name(), RequestStatus.OPEN.name()
        );
        RepairRequestStatsProjection empty = repository.getRepairRequestStats(
                new RepairRequestStatsFilter(
                        UUID.randomUUID(), null, null, null, null, null,
                        null, null, null, null, null, null, null, null
                ),
                PriorityLevel.EMERGENCY.name(), RequestStatus.OPEN.name()
        );

        assertThat(scoped.getTotalRequests()).isEqualTo(2);
        assertThat(scoped.getOpen()).isZero();
        assertThat(empty.getTotalRequests()).isZero();
        assertThat(empty.getEmergency()).isZero();
        assertThat(empty.getOpen()).isZero();
        assertThat(empty.getWithWorkOrder()).isZero();
    }

    @Test
    void specificationWithCompletedOrClosedStatusScopeMatchesBothCompletedAndClosedRequests() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        saveRepairRequest("RR-SCOPE-OPEN", "Open", "Open request", departmentId, equipmentId, RequestStatus.OPEN, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-SCOPE-COMPLETED", "Completed", "Completed request", departmentId, equipmentId, RequestStatus.COMPLETED, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-SCOPE-CLOSED", "Closed", "Closed request", departmentId, equipmentId, RequestStatus.CLOSED, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-SCOPE-CANCELLED", "Cancelled", "Cancelled request", departmentId, equipmentId, RequestStatus.CANCELLED, PriorityLevel.MEDIUM);

        List<RepairRequest> result = repository.findAll(
                RepairRequestSpecifications.byFilter(filter(null, "COMPLETED_OR_CLOSED")));

        assertThat(result)
                .extracting(RepairRequest::getNumber)
                .containsExactlyInAnyOrder("RR-SCOPE-COMPLETED", "RR-SCOPE-CLOSED");
    }

    @Test
    void specificationPrefersExactStatusWhenStatusAndStatusScopeAreBothPresent() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        saveRepairRequest("RR-PRECEDENCE-COMPLETED", "Completed", "Completed request", departmentId, equipmentId, RequestStatus.COMPLETED, PriorityLevel.MEDIUM);
        saveRepairRequest("RR-PRECEDENCE-CLOSED", "Closed", "Closed request", departmentId, equipmentId, RequestStatus.CLOSED, PriorityLevel.MEDIUM);

        List<RepairRequest> result = repository.findAll(
                RepairRequestSpecifications.byFilter(filter(RequestStatus.COMPLETED, "COMPLETED_OR_CLOSED")));

        assertThat(result)
                .extracting(RepairRequest::getNumber)
                .containsExactly("RR-PRECEDENCE-COMPLETED");
    }

    @Test
    void specificationRejectsUnsupportedStatusScope() {
        assertThatThrownBy(() -> repository.findAll(
                RepairRequestSpecifications.byFilter(filter(null, "UNKNOWN"))))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unsupported repair request statusScope: UNKNOWN");
    }

    @Test
    void persistedRepairRequestCanBeLinkedFromDefect() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest request = saveRepairRequest(
                "RR-DEF-001",
                "Pump vibration",
                "Excess vibration on pump",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.HIGH
        );
        Defect linkedDefect = saveDefect(equipmentId, request.getId());
        Defect otherDefect = saveDefect(equipmentId, null);

        assertThat(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .extracting(Defect::getId)
                .containsExactly(linkedDefect.getId())
                .doesNotContain(otherDefect.getId());
    }

    private RepairRequest saveRepairRequest(
            String number,
            String title,
            String description,
            UUID departmentId,
            UUID equipmentId,
            RequestStatus status,
            PriorityLevel priority
    ) {
        return saveRepairRequest(number, title, description, departmentId, equipmentId, status, priority, false);
    }

    private RepairRequest saveRepairRequest(
            String number,
            String title,
            String description,
            UUID departmentId,
            UUID equipmentId,
            RequestStatus status,
            PriorityLevel priority,
            boolean deleted
    ) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setNumber(number);
        request.setTitle(title);
        request.setDescription(description);
        request.setDepartmentId(departmentId);
        request.setEquipmentId(equipmentId);
        request.setReporterId(UUID.randomUUID());
        request.setStatus(status);
        request.setPriority(priority);
        request.setDeleted(deleted);

        return repository.save(request);
    }

    private RepairRequest saveStatsCandidate(
            String suffix,
            UUID departmentId,
            UUID equipmentId,
            RequestStatus status,
            PriorityLevel priority,
            CriticalityLevel criticality,
            RequestSource source,
            Instant detectedAt,
            Instant targetAt
    ) {
        RepairRequest request = saveRepairRequest(
                "RR-FILTER-PUMP-" + suffix,
                "Filtered pump " + suffix,
                "Filter candidate",
                departmentId,
                equipmentId,
                status,
                priority
        );
        request.setCriticality(criticality);
        request.setSource(source);
        request.setDetectedAt(detectedAt);
        request.setTargetCompletionAt(targetAt);
        return repository.save(request);
    }

    private void linkStatsCandidate(
            RepairRequest request,
            UUID departmentId,
            UUID equipmentId,
            boolean withDefect,
            boolean withWorkOrder
    ) {
        if (withDefect) {
            saveDefect(equipmentId, request.getId());
        }
        if (withWorkOrder) {
            saveWorkOrder(request.getId(), departmentId, equipmentId);
        }
    }

    private RepairRequestFilterRequest filter(RequestStatus status, String statusScope) {
        return new RepairRequestFilterRequest(
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                statusScope
        );
    }

    private Defect saveDefect(UUID equipmentId, UUID repairRequestId) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode("DEF-" + UUID.randomUUID());
        defect.setTitle("Pump vibration defect");
        defect.setDescription("Vibration detected during inspection");
        defect.setEquipmentId(equipmentId);
        defect.setRepairRequestId(repairRequestId);
        defect.setStatus(DefectStatus.OPEN);
        defect.setDeleted(false);

        return defectRepository.saveAndFlush(defect);
    }

    private WorkOrder saveWorkOrder(
            UUID repairRequestId,
            UUID departmentId,
            UUID equipmentId
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-" + UUID.randomUUID());
        workOrder.setTitle("Work order for repair request");
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setType(WorkOrderType.values()[0]);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setDeleted(false);

        return workOrderRepository.save(workOrder);
    }
}
