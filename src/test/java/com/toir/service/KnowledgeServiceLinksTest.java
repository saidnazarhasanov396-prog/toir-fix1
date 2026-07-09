package com.toir.service;

import com.toir.dto.knowledge.KnowledgeArticleLinkDto;
import com.toir.dto.knowledge.KnowledgeArticleRequest;
import com.toir.dto.knowledge.KnowledgeContextResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.KnowledgeArticleLink;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.KnowledgeTargetType;
import com.toir.repository.KnowledgeArticleLinkRepository;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceLinksTest {

    KnowledgeArticleRepository repository;
    KnowledgeArticleLinkRepository linkRepository;
    EquipmentRepository equipmentRepository;
    DefectRepository defectRepository;
    WorkOrderRepository workOrderRepository;
    RepairRequestRepository repairRequestRepository;
    ScopeAccessService scopeAccessService;
    KnowledgeService service;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeArticleRepository.class);
        linkRepository = mock(KnowledgeArticleLinkRepository.class);
        equipmentRepository = mock(EquipmentRepository.class);
        defectRepository = mock(DefectRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        repairRequestRepository = mock(RepairRequestRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new KnowledgeService(
                repository,
                linkRepository,
                equipmentRepository,
                defectRepository,
                workOrderRepository,
                repairRequestRepository,
                scopeAccessService);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void createRequestPersistsLinksForEquipmentRepairRequestAndWorkOrderTargets() {
        UUID articleId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        stubCodeGeneration();
        stubArticleSave(articleId);
        List<KnowledgeArticleLink> savedLinks = new ArrayList<>();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest(repairRequestId, equipmentId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId, equipmentId, repairRequestId)));
        when(linkRepository.save(any(KnowledgeArticleLink.class))).thenAnswer(invocation -> {
            KnowledgeArticleLink link = invocation.getArgument(0);
            link.setId(UUID.randomUUID());
            savedLinks.add(link);
            return link;
        });
        when(linkRepository.findAllByKnowledgeArticleIdAndIsDeletedFalseOrderByUpdatedAtDesc(articleId))
                .thenAnswer(invocation -> savedLinks);

        var created = service.create(new KnowledgeArticleRequest(
                "Ignored client code",
                "Pump start procedure",
                "PROCEDURE",
                null,
                null,
                null,
                null,
                "Operator cannot start the machine",
                "Power switch position is unclear",
                "Press the left-side enable button before start",
                "Add a label near the button",
                List.of("operator", "start"),
                null,
                List.of(
                        new KnowledgeArticleLinkDto(null, KnowledgeTargetType.EQUIPMENT, equipmentId),
                        new KnowledgeArticleLinkDto(null, KnowledgeTargetType.REPAIR_REQUEST, repairRequestId),
                        new KnowledgeArticleLinkDto(null, KnowledgeTargetType.WORK_ORDER, workOrderId)
                )
        ));

        assertThat(created.links()).extracting(KnowledgeArticleLinkDto::targetType)
                .containsExactlyInAnyOrder(
                        KnowledgeTargetType.EQUIPMENT,
                        KnowledgeTargetType.REPAIR_REQUEST,
                        KnowledgeTargetType.WORK_ORDER);
        verify(linkRepository, times(3)).save(any(KnowledgeArticleLink.class));
    }

    @Test
    void contextReturnsDirectLinksAndExcludesThemFromSuggestions() {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID linkedArticleId = UUID.randomUUID();
        UUID suggestedArticleId = UUID.randomUUID();

        KnowledgeArticle linked = article(linkedArticleId, "Linked procedure", equipmentId, equipmentTypeId, "start button");
        KnowledgeArticle suggested = article(suggestedArticleId, "Similar start lesson", null, equipmentTypeId, "start button left");
        KnowledgeArticleLink directLink = link(linkedArticleId, KnowledgeTargetType.EQUIPMENT, equipmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, equipmentTypeId)));
        when(linkRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalseOrderByUpdatedAtDesc(KnowledgeTargetType.EQUIPMENT, equipmentId))
                .thenReturn(List.of(directLink));
        when(repository.findAllByIdInAndIsDeletedFalse(List.of(linkedArticleId))).thenReturn(List.of(linked));
        when(repository.search(any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(linked, suggested), PageRequest.of(0, 200), 2));
        when(linkRepository.findAllByKnowledgeArticleIdInAndIsDeletedFalse(List.of(linkedArticleId, suggestedArticleId)))
                .thenReturn(List.of(directLink));

        KnowledgeContextResponse response = service.context(KnowledgeTargetType.EQUIPMENT, equipmentId, 5);

        assertThat(response.linked()).extracting("id").containsExactly(linkedArticleId);
        assertThat(response.suggestions()).extracting(s -> s.article().id()).containsExactly(suggestedArticleId);
        assertThat(response.suggestions().getFirst().matchReasons()).contains("Same equipment type");
    }

    private void stubCodeGeneration() {
        int year = Year.now().getValue();
        String expectedCode = "LL-" + year + "-0001";
        when(repository.maxSequenceByCodePrefix("LL-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
    }

    private void stubArticleSave(UUID id) {
        when(repository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
            KnowledgeArticle article = invocation.getArgument(0);
            article.setId(id);
            return article;
        });
    }

    private KnowledgeArticle article(UUID id, String title, UUID equipmentId, UUID equipmentTypeId, String text) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(id);
        article.setCode("LL-2026-" + id.toString().substring(0, 4));
        article.setTitle(title);
        article.setKind("LESSON_LEARNED");
        article.setEquipmentId(equipmentId);
        article.setEquipmentTypeId(equipmentTypeId);
        article.setProblem(text);
        article.setRootCause("Known operator flow");
        article.setSolution("Use the left-side enable button");
        article.setTags(List.of("start", "button"));
        return article;
    }

    private KnowledgeArticleLink link(UUID articleId, KnowledgeTargetType targetType, UUID targetId) {
        KnowledgeArticleLink link = new KnowledgeArticleLink();
        link.setId(UUID.randomUUID());
        link.setKnowledgeArticleId(articleId);
        link.setTargetType(targetType);
        link.setTargetId(targetId);
        return link;
    }

    private Equipment equipment(UUID id) {
        return equipment(id, UUID.randomUUID());
    }

    private Equipment equipment(UUID id, UUID equipmentTypeId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-1");
        equipment.setName("Machine");
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setDepartmentId(UUID.randomUUID());
        return equipment;
    }

    private RepairRequest repairRequest(UUID id, UUID equipmentId) {
        RepairRequest request = new RepairRequest();
        request.setId(id);
        request.setNumber("RR-1");
        request.setTitle("Machine does not start");
        request.setDescription("Operator cannot start the machine");
        request.setEquipmentId(equipmentId);
        request.setDepartmentId(UUID.randomUUID());
        request.setReporterId(UUID.randomUUID());
        return request;
    }

    private WorkOrder workOrder(UUID id, UUID equipmentId, UUID repairRequestId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Check start button");
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setRepairRequestId(repairRequestId);
        return workOrder;
    }
}
