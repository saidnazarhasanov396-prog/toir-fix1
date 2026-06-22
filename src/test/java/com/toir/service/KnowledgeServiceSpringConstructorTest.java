package com.toir.service;

import com.toir.repository.KnowledgeArticleLinkRepository;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeServiceSpringConstructorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(KnowledgeArticleRepository.class, () -> mock(KnowledgeArticleRepository.class))
            .withBean(KnowledgeArticleLinkRepository.class, () -> mock(KnowledgeArticleLinkRepository.class))
            .withBean(EquipmentRepository.class, () -> mock(EquipmentRepository.class))
            .withBean(DefectRepository.class, () -> mock(DefectRepository.class))
            .withBean(WorkOrderRepository.class, () -> mock(WorkOrderRepository.class))
            .withBean(RepairRequestRepository.class, () -> mock(RepairRequestRepository.class))
            .withBean(ScopeAccessService.class, () -> mock(ScopeAccessService.class))
            .withBean(KnowledgeService.class);

    @Test
    void knowledgeServiceBeanStartsWithProductionConstructorSelection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KnowledgeService.class);
        });
    }
}
