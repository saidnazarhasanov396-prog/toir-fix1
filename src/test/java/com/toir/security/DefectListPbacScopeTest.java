package com.toir.security;

import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectListStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.defects.DefectListService;
import com.toir.util.AuditBuilderService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefectListPbacScopeTest {

    @Mock
    DefectListRepository repository;
    @Mock
    DefectListLineRepository lineRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    RepairRequestRepository repairRequestRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    DefectListService service;

    private UUID departmentA;
    private UUID departmentB;

    @BeforeEach
    void setUp() {
        departmentA = UUID.randomUUID();
        departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
    }

    @Test
    void detailAllowsListLinkedToEquipmentInOwnDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, null);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentA)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);

        var response = service.findById(listId);

        assertThat(response.id()).isEqualTo(listId);
    }

    @Test
    void detailAllowsListLinkedToRepairRequestInOwnDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, repairRequestId, null);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentA)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);

        var response = service.findById(listId);

        assertThat(response.id()).isEqualTo(listId);
    }

    @Test
    void detailAllowsListLinkedToWorkOrderInOwnDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, workOrderId);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId, departmentA)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);

        var response = service.findById(listId);

        assertThat(response.id()).isEqualTo(listId);
    }

    @Test
    void detailDeniesListLinkedOnlyToOtherDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, null);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(listId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detailDeniesConflictingLinkedDepartments() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, repairRequestId, null);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentA)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentB)));

        assertThatThrownBy(() -> service.findById(listId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingListRemainsNotFound() {
        UUID listId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(listId))
                .hasMessageContaining("Defect list not found");
    }

    @Test
    void systemAdminCanReadListInOtherDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));

        var response = service.findById(listId);

        assertThat(response.id()).isEqualTo(listId);
    }

    @Test
    void listFiltersOutOutOfScopeDefectLists() {
        UUID equipmentInScope = UUID.randomUUID();
        UUID equipmentOutOfScope = UUID.randomUUID();
        DefectList inScope = defectList(UUID.randomUUID(), equipmentInScope, null, null);
        DefectList outOfScope = defectList(UUID.randomUUID(), equipmentOutOfScope, null, null);
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(inScope, outOfScope), pageRequest, 2));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentInScope)).thenReturn(Optional.of(equipment(equipmentInScope, departmentA)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentOutOfScope)).thenReturn(Optional.of(equipment(equipmentOutOfScope, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        var result = service.search(null, 0, 20, null);

        assertThat(result.getContent()).extracting("id").containsExactly(inScope.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void createDeniesOutOfScopeEquipment() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(equipmentId, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(DefectList.class));
    }

    @Test
    void approveDeniesOutOfScopeListBeforeMutation() {
        UUID equipmentId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, null);
        list.getLines().add(line(list));
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(listId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(DefectList.class));
    }

    @Test
    void addLineDeniesOutOfScopeParentList() {
        UUID equipmentId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        DefectList list = defectList(listId, equipmentId, null, null);
        when(repository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(list));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.addLine(listId, lineDto()))
                .isInstanceOf(AccessDeniedException.class);

        verify(lineRepository, never()).save(any(DefectListLine.class));
    }

    @Test
    void removeLineDeniesOutOfScopeParentList() {
        UUID equipmentId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        DefectList list = defectList(UUID.randomUUID(), equipmentId, null, null);
        DefectListLine line = line(list);
        line.setId(lineId);
        when(lineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.removeLine(lineId))
                .isInstanceOf(AccessDeniedException.class);

        verify(lineRepository, never()).save(any(DefectListLine.class));
    }

    private DefectList defectList(UUID id, UUID equipmentId, UUID repairRequestId, UUID workOrderId) {
        DefectList list = new DefectList();
        list.setId(id);
        list.setCode("DL-2026-0001");
        list.setTitle("May defects");
        list.setEquipmentId(equipmentId);
        list.setRepairRequestId(repairRequestId);
        list.setWorkOrderId(workOrderId);
        list.setCreatedById(UUID.randomUUID());
        list.setStatus(DefectListStatus.DRAFT);
        return list;
    }

    private DefectListLine line(DefectList list) {
        DefectListLine line = new DefectListLine();
        line.setId(UUID.randomUUID());
        line.setDefectList(list);
        line.setDescription("Line");
        line.setRequiredQuantity(1);
        line.setEstimatedLaborHours(1);
        line.setEstimatedCost(1);
        return line;
    }

    private DefectListLineDto lineDto() {
        return new DefectListLineDto(
                null,
                UUID.randomUUID(),
                "Line",
                "Scope",
                "Materials",
                null,
                1,
                1,
                1
        );
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setName("Pump");
        equipment.setDepartmentId(departmentId);
        return equipment;
    }

    private RepairRequest repairRequest(UUID id, UUID departmentId) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setNumber("RR-2026-1001");
        repairRequest.setTitle("Repair request");
        repairRequest.setDescription("Short description");
        repairRequest.setDepartmentId(departmentId);
        repairRequest.setPriority(PriorityLevel.MEDIUM);
        repairRequest.setStatus(RequestStatus.OPEN);
        return repairRequest;
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Work order");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private DefectListRequest request(UUID equipmentId, UUID repairRequestId, UUID workOrderId) {
        return new DefectListRequest(
                null,
                "May defects",
                equipmentId,
                repairRequestId,
                workOrderId,
                UUID.randomUUID(),
                "Notes"
        );
    }
}
