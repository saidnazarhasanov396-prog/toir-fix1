package com.toir.service.repair;

import com.toir.dto.repairrequest.WarrantyDecisionRequest;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.WarrantyHandling;
import com.toir.exception.RestException;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.SupplierRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestTemplateActionRepository;
import com.toir.repository.repair.RepairRequestTemplateRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.service.MeterService;
import com.toir.service.NotificationService;
import com.toir.service.OperationalIssueLifecycleSyncService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRuleResolver;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.util.AuditBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyEmergencyOverridePermissionTest {

    @Mock
    RepairRequestRepository repository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    SupplierRepository supplierRepository;
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
    @Mock
    MaintenanceTemplateRepository maintenanceTemplateRepository;
    @Mock
    MaintenanceOperationRepository maintenanceOperationRepository;
    @Mock
    MaintenanceActionRepository maintenanceActionRepository;
    @Mock
    RepairRequestTemplateRepository repairRequestTemplateRepository;
    @Mock
    RepairRequestTemplateActionRepository repairRequestTemplateActionRepository;
    @Mock
    MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;
    @Mock
    EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;
    @Mock
    EquipmentMeterRepository equipmentMeterRepository;
    @Mock
    MeterReadingRepository meterReadingRepository;
    @Mock
    MeterService meterService;
    @Mock
    MaintenanceDueEventService maintenanceDueEventService;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;

    @InjectMocks
    RepairRequestService service;

    @BeforeEach
    void setUp() {
        lenient().when(repairRequestTemplateRepository.findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        lenient().when(repairRequestTemplateActionRepository.findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        lenient().when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of());
    }

    @Test
    void emergencyOverrideForbiddenWithoutElevatedPermission() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.hasAuthority(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE)).thenReturn(false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.EMERGENCY_OVERRIDE,
                        "comment",
                        null,
                        null,
                        "Critical production stop"
                ),
                UUID.randomUUID()
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("REPAIR_REQUEST_WARRANTY_OVERRIDE");
                });

        verify(repository, never()).save(any(RepairRequest.class));
    }

    @Test
    void emergencyOverrideAllowedWithOverridePermission() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.hasAuthority(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE)).thenReturn(true);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.EMERGENCY_OVERRIDE,
                        "comment",
                        null,
                        null,
                        "Critical production stop"
                ),
                currentUserId
        );

        verify(repository).save(any(RepairRequest.class));
    }

    @Test
    void emergencyOverrideAllowedForScopeAdmin() {
        UUID id = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.hasAuthority(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE)).thenReturn(false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.EMERGENCY_OVERRIDE,
                        "comment",
                        null,
                        null,
                        "Critical production stop"
                ),
                UUID.randomUUID()
        );

        verify(repository).save(any(RepairRequest.class));
    }

    @Test
    void emergencyOverrideRequiresEmergencyReasonBeforePermissionCheck() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.EMERGENCY_OVERRIDE,
                        "comment",
                        null,
                        null,
                        "   "
                ),
                UUID.randomUUID()
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("emergencyReason is required");
                });

        verify(scopeAccessService, never()).hasAuthority(any());
        verify(scopeAccessService, never()).isScopeAdmin();
        verify(repository, never()).save(any(RepairRequest.class));
    }

    @Test
    void emergencyOverridePersistsEmergencyReasonAndAudits() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(scopeAccessService.hasAuthority(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE)).thenReturn(true);
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.EMERGENCY_OVERRIDE,
                        "override comment",
                        null,
                        null,
                        "Critical production stop"
                ),
                currentUserId
        );

        verify(repository).save(argThat(saved ->
                saved.getWarrantyHandling() == WarrantyHandling.EMERGENCY_OVERRIDE
                        && "Critical production stop".equals(saved.getEmergencyReason())
                        && "override comment".equals(saved.getWarrantyDecisionComment())
                        && currentUserId.equals(saved.getWarrantyDecisionByUserId())
        ));
        verify(auditBuilderService).log(
                eq("repair_request"),
                eq(id.toString()),
                eq(AuditAction.UPDATE),
                eq(AuditModule.REPAIR_REQUEST),
                eq("Warranty decision recorded: EMERGENCY_OVERRIDE"),
                isNull(),
                any(RepairRequest.class)
        );
    }

    @Test
    void nonEmergencyHandlingDoesNotRequireOverridePermission() {
        UUID id = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setEquipmentId(equipmentId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
                .thenReturn(List.of());

        service.recordWarrantyDecision(
                id,
                new WarrantyDecisionRequest(
                        WarrantyHandling.INTERNAL_REPAIR_ALLOWED,
                        "allowed",
                        null,
                        null,
                        null
                ),
                UUID.randomUUID()
        );

        verify(scopeAccessService, never()).hasAuthority(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE);
        verify(scopeAccessService, never()).isScopeAdmin();
        verify(repository).save(any(RepairRequest.class));
    }

    private RepairRequest repairRequest(UUID id) {
        RepairRequest entity = new RepairRequest();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setNumber("RR-001");
        return entity;
    }
}
