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
import com.toir.enums.PlanStatus;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
    @Mock private PlannedShutdownWorkItemPolicy workItemPolicy;
    @Mock private AuditBuilderService auditBuilderService;

    @InjectMocks
    private PlannedShutdownService service;

    @Test
    void findAllFilteredAppliesFiltersCorrectly() {
        UUID departmentId = UUID.randomUUID();

        PlannedShutdown s1 = new PlannedShutdown();
        s1.setId(UUID.randomUUID());
        s1.setName("Annual Maintenance");
        s1.setDepartmentId(departmentId);
        s1.setStartAt(Instant.parse("2026-05-19T10:00:00Z"));
        s1.setEndAt(Instant.parse("2026-05-19T18:00:00Z"));
        s1.setReason("Routine check");
        s1.setStatus(PlanStatus.DRAFT);

        when(repository.findAllFiltered(departmentId, "DRAFT", "%annual%")).thenReturn(List.of(s1));

        List<PlannedShutdownDto> results = service.findAllFiltered(departmentId, PlanStatus.DRAFT, "annual");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Annual Maintenance");
        assertThat(results.get(0).status()).isEqualTo(PlanStatus.DRAFT);
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
        verify(repository).saveAndFlush(shutdown);
    }

    @Test
    void replaceScopeKeepsStableRowsAndSoftDeletesRemovedRows() {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID();
        UUID keptEquipmentId = UUID.randomUUID(); UUID removedEquipmentId = UUID.randomUUID(); UUID addedEquipmentId = UUID.randomUUID();
        PlannedShutdown shutdown = shutdown(id, departmentId, 2L, PlannedShutdownStatus.SCOPE_FORMATION);
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
