package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.MaintenanceKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MaintenanceTemplateRepositoryStatsTest {

    @Autowired
    private MaintenanceTemplateRepository repository;

    @Autowired
    private MaintenanceOperationRepository operationRepository;

    @Test
    void getTemplateStatsWithNoFiltersCountsAllTemplatesAndOperations() {
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceTemplate t1 = saveTemplate("T1", "Template One", equipmentTypeId, MaintenanceKind.PREVENTIVE);
        MaintenanceTemplate t2 = saveTemplate("T2", "Template Two", equipmentTypeId, MaintenanceKind.PREDICTIVE);
        
        saveOperation(t1, "Op 1", 1);
        saveOperation(t1, "Op 2", 2);
        saveOperation(t2, "Op 3", 1);

        MaintenanceTemplateStatsProjection stats = repository.getTemplateStats(null, null);

        assertThat(stats.getTotalTemplates()).isEqualTo(2);
        assertThat(stats.getWithOperations()).isEqualTo(2);
        assertThat(stats.getTotalOperations()).isEqualTo(3);
        assertThat(stats.getAvgOperationsPerTemplate()).isEqualTo(1.5);
    }

    @Test
    void getTemplateStatsWithKindFilterCountsOnlyThatKind() {
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceTemplate t1 = saveTemplate("T1", "Template One", equipmentTypeId, MaintenanceKind.PREVENTIVE);
        saveTemplate("T2", "Template Two", equipmentTypeId, MaintenanceKind.PREDICTIVE);

        saveOperation(t1, "Op 1", 1);

        MaintenanceTemplateStatsProjection stats = repository.getTemplateStats(null, MaintenanceKind.PREVENTIVE.name());

        assertThat(stats.getTotalTemplates()).isEqualTo(1);
        assertThat(stats.getWithOperations()).isEqualTo(1);
        assertThat(stats.getTotalOperations()).isEqualTo(1);
        assertThat(stats.getAvgOperationsPerTemplate()).isEqualTo(1.0);
    }

    @Test
    void getTemplateStatsExcludesDeletedEntities() {
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceTemplate t1 = saveTemplate("T1", "Template One", equipmentTypeId, MaintenanceKind.PREVENTIVE);
        MaintenanceTemplate t2 = saveTemplate("T2", "Template Two", equipmentTypeId, MaintenanceKind.PREVENTIVE);
        
        t2.setDeleted(true);
        repository.save(t2);

        saveOperation(t1, "Op 1", 1);

        MaintenanceTemplateStatsProjection stats = repository.getTemplateStats(null, MaintenanceKind.PREVENTIVE.name());

        assertThat(stats.getTotalTemplates()).isEqualTo(1);
        assertThat(stats.getWithOperations()).isEqualTo(1);
        assertThat(stats.getTotalOperations()).isEqualTo(1);
    }

    private MaintenanceTemplate saveTemplate(String code, String name, UUID equipmentTypeId, MaintenanceKind kind) {
        MaintenanceTemplate t = new MaintenanceTemplate();
        t.setCode(code);
        t.setName(name);
        t.setEquipmentTypeId(equipmentTypeId);
        t.setMaintenanceKind(kind);
        t.setNormativeLaborHours(2.5);
        t.setActive(true);
        t.setDeleted(false);
        return repository.save(t);
    }

    private void saveOperation(MaintenanceTemplate template, String name, int sequence) {
        MaintenanceOperation op = new MaintenanceOperation();
        op.setTemplate(template);
        op.setName(name);
        op.setSequence(sequence);
        op.setDurationHours(1.0);
        op.setDeleted(false);
        operationRepository.save(op);
    }
}
