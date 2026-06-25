package com.toir.service.attachment;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.FileCategory;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.EmployeeRepository;
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

import static org.assertj.core.api.Assertions.assertThatCode;
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
    ProcurementRequestRepository procurementRequestRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentCommissioningActRepository equipmentCommissioningActRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    EmployeeRepository employeeRepository;

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
                procurementRequestRepository,
                warehouseRepository,
                scopeAccessService,
                equipmentCommissioningActRepository,
                defectRepository,
                employeeRepository
        );
    }

    @Test
    void departmentScopedUserCanAccessHrEmployeePassportAttachments() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee employee = employee(employeeId, departmentId, null);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.HR_EMPLOYEE, employeeId))
                .doesNotThrowAnyException();
    }

    @Test
    void currentEmployeeCanAccessOwnHrEmployeePassportAttachments() {
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId, UUID.randomUUID(), null);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.HR_EMPLOYEE, employeeId))
                .doesNotThrowAnyException();
    }

    @Test
    void currentUserCanAccessOwnHrEmployeePassportAttachmentsByAssignedUser() {
        UUID employeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Employee employee = employee(employeeId, UUID.randomUUID(), userId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessAssignedUser(userId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.HR_EMPLOYEE, employeeId))
                .doesNotThrowAnyException();
    }

    @Test
    void forbiddenHrEmployeePassportAttachmentScopeThrows() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee employee = employee(employeeId, departmentId, null);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanAccess(AttachmentTargetType.HR_EMPLOYEE, employeeId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("employee scope");
    }

    @Test
    void hrEmployeeAttachmentsUsePassportFileCategory() {
        org.assertj.core.api.Assertions.assertThat(service.fileCategoryFor(AttachmentTargetType.HR_EMPLOYEE))
                .isEqualTo(FileCategory.PASSPORT);
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

    @Test
    void departmentScopedUserCanAccessProcurementRequestAttachments() {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest request = procurementRequest(departmentId, null, null);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.PROCUREMENT_REQUEST, requestId))
                .doesNotThrowAnyException();
    }

    @Test
    void warehouseScopedUserCanAccessProcurementRequestAttachments() {
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseDepartmentId = UUID.randomUUID();
        ProcurementRequest request = procurementRequest(null, warehouseId, null);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setDepartmentId(warehouseDepartmentId);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(warehouseDepartmentId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.PROCUREMENT_REQUEST, requestId))
                .doesNotThrowAnyException();
    }

    @Test
    void forbiddenProcurementRequestAttachmentScopeThrows() {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest request = procurementRequest(departmentId, null, null);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanAccess(AttachmentTargetType.PROCUREMENT_REQUEST, requestId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("procurement scope");
    }

    @Test
    void departmentScopedUserCanAccessDefectAttachments() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Defect defect = new Defect();
        defect.setEquipmentId(equipmentId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(departmentId);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThatCode(() -> service.assertCanAccess(AttachmentTargetType.DEFECT, defectId))
                .doesNotThrowAnyException();
    }

    @Test
    void forbiddenDefectAttachmentScopeThrows() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Defect defect = new Defect();
        defect.setEquipmentId(equipmentId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(departmentId);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanAccess(AttachmentTargetType.DEFECT, defectId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("defect scope");
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

    private ProcurementRequest procurementRequest(UUID departmentId, UUID warehouseId, UUID requestedBy) {
        ProcurementRequest request = new ProcurementRequest();
        request.setId(UUID.randomUUID());
        request.setDepartmentId(departmentId);
        request.setWarehouseId(warehouseId);
        request.setRequestedBy(requestedBy);
        return request;
    }

    private Employee employee(UUID employeeId, UUID departmentId, UUID userId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-" + employeeId.toString().substring(0, 8));
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setPosition("Mechanic");
        employee.setDepartmentId(departmentId);
        employee.setUserId(userId);
        employee.setActive(true);
        return employee;
    }
}
