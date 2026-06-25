package com.toir.security;

import com.toir.dto.defect.DefectRequest;
import com.toir.entity.defects.Defect;
import com.toir.entity.defects.DefectList;
import com.toir.enums.DefectListStatus;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.defects.DefectService;
import com.toir.util.AuditBuilderService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefectPbacScopeTest {

    @Mock
    DefectRepository repository;
    @Mock
    DefectListRepository defectListRepository;
    @Mock
    DefectListLineRepository defectListLineRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    RepairRequestRepository repairRequestRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    KnowledgeArticleRepository knowledgeRepository;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    DefectService service;

    private UUID departmentA;
    private UUID departmentB;

    @BeforeEach
    void setUp() {
        departmentA = UUID.randomUUID();
        departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
    }

    @Test
    void detailAllowsDefectLinkedToEquipmentInOwnDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentA)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);
        stubResponseDependencies(defect);

        var response = service.findById(defectId);

        assertThat(response.id()).isEqualTo(defectId);
    }

    @Test
    void detailAllowsDefectLinkedToRepairRequestInOwnDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, repairRequestId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentA)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);
        stubResponseDependencies(defect);

        var response = service.findById(defectId);

        assertThat(response.id()).isEqualTo(defectId);
    }

    @Test
    void detailDeniesDefectLinkedOnlyToOtherDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(defectId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detailDeniesConflictingLinkedDepartments() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, repairRequestId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentA)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentB)));

        assertThatThrownBy(() -> service.findById(defectId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detailDeniesUnscopedDefectForNonAdmin() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(defectId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingDefectRemainsNotFound() {
        UUID defectId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(defectId))
                .hasMessageContaining("Defect not found");
    }

    @Test
    void systemAdminCanReadDefectInOtherDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        stubResponseDependencies(defect);

        var response = service.findById(defectId);

        assertThat(response.id()).isEqualTo(defectId);
    }

    @Test
    void listFiltersOutOutOfScopeDefects() {
        UUID equipmentInScope = UUID.randomUUID();
        UUID equipmentOutOfScope = UUID.randomUUID();
        Defect inScope = defect(UUID.randomUUID(), equipmentInScope, null);
        Defect outOfScope = defect(UUID.randomUUID(), equipmentOutOfScope, null);
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, null, null, null, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(inScope, outOfScope), pageRequest, 2));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentInScope)).thenReturn(Optional.of(equipment(equipmentInScope, departmentA)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentOutOfScope)).thenReturn(Optional.of(equipment(equipmentOutOfScope, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentA)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);
        stubResponseDependencies(inScope);

        var result = service.search(null, null, null, null, null, 0, 20, null);

        assertThat(result.getContent()).extracting("id").containsExactly(inScope.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void createDeniesOutOfScopeEquipment() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(equipmentId, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(Defect.class));
    }

    @Test
    void resolveDeniesOutOfScopeDefectBeforeMutation() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.resolve(defectId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(Defect.class));
    }

    @Test
    void createLessonDeniesOutOfScopeDefect() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, equipmentId, null);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentB)));
        when(scopeAccessService.canAccessDepartment(departmentB)).thenReturn(false);

        assertThatThrownBy(() -> service.createLesson(defectId))
                .isInstanceOf(AccessDeniedException.class);

        verify(knowledgeRepository, never()).save(any());
    }

    private Defect defect(UUID id, UUID equipmentId, UUID repairRequestId) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setCode("DEF-2026-0001");
        defect.setTitle("Bearing overheating");
        defect.setDescription("Temperature threshold exceeded");
        defect.setEquipmentId(equipmentId);
        defect.setRepairRequestId(repairRequestId);
        defect.setStatus(DefectStatus.OPEN);
        return defect;
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

    private DefectRequest request(UUID equipmentId, UUID repairRequestId) {
        UUID defectListId = UUID.randomUUID();
        when(defectListRepository.findByIdAndIsDeletedFalse(defectListId))
                .thenReturn(Optional.of(defectList(defectListId, equipmentId, repairRequestId)));
        return new DefectRequest(
                null,
                "Bearing overheating",
                "Temperature threshold exceeded",
                equipmentId,
                null,
                defectListId,
                repairRequestId,
                "MECHANICAL",
                "HIGH",
                "Wear",
                "Insufficient lubrication"
        );
    }

    private DefectList defectList(UUID id, UUID equipmentId, UUID repairRequestId) {
        DefectList defectList = new DefectList();
        defectList.setId(id);
        defectList.setCode("DL-2026-0001");
        defectList.setTitle("Defect list");
        defectList.setEquipmentId(equipmentId);
        defectList.setRepairRequestId(repairRequestId);
        defectList.setCreatedById(UUID.randomUUID());
        defectList.setStatus(DefectListStatus.DRAFT);
        return defectList;
    }

    private void stubResponseDependencies(Defect defect) {
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(Mockito.anyCollection()))
                .thenReturn(List.of());
        if (defect.getRepairRequestId() != null) {
            when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(List.of(defect.getRepairRequestId())))
                    .thenReturn(List.of(repairRequest(defect.getRepairRequestId(), departmentA)));
        }
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());
        when(defectListLineRepository.findAllByDefectIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of());
        when(knowledgeRepository.findDefectIdsWithLesson(any(), eq("LESSON_LEARNED")))
                .thenReturn(List.of());
    }
}
