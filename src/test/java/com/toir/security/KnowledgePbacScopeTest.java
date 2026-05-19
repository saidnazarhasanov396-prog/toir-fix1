package com.toir.security;

import com.toir.entity.KnowledgeArticle;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgePbacScopeTest {

    KnowledgeArticleRepository repository;
    EquipmentRepository equipmentRepository;
    DefectRepository defectRepository;
    WorkOrderRepository workOrderRepository;
    ScopeAccessService scopeAccessService;
    KnowledgeService service;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeArticleRepository.class);
        equipmentRepository = mock(EquipmentRepository.class);
        defectRepository = mock(DefectRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new KnowledgeService(repository, equipmentRepository, defectRepository, workOrderRepository, scopeAccessService);
    }

    @Test
    void broadKnowledgeReadRemainsGlobalWhenNoScopedFilterIsRequested() {
        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(article(null)));

        service.list(null, null, null, 0, 20);

        verify(scopeAccessService, never()).assertCanAccessDepartment(any());
    }

    @Test
    void createWithInScopeEquipmentIsAllowed() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        stubSuccessfulSave();

        service.create(article(equipmentId));

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        verify(repository).save(any(KnowledgeArticle.class));
    }

    @Test
    void createWithOutOfScopeEquipmentIsDenied() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        assertThatThrownBy(() -> service.create(article(equipmentId)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(KnowledgeArticle.class));
    }

    @Test
    void deleteWithOutOfScopeLinkedEquipmentIsDenied() {
        UUID articleId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(repository.findByIdAndIsDeletedFalse(articleId)).thenReturn(Optional.of(article(equipmentId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        assertThatThrownBy(() -> service.delete(articleId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(KnowledgeArticle.class));
    }

    @Test
    void scopeAdminBypassesLinkedEquipmentValidation() {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        stubSuccessfulSave();

        service.create(article(equipmentId));

        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(repository).save(any(KnowledgeArticle.class));
    }

    private void stubSuccessfulSave() {
        int year = Year.now().getValue();
        String expectedCode = "LL-" + year + "-0001";
        when(repository.maxSequenceByCodePrefix("LL-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private KnowledgeArticle article(UUID equipmentId) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle("Pump lesson");
        article.setKind("LESSON_LEARNED");
        article.setEquipmentId(equipmentId);
        article.setProblem("Problem");
        article.setRootCause("Cause");
        article.setSolution("Solution");
        return article;
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
