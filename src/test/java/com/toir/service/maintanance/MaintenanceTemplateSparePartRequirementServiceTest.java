package com.toir.service.maintanance;

import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTemplateSparePartRequirementServiceTest {

    @Mock
    private MaintenanceTemplateSparePartRequirementRepository repository;
    @Mock
    private MaintenanceTemplateRepository templateRepository;
    @Mock
    private MaintenanceOperationRepository operationRepository;
    @Mock
    private MaintenanceActionRepository actionRepository;
    @Mock
    private SparePartRepository sparePartRepository;

    @InjectMocks
    private MaintenanceTemplateSparePartRequirementService service;

    @Test
    void createValidRequirementReturnsDto() {
        UUID templateId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        MaintenanceOperation operation = operation(operationId, template);
        SparePart sparePart = sparePart(sparePartId);
        MaintenanceTemplateSparePartRequirement saved = requirement(UUID.randomUUID(), template, operation, sparePart, 2.5);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(operationRepository.findByIdAndIsDeletedFalse(operationId)).thenReturn(Optional.of(operation));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.existsActiveByTemplateOperationAndSparePart(templateId, operationId, sparePartId, null))
                .thenReturn(false);
        when(repository.save(any(MaintenanceTemplateSparePartRequirement.class))).thenReturn(saved);

        var dto = service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                operationId,
                sparePartId,
                2.5,
                "pcs",
                "CRITICAL",
                "keep ready",
                true
        ));

        assertThat(dto.templateId()).isEqualTo(templateId);
        assertThat(dto.operationId()).isEqualTo(operationId);
        assertThat(dto.operationName()).isEqualTo("Inspect bearings");
        assertThat(dto.sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.sparePartCode()).isEqualTo("BRG-001");
        assertThat(dto.sparePartName()).isEqualTo("Bearing");
        assertThat(dto.quantity()).isEqualTo(2.5);
        assertThat(dto.unit()).isEqualTo("pcs");
        assertThat(dto.criticality()).isEqualTo("CRITICAL");
        assertThat(dto.active()).isTrue();
    }

    @Test
    void createRejectsNonPositiveQuantity() {
        UUID templateId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                UUID.randomUUID(),
                0,
                "pcs",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("quantity must be positive");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateRequirement() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId)));
        when(repository.existsActiveByTemplateOperationAndSparePart(templateId, null, sparePartId, null))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                1,
                "pcs",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsOperationFromAnotherTemplate() {
        UUID templateId = UUID.randomUUID();
        UUID otherTemplateId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template(templateId)));
        when(operationRepository.findByIdAndIsDeletedFalse(operationId))
                .thenReturn(Optional.of(operation(operationId, template(otherTemplateId))));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId)));

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                operationId,
                sparePartId,
                1,
                "pcs",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("operation belongs to another template");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsMaintenanceActionIdSubmittedAsOperationId() {
        UUID templateId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template(templateId)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId)));
        when(operationRepository.findByIdAndIsDeletedFalse(actionId)).thenReturn(Optional.empty());
        when(actionRepository.findByIdAndIsDeletedFalse(actionId)).thenReturn(Optional.of(action(actionId)));

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                actionId,
                sparePartId,
                1,
                "pcs",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("operationId must reference a maintenance operation, not a maintenance action");

        verify(repository, never()).save(any());
    }

    @Test
    void createDefaultsBlankUnitToSparePartUnit() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        SparePart sparePart = sparePart(sparePartId);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.existsActiveByTemplateOperationAndSparePart(templateId, null, sparePartId, null))
                .thenReturn(false);
        when(repository.save(any(MaintenanceTemplateSparePartRequirement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                1,
                " ",
                null,
                null,
                true
        ));

        assertThat(dto.unit()).isEqualTo("pcs");
    }

    @Test
    void createRejectsUnitDifferentFromSparePartUnit() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        SparePart sparePart = sparePart(sparePartId);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                1,
                "kg",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("unit must match spare part unit");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsUnitWithDifferentCodeCaseFromSparePartUnit() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        SparePart sparePart = sparePart(sparePartId);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                1,
                "PCS",
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("unit must match spare part unit");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsInvalidSparePartId() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(templateId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                1,
                null,
                null,
                null,
                true
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Spare part not found");

        verify(repository, never()).save(any());
    }

    @Test
    void updateQuantityDoesNotCorruptUnit() {
        UUID templateId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        SparePart sparePart = sparePart(sparePartId);
        MaintenanceTemplateSparePartRequirement entity = requirement(
                requirementId,
                template,
                null,
                sparePart,
                1
        );

        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(repository.findByIdAndTemplateIdAndIsDeletedFalse(requirementId, templateId))
                .thenReturn(Optional.of(entity));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.existsActiveByTemplateOperationAndSparePart(templateId, null, sparePartId, requirementId))
                .thenReturn(false);
        when(repository.save(any(MaintenanceTemplateSparePartRequirement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.update(templateId, requirementId, new MaintenanceTemplateSparePartRequirementRequest(
                null,
                sparePartId,
                3,
                null,
                "CRITICAL",
                "updated qty",
                true
        ));

        assertThat(dto.quantity()).isEqualTo(3);
        assertThat(dto.unit()).isEqualTo("pcs");
        assertThat(entity.getUnit()).isEqualTo("pcs");
    }

    @Test
    void deleteDeactivatesRequirement() {
        UUID templateId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId);
        MaintenanceTemplateSparePartRequirement entity = requirement(id, template, null, sparePart(UUID.randomUUID()), 1);

        when(repository.findByIdAndTemplateIdAndIsDeletedFalse(id, templateId)).thenReturn(Optional.of(entity));

        service.delete(templateId, id);

        assertThat(entity.isActive()).isFalse();
        verify(repository).save(entity);
    }

    private MaintenanceTemplate template(UUID id) {
        MaintenanceTemplate template = new MaintenanceTemplate();
        template.setId(id);
        template.setCode("MT-1");
        template.setName("Template");
        return template;
    }

    private MaintenanceOperation operation(UUID id, MaintenanceTemplate template) {
        MaintenanceOperation operation = new MaintenanceOperation();
        operation.setId(id);
        operation.setTemplate(template);
        operation.setName("Inspect bearings");
        operation.setSequence(1);
        return operation;
    }

    private MaintenanceAction action(UUID id) {
        MaintenanceAction action = new MaintenanceAction();
        action.setId(id);
        action.setCode("ACT-1");
        action.setName("Inspect bearings");
        action.setActive(true);
        return action;
    }

    private SparePart sparePart(UUID id) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("BRG-001");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");
        return sparePart;
    }

    private MaintenanceTemplateSparePartRequirement requirement(
            UUID id,
            MaintenanceTemplate template,
            MaintenanceOperation operation,
            SparePart sparePart,
            double quantity
    ) {
        MaintenanceTemplateSparePartRequirement requirement = new MaintenanceTemplateSparePartRequirement();
        requirement.setId(id);
        requirement.setTemplate(template);
        requirement.setTemplateId(template.getId());
        requirement.setOperation(operation);
        requirement.setOperationId(operation == null ? null : operation.getId());
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePart.getId());
        requirement.setQuantity(quantity);
        requirement.setUnit(sparePart.getUnit());
        requirement.setCriticality("CRITICAL");
        requirement.setNotes("keep ready");
        requirement.setActive(true);
        return requirement;
    }
}
